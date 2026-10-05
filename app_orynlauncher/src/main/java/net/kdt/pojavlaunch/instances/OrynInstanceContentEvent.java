package net.kdt.pojavlaunch.instances;

public final class OrynInstanceContentEvent {
    public final String instanceId;
    public final String contentType;
    public final String file;
    public final String projectId;
    public final String versionId;

    public OrynInstanceContentEvent(String instanceId, String contentType, String file,
                                    String projectId, String versionId) {
        this.instanceId = instanceId;
        this.contentType = contentType;
        this.file = file;
        this.projectId = projectId;
        this.versionId = versionId;
    }
}
