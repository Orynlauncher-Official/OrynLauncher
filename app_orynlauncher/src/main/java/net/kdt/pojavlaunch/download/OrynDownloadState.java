package net.kdt.pojavlaunch.download;

import net.kdt.pojavlaunch.instances.Instance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class OrynDownloadState {
    public enum Category {
        MOD("Mods", "mod"),
        RESOURCEPACK("Resource Packs", "resourcepack"),
        SHADER("Shaders", "shader"),
        MODPACK("Modpacks", "modpack");

        public final String title;
        public final String projectType;

        Category(String title, String projectType) {
            this.title = title;
            this.projectType = projectType;
        }

        public boolean usesLoader() {
            return this == MOD || this == MODPACK;
        }
    }

    public enum ListStatus { IDLE, LOADING, READY, ERROR }
    public enum DetailStatus { IDLE, LOADING, READY, ERROR }

    public final Category category;
    public final String minecraftVersion;
    public final String loader;
    public final String query;
    public final Instance selectedInstance;
    public final List<ModrinthProject> projects;
    public final int nextOffset;
    public final int totalHits;
    public final boolean hasMore;
    public final ListStatus listStatus;
    public final String listMessage;
    public final ModrinthProject selectedProject;
    public final List<ModrinthVersion> compatibleVersions;
    public final ModrinthVersion selectedVersion;
    public final DetailStatus detailStatus;
    public final String detailMessage;
    public final OrynInstallState installState;

    private OrynDownloadState(Category category, String minecraftVersion, String loader,
                              String query, Instance selectedInstance,
                              List<ModrinthProject> projects, int nextOffset, int totalHits,
                              boolean hasMore, ListStatus listStatus, String listMessage,
                              ModrinthProject selectedProject,
                              List<ModrinthVersion> compatibleVersions,
                              ModrinthVersion selectedVersion,
                              DetailStatus detailStatus, String detailMessage,
                              OrynInstallState installState) {
        this.category = category;
        this.minecraftVersion = minecraftVersion;
        this.loader = loader;
        this.query = query == null ? "" : query;
        this.selectedInstance = selectedInstance;
        this.projects = immutable(projects);
        this.nextOffset = nextOffset;
        this.totalHits = totalHits;
        this.hasMore = hasMore;
        this.listStatus = listStatus;
        this.listMessage = listMessage;
        this.selectedProject = selectedProject;
        this.compatibleVersions = immutable(compatibleVersions);
        this.selectedVersion = selectedVersion;
        this.detailStatus = detailStatus;
        this.detailMessage = detailMessage;
        this.installState = installState;
    }

    public static OrynDownloadState initial(Instance instance) {
        String version = "";
        String loader = "";
        if (instance != null) {
            version = instance.minecraftVersion == null
                    ? (instance.versionId == null ? "" : instance.versionId)
                    : instance.minecraftVersion;
            loader = instance.loaderType == null
                    ? "" : instance.loaderType.toLowerCase(java.util.Locale.ROOT);
        }
        return new OrynDownloadState(
                Category.MOD, version, loader, "", instance,
                Collections.<ModrinthProject>emptyList(), 0, 0, false,
                ListStatus.IDLE, "Select a category and start browsing.",
                null, Collections.<ModrinthVersion>emptyList(), null,
                DetailStatus.IDLE, "Select a project to view details.",
                OrynInstallState.idle());
    }

    public OrynDownloadState withFilters(Category category, String minecraftVersion, String loader, String query) {
        return new OrynDownloadState(
                category, minecraftVersion, loader, query, selectedInstance,
                Collections.<ModrinthProject>emptyList(), 0, 0, false,
                ListStatus.LOADING, "Loading projects…",
                null, Collections.<ModrinthVersion>emptyList(), null,
                DetailStatus.IDLE, "Select a project to view details.",
                OrynInstallState.idle());
    }

    public OrynDownloadState withProjects(List<ModrinthProject> projects, int nextOffset,
                                          int totalHits, boolean hasMore, String message) {
        return new OrynDownloadState(
                category, minecraftVersion, loader, query, selectedInstance,
                projects, nextOffset, totalHits, hasMore,
                ListStatus.READY, message,
                selectedProject, compatibleVersions, selectedVersion,
                detailStatus, detailMessage, installState);
    }

    public OrynDownloadState withListLoading(String message) {
        return new OrynDownloadState(
                category, minecraftVersion, loader, query, selectedInstance,
                projects, nextOffset, totalHits, hasMore,
                ListStatus.LOADING, message,
                selectedProject, compatibleVersions, selectedVersion,
                detailStatus, detailMessage, installState);
    }

    public OrynDownloadState withListError(String message) {
        return new OrynDownloadState(
                category, minecraftVersion, loader, query, selectedInstance,
                projects, nextOffset, totalHits, false,
                ListStatus.ERROR, message,
                selectedProject, compatibleVersions, selectedVersion,
                detailStatus, detailMessage, installState);
    }

    public OrynDownloadState withSelectedInstance(Instance instance) {
        return new OrynDownloadState(
                category, minecraftVersion, loader, query, instance,
                projects, nextOffset, totalHits, hasMore,
                listStatus, listMessage,
                selectedProject, compatibleVersions, selectedVersion,
                detailStatus, detailMessage, installState);
    }

    public OrynDownloadState withDetailsLoading(ModrinthProject project) {
        return new OrynDownloadState(
                category, minecraftVersion, loader, query, selectedInstance,
                projects, nextOffset, totalHits, hasMore,
                listStatus, listMessage,
                project, Collections.<ModrinthVersion>emptyList(), null,
                DetailStatus.LOADING, "Loading compatible versions…",
                OrynInstallState.idle());
    }

    public OrynDownloadState withDetails(ModrinthProject project, List<ModrinthVersion> versions) {
        ModrinthVersion first = versions == null || versions.isEmpty() ? null : versions.get(0);
        return new OrynDownloadState(
                category, minecraftVersion, loader, query, selectedInstance,
                projects, nextOffset, totalHits, hasMore,
                listStatus, listMessage,
                project, versions, first,
                DetailStatus.READY,
                versions == null || versions.isEmpty()
                        ? "No downloadable version matches the current filters."
                        : "Select a version, then download.",
                OrynInstallState.idle());
    }

    public OrynDownloadState withVersionContext(String minecraftVersion, String loader) {
        return new OrynDownloadState(
                category, minecraftVersion, loader, query, selectedInstance,
                projects, nextOffset, totalHits, hasMore,
                listStatus, listMessage,
                selectedProject, compatibleVersions, selectedVersion,
                detailStatus, detailMessage, installState);
    }

    public OrynDownloadState withSelectedVersion(ModrinthVersion version) {
        return new OrynDownloadState(
                category, minecraftVersion, loader, query, selectedInstance,
                projects, nextOffset, totalHits, hasMore,
                listStatus, listMessage,
                selectedProject, compatibleVersions, version,
                detailStatus, detailMessage, OrynInstallState.idle());
    }

    public OrynDownloadState withInstallState(OrynInstallState state) {
        return new OrynDownloadState(
                category, minecraftVersion, loader, query, selectedInstance,
                projects, nextOffset, totalHits, hasMore,
                listStatus, listMessage,
                selectedProject, compatibleVersions, selectedVersion,
                detailStatus, detailMessage, state);
    }

    private static <T> List<T> immutable(List<T> values) {
        if (values == null || values.isEmpty()) return Collections.emptyList();
        return Collections.unmodifiableList(new ArrayList<>(values));
    }
}
