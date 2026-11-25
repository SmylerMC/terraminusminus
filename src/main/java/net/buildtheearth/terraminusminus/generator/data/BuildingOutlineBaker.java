package net.buildtheearth.terraminusminus.generator.data;

import lombok.NonNull;
import net.buildtheearth.terraminusminus.dataset.IElementDataset;
import net.buildtheearth.terraminusminus.dataset.vector.geometry.VectorGeometry;
import net.buildtheearth.terraminusminus.generator.CachedChunkData;
import net.buildtheearth.terraminusminus.generator.GeneratorDatasets;
import net.buildtheearth.terraminusminus.projection.OutOfProjectionBoundsException;
import net.buildtheearth.terraminusminus.substitutes.ChunkPos;
import net.buildtheearth.terraminusminus.util.CornerBoundingBox2d;
import net.buildtheearth.terraminusminus.util.bvh.BVH;
import net.buildtheearth.terraminusminus.util.bvh.Bounds2d;

import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;

/**
 * @author Cinnazeyy
 */
public class BuildingOutlineBaker implements IEarthDataBaker<BVH<VectorGeometry>[]> {
    private final String datasetKey;

    public BuildingOutlineBaker(@NonNull String datasetKey) {
        this.datasetKey = datasetKey;
    }

    @Override
    public CompletableFuture<BVH<VectorGeometry>[]> requestData(
            ChunkPos pos,
            GeneratorDatasets datasets,
            Bounds2d bounds,
            CornerBoundingBox2d boundsGeo
    ) throws OutOfProjectionBoundsException {
        @SuppressWarnings("unchecked")
        IElementDataset<BVH<VectorGeometry>> dataset = datasets.getCustom(this.datasetKey, null);
        if (dataset == null) return CompletableFuture.completedFuture(null);
        return dataset.getAsync(bounds.expand(16.0d).toCornerBB(datasets.projection(), false).toGeo());
    }

    @Override
    public void bake(ChunkPos pos, CachedChunkData.Builder builder, BVH<VectorGeometry>[] regions) {
        if (regions == null) return;

        int baseX = pos.getMinBlockX();
        int baseZ = pos.getMinBlockZ();
        Bounds2d chunkBounds = Bounds2d.of(baseX, baseX + 16, baseZ, baseZ + 16);

        Set<VectorGeometry> elements = new TreeSet<>();
        for (BVH<VectorGeometry> region : regions) {
            region.forEachIntersecting(chunkBounds, elements::add);
        }
        elements.forEach(element -> element.apply(builder, pos.x(), pos.z(), chunkBounds));
    }
}
