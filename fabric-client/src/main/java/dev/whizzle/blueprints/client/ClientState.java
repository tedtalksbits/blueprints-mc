package dev.whizzle.blueprints.client;

import dev.whizzle.blueprints.client.model.Placement;
import dev.whizzle.blueprints.client.model.Transform;
import dev.whizzle.blueprints.client.net.Messages;
import dev.whizzle.blueprints.client.net.Net;

import java.util.*;

/**
 * Everything the client knows for the current server connection. Only touched on the client thread
 * (network handlers run there too). Reset on disconnect.
 */
public final class ClientState {
	public enum Connection { UNKNOWN, CONNECTED, NO_PLUGIN, VERSION_MISMATCH }

	public static final ClientState INSTANCE = new ClientState();

	public Connection connection = Connection.UNKNOWN;
	public Messages.Welcome welcome;
	public List<Messages.Entry> catalog = List.of();
	public Messages.Catalog catalogInfo = Messages.Catalog.EMPTY;
	public final Map<String, Messages.Plan> plans = new HashMap<>();
	public List<Messages.Job> jobs = List.of();
	public Messages.Materials materials;

	/** Blueprint selected in the screen; may be previewed or not. */
	public String selectedId;
	/** Active preview, or null. */
	public Placement placement;
	public String previewId;
	public boolean previewVisible = true;
	public float opacity = 0.45f;
	public int changeCounter;

	/** Capture mode: 0 off, 1 waiting for the first corner, 2 waiting for the second. */
	public int captureStep;
	public int[] corner1, corner2;

	public Messages.Result lastResult;
	public long lastResultAt;
	public int ticksSinceHello;

	private ClientState() {}

	public void reset() {
		connection = Connection.UNKNOWN; welcome = null; catalog = List.of(); catalogInfo = Messages.Catalog.EMPTY; plans.clear(); jobs = List.of(); materials = null;
		selectedId = null; placement = null; previewId = null; lastResult = null; ticksSinceHello = 0; captureStep = 0; corner1 = corner2 = null; changeCounter++;
	}

	public boolean ready() { return connection == Connection.CONNECTED; }
	public boolean previewing() { return placement != null; }
	public boolean capturing() { return captureStep > 0; }

	public Optional<Messages.Entry> entry(String id) { return catalog.stream().filter(e -> e.id().equals(id)).findFirst(); }

	/** A fresh catalog. Blueprints that disappeared (deleted) drop out of the selection, preview, and plan cache. */
	public void setCatalog(Messages.Catalog c) {
		catalogInfo = c;
		catalog = c.entries();
		java.util.Set<String> ids = new java.util.HashSet<>();
		for (Messages.Entry e : catalog) ids.add(e.id());
		plans.keySet().retainAll(ids);
		if (selectedId != null && !ids.contains(selectedId)) { selectedId = null; materials = null; }
		if (previewId != null && !ids.contains(previewId)) { placement = null; previewId = null; }
		changeCounter++;
	}

	public void select(String id) {
		selectedId = id;
		materials = null;
		if (!plans.containsKey(id)) Net.send(Messages.PLAN_REQUEST, Messages.id(id));
		Net.send(Messages.MATERIALS_REQUEST, Messages.id(id));
	}

	/** Starts (or restarts) a preview of the plan, placed in front of the player. */
	public boolean preview(String id, int targetX, int targetY, int targetZ, float yaw) {
		Messages.Plan plan = plans.get(id);
		if (plan == null) return false;
		Transform keep = placement != null && id.equals(previewId) ? placement.transform() : Transform.IDENTITY;
		placement = new Placement(plan.blocks(), 0, 0, 0);
		placement.setTransform(keep);
		placement.placeInFront(targetX, targetY, targetZ, yaw);
		previewId = id;
		previewVisible = true;
		changeCounter++;
		return true;
	}

	public void clearPreview() { placement = null; previewId = null; changeCounter++; }

	public void setTransform(Transform transform) {
		if (placement == null) return;
		placement.setTransform(transform);
		changeCounter++;
	}

	public void nudgeOpacity(float delta) { opacity = Math.max(0.1f, Math.min(0.9f, opacity + delta)); }

	public void showResult(Messages.Result result) { lastResult = result; lastResultAt = System.currentTimeMillis(); }

	public boolean hasActiveJobs() { return jobs.stream().anyMatch(Messages.Job::active); }
}
