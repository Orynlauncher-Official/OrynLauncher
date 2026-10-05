package net.kdt.pojavlaunch.download;

import java.util.ArrayList;
import java.util.List;

public final class ModrinthSearchResult {
    public final List<ModrinthProject> projects;
    public final int offset;
    public final int totalHits;

    public ModrinthSearchResult(List<ModrinthProject> projects, int offset, int totalHits) {
        this.projects = projects == null ? new ArrayList<ModrinthProject>() : projects;
        this.offset = offset;
        this.totalHits = totalHits;
    }

    public boolean hasMore() {
        return offset + projects.size() < totalHits;
    }
}
