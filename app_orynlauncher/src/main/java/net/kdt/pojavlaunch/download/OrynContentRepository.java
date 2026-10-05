package net.kdt.pojavlaunch.download;

import android.graphics.Bitmap;

import java.util.List;

public final class OrynContentRepository {
    public interface Listener<T> {
        void onSuccess(T value);
        void onError(Exception error);
    }

    private final ModrinthRemoteRepository delegate;

    public OrynContentRepository() {
        delegate = new ModrinthRemoteRepository();
    }

    public void search(String query, String projectType, String minecraftVersion, String loader,
                       int offset, final Listener<ModrinthSearchResult> listener) {
        delegate.searchAsync(query, projectType, minecraftVersion, loader, offset,
                new ModrinthRemoteRepository.SearchCallback() {
                    @Override public void onLoading() { }
                    @Override public void onSuccess(ModrinthSearchResult result, boolean append) {
                        listener.onSuccess(result);
                    }
                    @Override public void onError(Exception error) {
                        listener.onError(error);
                    }
                });
    }

    public void getProjectDetails(final ModrinthProject project, String projectType,
                                  String minecraftVersion, String loader,
                                  final Listener<ProjectDetails> listener) {
        delegate.loadProjectDetailsAsync(project, projectType, minecraftVersion, loader,
                new ModrinthRemoteRepository.DetailsCallback() {
                    @Override public void onLoading() { }
                    @Override public void onSuccess(ModrinthProject fullProject,
                                                    List<ModrinthVersion> compatibleVersions) {
                        listener.onSuccess(new ProjectDetails(fullProject, compatibleVersions));
                    }
                    @Override public void onError(Exception error) {
                        listener.onError(error);
                    }
                });
    }

    public void loadGameVersions(final Listener<List<String>> listener) {
        delegate.loadGameVersionsAsync(new ModrinthRemoteRepository.ValuesCallback() {
            @Override public void onSuccess(List<String> values) { listener.onSuccess(values); }
            @Override public void onError(Exception error) { listener.onError(error); }
        });
    }

    public void loadLoaders(final Listener<List<String>> listener) {
        delegate.loadLoadersAsync(new ModrinthRemoteRepository.ValuesCallback() {
            @Override public void onSuccess(List<String> values) { listener.onSuccess(values); }
            @Override public void onError(Exception error) { listener.onError(error); }
        });
    }

    public void loadIcon(String url, final Listener<Bitmap> listener) {
        delegate.loadIconAsync(url, new ModrinthRemoteRepository.IconCallback() {
            @Override public void onSuccess(Bitmap bitmap) { listener.onSuccess(bitmap); }
            @Override public void onError() { listener.onError(new Exception("Icon unavailable")); }
        });
    }

    public ModrinthFile selectFile(ModrinthVersion version, String projectType) {
        return delegate.selectFile(version, projectType);
    }

    public boolean isCompatible(ModrinthVersion version, String projectType,
                                 String minecraftVersion, String loader) {
        return delegate.isCompatible(version, projectType, minecraftVersion, loader);
    }

    public void shutdown() {
        delegate.shutdown();
    }

    public static final class ProjectDetails {
        public final ModrinthProject project;
        public final List<ModrinthVersion> compatibleVersions;

        public ProjectDetails(ModrinthProject project, List<ModrinthVersion> compatibleVersions) {
            this.project = project;
            this.compatibleVersions = compatibleVersions;
        }
    }
}
