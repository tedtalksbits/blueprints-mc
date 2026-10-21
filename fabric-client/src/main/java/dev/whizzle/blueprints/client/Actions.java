package dev.whizzle.blueprints.client;

import dev.whizzle.blueprints.client.model.Placement;
import dev.whizzle.blueprints.client.net.Messages;
import dev.whizzle.blueprints.client.net.Net;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** Player intents shared by keys and screen buttons. */
public final class Actions {
	private Actions() {}

	private static ClientState s() { return ClientState.INSTANCE; }

	/** Target block for placing: the block surface under the crosshair, or the block at the player's feet. */
	private static BlockPos target(Minecraft mc) {
		if (mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
			BlockPos pos = hit.getBlockPos();
			return mc.level != null && mc.level.getBlockState(pos).canBeReplaced() ? pos : pos.above();
		}
		return mc.player.blockPosition();
	}

	public static void previewSelectedHere() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || s().selectedId == null) return;
		BlockPos t = target(mc);
		if (!s().preview(s().selectedId, t.getX(), t.getY(), t.getZ(), mc.player.getYRot()))
			toast(false, "Still loading that blueprint; try again in a moment.");
	}

	public static void moveHere() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || !s().previewing()) return;
		BlockPos t = target(mc);
		s().placement.placeInFront(t.getX(), t.getY(), t.getZ(), mc.player.getYRot());
		s().changeCounter++;
	}

	public static void move(int forward, int right, int up) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || !s().previewing()) return;
		s().placement.moveRelative(mc.player.getYRot(), forward, right, up);
		s().changeCounter++;
	}

	public static void rotate() { if (s().previewing()) s().setTransform(s().placement.transform().rotatedClockwise()); }
	public static void mirror() { if (s().previewing()) s().setTransform(s().placement.transform().mirrored()); }

	public static void togglePreview() {
		if (s().previewing()) s().clearPreview();
		else previewSelectedHere();
	}

	public static void startBuild() {
		ClientState state = s();
		Placement p = state.placement;
		if (p == null || state.previewId == null) return;
		if (state.welcome != null && !state.welcome.canBuild()) { toast(false, "You don't have permission to start builds on this server."); return; }
		Net.send(Messages.START, Messages.start(state.previewId, p.origin(), p.transform().rotation(), p.transform().mirrorCode()));
	}

	/** The block under the crosshair (for capture corners), or null. */
	public static BlockPos lookedAtBlock() {
		Minecraft mc = Minecraft.getInstance();
		return mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK ? hit.getBlockPos() : null;
	}

	public static void beginCapture() {
		ClientState state = s();
		if (state.welcome == null || !state.welcome.canCapture()) { toast(false, "You don't have permission to capture blueprints on this server."); return; }
		state.clearPreview();
		state.captureStep = 1;
		state.corner1 = state.corner2 = null;
		state.changeCounter++;
	}

	public static void captureCorner() {
		ClientState state = s();
		BlockPos pos = lookedAtBlock();
		if (pos == null) { toast(false, "Look at a block of your build (within reach) and try again."); return; }
		int[] corner = {pos.getX(), pos.getY(), pos.getZ()};
		if (state.captureStep == 1) { state.corner1 = corner; state.captureStep = 2; }
		else if (state.captureStep == 2) {
			state.corner2 = corner;
			Minecraft.getInstance().gui.setScreen(new dev.whizzle.blueprints.client.ui.CaptureNameScreen());
		}
	}

	public static void cancelCapture() { s().captureStep = 0; s().corner1 = s().corner2 = null; }

	public static void saveCapture(String name) {
		ClientState state = s();
		String id = Messages.toId(name);
		if (id.isEmpty() || state.corner1 == null || state.corner2 == null) { toast(false, "Give the blueprint a name with letters or digits."); return; }
		Net.send(Messages.CAPTURE, Messages.capture(id, state.corner1, state.corner2));
		cancelCapture();
	}

	public static void cancelJob(String jobId) { Net.send(Messages.CANCEL, Messages.id(jobId)); }

	public static void deleteBlueprint(String id) { Net.send(Messages.DELETE, Messages.id(id)); }

	public static void toast(boolean ok, String message) {
		s().showResult(new Messages.Result(ok, message));
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) mc.player.sendSystemMessage(Component.literal((ok ? "§a" : "§c") + "[Blueprints] " + message));
	}
}
