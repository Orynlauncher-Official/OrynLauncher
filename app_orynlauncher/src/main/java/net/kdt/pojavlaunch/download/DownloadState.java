package net.kdt.pojavlaunch.download;

public final class DownloadState {
    public enum Status { IDLE, CHECKING, DOWNLOADING, INSTALLING, INSTALLED, FAILED }

    public final Status status;
    public final int progress;
    public final String message;

    private DownloadState(Status status, int progress, String message) {
        this.status = status;
        this.progress = progress;
        this.message = message;
    }

    public static DownloadState idle() { return new DownloadState(Status.IDLE, 0, ""); }
    public static DownloadState checking() { return new DownloadState(Status.CHECKING, 0, "Checking compatibility…"); }
    public static DownloadState downloading(int progress) { return new DownloadState(Status.DOWNLOADING, progress, "Downloading…"); }
    public static DownloadState installing() { return new DownloadState(Status.INSTALLING, 100, "Installing…"); }
    public static DownloadState installed() { return new DownloadState(Status.INSTALLED, 100, "Installed successfully"); }
    public static DownloadState failed(String message) { return new DownloadState(Status.FAILED, 0, message); }
}
