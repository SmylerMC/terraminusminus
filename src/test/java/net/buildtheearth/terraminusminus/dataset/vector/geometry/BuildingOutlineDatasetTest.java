package net.buildtheearth.terraminusminus.dataset.vector.geometry;

import net.buildtheearth.terraminusminus.TerraMinusMinusTest;
import net.buildtheearth.terraminusminus.dataset.vector.geometry.line.OutlineFillPolygon;
import net.buildtheearth.terraminusminus.projection.EquirectangularProjection;
import net.buildtheearth.terraminusminus.substitutes.BlockState;
import net.buildtheearth.terraminusminus.substitutes.ChunkPos;
import net.buildtheearth.terraminusminus.util.CornerBoundingBox2d;
import net.buildtheearth.terraminusminus.util.bvh.BVH;
import net.buildtheearth.terraminusminus.util.bvh.Bounds2d;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

public class BuildingOutlineDatasetTest extends TerraMinusMinusTest {

    private static final BlockState OUTLINE = BlockState.parse("minecraft:stone");
    private static final BlockState INTERIOR = BlockState.parse("minecraft:dirt");

    @Test
    void loadExistingTile(@TempDir Path tempDir) throws Exception {
        writeTile(tempDir, 0, 0, "{\"type\":\"Feature\",\"id\":\"f1\",\"geometry\":{\"type\":\"Polygon\",\"coordinates\":[[[0.0,0.0],[1.0,0.0],[1.0,1.0],[0.0,1.0],[0.0,0.0]]]},\"properties\":{}}\n");
        BuildingOutlineDataset dataset = newDataset(tempDir);

        BVH<VectorGeometry> bvh = dataset.getAsync(new ChunkPos(0, 0)).get();
        assertEquals(1, bvh.size());
        assertInstanceOf(OutlineFillPolygon.class, bvh.getAllIntersecting(Bounds2d.of(0.0d, 1.0d, 0.0d, 1.0d)).get(0));
    }

    @Test
    void loadMissingTileReturnsEmptyBvh(@TempDir Path tempDir) throws Exception {
        BuildingOutlineDataset dataset = newDataset(tempDir);

        BVH<VectorGeometry> bvh = dataset.getAsync(new ChunkPos(0, 0)).get();
        assertEquals(0, bvh.size());
    }

    @Test
    void getAsyncReturnsNullWhenNoTilesExist(@TempDir Path tempDir) throws Exception {
        BuildingOutlineDataset dataset = newDataset(tempDir);

        CornerBoundingBox2d bounds = new CornerBoundingBox2d(10.0d, 10.0d, 1.0d, 1.0d, new EquirectangularProjection(), true);
        CompletableFuture<BVH<VectorGeometry>[]> future = dataset.getAsync(bounds);
        assertNull(future.get());
    }

    @Test
    void getAsyncReturnsBvhsForIntersectingTiles(@TempDir Path tempDir) throws Exception {
        writeTile(tempDir, 0, 0, "{\"type\":\"Feature\",\"id\":\"f1\",\"geometry\":{\"type\":\"Polygon\",\"coordinates\":[[[0.0,0.0],[1.0,0.0],[1.0,1.0],[0.0,1.0],[0.0,0.0]]]},\"properties\":{}}\n");
        BuildingOutlineDataset dataset = newDataset(tempDir);

        CornerBoundingBox2d bounds = new CornerBoundingBox2d(0.0d, 0.0d, 1.0d, 1.0d, new EquirectangularProjection(), true);
        BVH<VectorGeometry>[] bvhs = dataset.getAsync(bounds).get();
        assertNotNull(bvhs);
        long nonEmpty = java.util.Arrays.stream(bvhs).filter(bvh -> bvh.size() > 0).count();
        assertEquals(1L, nonEmpty);
    }

    private static BuildingOutlineDataset newDataset(Path tempDir) {
        return new BuildingOutlineDataset("test", tempDir, 1.0d, 0.0d, OUTLINE, INTERIOR, new EquirectangularProjection());
    }

    private static void writeTile(Path tempDir, int x, int z, String content) throws Exception {
        Path tileDir = tempDir.resolve("tile").resolve(x + "");
        Files.createDirectories(tileDir);
        Files.write(tileDir.resolve(z + ".json"), content.getBytes());
    }
}
