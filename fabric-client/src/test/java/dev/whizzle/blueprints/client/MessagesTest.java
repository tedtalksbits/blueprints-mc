package dev.whizzle.blueprints.client;

import dev.whizzle.blueprints.client.net.Messages;
import dev.whizzle.blueprints.client.net.Wire;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Message layouts follow docs/PROTOCOL.md; the plugin's ClientBridgeTest checks the same layouts from the other side. */
class MessagesTest {
	@Test void parsesEveryClientboundMessage() {
		assertEquals(new Messages.Welcome(1, true, 16, false, 4096, 65, true, 2097152),
			Messages.welcome(new Wire.Writer().varInt(1).bool(true).varInt(16).bool(false).varInt(4096).varInt(65).bool(true).varInt(2097152).bytes()));
		var catalog = Messages.catalog(new Wire.Writer().varInt(1).utf("cabin").utf("Cabin").varInt(12).varInt(5).varInt(3).varInt(4).bytes());
		assertEquals(new Messages.Entry("cabin", "Cabin", 12, 5, 3, 4), catalog.getFirst());
		var plan = Messages.plan(new Wire.Writer().utf("cabin").utf("Cabin").varInt(2).utf("minecraft:stone").utf("minecraft:oak_log[axis=x]")
			.varInt(2).varInt(0).varInt(0).varInt(0).varInt(0).varInt(1).varInt(0).varInt(0).varInt(1).bytes());
		assertEquals(2, plan.blocks().size()); assertEquals("minecraft:oak_log[axis=x]", plan.palette().get(plan.blocks().get(1).state()));
		var mats = Messages.materials(new Wire.Writer().utf("cabin").bool(true).int32(-3).int32(64).int32(9).varInt(1).utf("STONE").varInt(10).varInt(4).bytes());
		assertTrue(mats.chestFound()); assertEquals(-3, mats.chestX()); assertEquals(6, mats.totalMissing());
		assertEquals(new Messages.Result(false, "nope"), Messages.result(new Wire.Writer().bool(false).utf("nope").bytes()));
		var jobs = Messages.jobs(new Wire.Writer().varInt(1).utf("uuid").utf("cabin").utf("Cabin").varInt(3).varInt(10).utf("PAUSED_MATERIALS")
			.utf("Missing 1 STONE").int32(1).int32(2).int32(-3).bytes());
		assertTrue(jobs.getFirst().active()); assertEquals(-3, jobs.getFirst().z());
	}
	@Test void rejectsCorruptCountsAndPaletteIndexes() {
		assertThrows(IllegalArgumentException.class, () -> Messages.catalog(new Wire.Writer().varInt(-5).bytes()));
		assertThrows(IllegalArgumentException.class, () -> Messages.plan(new Wire.Writer().utf("a").utf("A").varInt(1).utf("minecraft:stone")
			.varInt(1).varInt(0).varInt(0).varInt(0).varInt(7).bytes()));
		assertThrows(IllegalArgumentException.class, () -> Messages.result(new byte[0]));
	}
	@Test void captureNamesBecomeValidIds() {
		assertEquals("my-cool-house", Messages.toId("  My Cool House! "));
		assertEquals("tower_2", Messages.toId("Tower_2"));
		var in = new Wire.Reader(Messages.capture("hut", new int[]{1, -60, 3}, new int[]{-4, -50, 9}));
		assertEquals("hut", in.utf()); assertEquals(1, in.int32()); assertEquals(-60, in.int32()); assertEquals(3, in.int32());
		assertEquals(-4, in.int32()); assertEquals(-50, in.int32()); assertEquals(9, in.int32());
	}
	@Test void uploadChunksCarryOffsetAndLengthPrefixedBytes() {
		var in = new Wire.Reader(Messages.uploadChunk(30000, new byte[]{7, 8, 9}));
		assertEquals(30000, in.varInt()); assertEquals(3, in.varInt());
		var begin = new Wire.Reader(Messages.uploadBegin("tower", 123456));
		assertEquals("tower", begin.utf()); assertEquals(123456, begin.varInt());
	}
	@Test void startEncodesSignedOriginAndTransform() {
		var in = new Wire.Reader(Messages.start("cabin", new int[]{-10, 64, 3}, 270, 1));
		assertEquals("cabin", in.utf()); assertEquals(-10, in.int32()); assertEquals(64, in.int32()); assertEquals(3, in.int32());
		assertEquals(270, in.varInt()); assertEquals(1, in.varInt());
	}
}
