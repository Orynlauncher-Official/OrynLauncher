package net.kdt.pojavlaunch.download;

public final class OrynInstallState {
    public enum Status { IDLE, CHECKING, DOWNLOADING, INSTALLING, INSTALLED, FAILED }

    public final Status status;
    public final int progress;
    public final String message;

    private OrynInstallState(Status status, int progress, String message) {
        this.status = status;
        this.progress = progress;
        this.message = message;
    }

    public static OrynInstallState idle() {
        return new OrynInstallState(Status.IDLE, 0, "");
    }

    public static OrynInstallState checking() {
        return new OrynInstallState(Status.CHECKING, 0, "Checking");
    }

    public static OrynInstallState downloading(int progress) {
        return new OrynInstallState(Status.DOWNLOADING, progress, "Downloading");
    }

    public static OrynInstallState installing() {
        return new OrynInstallState(Status.INSTALLING, 0, "Installing");
    }

    public static OrynInstallState installed() {
        return new OrynInstallState(Status.INSTALLED, 100, "Installed");
    }

    public static OrynInstallState failed(String message) {
        return new OrynInstallState(Status.FAILED, 0,
                message == null || message.isEmpty() ? "Download failed" : message);
    }
}
