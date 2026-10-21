package dev.whizzle.blueprints.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * A short, rebindable set (Options > Controls > Blueprints). Everything except "open" only acts while a preview is
 * shown, and the HUD always lists the current bindings, so nothing has to be memorized.
 */
public final class Keys {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("blueprints", "main"));

	public static KeyMapping OPEN, FORWARD, BACK, LEFT, RIGHT, UP, DOWN, ROTATE, MIRROR, HERE, CONFIRM, HIDE;

	private Keys() {}

	private static KeyMapping key(String name, int glfw) {
		return KeyMappingHelper.registerKeyMapping(new KeyMapping("key.blueprints." + name, InputConstants.Type.KEYSYM, glfw, CATEGORY));
	}

	public static void register() {
		OPEN = key("open", GLFW.GLFW_KEY_B);
		FORWARD = key("forward", GLFW.GLFW_KEY_UP);
		BACK = key("back", GLFW.GLFW_KEY_DOWN);
		LEFT = key("left", GLFW.GLFW_KEY_LEFT);
		RIGHT = key("right", GLFW.GLFW_KEY_RIGHT);
		UP = key("up", GLFW.GLFW_KEY_PAGE_UP);
		DOWN = key("down", GLFW.GLFW_KEY_PAGE_DOWN);
		ROTATE = key("rotate", GLFW.GLFW_KEY_R);
		MIRROR = key("mirror", GLFW.GLFW_KEY_M);
		HERE = key("here", GLFW.GLFW_KEY_G);
		CONFIRM = key("confirm", GLFW.GLFW_KEY_ENTER);
		HIDE = key("hide", GLFW.GLFW_KEY_BACKSPACE);
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
