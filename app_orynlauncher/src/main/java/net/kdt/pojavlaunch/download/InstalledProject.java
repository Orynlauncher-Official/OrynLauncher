package net.kdt.pojavlaunch.download;

public final class InstalledProject {
    public final String projectId;
    public final String category;
    public final String filename;
    public final long installedAt;

    public InstalledProject(String projectId, String category, String filename, long installedAt) {
        this.projectId = projectId;
        this.category = category;
        this.filename = filename;
        this.installedAt = installedAt;
    }
}
