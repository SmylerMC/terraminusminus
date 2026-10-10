package net.buildtheearth.terraminusminus.dataset.building;

import net.buildtheearth.terraminusminus.TerraMinusMinusTest;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

public class BuildingShellBoundsTest extends TerraMinusMinusTest {

    @Test
    void intersects2DOverlapping() {
        BuildingShellBounds a = new BuildingShellBounds(0.0d, 0.0d, 0.0d, 2.0d, 2.0d, 2.0d);
        BuildingShellBounds b = new BuildingShellBounds(1.0d, 1.0d, 0.0d, 3.0d, 3.0d, 1.0d);
        assertTrue(a.intersects2D(b.minLon(), b.minLat(), b.maxLon(), b.maxLat()));
    }

    @Test
    void intersects2DDisjoint() {
        BuildingShellBounds a = new BuildingShellBounds(0.0d, 0.0d, 0.0d, 1.0d, 1.0d, 1.0d);
        BuildingShellBounds b = new BuildingShellBounds(2.0d, 2.0d, 0.0d, 3.0d, 3.0d, 1.0d);
        assertFalse(a.intersects2D(b.minLon(), b.minLat(), b.maxLon(), b.maxLat()));
    }

    @Test
    void distanceToInsideIsZero() {
        BuildingShellBounds bounds = new BuildingShellBounds(0.0d, 0.0d, 0.0d, 1.0d, 1.0d, 1.0d);
        assertEquals(0.0d, bounds.distanceTo(0.5d, 0.5d), 1.0d);
    }

    @Test
    void distanceToCornerIsApproximate() {
        BuildingShellBounds bounds = new BuildingShellBounds(0.0d, 0.0d, 0.0d, 1.0d, 1.0d, 1.0d);
        double distance = bounds.distanceTo(2.0d, 2.0d);
        assertTrue(distance > 150_000.0d && distance < 165_000.0d, "unexpected diagonal distance: " + distance);
    }

    @Test
    void fromTrianglesComputesBounds() {
        BuildingShell.Triangle tri = new BuildingShell.Triangle(new BuildingShell.Vertex[]{
                new BuildingShell.Vertex(1.0d, 2.0d, 3.0d),
                new BuildingShell.Vertex(4.0d, 5.0d, 6.0d),
                new BuildingShell.Vertex(7.0d, 8.0d, 9.0d)
        });
        BuildingShellBounds bounds = BuildingShellBounds.fromTriangles(Collections.singletonList(tri));
        assertEquals(1.0d, bounds.minLon(), 0.0d);
        assertEquals(2.0d, bounds.minLat(), 0.0d);
        assertEquals(3.0d, bounds.minZ(), 0.0d);
        assertEquals(7.0d, bounds.maxLon(), 0.0d);
        assertEquals(8.0d, bounds.maxLat(), 0.0d);
        assertEquals(9.0d, bounds.maxZ(), 0.0d);
    }

    @Test
    void fromEmptyTrianglesReturnsZeroBounds() {
        BuildingShellBounds bounds = BuildingShellBounds.fromTriangles(Collections.emptyList());
        assertEquals(0.0d, bounds.minLon(), 0.0d);
        assertEquals(0.0d, bounds.maxLon(), 0.0d);
    }
}
