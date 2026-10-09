package net.buildtheearth.terraminusminus.dataset.vector.geometry;

import static net.daporkchop.lib.common.util.PorkUtil.uncheckedCast;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import lombok.NonNull;
import net.buildtheearth.terraminusminus.dataset.IElementDataset;
import net.buildtheearth.terraminusminus.dataset.TiledDataset;
import net.buildtheearth.terraminusminus.dataset.geojson.GeoJson;
import net.buildtheearth.terraminusminus.dataset.geojson.GeoJsonObject;
import net.buildtheearth.terraminusminus.dataset.geojson.Geometry;
import net.buildtheearth.terraminusminus.dataset.geojson.geometry.MultiPolygon;
import net.buildtheearth.terraminusminus.dataset.geojson.geometry.Polygon;
import net.buildtheearth.terraminusminus.dataset.geojson.object.Feature;
import net.buildtheearth.terraminusminus.dataset.vector.geometry.line.OutlineFillPolygon;
import net.buildtheearth.terraminusminus.projection.EquirectangularProjection;
import net.buildtheearth.terraminusminus.projection.GeographicProjection;
import net.buildtheearth.terraminusminus.projection.OutOfProjectionBoundsException;
import net.buildtheearth.terraminusminus.substitutes.BlockState;
import net.buildtheearth.terraminusminus.substitutes.ChunkPos;
import net.buildtheearth.terraminusminus.util.CornerBoundingBox2d;
import net.buildtheearth.terraminusminus.util.bvh.BVH;
import net.buildtheearth.terraminusminus.util.bvh.Bounds2d;

/**
 * @author Cinnazeyy
 */
public class BuildingOutlineDataset extends TiledDataset<BVH<VectorGeometry>> implements IElementDataset<BVH<VectorGeometry>> {
    private final String id;
    private final Path tileDirectory;
    private final double priority;
    private final BlockState outlineBlock;
    private final BlockState interiorBlock;
    private final GeographicProjection geometryProjection;

    public BuildingOutlineDataset(
            @NonNull String id,
            @NonNull Path tileDirectory,
            double tileSizeDegrees,
            double priority,
            @NonNull BlockState outlineBlock,
            @NonNull BlockState interiorBlock,
            @NonNull GeographicProjection geometryProjection
    ) {
        super(new EquirectangularProjection(), tileSizeDegrees);
        this.id = id;
        this.tileDirectory = tileDirectory;
        this.priority = priority;
        this.outlineBlock = outlineBlock;
        this.interiorBlock = interiorBlock;
        this.geometryProjection = geometryProjection;
    }

    @Override
    public @NonNull CompletableFuture<BVH<VectorGeometry>> load(@NonNull ChunkPos key) {
        Path tileFile = this.tileFile(key);
        if (!Files.exists(tileFile)) {
            return CompletableFuture.completedFuture(BVH.of(new VectorGeometry[0]));
        }

        List<VectorGeometry> polygons = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(tileFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }

                GeoJsonObject obj = GeoJson.parse(line);
                if (!(obj instanceof Feature)) {
                    continue;
                }
                Feature feature = (Feature) obj;

                Geometry geometry = feature.geometry();
                if (geometry instanceof Polygon) {
                    geometry = new MultiPolygon(new Polygon[]{ (Polygon) geometry });
                }
                if (!(geometry instanceof MultiPolygon)) {
                    continue;
                }
                MultiPolygon multiPolygon = (MultiPolygon) geometry;

                MultiPolygon projected = multiPolygon.project(this.geometryProjection::fromGeo);
                String featureId = feature.id() != null ? feature.id() : "";
                polygons.add(new OutlineFillPolygon(
                        this.id + ":" + featureId,
                        this.priority,
                        projected,
                        this.outlineBlock,
                        this.interiorBlock
                ));
            }
        } catch (IOException | OutOfProjectionBoundsException e) {
            return CompletableFuture.completedFuture(BVH.of(new VectorGeometry[0]));
        }
        return CompletableFuture.completedFuture(BVH.of(polygons.toArray(new VectorGeometry[0])));
    }

    public void invalidateCache() {
        this.cache.invalidateAll();
    }

    @Override
    public CompletableFuture<BVH<VectorGeometry>[]> getAsync(@NonNull CornerBoundingBox2d bounds) throws OutOfProjectionBoundsException {
        Bounds2d localBounds = bounds.fromGeo(this.projection).axisAlign();
        ChunkPos[] tiles = localBounds.toTiles(this.tileSize);

        boolean anyTileExists = false;
        for (ChunkPos tile : tiles) {
            if (!Files.exists(this.tileFile(tile))) {
                continue;
            }
            anyTileExists = true;
            break;
        }
        if (!anyTileExists) {
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<BVH<VectorGeometry>>[] futures = uncheckedCast(Arrays.stream(tiles).map(this::getAsync).toArray(CompletableFuture[]::new));
        return CompletableFuture.allOf(futures).thenApply(unused -> uncheckedCast(Arrays.stream(futures).map(CompletableFuture::join).toArray(BVH[]::new)));
    }

    private Path tileFile(ChunkPos tile) {
        return this.tileDirectory.resolve("tile").resolve(tile.x() + "/" + tile.z() + ".json");
    }
}

