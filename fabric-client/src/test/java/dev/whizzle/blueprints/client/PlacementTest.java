package dev.whizzle.blueprints.client;

import dev.whizzle.blueprints.client.model.Placement;
import dev.whizzle.blueprints.client.model.Transform;
import org.junit.jupiter.api.Test;

import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PlacementTest {
	// An L-shaped 3x1x2 footprint: asymmetric, so every orientation is distinguishable.
	static final List<Placement.Block> L = List.of(new Placement.Block(0, 0, 0, 0), new Placement.Block(1, 0, 0, 0),
		new Placement.Block(2, 0, 0, 0), new Placement.Block(0, 0, 1, 1));

	static Set<String> placed(Placement p) {
		Set<String> out = new HashSet<>();
		p.forEachPlaced(b -> out.add(b.x() + "," + b.y() + "," + b.z()));
		return out;
	}

	@Test void visibleBoxStartsAtTheAnchorInEveryOrientation() {
		for (int r : new int[]{0, 90, 180, 270}) for (boolean m : new boolean[]{false, true}) {
			Placement p = new Placement(L, 100, 64, 200);
			p.setTransform(new Transform(r, m));
			p.moveTo(100, 64, 200);
			int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
			for (String s : placed(p)) { String[] c = s.split(","); minX = Math.min(minX, Integer.parseInt(c[0])); minZ = Math.min(minZ, Integer.parseInt(c[2])); }
			assertEquals(100, minX, r + " " + m); assertEquals(200, minZ, r + " " + m);
		}
	}
	@Test void serverPlacementFromOriginMatchesPreview() {
		// The plugin places local (x,y,z) at origin + transform(x,y,z). The preview must show exactly that.
		Placement p = new Placement(L, 10, 5, -7);
		p.setTransform(new Transform(90, true));
		int[] o = p.origin();
		Set<String> server = new HashSet<>();
		for (Placement.Block b : L) { int[] t = p.transform().apply(b.x(), b.y(), b.z()); server.add((o[0] + t[0]) + "," + (o[1] + t[1]) + "," + (o[2] + t[2])); }
		assertEquals(server, placed(p));
	}
	@Test void rotatingTurnsInPlace() {
		Placement p = new Placement(L, 0, 0, 0); // 3 wide (x), 2 deep (z): center (1.5, 1.0)
		p.setTransform(p.transform().rotatedClockwise()); // now 2 wide, 3 deep
		assertEquals(2, p.sizeX()); assertEquals(3, p.sizeZ());
		assertEquals(0, p.anchorX()); assertEquals(-1, p.anchorZ()); // floor(1.5-1)=0, floor(1-1.5)=-1
		for (int i = 0; i < 3; i++) p.setTransform(p.transform().rotatedClockwise());
		assertEquals(new Transform(0, false), p.transform()); assertEquals(3, p.sizeX());
	}
	@Test void fourClockwiseRotationsAndTwoMirrorsAreIdentity() {
		Transform t = new Transform(90, true);
		assertEquals(t, t.rotatedClockwise().rotatedClockwise().rotatedClockwise().rotatedClockwise());
		assertEquals(t, t.mirrored().mirrored());
		assertEquals(t, t.rotatedClockwise().rotatedCounterClockwise());
	}
	@Test void movesFollowTheCamera() {
		Placement p = new Placement(L, 0, 0, 0);
		p.moveRelative(0f, 1, 0, 0);    // facing south (+z): forward
		assertArrayEquals(new int[]{0, 0, 1}, new int[]{p.anchorX(), p.anchorY(), p.anchorZ()});
		p.moveRelative(180f, 0, 1, 0);  // facing north: right is east (+x)
		assertArrayEquals(new int[]{1, 0, 1}, new int[]{p.anchorX(), p.anchorY(), p.anchorZ()});
		p.moveRelative(90f, 2, 0, -1);  // facing west (-x), two forward, one down
		assertArrayEquals(new int[]{-1, -1, 1}, new int[]{p.anchorX(), p.anchorY(), p.anchorZ()});
		assertArrayEquals(new int[]{1, 0}, Placement.facing(-90f)); // east
		assertArrayEquals(new int[]{0, 1}, Placement.facing(359f));
	}
	@Test void placeInFrontPutsTheNearEdgeJustPastTheTarget() {
		Placement p = new Placement(L, 0, 0, 0); // 3 x 2
		p.placeInFront(10, 64, 10, 180f);         // facing north (-z)
		assertEquals(9, p.anchorX()); assertEquals(64, p.anchorY()); assertEquals(8, p.anchorZ()); // z 8..9, x 9..11
		p.placeInFront(10, 64, 10, -90f);         // facing east (+x)
		assertEquals(11, p.anchorX()); assertEquals(9, p.anchorZ());
	}
}
