package net.kdt.pojavlaunch.download;

import java.util.ArrayList;
import java.util.List;

public final class ModrinthVersion {
    public final String id;
    public final String name;
    public final String versionNumber;
    public final boolean featured;
    public final List<String> gameVersions;
    public final List<String> loaders;
    public final List<ModrinthFile> files;

    public ModrinthVersion(String id, String name, String versionNumber, boolean featured,
                           List<String> gameVersions, List<String> loaders,
                           List<ModrinthFile> files) {
        this.id = id;
        this.name = name;
        this.versionNumber = versionNumber;
        this.featured = featured;
        this.gameVersions = gameVersions == null ? new ArrayList<String>() : gameVersions;
        this.loaders = loaders == null ? new ArrayList<String>() : loaders;
        this.files = files == null ? new ArrayList<ModrinthFile>() : files;
    }
}
