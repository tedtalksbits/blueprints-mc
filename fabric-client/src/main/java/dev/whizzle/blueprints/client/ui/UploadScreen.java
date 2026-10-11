package dev.whizzle.blueprints.client.ui;

import dev.whizzle.blueprints.client.ClientState;
import dev.whizzle.blueprints.client.Uploader;
import dev.whizzle.blueprints.client.net.Messages;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** Pick a .litematic from the game's schematics folder (Litematica's default), name it, upload it. */
public final class UploadScreen extends Screen {
	private static final int ROW = 22, LIST_WIDTH = 220;
	private final Screen parent;
	private final Path folder;
	private List<Path> files = List.of();
	private Path chosen;
	private int page;
	private EditBox name;
	private Button upload;

	public UploadScreen(Screen parent) {
		super(Component.literal("Upload a .litematic"));
		this.parent = parent;
		this.folder = Minecraft.getInstance().gameDirectory.toPath().resolve("schematics");
	}

	private void scan() {
		List<Path> found = new ArrayList<>();
		if (Files.isDirectory(folder)) {
			try (Stream<Path> walk = Files.walk(folder, 4)) {
				walk.filter(p -> p.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".litematic") && Files.isRegularFile(p))
					.sorted(Comparator.comparing(UploadScreen::modified).reversed()).forEach(found::add);
			} catch (IOException ignored) { }
		}
		files = found;
	}

	private static long modified(Path p) {
		try { return Files.getLastModifiedTime(p).toMillis(); } catch (IOException e) { return 0; }
	}

	@Override
	protected void init() {
		scan();
		int perPage = Math.max(1, (height - 110) / ROW);
		int pages = Math.max(1, (files.size() + perPage - 1) / perPage);
		page = Math.min(page, pages - 1);
		for (int i = page * perPage; i < Math.min(files.size(), (page + 1) * perPage); i++) {
			Path file = files.get(i);
			String label = folder.relativize(file).toString().replace('\\', '/');
			label = font.plainSubstrByWidth(label, LIST_WIDTH - 60) + "  " + Math.max(1, sizeOf(file) >> 10) + " KB";
			addRenderableWidget(Button.builder(Component.literal(file.equals(chosen) ? "> " + label + " <" : label), b -> {
				chosen = file;
				String base = file.getFileName().toString();
				name.setValue(base.substring(0, base.length() - ".litematic".length()));
				rebuildWidgets();
			}).pos(10, 30 + (i - page * perPage) * ROW).size(LIST_WIDTH, 20).build());
		}
		if (pages > 1) {
			addRenderableWidget(Button.builder(Component.literal("<"), b -> { page--; rebuildWidgets(); }).pos(10, height - 78).size(30, 20).build()).active = page > 0;
			addRenderableWidget(Button.builder(Component.literal(">"), b -> { page++; rebuildWidgets(); }).pos(LIST_WIDTH - 20, height - 78).size(30, 20).build()).active = page < pages - 1;
		}
		addRenderableWidget(Button.builder(Component.literal("Open folder"), b -> {
			try { Files.createDirectories(folder); } catch (IOException ignored) { }
			Util.getPlatform().openPath(folder);
		}).pos(10, height - 52).size(LIST_WIDTH / 2 - 2, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Refresh"), b -> rebuildWidgets()).pos(10 + LIST_WIDTH / 2 + 2, height - 52).size(LIST_WIDTH / 2 - 2, 20).build());

		int x = LIST_WIDTH + 30, w = Math.min(220, width - x - 10);
		String previous = name == null ? "" : name.getValue();
		name = addRenderableWidget(new EditBox(font, x, 60, w, 20, Component.literal("Name")));
		name.setMaxLength(64);
		name.setValue(previous);
		name.setHint(Component.literal("Blueprint name"));
		name.setResponder(text -> { if (upload != null) upload.active = chosen != null && !Messages.toId(text).isEmpty() && !Uploader.active(); });
		upload = addRenderableWidget(Button.builder(Component.literal("Upload"), b -> { Uploader.start(chosen, name.getValue()); onClose(); })
			.pos(x, 90).size(w / 2 - 2, 20).build());
		upload.active = chosen != null && !Messages.toId(name.getValue()).isEmpty() && !Uploader.active();
		addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose()).pos(x + w / 2 + 2, 90).size(w / 2 - 2, 20).build());
	}

	private static long sizeOf(Path p) { try { return Files.size(p); } catch (IOException e) { return 0; } }

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);
		g.centeredText(font, title.getString(), width / 2, 10, Hud.WHITE);
		int x = LIST_WIDTH + 30;
		if (files.isEmpty()) {
			g.text(font, "No .litematic files found in:", 10, 30, Hud.GRAY);
			g.text(font, font.plainSubstrByWidth(folder.toString(), LIST_WIDTH + 10), 10, 42, Hud.GRAY);
			g.text(font, "Save with Litematica, or copy files there, then Refresh.", 10, 58, Hud.GRAY);
		}
		g.text(font, chosen == null ? "Choose a file on the left." : "File: " + chosen.getFileName(), x, 30, chosen == null ? Hud.GRAY : Hud.WHITE);
		String id = Messages.toId(name.getValue());
		if (!id.isEmpty()) g.text(font, "Saved on the server as: " + id, x, 116, Hud.GRAY);
		var welcome = ClientState.INSTANCE.welcome;
		if (welcome != null) {
			g.text(font, "Server limits: " + (welcome.maxUploadBytes() >> 10) + " KB file, " + welcome.maxBlocks() + " blocks, " + welcome.maxSpan() + " per side.", x, 132, Hud.GRAY);
			g.text(font, "Unsupported blocks (chests, beds, rails...) are skipped and listed.", x, 144, Hud.GRAY);
		}
	}

	@Override
	public void onClose() { minecraft.gui.setScreen(parent); }

	@Override
	public boolean isPauseScreen() { return false; }
}
