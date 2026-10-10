package dev.whizzle.blueprints.client.ui;

import dev.whizzle.blueprints.client.Actions;
import dev.whizzle.blueprints.client.ClientState;
import dev.whizzle.blueprints.client.net.Messages;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * One screen for the whole flow: pick a blueprint, see what it needs and what your Builder's Chest holds,
 * preview / rotate / mirror / adjust opacity, start, and watch or cancel your builds.
 */
public final class BlueprintsScreen extends Screen {
	private static final int LIST_WIDTH = 140, ROW = 22;
	private int listPage, materialScroll, seenChange = -1;
	private final List<Button> listButtons = new ArrayList<>();

	public BlueprintsScreen() { super(Component.literal("Blueprints")); }

	private ClientState s() { return ClientState.INSTANCE; }

	@Override
	protected void init() {
		listButtons.clear();
		int perPage = Math.max(1, (height - 112) / ROW);
		List<Messages.Entry> catalog = s().catalog;
		int pages = Math.max(1, (catalog.size() + perPage - 1) / perPage);
		listPage = Math.min(listPage, pages - 1);
		for (int i = listPage * perPage; i < Math.min(catalog.size(), (listPage + 1) * perPage); i++) {
			Messages.Entry e = catalog.get(i);
			boolean chosen = e.id().equals(s().selectedId);
			String label = font.plainSubstrByWidth(e.name(), LIST_WIDTH - 20);
			Button b = addRenderableWidget(Button.builder(Component.literal(chosen ? "> " + label + " <" : label), btn -> {
				s().select(e.id());
				materialScroll = 0;
				rebuildWidgets();
			}).pos(10, 30 + (i - listPage * perPage) * ROW).size(LIST_WIDTH, 20).build());
			listButtons.add(b);
		}
		if (pages > 1) {
			addRenderableWidget(Button.builder(Component.literal("<"), b -> { listPage--; rebuildWidgets(); }).pos(10, height - 76).size(30, 20).build()).active = listPage > 0;
			addRenderableWidget(Button.builder(Component.literal(">"), b -> { listPage++; rebuildWidgets(); }).pos(LIST_WIDTH - 20, height - 76).size(30, 20).build()).active = listPage < pages - 1;
		}

		if (s().welcome != null && s().welcome.canUpload())
			addRenderableWidget(Button.builder(Component.literal("Upload .litematic..."), b -> minecraft.gui.setScreen(new UploadScreen(this)))
				.pos(10, height - 52).size(LIST_WIDTH, 20).build());
		if (s().welcome != null && s().welcome.canCapture())
			addRenderableWidget(Button.builder(Component.literal("Capture new..."), b -> { Actions.beginCapture(); onClose(); })
				.pos(10, height - 28).size(LIST_WIDTH, 20).build());

		int x = LIST_WIDTH + 20, y = height - 28, w = (width - x - 10 - 5 * 4) / 6;
		boolean selected = s().selectedId != null, previewing = s().previewing();
		addRenderableWidget(Button.builder(Component.literal(previewing ? "Move here" : "Preview here"), b -> { if (s().previewing()) Actions.moveHere(); else Actions.previewSelectedHere(); onClose(); })
			.pos(x, y).size(w, 20).build()).active = selected;
		addRenderableWidget(Button.builder(Component.literal("Rotate"), b -> { Actions.rotate(); rebuildWidgets(); }).pos(x + (w + 4), y).size(w, 20).build()).active = previewing;
		addRenderableWidget(Button.builder(Component.literal("Mirror"), b -> { Actions.mirror(); rebuildWidgets(); }).pos(x + 2 * (w + 4), y).size(w, 20).build()).active = previewing;
		addRenderableWidget(Button.builder(Component.literal("Opacity -"), b -> s().nudgeOpacity(-0.1f)).pos(x + 3 * (w + 4), y).size(w, 20).build()).active = previewing;
		addRenderableWidget(Button.builder(Component.literal("Opacity +"), b -> s().nudgeOpacity(0.1f)).pos(x + 4 * (w + 4), y).size(w, 20).build()).active = previewing;
		Button start = addRenderableWidget(Button.builder(Component.literal("Start build"), b -> { Actions.startBuild(); onClose(); })
			.pos(x + 5 * (w + 4), y).size(w, 20).build());
		start.active = previewing && selected && s().selectedId.equals(s().previewId) && s().welcome != null && s().welcome.canBuild();
		if (previewing)
			addRenderableWidget(Button.builder(Component.literal("Close preview"), b -> { s().clearPreview(); rebuildWidgets(); })
				.pos(width - 10 - w, 6).size(w, 20).build());

		int jy = height - 54;
		for (Messages.Job job : s().jobs) {
			if (!job.active()) continue;
			addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> Actions.cancelJob(job.id())).pos(width - 60, jy).size(50, 20).build());
			jy -= ROW;
		}
		seenChange = s().changeCounter;
	}

	@Override
	public void tick() {
		if (seenChange != s().changeCounter) rebuildWidgets();
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (mouseX > LIST_WIDTH + 20) { materialScroll = Math.max(0, materialScroll - (int) Math.signum(scrollY)); return true; }
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);
		g.centeredText(font, title.getString(), width / 2, 10, Hud.WHITE);
		int x = LIST_WIDTH + 20, y = 30;
		ClientState s = s();
		switch (s.connection) {
			case NO_PLUGIN -> { g.text(font, "This server doesn't have the Blueprints plugin.", x, y, Hud.RED); return; }
			case VERSION_MISMATCH -> { g.text(font, "The server's Blueprints plugin needs a different version of this mod.", x, y, Hud.RED); return; }
			case UNKNOWN -> { g.text(font, "Connecting to Blueprints on this server...", x, y, Hud.GRAY); return; }
			default -> { }
		}
		if (s.catalog.isEmpty()) g.text(font, "No blueprints on this server yet. Capture one in Creative or import a .litematic.", 10, 30, Hud.GRAY);
		if (s.selectedId == null) { g.text(font, "Choose a blueprint on the left.", x, y, Hud.GRAY); return; }
		Messages.Entry e = s.entry(s.selectedId).orElse(null);
		if (e == null) return;
		g.text(font, e.name(), x, y, Hud.GOLD);
		y += 12;
		g.text(font, e.sizeX() + " x " + e.sizeY() + " x " + e.sizeZ() + "   " + e.blocks() + " blocks   id: " + e.id(), x, y, Hud.GRAY);
		y += 16;

		Messages.Materials m = s.materials;
		if (m == null || !m.id().equals(e.id())) { g.text(font, "Loading materials...", x, y, Hud.GRAY); return; }
		g.text(font, m.chestFound() ? "Your Builder's Chest at " + m.chestX() + " " + m.chestY() + " " + m.chestZ()
			: "No free Builder's Chest of yours nearby. Place one (each build needs its own).", x, y, m.chestFound() ? Hud.GRAY : Hud.RED);
		y += 14;
		int col1 = x, col2 = x + 150, col3 = x + 200, col4 = x + 255;
		g.text(font, "Material", col1, y, Hud.WHITE); g.text(font, "Needed", col2, y, Hud.WHITE);
		g.text(font, "In chest", col3, y, Hud.WHITE); g.text(font, "Missing", col4, y, Hud.WHITE);
		y += 12;
		List<Messages.Material> rows = new ArrayList<>(m.rows());
		rows.sort(Comparator.comparingInt(Messages.Material::missing).reversed().thenComparing(Messages.Material::material));
		int bottom = height - 60 - jobsHeight(s), visible = Math.max(1, (bottom - y) / 11);
		materialScroll = Math.min(materialScroll, Math.max(0, rows.size() - visible));
		for (int i = materialScroll; i < Math.min(rows.size(), materialScroll + visible); i++) {
			Messages.Material r = rows.get(i);
			g.text(font, pretty(r.material()), col1, y, Hud.GRAY);
			g.text(font, String.valueOf(r.needed()), col2, y, Hud.GRAY);
			g.text(font, String.valueOf(r.inChest()), col3, y, Hud.GRAY);
			g.text(font, r.missing() == 0 ? "ok" : String.valueOf(r.missing()), col4, y, r.missing() == 0 ? Hud.GREEN : Hud.RED);
			y += 11;
		}
		if (rows.size() > visible) g.text(font, "Scroll for more (" + rows.size() + " materials)", x, y + 2, Hud.GRAY);

		int jy = height - 50;
		for (Messages.Job job : s.jobs) {
			if (!job.active()) continue;
			Hud.Line line = Hud.jobLine(job);
			g.text(font, font.plainSubstrByWidth(line.text(), width - x - 80), x, jy, line.color());
			jy -= ROW;
		}
		if (s.previewing() && !s.previewId.equals(s.selectedId))
			g.text(font, "Previewing a different blueprint; \"Preview here\" switches to this one.", x, height - 40 - jobsHeight(s), Hud.GOLD);
	}

	private static int jobsHeight(ClientState s) { return (int) s.jobs.stream().filter(Messages.Job::active).count() * ROW; }

	static String pretty(String material) {
		String[] words = material.toLowerCase(Locale.ROOT).split("_");
		StringBuilder sb = new StringBuilder();
		for (String w : words) sb.append(sb.isEmpty() ? "" : " ").append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
		return sb.toString();
	}

	@Override
	public boolean isPauseScreen() { return false; }
}
