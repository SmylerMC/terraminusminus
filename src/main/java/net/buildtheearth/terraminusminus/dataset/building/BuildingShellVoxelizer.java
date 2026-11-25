package net.buildtheearth.terraminusminus.dataset.building;


import lombok.NonNull;
import net.buildtheearth.terraminusminus.projection.GeographicProjection;
import net.buildtheearth.terraminusminus.projection.OutOfProjectionBoundsException;
import net.buildtheearth.terraminusminus.substitutes.BlockPos;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Voxelizes a BuildingShell mesh into a hollow 1-block-thick shell.
 *
 * @author Cinnazeyy
 */
public final class BuildingShellVoxelizer {
    private static final double TWO_PI = 2.0d * Math.PI;
    private static final double AMBIGUOUS_LOW = Math.PI / 4.0d;

    private static final int RAY_COUNT = 5;
    private static final double[][] RAY_DIRECTIONS = generateFibonacciSphere();
    private static final int RAY_VOTE_THRESHOLD = (RAY_COUNT + 1) / 2;

    private static final double VERTEX_WELD_GRID = 1.0e-3d;
    private static final double EPSILON = 1e-6d;

    /**
     * Generates well-separated unit directions on the unit sphere using a spherical Fibonacci spiral.
     * Ref: <a href="https://observablehq.com/@meetamit/fibonacci-lattices">Fibonacci Lattices</a>
     */
    private static double[][] generateFibonacciSphere() {
        double[][] dirs = new double[BuildingShellVoxelizer.RAY_COUNT][3];
        double goldenAngle = Math.PI * (3.0d - Math.sqrt(5.0d));
        for (int i = 0; i < BuildingShellVoxelizer.RAY_COUNT; i++) {
            double y = 1.0d - 2.0d * (i + 0.5d) / BuildingShellVoxelizer.RAY_COUNT;
            double r = Math.sqrt(1.0d - y * y);
            double phi = i * goldenAngle;
            dirs[i][0] = r * Math.cos(phi);
            dirs[i][1] = y;
            dirs[i][2] = r * Math.sin(phi);
        }
        return dirs;
    }

    /**
     * voxelizes a {@code BuildingShell} into block positions
     *
     * @param shell      the shell to voxelize
     * @param projection the projection to use
     * @param yOffset    elevation offset
     * @return block positions forming the hollow shell
     */
    public static Set<BlockPos> voxelize(@NonNull BuildingShell shell, @NonNull GeographicProjection projection, int yOffset) {
        Map<LongPoint, Vec3> weldMap = new HashMap<>();
        Triangle[] mcTriangles = new Triangle[shell.triangles().size()];
        int triIdx = 0;
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;

        for (BuildingShell.Triangle tri : shell.triangles()) {
            Vec3[] verts = new Vec3[3];
            for (int i = 0; i < 3; i++) {
                BuildingShell.Vertex v = tri.vertices()[i];
                try {
                    double[] mc = projection.fromGeo(v.lon(), v.lat());
                    Vec3 welded = weldVertex(weldMap, mc[0], v.elevation() + yOffset, mc[1]);
                    verts[i] = welded;
                    minX = Math.min(minX, welded.x);
                    minY = Math.min(minY, welded.y);
                    minZ = Math.min(minZ, welded.z);
                    maxX = Math.max(maxX, welded.x);
                    maxY = Math.max(maxY, welded.y);
                    maxZ = Math.max(maxZ, welded.z);
                } catch (OutOfProjectionBoundsException e) {
                    verts[i] = null;
                }
            }
            if (verts[0] == null || verts[1] == null || verts[2] == null) continue;
            if (verts[0].equals(verts[1]) || verts[1].equals(verts[2]) || verts[0].equals(verts[2])) continue;
            mcTriangles[triIdx++] = new Triangle(verts[0], verts[1], verts[2]);
        }

        if (triIdx == 0) return Collections.emptySet();

        Triangle[] validTriangles = new Triangle[triIdx];
        System.arraycopy(mcTriangles, 0, validTriangles, 0, triIdx);

        int startX = (int) Math.floor(minX) - 1;
        int startY = (int) Math.floor(minY) - 1;
        int startZ = (int) Math.floor(minZ) - 1;
        int endX = (int) Math.ceil(maxX) + 1;
        int endY = (int) Math.ceil(maxY) + 1;
        int endZ = (int) Math.ceil(maxZ) + 1;

        // classify inside/outside per block
        boolean[][][] inside = getInsideBlocks(startX, startY, startZ, endX, endY, endZ, validTriangles);

        // extract surface blocks (inside with at least one outside neighbor)
        Set<BlockPos> shellBlocks = new HashSet<>();
        int dx = endX - startX;
        int dy = endY - startY;
        int dz = endZ - startZ;

        for (int ix = 0; ix <= dx; ix++) {
            for (int iy = 0; iy <= dy; iy++) {
                for (int iz = 0; iz <= dz; iz++) {
                    if (!inside[ix][iy][iz]) continue;

                    boolean isSurface =
                            (ix == 0 || !inside[ix - 1][iy][iz]) ||
                            (ix == dx || !inside[ix + 1][iy][iz]) ||
                            (iy == 0 || !inside[ix][iy - 1][iz]) ||
                            (iy == dy || !inside[ix][iy + 1][iz]) ||
                            (iz == 0 || !inside[ix][iy][iz - 1]) ||
                            (iz == dz || !inside[ix][iy][iz + 1]);

                    if (!isSurface) continue;
                    shellBlocks.add(new BlockPos(startX + ix, startY + iy, startZ + iz));
                }
            }
        }

        return shellBlocks;
    }

