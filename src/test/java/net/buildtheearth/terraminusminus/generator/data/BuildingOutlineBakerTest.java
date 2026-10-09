package net.buildtheearth.terraminusminus.generator.data;

import net.buildtheearth.terraminusminus.TerraMinusMinusTest;
import net.buildtheearth.terraminusminus.dataset.vector.geometry.BuildingOutlineDataset;
import net.buildtheearth.terraminusminus.dataset.vector.geometry.VectorGeometry;
import net.buildtheearth.terraminusminus.generator.CachedChunkData;
import net.buildtheearth.terraminusminus.generator.EarthGeneratorPipelines;
import net.buildtheearth.terraminusminus.generator.GeneratorDatasets;
import net.buildtheearth.terraminusminus.projection.EquirectangularProjection;
import net.buildtheearth.terraminusminus.substitutes.BlockState;
import net.buildtheearth.terraminusminus.substitutes.ChunkPos;
import net.buildtheearth.terraminusminus.util.bvh.BVH;
import net.buildtheearth.terraminusminus.util.bvh.Bounds2d;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

public class BuildingOutlineBakerTest extends TerraMinusMinusTest {
    private static final BlockState OUTLINE = BlockState.parse("minecraft:stone");
    private static final BlockState INTERIOR = BlockState.parse("minecraft:dirt");

    @Test
    void noDatasetDoesNothing() throws Exception {
        GeneratorDatasets datasets = new GeneratorDatasets(Collections.emptyMap(), new EquirectangularProjection());
        BuildingOutlineBaker baker = new BuildingOutlineBaker(EarthGeneratorPipelines.KEY_DATASET_BUILDING_OUTLINES);

        CompletableFuture<BVH<VectorGeometry>[]> future = baker.requestData(new ChunkPos(0, 0), datasets, Bounds2d.of(0.0d, 16.0d, 0.0d, 16.0d), null);

        assertNotNull(future);
        assertNull(future.get());

        CachedChunkData.Builder builder = CachedChunkData.builder();
        baker.bake(new ChunkPos(0, 0), builder, null);
        for (int i = 0; i < 16 * 16; i++) {
            assertNull(builder.surfaceBlocks()[i]);
        }
    }

    @Test
    void bakesIntersectingOutlines(@TempDir Path tempDir) throws Exception {
        writeTile(tempDir, 0, 0, "{\"type\":\"Feature\",\"id\":\"f1\",\"geometry\":{\"type\":\"Polygon\",\"coordinates\":[[[0.0,0.0],[16.0,0.0],[16.0,16.0],[0.0,16.0],[0.0,0.0]]]},\"properties\":{}}\n");

        BuildingOutlineDataset dataset = new BuildingOutlineDataset("test", tempDir, 1.0d, 0.0d, OUTLINE, INTERIOR, new EquirectangularProjection());
        GeneratorDatasets datasets = new GeneratorDatasets(Collections.singletonMap(EarthGeneratorPipelines.KEY_DATASET_BUILDING_OUTLINES, dataset), new EquirectangularProjection());
        BuildingOutlineBaker baker = new BuildingOutlineBaker(EarthGeneratorPipelines.KEY_DATASET_BUILDING_OUTLINES);

        CompletableFuture<BVH<VectorGeometry>[]> future = baker.requestData(new ChunkPos(0, 0), datasets, Bounds2d.of(0.0d, 16.0d, 0.0d, 16.0d), null);

        BVH<VectorGeometry>[] regions = future.get();
        assertNotNull(regions);

        CachedChunkData.Builder builder = CachedChunkData.builder();
        baker.bake(new ChunkPos(0, 0), builder, regions);

        assertEquals(INTERIOR, builder.surfaceBlocks()[7 * 16 + 7]);
        // suppressing to make it clear what im doing
        //noinspection PointlessArithmeticExpression
        assertEquals(OUTLINE, builder.surfaceBlocks()[0 * 16 + 7]);
    }

    private static void writeTile(Path tempDir, int x, int z, String content) throws Exception {
        Path tileDir = tempDir.resolve("tile").resolve(x + "");
        Files.createDirectories(tileDir);
        Files.write(tileDir.resolve(z + ".json"), content.getBytes());
    }
}
