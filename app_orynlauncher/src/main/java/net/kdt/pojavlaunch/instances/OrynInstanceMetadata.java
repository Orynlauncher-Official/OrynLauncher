package net.kdt.pojavlaunch.instances;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OrynLauncher per-instance launch metadata.
 *
 * This object is persisted inside the instance metadata file and deliberately
 * contains no global launcher state. It describes only this instance.
 */
public class OrynInstanceMetadata {
    public String id;
    public String minecraftVersion;
    public String loaderType;
    public String loaderVersion;

    public boolean isModpack;
    public String modpackName;
    public String modpackVersion;
    public String modpackProjectId;

    public String javaRuntime;
    public int memoryMin = -1;
    public int memoryMax = -1;

    public long createdAt;
    public long lastPlayedAt;

    /**
     * Extensible per-instance launch settings. Existing Instance fields remain
     * the compatibility layer for the current launcher UI.
     */
    public Map<String, String> launchConfiguration = new LinkedHashMap<>();

    /**
     * IDs/categories of content installed into this instance.
     */
    public Map<String, String> installedContent = new LinkedHashMap<>();

    public void sanitize() {
        if (launchConfiguration == null) launchConfiguration = new LinkedHashMap<>();
        if (installedContent == null) installedContent = new LinkedHashMap<>();
    }
}
