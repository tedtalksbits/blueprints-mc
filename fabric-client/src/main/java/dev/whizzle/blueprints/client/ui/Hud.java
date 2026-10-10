package dev.whizzle.blueprints.client.ui;

import dev.whizzle.blueprints.client.ClientState;
import dev.whizzle.blueprints.client.Keys;
import dev.whizzle.blueprints.client.model.Placement;
import dev.whizzle.blueprints.client.net.Messages;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/** Top-left panel while previewing (with the live key bindings), job progress otherwise, and a result toast. */
public final class Hud {
	static final int WHITE = 0xFFFFFFFF, GRAY = 0xFFB8C0CC, GREEN = 0xFF6EE07A, RED = 0xFFFF6B6B, GOLD = 0xFFFFC857, PANEL = 0xA0101418;

	private Hud() {}

	public static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return; // Fabric skips HUD elements while the GUI is hidden (F1)
		ClientState s = ClientState.INSTANCE;
		Font font = mc.font;
		List<Line> lines = new ArrayList<>();
		if (s.capturing()) captureLines(s, lines);
		else if (s.previewing()) previewLines(s, lines);
		else for (Messages.Job job : s.jobs) if (job.active()) lines.add(jobLine(job));
		if (!lines.isEmpty()) panel(g, font, 6, 6, lines);
		toast(g, font, s);
	}

	record Line(String text, int color) {}

	private static void previewLines(ClientState s, List<Line> out) {
		Placement p = s.placement;
		Messages.Entry entry = s.entry(s.previewId).orElse(null);
		String name = entry == null ? s.previewId : entry.name();
		String turn = p.transform().rotation() + "°" + (p.transform().mirrorX() ? ", mirrored" : "");
		out.add(new Line(name + "  (" + turn + ")", GOLD));
		out.add(new Line(p.sizeX() + "x" + p.sizeY() + "x" + p.sizeZ() + ", " + p.blockCount() + " blocks at " + p.anchorX() + " " + p.anchorY() + " " + p.anchorZ(), GRAY));
		Messages.Materials m = s.materials;
		if (m == null || !m.id().equals(s.previewId)) out.add(new Line("Checking materials...", GRAY));
		else if (!m.chestFound()) out.add(new Line("No free Builder's Chest of yours nearby", RED));
		else if (m.totalMissing() > 0) out.add(new Line(m.totalMissing() + " items still missing from your Builder's Chest", RED));
		else out.add(new Line("All materials are in your Builder's Chest", GREEN));
		if (!s.previewVisible) out.add(new Line("Preview hidden", GRAY));
		out.add(new Line(keys(Keys.FORWARD, Keys.BACK, Keys.LEFT, Keys.RIGHT) + " move  " + keys(Keys.UP, Keys.DOWN) + " height  " + keys(Keys.HERE) + " to crosshair", WHITE));
		out.add(new Line(keys(Keys.ROTATE) + " rotate  " + keys(Keys.MIRROR) + " mirror  " + keys(Keys.CONFIRM) + " build  " + keys(Keys.HIDE) + " close  " + keys(Keys.OPEN) + " menu", WHITE));
	}

	private static void captureLines(ClientState s, List<Line> out) {
		out.add(new Line("Capture a blueprint (Creative)", GOLD));
		out.add(new Line(s.captureStep == 1 ? "Look at one corner block of your build and press " + keys(Keys.CONFIRM)
			: "Now look at the opposite corner block and press " + keys(Keys.CONFIRM), WHITE));
		if (s.corner1 != null) {
			var look = dev.whizzle.blueprints.client.Actions.lookedAtBlock();
			if (look != null) out.add(new Line("Selection " + (Math.abs(look.getX() - s.corner1[0]) + 1) + "x" + (Math.abs(look.getY() - s.corner1[1]) + 1)
				+ "x" + (Math.abs(look.getZ() - s.corner1[2]) + 1) + " (max 65 per side)", GRAY));
		}
		out.add(new Line(keys(Keys.HIDE) + " cancel", GRAY));
	}

	static Line jobLine(Messages.Job job) {
		boolean paused = job.state().startsWith("PAUSED") || job.state().equals("RECOVERY_REQUIRED");
		String text = job.name() + "  " + job.done() + "/" + job.total() + (paused ? "  paused: " + job.reason() : "  building");
		return new Line(text, paused ? GOLD : GREEN);
	}

	private static String keys(net.minecraft.client.KeyMapping... mappings) {
		StringBuilder sb = new StringBuilder();
		boolean compact = true;
		for (var k : mappings) compact &= Keys.label(k).length() == 1;
		for (var k : mappings) sb.append(sb.isEmpty() ? "[" : compact ? "" : "/").append(Keys.label(k));
		return sb.append("]").toString();
	}

	static void panel(GuiGraphicsExtractor g, Font font, int x, int y, List<Line> lines) {
		int width = 0;
		for (Line l : lines) width = Math.max(width, font.width(l.text()));
		int height = lines.size() * (font.lineHeight + 2) + 6;
		g.fill(x, y, x + width + 10, y + height, PANEL);
		int ty = y + 4;
		for (Line l : lines) { g.text(font, l.text(), x + 5, ty, l.color()); ty += font.lineHeight + 2; }
	}

	private static void toast(GuiGraphicsExtractor g, Font font, ClientState s) {
		if (s.lastResult == null || System.currentTimeMillis() - s.lastResultAt > 6000) return;
		String text = s.lastResult.message();
		int w = Math.min(font.width(text), g.guiWidth() - 20);
		int x = (g.guiWidth() - w) / 2, y = 30;
		g.fill(x - 6, y - 4, x + w + 6, y + font.lineHeight + 4, PANEL);
		g.text(font, font.plainSubstrByWidth(text, w), x, y, s.lastResult.ok() ? GREEN : RED);
	}
}
