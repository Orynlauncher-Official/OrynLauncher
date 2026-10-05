package net.kdt.pojavlaunch.download;

import java.util.ArrayList;
import java.util.List;

public final class ModrinthProject {
    public final String id;
    public final String title;
    public final String author;
    public final String description;
    public final String iconUrl;
    public final String projectType;
    public final long downloads;
    public final long followers;
    public final List<String> categories;
    public final List<String> gameVersions;
    public final List<String> loaders;

    public ModrinthProject(String id, String title, String author, String description,
                           String iconUrl, String projectType, long downloads, long followers,
                           List<String> categories, List<String> gameVersions, List<String> loaders) {
        this.id = id;
        this.title = title;
        this.author = author;
        this.description = description;
        this.iconUrl = iconUrl;
        this.projectType = projectType;
        this.downloads = downloads;
        this.followers = followers;
        this.categories = categories == null ? new ArrayList<String>() : categories;
        this.gameVersions = gameVersions == null ? new ArrayList<String>() : gameVersions;
        this.loaders = loaders == null ? new ArrayList<String>() : loaders;
    }
}
