package net.kdt.pojavlaunch.download;

public final class ModrinthFile {
    public final String url;
    public final String filename;
    public final String sha1;
    public final long size;
    public final boolean primary;

    public ModrinthFile(String url, String filename, String sha1, long size, boolean primary) {
        this.url = url;
        this.filename = filename;
        this.sha1 = sha1;
        this.size = size;
        this.primary = primary;
    }
}
