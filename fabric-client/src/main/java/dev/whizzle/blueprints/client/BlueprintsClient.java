package dev.whizzle.blueprints.client;

import dev.whizzle.blueprints.client.net.Messages;
import dev.whizzle.blueprints.client.net.Net;
import dev.whizzle.blueprints.client.render.PreviewRenderer;
import dev.whizzle.blueprints.client.ui.BlueprintsScreen;
import dev.whizzle.blueprints.client.ui.Hud;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ServerboundPlayChannelEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.util.Map;

/** Wires the Blueprints client together: protocol, keys, per-tick polling, world preview, and HUD. */
public final class BlueprintsClient implements ClientModInitializer {
	private static final int HELLO_TIMEOUT_TICKS = 200, MATERIALS_EVERY = 20, JOBS_EVERY = 40, HOLD_DELAY = 8, HOLD_REPEAT = 2;
	private final ClientState s = ClientState.INSTANCE;
	private long tick;
	private boolean helloSent;

	@Override
	public void onInitializeClient() {
		Net.register(Map.of(
			Messages.WELCOME, this::onWelcome,
			Messages.CATALOG, data -> { s.catalog = Messages.catalog(data); s.changeCounter++; },
			Messages.PLAN, data -> { Messages.Plan plan = Messages.plan(data); s.plans.put(plan.id(), plan); s.changeCounter++; },
			Messages.MATERIALS, data -> { s.materials = Messages.materials(data); },
			Messages.RESULT, this::onResult,
			Messages.JOBS, data -> { s.jobs = Messages.jobs(data); s.changeCounter++; },
			Messages.UPLOAD_READY, data -> { Messages.Result r = Messages.result(data); Uploader.onReady(r.ok(), r.message()); }));
		Keys.register();

		ClientPlayConnectionEvents.JOIN.register((listener, sender, client) -> { s.reset(); helloSent = false; sendHello(); });
		ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> { s.reset(); Uploader.cancel(); });
		// The server announces its channels after join; greet again once it can hear us.
		ServerboundPlayChannelEvents.REGISTER.register((listener, sender, client, channels) -> {
			if (s.connection == ClientState.Connection.UNKNOWN && channels.contains(Identifier.fromNamespaceAndPath("blueprints", Messages.HELLO))) {
				helloSent = false;
				sendHello();
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
		LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES.register(PreviewRenderer::render);
		LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES.register(PreviewRenderer::renderSelection);
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("blueprints", "hud"), Hud::extract);
	}

	private void sendHello() {
		if (helloSent || Minecraft.getInstance().getConnection() == null) return;
		Net.send(Messages.HELLO, Messages.hello());
		helloSent = true;
	}

	private void onWelcome(byte[] data) {
		s.welcome = Messages.welcome(data);
		if (s.welcome.protocol() != Messages.PROTOCOL) { s.connection = ClientState.Connection.VERSION_MISMATCH; return; }
		s.connection = ClientState.Connection.CONNECTED;
		Net.send(Messages.CATALOG_REQUEST, Messages.empty());
		Net.send(Messages.JOBS_REQUEST, Messages.empty());
		s.changeCounter++;
	}

	private void onResult(byte[] data) {
		Messages.Result result = Messages.result(data);
		Actions.toast(result.ok(), result.message());
		if (result.ok() && s.previewing()) s.clearPreview();
	}

	private void onTick(Minecraft mc) {
		if (mc.player == null || mc.getConnection() == null) return;
		tick++;
		if (s.connection == ClientState.Connection.UNKNOWN) {
			s.ticksSinceHello++;
			if (s.ticksSinceHello == 60 || s.ticksSinceHello == 140) { helloSent = false; sendHello(); } // channel registration can lag
			if (s.ticksSinceHello > HELLO_TIMEOUT_TICKS) s.connection = ClientState.Connection.NO_PLUGIN;
		}

		while (Keys.OPEN.consumeClick()) {
			if (s.connection == ClientState.Connection.CONNECTED) Net.send(Messages.CATALOG_REQUEST, Messages.empty());
			mc.gui.setScreen(new BlueprintsScreen());
		}
		if (mc.gui.screen() == null && s.capturing()) captureKeys();
		else if (mc.gui.screen() == null && s.previewing()) previewKeys();
		else drainPreviewKeys();

		if (!s.ready()) return;
		Uploader.tick();
		String watched = s.previewing() ? s.previewId : (mc.gui.screen() instanceof BlueprintsScreen ? s.selectedId : null);
		if (watched != null && tick % MATERIALS_EVERY == 0) Net.send(Messages.MATERIALS_REQUEST, Messages.id(watched));
		if ((s.hasActiveJobs() || mc.gui.screen() instanceof BlueprintsScreen) && tick % JOBS_EVERY == 0) Net.send(Messages.JOBS_REQUEST, Messages.empty());
	}

	private void captureKeys() {
		while (Keys.CONFIRM.consumeClick()) Actions.captureCorner();
		while (Keys.HIDE.consumeClick()) Actions.cancelCapture();
		drainPreviewKeys();
	}

	private void previewKeys() {
		while (Keys.ROTATE.consumeClick()) Actions.rotate();
		while (Keys.MIRROR.consumeClick()) Actions.mirror();
		while (Keys.HERE.consumeClick()) Actions.moveHere();
		while (Keys.CONFIRM.consumeClick()) Actions.startBuild();
		while (Keys.HIDE.consumeClick()) s.clearPreview();
		repeatMove(Keys.FORWARD, 1, 0, 0);
		repeatMove(Keys.BACK, -1, 0, 0);
		repeatMove(Keys.RIGHT, 0, 1, 0);
		repeatMove(Keys.LEFT, 0, -1, 0);
		repeatMove(Keys.UP, 0, 0, 1);
		repeatMove(Keys.DOWN, 0, 0, -1);
	}

	private final Map<KeyMapping, int[]> held = new java.util.HashMap<>();

	/** One step per press, then steady repeats while held. */
	private void repeatMove(KeyMapping key, int forward, int right, int up) {
		int[] h = held.computeIfAbsent(key, k -> new int[1]);
		while (key.consumeClick()) { Actions.move(forward, right, up); h[0] = 0; }
		if (key.isDown()) { if (++h[0] > HOLD_DELAY && h[0] % HOLD_REPEAT == 0) Actions.move(forward, right, up); }
		else h[0] = 0;
	}

	/** Discards presses made while no preview is active, so they don't fire later. */
	private void drainPreviewKeys() {
		for (KeyMapping k : new KeyMapping[]{Keys.ROTATE, Keys.MIRROR, Keys.HERE, Keys.CONFIRM, Keys.HIDE, Keys.FORWARD, Keys.BACK, Keys.LEFT, Keys.RIGHT, Keys.UP, Keys.DOWN})
			while (k.consumeClick()) { }
	}
}
