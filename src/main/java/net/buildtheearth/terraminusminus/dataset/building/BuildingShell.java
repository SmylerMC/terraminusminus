package net.buildtheearth.terraminusminus.dataset.building;


import lombok.NonNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A building shell represented as a triangle mesh in WGS84 coordinates.
 *
 * @author Cinnazeyy
 */
public final class BuildingShell {
    private final String datasetId;
    private final String id;
    private final String uuid;
    private final List<Triangle> triangles;
    private final BuildingShellBounds bounds;
    private final Footprint footprint;

    public BuildingShell(String datasetId, String id, String uuid, List<Triangle> triangles, BuildingShellBounds bounds, Footprint footprint) {
        this.datasetId = datasetId;
        this.id = id;
        this.uuid = uuid;
        this.triangles = triangles;
        this.bounds = bounds != null ? bounds : BuildingShellBounds.fromTriangles(triangles);
        this.footprint = footprint != null ? footprint : Footprint.fromBounds(this.bounds);
    }

    public String datasetId() {
        return this.datasetId;
    }

    public String id() {
        return this.id;
    }

    public String uuid() {
        return this.uuid;
    }

    public List<Triangle> triangles() {
        return this.triangles;
    }

    public BuildingShellBounds bounds() {
        return this.bounds;
    }

    public Footprint footprint() {
        return this.footprint;
    }

    public double[] centroid() {
        double sumLon = 0.0d;
        double sumLat = 0.0d;
        double sumZ = 0.0d;
        int count = 0;

        for (Triangle tri : this.triangles) {
            for (Vertex v : tri.vertices()) {
                sumLon += v.lon();
                sumLat += v.lat();
                sumZ += v.elevation();
                count++;
            }
        }

        if (count == 0) return new double[]{0.0d, 0.0d, 0.0d};
        return new double[]{sumLon / count, sumLat / count, sumZ / count};
    }

    public boolean footprintOverlaps(BuildingShell other) {
        return this.footprint.overlaps(other.footprint);
    }

    public static final class Triangle {
        private final Vertex[] vertices;

        public Triangle(Vertex[] vertices) {
            if (vertices.length != 3) {
                throw new IllegalArgumentException("Triangle must have exactly 3 vertices");
            }
            this.vertices = vertices;
        }

        public Vertex[] vertices() {
            return this.vertices;
        }
    }

    public static final class Vertex {
        private final double lon;
        private final double lat;
        private final double elevation;

        public Vertex(double lon, double lat, double elevation) {
            this.lon = lon;
            this.lat = lat;
            this.elevation = elevation;
        }

        public double lon() {
            return this.lon;
        }

        public double lat() {
            return this.lat;
        }

        public double elevation() {
            return this.elevation;
        }
    }

    public static final class Footprint {
        private final List<Ring> rings;
        private final BuildingShellBounds bounds;

        public Footprint(@NonNull List<Ring> rings, BuildingShellBounds bounds) {
            this.rings = rings;
            this.bounds = bounds;
        }

        public static Footprint fromBounds(BuildingShellBounds bounds) {
            List<Point> points = Collections.unmodifiableList(Arrays.asList(
                    new Point(bounds.minLon(), bounds.minLat()),
                    new Point(bounds.minLon(), bounds.maxLat()),
                    new Point(bounds.maxLon(), bounds.maxLat()),
                    new Point(bounds.maxLon(), bounds.minLat()),
                    new Point(bounds.minLon(), bounds.minLat())
            ));
            return new Footprint(Collections.singletonList(new Ring(points)), bounds);
        }

        public List<Ring> rings() {
            return this.rings;
        }

        public BuildingShellBounds bounds() {
            return this.bounds;
        }

        public boolean overlaps(Footprint other) {
            if (!this.bounds.intersects2D(other.bounds.minLon(), other.bounds.minLat(), other.bounds.maxLon(), other.bounds.maxLat())) {
                return false;
            }
            for (Ring ring : this.rings) {
                for (Point point : ring.points()) {
                    if (other.contains(point)) return true;
                }
            }
            for (Ring ring : other.rings) {
                for (Point point : ring.points()) {
                    if (this.contains(point)) return true;
                }
            }
            return false;
        }

        private boolean contains(Point point) {
            for (Ring ring : this.rings) {
                if (ring.contains(point)) return true;
            }
            return false;
        }
    }

    public static final class Ring {
        private final List<Point> points;

        public Ring(@NonNull List<Point> points) {
            this.points = points;
        }

        public List<Point> points() {
            return this.points;
        }

        boolean contains(Point point) {
            boolean inside = false;
            for (int i = 0, j = this.points.size() - 1; i < this.points.size(); j = i++) {
                Point current = this.points.get(i);
                Point previous = this.points.get(j);

                if (pointOnSegment(point, previous, current)) return true;

                boolean intersects = ((current.lat() > point.lat()) != (previous.lat() > point.lat()))
                                     && (point.lon() < (previous.lon() - current.lon()) * (point.lat() - current.lat()) / (previous.lat() - current.lat()) + current.lon());
                if (intersects) inside = !inside;
            }
            return inside;
        }

        private static boolean pointOnSegment(Point point, Point a, Point b) {
            double cross = (point.lon() - a.lon()) * (b.lat() - a.lat()) - (point.lat() - a.lat()) * (b.lon() - a.lon());
            if (Math.abs(cross) > 1.0e-12d) return false;

            double dot = (point.lon() - a.lon()) * (b.lon() - a.lon()) + (point.lat() - a.lat()) * (b.lat() - a.lat());
            if (dot < 0.0d) return false;

            double squaredLength = (b.lon() - a.lon()) * (b.lon() - a.lon()) + (b.lat() - a.lat()) * (b.lat() - a.lat());
            return dot <= squaredLength;
        }
    }

    public static final class Point {
        private final double lon;
        private final double lat;

        public Point(double lon, double lat) {
            this.lon = lon;
            this.lat = lat;
        }

        public double lon() {
            return this.lon;
        }

        public double lat() {
            return this.lat;
        }
    }
}
