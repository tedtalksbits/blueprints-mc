package dev.whizzle.blueprints.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/**
 * A short, rebindable set (Options > Controls > Blueprints). Everything except "open" only acts while a preview is
 * shown, and the HUD always lists the current bindings, so nothing has to be memorized.
 */
public final class Keys {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("blueprints", "main"));

	public static KeyMapping OPEN, FORWARD, BACK, LEFT, RIGHT, UP, DOWN, ROTATE, MIRROR, HERE, CONFIRM, HIDE;

	private Keys() {}

	private static KeyMapping key(String name, int keyCode) {
		return KeyMappingHelper.registerKeyMapping(new KeyMapping("key.blueprints." + name, InputConstants.Type.KEYBOARD, keyCode, CATEGORY));
	}

	public static void register() {
		OPEN = key("open", InputConstants.KEY_B);
		FORWARD = key("forward", InputConstants.KEY_UP);
		BACK = key("back", InputConstants.KEY_DOWN);
		LEFT = key("left", InputConstants.KEY_LEFT);
		RIGHT = key("right", InputConstants.KEY_RIGHT);
		UP = key("up", InputConstants.KEY_PAGEUP);
		DOWN = key("down", InputConstants.KEY_PAGEDOWN);
		ROTATE = key("rotate", InputConstants.KEY_R);
		MIRROR = key("mirror", InputConstants.KEY_M);
		HERE = key("here", InputConstants.KEY_G);
		CONFIRM = key("confirm", InputConstants.KEY_RETURN);
		HIDE = key("hide", InputConstants.KEY_BACKSPACE);
	}

	/** Compact label of the bound key: arrows as symbols, long names shortened ("PgUp", "Bksp"). */
	public static String label(KeyMapping key) {
		String name = key.getTranslatedKeyMessage().getString();
		return switch (name) {
			case "Up Arrow" -> "\u2191";
			case "Down Arrow" -> "\u2193";
			case "Left Arrow" -> "\u2190";
			case "Right Arrow" -> "\u2192";
			case "Page Up" -> "PgUp";
			case "Page Down" -> "PgDn";
			case "Backspace" -> "Bksp";
			default -> name;
		};
	}
}