    /**
     * Samples a 3×3×3 grid of points to check whether the winding number produces clean results.
     * Returns true if the winding number is safe to use (no ambiguous values), false if the mesh needs ray-casting fallback.
     */
    private static boolean preflightWindingNumber(
            int startX, int startY, int startZ,
            int endX, int endY, int endZ,
            Triangle[] mesh
    ) {
        double[] fracs = { 0.25d, 0.5d, 0.75d };
        for (double fx : fracs) {
            for (double fy : fracs) {
                for (double fz : fracs) {
                    double ox = startX + fx * (endX - startX);
                    double oy = startY + fy * (endY - startY);
                    double oz = startZ + fz * (endZ - startZ);
                    double w = computeWindingNumber(ox, oy, oz, mesh);
                    if (w >= AMBIGUOUS_LOW && w < TWO_PI) return false;
                }
            }
        }
        return true;
    }

    /**
     * Classifies all blocks in the given bound as either inside or outside the given mesh.
     * Uses the generalized winding number if the mesh is clean, or multi ray voting if not.
     * @return boolean values for whether each block is inside or not
     */
    private static boolean[][][] getInsideBlocks(
            int startX, int startY, int startZ,
            int endX, int endY, int endZ,
            Triangle[] mesh
    ) {
        // check if the winding number produces clean results for this mesh
        boolean useWindingNumber = preflightWindingNumber(startX, startY, startZ, endX, endY, endZ, mesh);

        boolean[][][] inside = new boolean[endX - startX + 1][endY - startY + 1][endZ - startZ + 1];

        for (int bx = startX; bx <= endX; bx++) {
            for (int by = startY; by <= endY; by++) {
                for (int bz = startZ; bz <= endZ; bz++) {
                    double ox = bx + 0.5d;
                    double oy = by + 0.5d;
                    double oz = bz + 0.5d;

                    // generalized winding number
                    if (useWindingNumber) {
                        inside[bx - startX][by - startY][bz - startZ] = computeWindingNumber(ox, oy, oz, mesh) >= TWO_PI;
                        continue;
                    }

                    // multi ray voting
                    int votes = 0;
                    for (double[] dir : RAY_DIRECTIONS) {
                        if ((countRayIntersections(ox, oy, oz, dir[0], dir[1], dir[2], mesh) % 2) != 1) continue;
                        votes++;
                    }
                    inside[bx - startX][by - startY][bz - startZ] = votes >= RAY_VOTE_THRESHOLD;
                }
            }
        }
        return inside;
    }

