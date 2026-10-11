package dev.whizzle.blueprints.client;

import net.fabricmc.loader.api.FabricLoader;

/** Optional integrations, detected at runtime so the mod has no hard dependencies. */
public final class Compat {
	private static long checkedAt;
	private static boolean shaders;

	private Compat() {}

	/**
	 * True while an Iris shader pack is active. Shader packs replace the world rendering pipeline and can hide the
	 * preview; the HUD then suggests toggling shaders while placing. Checked at most once a second.
	 */
	public static boolean shadersOn() {
		long now = System.currentTimeMillis();
		if (now - checkedAt < 1000) return shaders;
		checkedAt = now;
		shaders = false;
		if (!FabricLoader.getInstance().isModLoaded("iris")) return false;
		try {
			Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
			Object instance = api.getMethod("getInstance").invoke(null);
			shaders = (Boolean) api.getMethod("isShaderPackInUse").invoke(instance);
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			shaders = false;
		}
		return shaders;
	}
}
