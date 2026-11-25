package net.buildtheearth.terraminusminus.dataset.building;

import net.buildtheearth.terraminusminus.TerraMinusMinusTest;
import net.buildtheearth.terraminusminus.projection.EquirectangularProjection;
import net.buildtheearth.terraminusminus.projection.GeographicProjection;
import net.buildtheearth.terraminusminus.projection.transform.OffsetProjectionTransform;
import net.buildtheearth.terraminusminus.projection.transform.ScaleProjectionTransform;
import net.buildtheearth.terraminusminus.substitutes.BlockPos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class BuildingShellDatasetTest extends TerraMinusMinusTest {

    @Test
    void parseAndFindNearestBuilding(@TempDir Path tempDir) throws Exception {
        writeSampleDataset(tempDir);
        BuildingShellDataset dataset = new BuildingShellDataset("test", tempDir);

        BuildingShell nearest = dataset.findNearestBuilding(0.5d, 0.5d, 100_000.0d);
        assertNotNull(nearest);
        assertEquals("house", nearest.id());
        assertEquals("abc", nearest.uuid());
    }

    @Test
    void findNearestBuildingOutsideRadiusReturnsNull(@TempDir Path tempDir) throws Exception {
        writeSampleDataset(tempDir);
        BuildingShellDataset dataset = new BuildingShellDataset("test", tempDir);

        assertNull(dataset.findNearestBuilding(10.0d, 10.0d, 1_000.0d));
    }

    @Test
    void findBuildingsIntersecting(@TempDir Path tempDir) throws Exception {
        writeSampleDataset(tempDir);
        BuildingShellDataset dataset = new BuildingShellDataset("test", tempDir);

        List<BuildingShell> matches = dataset.findBuildingsIntersecting(0.5d, 0.5d, 1.5d, 1.5d);
        assertEquals(1, matches.size());
        assertEquals("house", matches.getFirst().id());
    }

    @Test
    void poleQueryDoesNotCrash(@TempDir Path tempDir) throws Exception {
        writeSampleDataset(tempDir);
        BuildingShellDataset dataset = new BuildingShellDataset("test", tempDir);

        assertDoesNotThrow(() -> dataset.findNearestBuilding(0.0d, 90.0d, 10_000.0d));
        assertNull(dataset.findNearestBuilding(0.0d, 90.0d, 10_000.0d));
    }

    @Test
    void negativeRadiusThrows(@TempDir Path tempDir) throws Exception {
        writeSampleDataset(tempDir);
        BuildingShellDataset dataset = new BuildingShellDataset("test", tempDir);

        assertThrows(IllegalArgumentException.class, () -> dataset.findNearestBuilding(0.0d, 0.0d, -1.0d));
    }

    @Test
    void realWorldBuildingShell(@TempDir Path tempDir) throws Exception {
        copyShellResource(tempDir);
        BuildingShellDataset dataset = new BuildingShellDataset("real", tempDir);

        List<BuildingShell> shells = dataset.findBuildingsIntersecting(8.0d, 47.0d, 9.0d, 48.0d);
        assertEquals(1, shells.size());
        BuildingShell shell = shells.getFirst();

        double scale = 100_000.0d;
        double dx = -shell.bounds().minLon() * scale;
        double dy = -shell.bounds().minLat() * scale;
        GeographicProjection projection = new OffsetProjectionTransform(new ScaleProjectionTransform(new EquirectangularProjection(), scale, scale), dx, dy);
        int yOffset = -(int) Math.floor(shell.bounds().minZ());

        Set<BlockPos> blocks = BuildingShellVoxelizer.voxelize(shell, projection, yOffset);
        assertFalse(blocks.isEmpty(), "real building shell should voxelize to non-empty blocks");

        IntSummaryStatistics xs = blocks.stream().mapToInt(BlockPos::x).summaryStatistics();
        IntSummaryStatistics ys = blocks.stream().mapToInt(BlockPos::y).summaryStatistics();
        IntSummaryStatistics zs = blocks.stream().mapToInt(BlockPos::z).summaryStatistics();

        double footprintWidth = (shell.bounds().maxLon() - shell.bounds().minLon()) * scale;
        double footprintDepth = (shell.bounds().maxLat() - shell.bounds().minLat()) * scale;
        double height = shell.bounds().maxZ() - shell.bounds().minZ();

        assertEquals(0, xs.getMin(), 1.0d, "shell should start near x=0");
        assertEquals(0, zs.getMin(), 1.0d, "shell should start near z=0");
        assertEquals(0, ys.getMin(), 1.0d, "shell should start near y=0");
        assertTrue(xs.getMax() <= footprintWidth + 2.0d, "shell x extent too large");
        assertTrue(zs.getMax() <= footprintDepth + 2.0d, "shell z extent too large");
        assertTrue(ys.getMax() <= height + 2.0d, "shell height too large");
    }

    private static void copyShellResource(Path tempDir) throws Exception {
        Path tileDir = tempDir.resolve("1091-34");
        Files.createDirectories(tileDir);
        try (InputStream index = BuildingShellDatasetTest.class.getResourceAsStream("/shell/tile_index.json");
             InputStream buildings = BuildingShellDatasetTest.class.getResourceAsStream("/shell/1091-34/buildings.jsonl")) {
            assertNotNull(index);
            Files.copy(index, tempDir.resolve("tile_index.json"));
            assertNotNull(buildings);
            Files.copy(buildings, tileDir.resolve("buildings.jsonl"));
        }
    }

    private static void writeSampleDataset(Path tempDir) throws Exception {
        Path tileDir = tempDir.resolve("tile");
        Files.createDirectories(tileDir);

        String building = "{"
                + "\"id\":\"house\","
                + "\"properties\":{\"uuid\":\"abc\"},"
                + "\"triangles\":[[[0.0,0.0,0.0],[1.0,0.0,0.0],[0.0,1.0,0.0]]],"
                + "\"bounds\":{\"minLon\":0.0,\"minLat\":0.0,\"minZ\":0.0,\"maxLon\":1.0,\"maxLat\":1.0,\"maxZ\":0.0},"
                + "\"footprint\":{\"type\":\"Polygon\",\"coordinates\":[[[0.0,0.0],[0.0,1.0],[1.0,1.0],[1.0,0.0],[0.0,0.0]]]}"
                + "}";

        Files.write(tileDir.resolve("buildings.jsonl"), (building + "\n\nnot valid json\n").getBytes());
        Files.write(tempDir.resolve("tile_index.json"), ("{\"tiles\":[{\"name\":\"tile\",\"bounds\":{\"minLon\":-1.0,\"minLat\":-1.0,\"maxLon\":2.0,\"maxLat\":2.0}}]}").getBytes());
    }
}
