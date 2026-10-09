package net.buildtheearth.terraminusminus.dataset.building;

import net.buildtheearth.terraminusminus.TerraMinusMinusTest;
import net.buildtheearth.terraminusminus.projection.EquirectangularProjection;
import net.buildtheearth.terraminusminus.substitutes.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class BuildingShellVoxelizerTest extends TerraMinusMinusTest {

    @Test
    void voxelizeUnitCubeProducesHollowShell() {
        // 2x2x2 cube should produce all 8 blocks as surface blocks
        List<BuildingShell.Triangle> triangles = cubeTriangles(0.0d, 0.0d, 0.0d, 2.0d);
        BuildingShell shell = new BuildingShell("test", "cube", null, triangles, null, null);

        Set<BlockPos> blocks = BuildingShellVoxelizer.voxelize(shell, new EquirectangularProjection(), 0);

        assertEquals(8, blocks.size());
        for (int x = 0; x < 2; x++) {
            for (int y = 0; y < 2; y++) {
                for (int z = 0; z < 2; z++) {
                    assertTrue(blocks.contains(new BlockPos(x, y, z)), "expected block at (" + x + "," + y + "," + z + ")");
                }
            }
        }
    }

    @Test
    void voxelizeWithYOffset() {
        List<BuildingShell.Triangle> triangles = cubeTriangles(0.0d, 0.0d, 0.0d, 1.0d);
        BuildingShell shell = new BuildingShell("test", "cube", null, triangles, null, null);

        Set<BlockPos> blocks = BuildingShellVoxelizer.voxelize(shell, new EquirectangularProjection(), 10);
        assertTrue(blocks.contains(new BlockPos(0, 10, 0)));
        assertFalse(blocks.contains(new BlockPos(0, 0, 0)));
    }

    @Test
    void voxelizeEmptyShellReturnsEmptySet() {
        BuildingShell shell = new BuildingShell("test", "empty", null, new ArrayList<>(), null, null);
        Set<BlockPos> blocks = BuildingShellVoxelizer.voxelize(shell, new EquirectangularProjection(), 0);
        assertTrue(blocks.isEmpty());
    }

    private static List<BuildingShell.Triangle> cubeTriangles(double x, double z, double y, double size) {
        //noinspection UnnecessaryLocalVariable
        double x0 = x;
        double x1 = x + size;
        //noinspection UnnecessaryLocalVariable
        double z0 = z;
        double z1 = z + size;
        //noinspection UnnecessaryLocalVariable
        double y0 = y;
        double y1 = y + size;

        BuildingShell.Vertex v000 = v(x0, z0, y0);
        BuildingShell.Vertex v001 = v(x0, z1, y0);
        BuildingShell.Vertex v010 = v(x0, z0, y1);
        BuildingShell.Vertex v011 = v(x0, z1, y1);
        BuildingShell.Vertex v100 = v(x1, z0, y0);
        BuildingShell.Vertex v101 = v(x1, z1, y0);
        BuildingShell.Vertex v110 = v(x1, z0, y1);
        BuildingShell.Vertex v111 = v(x1, z1, y1);

        List<BuildingShell.Triangle> triangles = new ArrayList<>();
        // bottom y=0
        addQuad(triangles, v000, v100, v101, v001);
        // top y=size
        addQuad(triangles, v010, v011, v111, v110);
        // south z=0
        addQuad(triangles, v000, v010, v110, v100);
        // north z=size
        addQuad(triangles, v001, v101, v111, v011);
        // west x=0
        addQuad(triangles, v000, v001, v011, v010);
        // east x=size
        addQuad(triangles, v100, v110, v111, v101);
        return triangles;
    }

    private static void addQuad(List<BuildingShell.Triangle> list, BuildingShell.Vertex a, BuildingShell.Vertex b, BuildingShell.Vertex c, BuildingShell.Vertex d) {
        list.add(new BuildingShell.Triangle(new BuildingShell.Vertex[]{ a, b, c }));
        list.add(new BuildingShell.Triangle(new BuildingShell.Vertex[]{ a, c, d }));
    }

    private static BuildingShell.Vertex v(double lon, double lat, double elevation) {
        return new BuildingShell.Vertex(lon, lat, elevation);
    }
}
