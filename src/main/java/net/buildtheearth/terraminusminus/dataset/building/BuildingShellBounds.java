package net.buildtheearth.terraminusminus.dataset.building;


import java.util.List;

/**
 * Bounding box for a building shell in WGS84.
 *
 * @author Cinnazeyy
 */
public final class BuildingShellBounds {
    private final double minLon;
    private final double minLat;
    private final double minZ;
    private final double maxLon;
    private final double maxLat;
    private final double maxZ;

    public BuildingShellBounds(double minLon, double minLat, double minZ, double maxLon, double maxLat, double maxZ) {
        this.minLon = minLon;
        this.minLat = minLat;
        this.minZ = minZ;
        this.maxLon = maxLon;
        this.maxLat = maxLat;
        this.maxZ = maxZ;
    }

    public double minLon() {
        return this.minLon;
    }

    public double minLat() {
        return this.minLat;
    }

    public double minZ() {
        return this.minZ;
    }

    public double maxLon() {
        return this.maxLon;
    }

    public double maxLat() {
        return this.maxLat;
    }

    public double maxZ() {
        return this.maxZ;
    }

    public boolean intersects2D(double otherMinLon, double otherMinLat, double otherMaxLon, double otherMaxLat) {
        return this.minLon <= otherMaxLon && this.maxLon >= otherMinLon && this.minLat <= otherMaxLat && this.maxLat >= otherMinLat;
    }

    public double distanceTo(double lon, double lat) {
        double closestLon = clamp(lon, this.minLon, this.maxLon);
        double closestLat = clamp(lat, this.minLat, this.maxLat);
        return BuildingShellGeoMath.haversine(lat, lon, closestLat, closestLon);
    }

    private static double clamp(double value, double min, double max) {
        return value < min ? min : (Math.min(value, max));
    }

    public static BuildingShellBounds fromTriangles(List<BuildingShell.Triangle> triangles) {
        double minLon = Double.POSITIVE_INFINITY;
        double minLat = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxLon = Double.NEGATIVE_INFINITY;
        double maxLat = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;

        for (BuildingShell.Triangle tri : triangles) {
            for (BuildingShell.Vertex v : tri.vertices()) {
                minLon = Math.min(minLon, v.lon());
                minLat = Math.min(minLat, v.lat());
                minZ = Math.min(minZ, v.elevation());
                maxLon = Math.max(maxLon, v.lon());
                maxLat = Math.max(maxLat, v.lat());
                maxZ = Math.max(maxZ, v.elevation());
            }
        }

        return !Double.isInfinite(minLon)
                ? new BuildingShellBounds(minLon, minLat, minZ, maxLon, maxLat, maxZ)
                : new BuildingShellBounds(0.0d, 0.0d, 0.0d, 0.0d, 0.0d, 0.0d);
    }
}