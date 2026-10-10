package net.buildtheearth.terraminusminus.dataset.building;

import net.buildtheearth.terraminusminus.TerraMinusMinusTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BuildingShellGeoMathTest extends TerraMinusMinusTest {
    private static final double EQUATOR_DEGREE_METERS = 111_319.5d;
    private static final double DEGREE_METERS = 111_194.9d;
    private static final double EPSILON = 200.0d;

    @Test
    void haversineOneDegreeLatitude() {
        double distance = BuildingShellGeoMath.haversine(0.0d, 0.0d, 1.0d, 0.0d);
        assertEquals(DEGREE_METERS, distance, EPSILON);
    }

    @Test
    void haversineOneDegreeLongitudeAtEquator() {
        double distance = BuildingShellGeoMath.haversine(0.0d, 0.0d, 0.0d, 1.0d);
        assertEquals(EQUATOR_DEGREE_METERS, distance, EPSILON);
    }

    @Test
    void haversineSamePointIsZero() {
        assertEquals(0.0d, BuildingShellGeoMath.haversine(12.34d, 56.78d, 12.34d, 56.78d), 0.0d);
    }

    @Test
    void haversineScaledAtFortyFiveDegrees() {
        double distance = BuildingShellGeoMath.haversine(45.0d, 0.0d, 45.0d, 1.0d);
        double expected = EQUATOR_DEGREE_METERS * Math.cos(Math.toRadians(45.0d));
        assertEquals(expected, distance, EPSILON);
    }
}
