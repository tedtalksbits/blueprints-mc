package dev.whizzle.blueprints;

import java.util.*;

public final class BuildJob {
    public enum State { BUILDING, PAUSED_MATERIALS, PAUSED_CHUNK, PAUSED_OBSTRUCTION, PAUSED_OWNER, RECOVERY_REQUIRED, COMPLETE, CANCELLED }
    private final UUID id, owner, world;
    private final ConstructionPlan plan;
    private final int originX, originY, originZ, chestX, chestY, chestZ;
    private int nextBlock;
    private State state;
    private String pauseReason;

    public BuildJob(UUID id, UUID owner, ConstructionPlan plan, UUID world,
                    int originX, int originY, int originZ, int chestX, int chestY, int chestZ,
                    int nextBlock, State state, String pauseReason) {
        this.id = Objects.requireNonNull(id);
        this.owner = Objects.requireNonNull(owner);
        this.plan = Objects.requireNonNull(plan);
        this.world = Objects.requireNonNull(world);
        this.originX = originX; this.originY = originY; this.originZ = originZ;
        this.chestX = chestX; this.chestY = chestY; this.chestZ = chestZ;
        if (nextBlock < 0 || nextBlock > plan.steps().size()) throw new IllegalArgumentException("Invalid progress cursor");
        this.nextBlock = nextBlock;
        this.state = Objects.requireNonNull(state);
        this.pauseReason = Objects.requireNonNullElse(pauseReason, "");
        if (state == State.COMPLETE && nextBlock != plan.steps().size()) throw new IllegalArgumentException("Incomplete completed job");
        if (nextBlock == plan.steps().size() && state != State.COMPLETE && state != State.CANCELLED)
            throw new IllegalArgumentException("Finished cursor requires a terminal state");
    }
    public UUID id() { return id; }
    public UUID owner() { return owner; }
    public UUID world() { return world; }
    public ConstructionPlan plan() { return plan; }
    public String blueprintId() { return plan.id(); }
    public int originX() { return originX; }
    public int originY() { return originY; }
    public int originZ() { return originZ; }
    public int chestX() { return chestX; }
    public int chestY() { return chestY; }
    public int chestZ() { return chestZ; }
    public int nextBlock() { return nextBlock; }
    public State state() { return state; }
    public String pauseReason() { return pauseReason; }
    public boolean terminal() { return state == State.COMPLETE || state == State.CANCELLED; }
    public boolean runnable() { return !terminal() && state != State.RECOVERY_REQUIRED; }
    public void advance() {
        if (!runnable() || nextBlock >= plan.steps().size()) throw new IllegalStateException("Cannot advance job");
        nextBlock++;
        state(nextBlock == plan.steps().size() ? State.COMPLETE : State.BUILDING, "");
    }
    public void state(State state, String reason) { this.state = Objects.requireNonNull(state); pauseReason = Objects.requireNonNullElse(reason, ""); }
    public void cancel() { state(State.CANCELLED, "Cancelled; placed blocks remain, no refund"); }
}
