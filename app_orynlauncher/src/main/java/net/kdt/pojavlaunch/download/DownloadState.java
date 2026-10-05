package net.kdt.pojavlaunch.download;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class DownloadState {
    public enum Status {
        IDLE, SEARCHING, CHECKING, DOWNLOADING, INSTALLING, INSTALLED, FAILED
    }

    /*
     * Single source of truth for the Download screen.
     * The project UI must render exactly this list and the visible count must
     * always be projects.size().
     */
    public final List<ModrinthProject> projects;
    public final int offset;
    public final int totalHits;
    public final boolean hasMore;

    public final Status status;
    public final int progress;
    public final String message;

    private DownloadState(List<ModrinthProject> projects, int offset, int totalHits,
                          boolean hasMore, Status status, int progress, String message) {
        this.projects = projects == null
                ? Collections.<ModrinthProject>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(projects));
        this.offset = offset;
        this.totalHits = totalHits;
        this.hasMore = hasMore;
        this.status = status;
        this.progress = progress;
        this.message = message == null ? "" : message;
    }

    public static DownloadState idle() {
        return new DownloadState(Collections.<ModrinthProject>emptyList(), 0, 0, false,
                Status.IDLE, 0, "");
    }

    public static DownloadState searching(List<ModrinthProject> projects, int offset,
                                          int totalHits, boolean hasMore) {
        return new DownloadState(projects, offset, totalHits, hasMore,
                Status.SEARCHING, 0, "Loading Modrinth…");
    }

    public static DownloadState projects(List<ModrinthProject> projects, int offset,
                                         int totalHits, boolean hasMore) {
        return new DownloadState(projects, offset, totalHits, hasMore,
                Status.IDLE, 0, "");
    }

    public static DownloadState checking() {
        return new DownloadState(Collections.<ModrinthProject>emptyList(), 0, 0, false,
                Status.CHECKING, 0, "Checking compatibility…");
    }

    public static DownloadState downloading(int progress) {
        return new DownloadState(Collections.<ModrinthProject>emptyList(), 0, 0, false,
                Status.DOWNLOADING, progress, "Downloading…");
    }

    public static DownloadState installing() {
        return new DownloadState(Collections.<ModrinthProject>emptyList(), 0, 0, false,
                Status.INSTALLING, 100, "Installing…");
    }

    public static DownloadState installed() {
        return new DownloadState(Collections.<ModrinthProject>emptyList(), 0, 0, false,
                Status.INSTALLED, 100, "Installed successfully");
    }

    public static DownloadState failed(String message) {
        return new DownloadState(Collections.<ModrinthProject>emptyList(), 0, 0, false,
                Status.FAILED, 0, message);
    }
}
