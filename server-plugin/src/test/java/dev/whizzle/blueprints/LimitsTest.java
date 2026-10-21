package dev.whizzle.blueprints;

import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** limits.max-blocks / limits.max-span from config.yml govern every plan, with hard ceilings. */
class LimitsTest {
    @BeforeEach void setUp() { MockBukkit.mock(); }
    @AfterEach void tearDown() {
        ConstructionPlan.configureLimits(ConstructionPlan.DEFAULT_MAX_BLOCKS, ConstructionPlan.DEFAULT_MAX_SPAN);
        MockBukkit.unmock();
    }
    static ConstructionPlan row(int length) {
        List<BlueprintBlock> blocks = new ArrayList<>();
        for (int x = 0; x < length; x++) blocks.add(new BlueprintBlock(x, 0, 0, "minecraft:stone"));
        return PlanCompiler.compile(new BlueprintDefinition("row", "Row", blocks));
    }

    @Test void configuredLimitsApplyToBlocksAndSpan() {
        ConstructionPlan.configureLimits(10, 6);
        assertEquals(6, row(6).blockCount());
        assertThrows(IllegalArgumentException.class, () -> row(7));    // side of 7 > span 6
        ConstructionPlan.configureLimits(5, 100);
        assertThrows(IllegalArgumentException.class, () -> row(6));    // 6 blocks > 5
    }
    @Test void raisingLimitsAllowsLargerPlansAndTransforms() {
        ConstructionPlan.configureLimits(200, 100);
        ConstructionPlan big = row(100);
        assertEquals(100, PlanTransformer.apply(big, new BlueprintTransform(180, BlueprintTransform.Mirror.NONE)).blockCount()); // offsets reach -99
    }
    @Test void valuesAreClampedToTheEngineCeilings() {
        ConstructionPlan.configureLimits(10_000_000, 10_000);
        assertEquals(ConstructionPlan.CEILING_BLOCKS, ConstructionPlan.maxBlocks());
        assertEquals(ConstructionPlan.CEILING_SPAN, ConstructionPlan.maxSpan());
        ConstructionPlan.configureLimits(0, -5);
        assertEquals(1, ConstructionPlan.maxBlocks()); assertEquals(1, ConstructionPlan.maxSpan());
    }
}
