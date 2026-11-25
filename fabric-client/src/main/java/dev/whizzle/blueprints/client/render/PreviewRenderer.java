package dev.whizzle.blueprints.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.whizzle.blueprints.client.ClientState;
import dev.whizzle.blueprints.client.model.Placement;
import dev.whizzle.blueprints.client.model.Transform;
import dev.whizzle.blueprints.client.net.Messages;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ConcretePowderBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws the preview as translucent copies of each block's outline shape (so stairs, slabs, doors, fences and walls
 * show their orientation), tinted with the block's map color. Blocks already built correctly are not drawn;
 * positions occupied by something else are drawn red.
 */
public final class PreviewRenderer {
	private record Look(BlockState state, List<AABB> boxes, int rgb) {}

	private static List<Look> looks = List.of();
	private static String cachedFor = "";
	private static final int OBSTRUCTED_RGB = 0xE03030;

	private PreviewRenderer() {}

	public static void render(LevelRenderContext context) {
		ClientState s = ClientState.INSTANCE;
		Placement placement = s.placement;
		Minecraft mc = Minecraft.getInstance();
		if (placement == null || !s.previewVisible || mc.level == null) return;
		Messages.Plan plan = s.plans.get(s.previewId);
		if (plan == null) return;
		refreshLooks(plan, placement.transform());

		Vec3 camera = context.levelState().cameraRenderState.pos;
		PoseStack pose = context.poseStack();
		pose.pushPose();
		pose.translate(-camera.x, -camera.y, -camera.z);
		int alpha = Math.round(s.opacity * 255f);
		List<Look> current = looks;
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		context.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.debugFilledBox(), (p, buffer) -> {
			placement.forEachPlaced(b -> {
				Look look = current.get(b.state());
				cursor.set(b.x(), b.y(), b.z());
				BlockState there = mc.level.getBlockState(cursor);
				if (sameIgnoringWater(there, look.state())) return;            // already built (a slab built in water is waterlogged)
				boolean obstructed = obstructed(there, look.state());
				int color = ARGB.color(obstructed ? Math.max(alpha, 200) : alpha, obstructed ? OBSTRUCTED_RGB : look.rgb());
				// Obstructions are drawn slightly larger so they sit on top of the real block's faces instead of flickering.
				for (AABB box : look.boxes()) drawBox(p, buffer, (obstructed ? box.inflate(0.01) : box).move(b.x(), b.y(), b.z()), color);
			});
			drawFrame(p, buffer, placement, ARGB.color(Math.min(255, alpha + 60), 0xFFFFFF));
		});
		pose.popPose();
	}

	/**
	 * Mirrors the plugin's BuildManager.obstruction: air is free; water, seagrass and bubble columns are built into
	 * unless the block would wash away or change there (torches, flowers, sponge, concrete powder); grass, flowers and
	 * other replaceable blocks are cleared; lava and everything else block the build.
	 */
	static boolean obstructed(BlockState there, BlockState planned) {
		if (there.isAir()) return false;
		if (there.is(Blocks.WATER) || there.is(Blocks.BUBBLE_COLUMN) || there.is(Blocks.SEAGRASS) || there.is(Blocks.TALL_SEAGRASS))
			return !canPlaceInWater(planned);
		if (!there.getFluidState().isEmpty()) return true;
		return !there.canBeReplaced();
	}

	static boolean canPlaceInWater(BlockState planned) {
		if (planned.is(Blocks.SPONGE) || planned.getBlock() instanceof ConcretePowderBlock) return false;
		// Same test as Paper's Material.isSolid(), which the plugin uses.
		return planned.hasProperty(BlockStateProperties.WATERLOGGED) || planned.getBlock().defaultBlockState().is(BlockTags.BLOCKS_MOTION);
	}

	static boolean sameIgnoringWater(BlockState a, BlockState b) {
		if (a.equals(b)) return true;
		if (!a.hasProperty(BlockStateProperties.WATERLOGGED) || !b.hasProperty(BlockStateProperties.WATERLOGGED)) return false;
		return a.setValue(BlockStateProperties.WATERLOGGED, false).equals(b.setValue(BlockStateProperties.WATERLOGGED, false));
	}

	/** Capture selection: a yellow box from the first corner to the second (or to the block under the crosshair). */
	public static void renderSelection(LevelRenderContext context) {
		ClientState s = ClientState.INSTANCE;
		if (!s.capturing()) return;
		int[] a = s.corner1, b = s.corner2;
		if (b == null) {
			BlockPos look = dev.whizzle.blueprints.client.Actions.lookedAtBlock();
			if (look != null) b = new int[]{look.getX(), look.getY(), look.getZ()};
		}
		if (a == null) a = b;          // no first corner yet: highlight the block under the crosshair
		if (b == null) b = a;          // aiming at the sky: show just the first corner
		if (a == null) return;
		int[] lo = {Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.min(a[2], b[2])};
		int[] hi = {Math.max(a[0], b[0]) + 1, Math.max(a[1], b[1]) + 1, Math.max(a[2], b[2]) + 1};
		Vec3 camera = context.levelState().cameraRenderState.pos;
		PoseStack pose = context.poseStack();
		pose.pushPose();
		pose.translate(-camera.x, -camera.y, -camera.z);
		context.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.debugFilledBox(), (p, buffer) -> {
			drawFrame(p, buffer, lo[0], lo[1], lo[2], hi[0], hi[1], hi[2], ARGB.color(230, 0xFFD23F));
			drawBox(p, buffer, new AABB(lo[0], lo[1], lo[2], hi[0], hi[1], hi[2]).inflate(0.005), ARGB.color(40, 0xFFD23F));
		});
		pose.popPose();
	}

	/** Parses and transforms each palette state once per orientation change. */
	private static void refreshLooks(Messages.Plan plan, Transform transform) {
		String key = plan.id() + "|" + transform;
		if (key.equals(cachedFor)) return;
		Mirror mirror = transform.mirrorX() ? Mirror.FRONT_BACK : Mirror.NONE;
		Rotation rotation = switch (transform.rotation()) {
			case 90 -> Rotation.CLOCKWISE_90;
			case 180 -> Rotation.CLOCKWISE_180;
			case 270 -> Rotation.COUNTERCLOCKWISE_90;
			default -> Rotation.NONE;
		};
		List<Look> built = new ArrayList<>(plan.palette().size());
		for (String text : plan.palette()) {
			BlockState state;
			try { state = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, text, false).blockState(); }
			catch (Exception e) { state = net.minecraft.world.level.block.Blocks.CONCRETE.pick(net.minecraft.world.item.DyeColor.MAGENTA).defaultBlockState(); }
			state = state.mirror(mirror).rotate(rotation);
			List<AABB> boxes = state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).toAabbs();
			if (boxes.isEmpty()) boxes = List.of(new AABB(0.3, 0, 0.3, 0.7, 0.6, 0.7)); // e.g. plants without an outline
			int rgb = state.getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).col;
			if (rgb == 0) rgb = 0x9AA4B0;
			built.add(new Look(state, boxes, rgb));
		}
		looks = built;
		cachedFor = key;
	}

	private static void drawFrame(PoseStack.Pose pose, VertexConsumer buffer, Placement p, int color) {
		drawFrame(pose, buffer, p.anchorX(), p.anchorY(), p.anchorZ(), p.anchorX() + p.sizeX(), p.anchorY() + p.sizeY(), p.anchorZ() + p.sizeZ(), color);
	}

	private static void drawFrame(PoseStack.Pose pose, VertexConsumer buffer, double x0, double y0, double z0, double x1, double y1, double z1, int color) {
		double t = 0.04;
		double[][] edges = {
			{x0, y0, z0, x1, y0 + t, z0 + t}, {x0, y1 - t, z0, x1, y1, z0 + t}, {x0, y0, z1 - t, x1, y0 + t, z1}, {x0, y1 - t, z1 - t, x1, y1, z1},
			{x0, y0, z0, x0 + t, y1, z0 + t}, {x1 - t, y0, z0, x1, y1, z0 + t}, {x0, y0, z1 - t, x0 + t, y1, z1}, {x1 - t, y0, z1 - t, x1, y1, z1},
			{x0, y0, z0, x0 + t, y0 + t, z1}, {x1 - t, y0, z0, x1, y0 + t, z1}, {x0, y1 - t, z0, x0 + t, y1, z1}, {x1 - t, y1 - t, z0, x1, y1, z1}};
		for (double[] e : edges) drawBox(pose, buffer, new AABB(e[0], e[1], e[2], e[3], e[4], e[5]), color);
	}

	private static void drawBox(PoseStack.Pose pose, VertexConsumer v, AABB b, int color) {
		float x0 = (float) b.minX, y0 = (float) b.minY, z0 = (float) b.minZ, x1 = (float) b.maxX, y1 = (float) b.maxY, z1 = (float) b.maxZ;
		v.addVertex(pose, x0, y0, z0).setColor(color); v.addVertex(pose, x1, y0, z0).setColor(color); v.addVertex(pose, x1, y1, z0).setColor(color); v.addVertex(pose, x0, y1, z0).setColor(color);
		v.addVertex(pose, x1, y0, z1).setColor(color); v.addVertex(pose, x0, y0, z1).setColor(color); v.addVertex(pose, x0, y1, z1).setColor(color); v.addVertex(pose, x1, y1, z1).setColor(color);
		v.addVertex(pose, x0, y0, z1).setColor(color); v.addVertex(pose, x0, y0, z0).setColor(color); v.addVertex(pose, x0, y1, z0).setColor(color); v.addVertex(pose, x0, y1, z1).setColor(color);
		v.addVertex(pose, x1, y0, z0).setColor(color); v.addVertex(pose, x1, y0, z1).setColor(color); v.addVertex(pose, x1, y1, z1).setColor(color); v.addVertex(pose, x1, y1, z0).setColor(color);
		v.addVertex(pose, x0, y1, z0).setColor(color); v.addVertex(pose, x1, y1, z0).setColor(color); v.addVertex(pose, x1, y1, z1).setColor(color); v.addVertex(pose, x0, y1, z1).setColor(color);
		v.addVertex(pose, x0, y0, z1).setColor(color); v.addVertex(pose, x1, y0, z1).setColor(color); v.addVertex(pose, x1, y0, z0).setColor(color); v.addVertex(pose, x0, y0, z0).setColor(color);
	}
}
