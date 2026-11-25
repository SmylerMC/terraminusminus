package net.buildtheearth.terraminusminus.dataset.building;

/**
 * Shared geographic math utilities for building shell operations.
 *
 * @author Cinnazeyy
 */
public final class BuildingShellGeoMath {
    private static final double EARTH_RADIUS_METERS = 6_371_000.0d;

    private BuildingShellGeoMath() {
    }

    /**
     * Computes the great-circle distance between two lat/lon points using the Haversine formula.
     * Ref: <a href="https://en.wikipedia.org/wiki/Haversine_formula">Haversine formula</a>
     *
     * @return distance in meters
     */
    public static double haversine(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2.0d) * Math.sin(dLat / 2.0d)
                   + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2.0d) * Math.sin(dLon / 2.0d);
        double c = 2.0d * Math.atan2(Math.sqrt(a), Math.sqrt(1.0d - a));
        return EARTH_RADIUS_METERS * c;
    }
}