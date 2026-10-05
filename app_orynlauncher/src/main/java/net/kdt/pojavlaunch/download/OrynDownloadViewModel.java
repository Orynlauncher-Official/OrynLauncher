package net.kdt.pojavlaunch.download;

import android.util.Log;

import net.kdt.pojavlaunch.instances.Instance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class OrynDownloadViewModel {
    public interface Observer {
        void onStateChanged(OrynOrynInstallState state);
    }

    private static final String TAG = "OrynDownload";
    private static final int PAGE_SIZE = 20;

    private final OrynContentRepository repository;
    private final OrynDownloadManager downloadManager;
    private final OrynInstallationManager installationManager;
    private final ExecutorService worker = Executors.newCachedThreadPool();

    private OrynOrynInstallState state;
    private Observer observer;
    private int searchGeneration;
    private boolean closed;

    public OrynDownloadViewModel(Instance selectedInstance) {
        repository = new OrynContentRepository();
        downloadManager = new OrynDownloadManager();
        installationManager = new OrynInstallationManager(new InstalledProjectStore());
        state = OrynOrynInstallState.initial(selectedInstance);
    }

    public synchronized void observe(Observer observer) {
        this.observer = observer;
        if (observer != null) observer.onStateChanged(state);
    }

    public synchronized OrynOrynInstallState getState() {
        return state;
    }

    public void setInstance(Instance instance) {
        synchronized (this) {
            if (closed) return;
            state = state.withSelectedInstance(instance);
        }
        publish();
    }

    public void setFilters(OrynOrynInstallState.Category category, String version, String loader, String query) {
        synchronized (this) {
            if (closed) return;
            searchGeneration++;
            state = state.withFilters(category, version, loader, query);
        }
        publish();
        search(false);
    }

    public void loadMore() {
        OrynOrynInstallState snapshot;
        synchronized (this) {
            if (closed || state.listStatus == OrynOrynInstallState.ListStatus.LOADING
                    || !state.hasMore || state.projects.isEmpty()) return;
            snapshot = state;
        }
        search(true);
    }

    private void search(final boolean append) {
        final int generation;
        final OrynOrynInstallState snapshot;
        synchronized (this) {
            generation = searchGeneration;
            snapshot = state;
        }

        final int offset = append ? snapshot.nextOffset : 0;
        if (!append) {
            synchronized (this) {
                state = state.withFilters(snapshot.category, snapshot.minecraftVersion,
                        snapshot.loader, snapshot.query);
            }
            publish();
        }

        Log.d(TAG, "Request projects type=" + snapshot.category.projectType
                + " version=" + snapshot.minecraftVersion
                + " loader=" + snapshot.loader
                + " offset=" + offset + " query=" + snapshot.query);

        repository.search(snapshot.query, snapshot.category.projectType, snapshot.minecraftVersion,
                snapshot.category.usesLoader() ? snapshot.loader : null, offset,
                new OrynContentRepository.Listener<ModrinthSearchResult>() {
                    @Override public void onSuccess(ModrinthSearchResult result) {
                        synchronized (OrynDownloadViewModel.this) {
                            if (closed || generation != searchGeneration) return;

                            List<ModrinthProject> merged = append
                                    ? new ArrayList<>(state.projects)
                                    : new ArrayList<ModrinthProject>();
                            Set<String> ids = new HashSet<>();
                            for (ModrinthProject project : merged) ids.add(project.id);
                            for (ModrinthProject project : result.projects) {
                                if (project != null && project.id != null && ids.add(project.id)) {
                                    merged.add(project);
                                }
                            }

                            boolean hasMore = result.hasMore() && !merged.isEmpty();
                            state = state.withProjects(
                                    merged,
                                    result.offset + result.projects.size(),
                                    result.totalHits,
                                    hasMore,
                                    merged.isEmpty() ? "No projects found" : merged.size() + " projects");
                            Log.d(TAG, "State projects = " + state.projects.size());
                        }
                        publish();
                    }

                    @Override public void onError(Exception error) {
                        synchronized (OrynDownloadViewModel.this) {
                            if (closed || generation != searchGeneration) return;
                            String message = error == null || error.getMessage() == null
                                    ? "Could not load Modrinth projects" : error.getMessage();
                            if (state.projects.isEmpty()) state = state.withListError(message);
                        }
                        publish();
                    }
                });
    }

    public void selectProject(final ModrinthProject project) {
        if (project == null) return;
        synchronized (this) {
            if (closed) return;
            searchGeneration++;
            state = state.withDetailsLoading(project);
        }
        publish();

        final int generation;
        synchronized (this) { generation = searchGeneration; }
        repository.getProjectDetails(project, state.category.projectType,
                state.minecraftVersion, state.category.usesLoader() ? state.loader : null,
                new OrynContentRepository.Listener<OrynContentRepository.ProjectDetails>() {
                    @Override public void onSuccess(OrynContentRepository.ProjectDetails details) {
                        synchronized (OrynDownloadViewModel.this) {
                            if (closed || generation != searchGeneration) return;
                            state = state.withDetails(details.project, details.compatibleVersions);
                        }
                        publish();
                    }

                    @Override public void onError(Exception error) {
                        synchronized (OrynDownloadViewModel.this) {
                            if (closed || generation != searchGeneration) return;
                            state = state.withDetails(
                                    project, Collections.<ModrinthVersion>emptyList());
                        }
                        publish();
                    }
                });
    }

    public void selectVersion(ModrinthVersion version) {
        synchronized (this) {
            if (closed) return;
            state = state.withSelectedVersion(version);
        }
        publish();
    }

    public void installSelected() {
        final OrynOrynInstallState snapshot;
        synchronized (this) {
            if (closed) return;
            snapshot = state;
        }

        if (snapshot.selectedInstance == null || snapshot.selectedProject == null
                || snapshot.selectedVersion == null) {
            publish();
            return;
        }

        final ModrinthFile sourceFile = repository.selectFile(
                snapshot.selectedVersion, snapshot.category.projectType);
        if (sourceFile == null) {
            synchronized (this) {
                state = state.withInstallState(
                        OrynInstallState.failed("No downloadable file for this version"));
            }
            publish();
            return;
        }

        worker.execute(() -> {
            try {
                synchronized (OrynDownloadViewModel.this) {
                    if (closed) return;
                    state = state.withInstallState(OrynInstallState.checking());
                }
                publish();

                if (installationManager.isInstalled(snapshot.selectedInstance,
                        snapshot.selectedProject, snapshot.category.projectType)) {
                    synchronized (OrynDownloadViewModel.this) {
                        state = state.withInstallState(OrynInstallState.installed());
                    }
                    publish();
                    return;
                }

                final java.io.File target = installationManager.createDownloadTarget(
                        snapshot.selectedProject, snapshot.selectedVersion,
                        snapshot.category.projectType, sourceFile);

                synchronized (OrynDownloadViewModel.this) {
                    state = state.withInstallState(OrynInstallState.downloading(0));
                }
                publish();

                java.io.File downloaded = downloadManager.download(sourceFile, target,
                        percent -> {
                            synchronized (OrynDownloadViewModel.this) {
                                if (!closed) state = state.withInstallState(
                                        OrynInstallState.downloading(percent));
                            }
                            publish();
                        });

                synchronized (OrynDownloadViewModel.this) {
                    if (closed) return;
                    state = state.withInstallState(OrynInstallState.installing());
                }
                publish();

                installationManager.install(snapshot.selectedProject, snapshot.selectedVersion,
                        snapshot.category.projectType, sourceFile, snapshot.selectedInstance, downloaded);

                synchronized (OrynDownloadViewModel.this) {
                    if (!closed) state = state.withInstallState(OrynInstallState.installed());
                }
                publish();
            } catch (Exception error) {
                synchronized (OrynDownloadViewModel.this) {
                    if (!closed) state = state.withInstallState(
                            OrynInstallState.failed(error.getMessage() == null
                                    ? "Download failed" : error.getMessage()));
                }
                publish();
            }
        });
    }

    private void publish() {
        Observer target;
        OrynOrynInstallState snapshot;
        synchronized (this) {
            target = observer;
            snapshot = state;
        }
        if (target != null && !closed) target.onStateChanged(snapshot);
    }

    public void shutdown() {
        synchronized (this) {
            closed = true;
            searchGeneration++;
            observer = null;
        }
        repository.shutdown();
        worker.shutdownNow();
    }
}
