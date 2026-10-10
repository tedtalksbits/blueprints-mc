package dev.whizzle.blueprints.client.ui;

import dev.whizzle.blueprints.client.Actions;
import dev.whizzle.blueprints.client.ClientState;
import dev.whizzle.blueprints.client.net.Messages;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Last step of a capture: name it and save. The server checks Creative mode, permission, and supported blocks. */
public final class CaptureNameScreen extends Screen {
	private EditBox name;
	private Button save;

	public CaptureNameScreen() { super(Component.literal("Save blueprint")); }

	@Override
	protected void init() {
		int w = 200, x = (width - w) / 2, y = height / 2 - 30;
		name = addRenderableWidget(new EditBox(font, x, y, w, 20, Component.literal("Name")));
		name.setMaxLength(64);
		name.setHint(Component.literal("e.g. cabin"));
		name.setResponder(text -> save.active = !Messages.toId(text).isEmpty());
		save = addRenderableWidget(Button.builder(Component.literal("Save"), b -> { Actions.saveCapture(name.getValue()); onClose(); })
			.pos(x, y + 30).size(w / 2 - 2, 20).build());
		save.active = false;
		addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> { Actions.cancelCapture(); onClose(); })
			.pos(x + w / 2 + 2, y + 30).size(w / 2 - 2, 20).build());
		setInitialFocus(name);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);
		ClientState s = ClientState.INSTANCE;
		g.centeredText(font, title.getString(), width / 2, height / 2 - 60, Hud.WHITE);
		if (s.corner1 != null && s.corner2 != null) {
			String size = (Math.abs(s.corner1[0] - s.corner2[0]) + 1) + " x " + (Math.abs(s.corner1[1] - s.corner2[1]) + 1) + " x " + (Math.abs(s.corner1[2] - s.corner2[2]) + 1);
			g.centeredText(font, "Selection " + size, width / 2, height / 2 - 46, Hud.GRAY);
		}
		String id = Messages.toId(name.getValue());
		if (!id.isEmpty()) g.centeredText(font, "Saved as: " + id, width / 2, height / 2 + 28 + 26, Hud.GRAY);
	}

	@Override
	public void onClose() {
		if (ClientState.INSTANCE.captureStep == 2 && ClientState.INSTANCE.corner2 != null) Actions.cancelCapture();
		super.onClose();
	}

	@Override
	public boolean isPauseScreen() { return false; }
}
