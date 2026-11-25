package dev.jxauctions.model;

import java.util.UUID;

public class LogEntry {

    private final int id;
    private final UUID actorUuid;
    private final String actorName;
    private final UUID targetUuid;
    private final String targetName;
    private final String action;
    private final String details;
    private final long timestamp;

    public LogEntry(int id, UUID actorUuid, String actorName, UUID targetUuid, String targetName,
                    String action, String details, long timestamp) {
        this.id = id;
        this.actorUuid = actorUuid;
        this.actorName = actorName;
        this.targetUuid = targetUuid;
        this.targetName = targetName;
        this.action = action;
        this.details = details;
        this.timestamp = timestamp;
    }

    public int getId() { return id; }
    public UUID getActorUuid() { return actorUuid; }
    public String getActorName() { return actorName; }
    public UUID getTargetUuid() { return targetUuid; }
    public String getTargetName() { return targetName; }
    public String getAction() { return action; }
    public String getDetails() { return details; }
    public long getTimestamp() { return timestamp; }
}
