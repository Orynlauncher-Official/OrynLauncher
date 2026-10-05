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
        void onStateChanged(OrynDownloadState state);
    }

    private static final String TAG = "OrynDownload";
    private static final int PAGE_SIZE = 20;

    private final OrynContentRepository repository;
    private final OrynDownloadManager downloadManager;
    private final OrynInstallationManager installationManager;
    private final ExecutorService worker = Executors.newCachedThreadPool();

    private OrynDownloadState state;
    private Observer observer;
    private int searchGeneration;
    private boolean closed;

    public OrynDownloadViewModel(Instance selectedInstance) {
        repository = new OrynContentRepository();
        downloadManager = new OrynDownloadManager();
        installationManager = new OrynInstallationManager(new InstalledProjectStore());
        state = OrynDownloadState.initial(selectedInstance);
    }

    public synchronized void observe(Observer observer) {
        this.observer = observer;
        if (observer != null) observer.onStateChanged(state);
    }

    public synchronized OrynDownloadState getState() {
        return state;
    }

    public void setInstance(Instance instance) {
        synchronized (this) {
            if (closed) return;
            state = state.withSelectedInstance(instance);
        }
        publish();
    }

    public void setFilters(OrynDownloadState.Category category, String version, String loader, String query) {
        synchronized (this) {
            if (closed) return;
            searchGeneration++;
            state = state.withFilters(category, version, loader, query);
        }
        publish();
        search(false);
    }

    public void loadMore() {
        OrynDownloadState snapshot;
        synchronized (this) {
            if (closed || state.listStatus == OrynDownloadState.ListStatus.LOADING
                    || !state.hasMore || state.projects.isEmpty()) return;
            snapshot = state;
        }
        search(true);
    }

    private void search(final boolean append) {
        if (append) {
            synchronized (this) {
                if (closed) return;
                state = state.withListLoading("Loading more projects…");
            }
            publish();
        }
        final int generation;
        final OrynDownloadState snapshot;
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
                            if (state.projects.isEmpty()) {
                                state = state.withListError(message);
                            } else {
                                state = state.withProjects(
                                        state.projects, state.nextOffset, state.totalHits,
                                        state.hasMore, "Could not load more projects: " + message);
                            }
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
            if (state.selectedVersion != null && version != null
                    && state.selectedVersion.id.equals(version.id)) return;
            state = state.withSelectedVersion(version);
        }
        publish();
    }

    public void installSelected() {
        final OrynDownloadState snapshot;
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

                // InstallationManager performs destination and file verification.
                // Modpacks intentionally create and select a NEW instance, so never verify
                // them against the instance that was selected before the download started.
                Instance verifiedInstance = snapshot.selectedInstance;
                if ("modpack".equals(snapshot.category.projectType)) {
                    verifiedInstance = net.kdt.pojavlaunch.instances.Instances.loadSelectedInstance();
                    if (verifiedInstance == null
                            || !installationManager.isInstalled(verifiedInstance,
                            snapshot.selectedProject, snapshot.category.projectType)) {
                        throw new Exception("Modpack installed but the new instance is not visible");
                    }
                    synchronized (OrynDownloadViewModel.this) {
                        if (!closed) state = state.withSelectedInstance(verifiedInstance);
                    }
                    publish();
                } else if (!installationManager.isInstalled(snapshot.selectedInstance,
                        snapshot.selectedProject, snapshot.category.projectType)) {
                    throw new Exception("Installation completed but the content is not visible in the selected instance");
                }
                Log.d(TAG, "[ORYN-DOWNLOAD] type=" + snapshot.category.projectType
                        + " project=" + snapshot.selectedProject.id
                        + " version=" + snapshot.selectedVersion.id
                        + " instance=" + verifiedInstance.getGameDirectory().getAbsolutePath()
                        + " installed=true fileExists=true");

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
        OrynDownloadState snapshot;
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
