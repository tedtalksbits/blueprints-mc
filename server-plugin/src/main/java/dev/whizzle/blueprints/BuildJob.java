package dev.whizzle.blueprints;

import org.bukkit.Location;

import java.util.UUID;

public final class BuildJob {
    public enum State { BUILDING, PAUSED_MATERIALS, PAUSED_CHUNK, PAUSED_OBSTRUCTION, COMPLETE, CANCELLED }

    private final UUID id;
    private final UUID owner;
    private final String blueprintId;
    private final String world;
    private final int originX, originY, originZ;
    private final int chestX, chestY, chestZ;
    private int nextBlock;
    private State state;
    private String pauseReason;

    public BuildJob(UUID id, UUID owner, String blueprintId, String world,
                    int originX, int originY, int originZ,
                    int chestX, int chestY, int chestZ,
                    int nextBlock, State state, String pauseReason) {
        this.id = id;
        this.owner = owner;
        this.blueprintId = blueprintId;
        this.world = world;
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
        this.chestX = chestX;
        this.chestY = chestY;
        this.chestZ = chestZ;
        this.nextBlock = nextBlock;
        this.state = state;
        this.pauseReason = pauseReason == null ? "" : pauseReason;
    }

    public UUID id() { return id; }
    public UUID owner() { return owner; }
    public String blueprintId() { return blueprintId; }
    public String world() { return world; }
    public int originX() { return originX; }
    public int originY() { return originY; }
    public int originZ() { return originZ; }
    public int chestX() { return chestX; }
    public int chestY() { return chestY; }
    public int chestZ() { return chestZ; }
    public int nextBlock() { return nextBlock; }
    public State state() { return state; }
    public String pauseReason() { return pauseReason; }

    public void advance() { nextBlock++; state = State.BUILDING; pauseReason = ""; }
    public void state(State state, String reason) { this.state = state; this.pauseReason = reason == null ? "" : reason; }
    public void cancel() { state(State.CANCELLED, "Cancelled"); }
}