    /**
     * Generalized winding number via the Van Oosterom–Strackee solid angle formula.
     * Ref: <a href="https://en.wikipedia.org/wiki/Solid_angle">Solid Angle</a>
     */
    private static double computeWindingNumber(double ox, double oy, double oz, Triangle[] triangles) {
        double sum = 0.0d;
        for (Triangle tri : triangles) {
            double ax = tri.v0.x - ox, ay = tri.v0.y - oy, az = tri.v0.z - oz;
            double bx = tri.v1.x - ox, by = tri.v1.y - oy, bz = tri.v1.z - oz;
            double cx = tri.v2.x - ox, cy = tri.v2.y - oy, cz = tri.v2.z - oz;

            double lenA = Math.sqrt(ax * ax + ay * ay + az * az);
            double lenB = Math.sqrt(bx * bx + by * by + bz * bz);
            double lenC = Math.sqrt(cx * cx + cy * cy + cz * cz);
            if (lenA < 1e-12d || lenB < 1e-12d || lenC < 1e-12d) continue;

            double numerator = ax * (by * cz - bz * cy)
                               + ay * (bz * cx - bx * cz)
                               + az * (bx * cy - by * cx);

            double denominator = lenA * lenB * lenC
                                 + (ax * bx + ay * by + az * bz) * lenC
                                 + (bx * cx + by * cy + bz * cz) * lenA
                                 + (cx * ax + cy * ay + cz * az) * lenB;

            if (Math.abs(denominator) < 1e-18d) continue;

            sum += 2.0d * Math.atan2(numerator, denominator);
        }
        return Math.abs(sum);
    }

    /**
     * Counts ray-mesh intersections via Möller–Trumbore.
     * Ref: <a href="https://en.wikipedia.org/wiki/M%C3%B6ller%E2%80%93Trumbore_intersection_algorithm">Möller-Trumbore intersection algorithm</a>
     */
    private static int countRayIntersections(
            double ox, double oy, double oz,
            double dx, double dy, double dz,
            Triangle[] triangles
    ) {
        int count = 0;
        for (Triangle tri : triangles) {
            if (rayIntersectsTriangle(ox, oy, oz, dx, dy, dz, tri)) count++;
        }
        return count;
    }

    private static boolean rayIntersectsTriangle(
            double ox, double oy, double oz,
            double dx, double dy, double dz,
            Triangle tri
    ) {
        double e1x = tri.v1.x - tri.v0.x;
        double e1y = tri.v1.y - tri.v0.y;
        double e1z = tri.v1.z - tri.v0.z;

        double e2x = tri.v2.x - tri.v0.x;
        double e2y = tri.v2.y - tri.v0.y;
        double e2z = tri.v2.z - tri.v0.z;

        double hx = dy * e2z - dz * e2y;
        double hy = dz * e2x - dx * e2z;
        double hz = dx * e2y - dy * e2x;

        double a = e1x * hx + e1y * hy + e1z * hz;
        if (Math.abs(a) < EPSILON) return false;

        double f = 1.0d / a;
        double sx = ox - tri.v0.x, sy = oy - tri.v0.y, sz = oz - tri.v0.z;
        double u = f * (sx * hx + sy * hy + sz * hz);
        if (u < 0.0d || u > 1.0d) return false;

        double qx = sy * e1z - sz * e1y, qy = sz * e1x - sx * e1z, qz = sx * e1y - sy * e1x;
        double v = f * (dx * qx + dy * qy + dz * qz);
        if (v < 0.0d || u + v > 1.0d) return false;
        return f * (e2x * qx + e2y * qy + e2z * qz) > EPSILON;
    }

    /**
     * Snap a vertex to the quantization grid and deduplicate.
     */
    private static Vec3 weldVertex(Map<LongPoint, Vec3> weldMap, double x, double y, double z) {
        long gx = Math.round(x / VERTEX_WELD_GRID);
        long gy = Math.round(y / VERTEX_WELD_GRID);
        long gz = Math.round(z / VERTEX_WELD_GRID);
        LongPoint key = new LongPoint(gx, gy, gz);
        return weldMap.computeIfAbsent(key, k -> new Vec3(gx * VERTEX_WELD_GRID, gy * VERTEX_WELD_GRID, gz * VERTEX_WELD_GRID));
    }

    private static final class Vec3 {
        public final double x;
        public final double y;
        public final double z;

        Vec3(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static final class Triangle {
        public final Vec3 v0;
        public final Vec3 v1;
        public final Vec3 v2;

        Triangle(Vec3 v0, Vec3 v1, Vec3 v2) {
            this.v0 = v0;
            this.v1 = v1;
            this.v2 = v2;
        }
    }

    private static final class LongPoint {
        public final long x;
        public final long y;
        public final long z;

        LongPoint(long x, long y, long z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof LongPoint)) return false;
            LongPoint other = (LongPoint) o;
            return this.x == other.x && this.y == other.y && this.z == other.z;
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.x, this.y, this.z);
        }
    }
}