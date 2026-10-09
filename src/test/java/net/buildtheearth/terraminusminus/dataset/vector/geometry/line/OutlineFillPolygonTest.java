package net.buildtheearth.terraminusminus.dataset.vector.geometry.line;

import net.buildtheearth.terraminusminus.TerraMinusMinusTest;
import net.buildtheearth.terraminusminus.dataset.geojson.GeoJson;
import net.buildtheearth.terraminusminus.dataset.geojson.GeoJsonObject;
import net.buildtheearth.terraminusminus.dataset.geojson.Geometry;
import net.buildtheearth.terraminusminus.dataset.geojson.geometry.LineString;
import net.buildtheearth.terraminusminus.dataset.geojson.geometry.MultiPolygon;
import net.buildtheearth.terraminusminus.dataset.geojson.geometry.Point;
import net.buildtheearth.terraminusminus.dataset.geojson.geometry.Polygon;
import net.buildtheearth.terraminusminus.dataset.geojson.object.Feature;
import net.buildtheearth.terraminusminus.generator.CachedChunkData;
import net.buildtheearth.terraminusminus.substitutes.BlockState;
import net.buildtheearth.terraminusminus.util.bvh.Bounds2d;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class OutlineFillPolygonTest extends TerraMinusMinusTest {
    private static final BlockState OUTLINE = BlockState.parse("minecraft:stone");
    private static final BlockState INTERIOR = BlockState.parse("minecraft:dirt");

    @Test
    void fillsRectangleWithOutlineAndInterior() {
        OutlineFillPolygon polygon = rectangle(0.0d, 0.0d, 16.0d, 16.0d);
        CachedChunkData.Builder builder = CachedChunkData.builder();

        polygon.apply(builder, 0, 0, Bounds2d.of(0.0d, 16.0d, 0.0d, 16.0d));

        // interior block
        assertEquals(INTERIOR, builder.surfaceBlocks()[7 * 16 + 7]);

        // boundary blocks
        // suppressing to keep it clear what im doing
        //noinspection PointlessArithmeticExpression
        assertEquals(OUTLINE, builder.surfaceBlocks()[7 * 16 + 0]);
        //noinspection PointlessArithmeticExpression
        assertEquals(OUTLINE, builder.surfaceBlocks()[0 * 16 + 7]);
        assertEquals(OUTLINE, builder.surfaceBlocks()[15 * 16 + 15]);
    }

    @Test
    void polygonOutsideChunkDoesNothing() {
        OutlineFillPolygon polygon = rectangle(32.0d, 0.0d, 48.0d, 16.0d);
        CachedChunkData.Builder builder = CachedChunkData.builder();

        polygon.apply(builder, 0, 0, Bounds2d.of(0.0d, 16.0d, 0.0d, 16.0d));

        for (int i = 0; i < 16 * 16; i++) {
            assertNull(builder.surfaceBlocks()[i]);
        }
    }

    @Test
    void partialOverlapMarksOnlyInsideBlocks() {
        OutlineFillPolygon polygon = rectangle(8.0d, 0.0d, 24.0d, 16.0d);
        CachedChunkData.Builder builder = CachedChunkData.builder();

        polygon.apply(builder, 0, 0, Bounds2d.of(0.0d, 16.0d, 0.0d, 16.0d));

        // left half of the chunk should remain untouched
        assertNull(builder.surfaceBlocks()[3 * 16 + 3]);
        // right half should be filled
        assertNotNull(builder.surfaceBlocks()[12 * 16 + 3]);
    }

    @Test
    void realWorldBuildingOutline() throws Exception {
        String json;
        try (InputStream in = OutlineFillPolygonTest.class.getResourceAsStream("/outline/tile/201/1478.json")) {
            assertNotNull(in);
            json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        GeoJsonObject obj = GeoJson.parse(json);
        assertInstanceOf(Feature.class, obj);
        Feature feature = (Feature) obj;
        Geometry geometry = feature.geometry();
        assertInstanceOf(MultiPolygon.class, geometry);
        MultiPolygon multi = (MultiPolygon) geometry;

        Bounds2d geoBounds = multi.bounds();
        double scale = 100_000.0d;
        MultiPolygon scaled = multi.project((lon, lat) -> new double[]{
                (lon - geoBounds.minX()) * scale,
                (lat - geoBounds.minZ()) * scale
        });

        OutlineFillPolygon polygon = new OutlineFillPolygon(feature.id(), 0.0d, scaled, OUTLINE, INTERIOR);
        CachedChunkData.Builder builder = CachedChunkData.builder();
        polygon.apply(builder, 0, 0, Bounds2d.of(0.0d, 16.0d, 0.0d, 16.0d));

        long outlineBlocks = 0;
        long interiorBlocks = 0;
        for (BlockState state : builder.surfaceBlocks()) {
            if (OUTLINE.equals(state)) {
                outlineBlocks++;
            } else if (INTERIOR.equals(state)) {
                interiorBlocks++;
            }
        }

        assertTrue(outlineBlocks > 0, "real outline should have boundary blocks");
        assertTrue(interiorBlocks > 0, "real outline should have interior blocks");
        assertTrue(outlineBlocks + interiorBlocks > 50, "real outline should cover a meaningful area");
    }

    private static OutlineFillPolygon rectangle(double minX, double minZ, double maxX, double maxZ) {
        LineString ring = new LineString(new Point[]{
                p(minX, minZ),
                p(maxX, minZ),
                p(maxX, maxZ),
                p(minX, maxZ),
                p(minX, minZ)
        });
        Polygon polygon = new Polygon(ring, new LineString[0]);
        return new OutlineFillPolygon("test", 0.0d, new MultiPolygon(new Polygon[]{ polygon }), OUTLINE, INTERIOR);
    }

    private static Point p(double lon, double lat) {
        return new Point(lon, lat);
    }
}
