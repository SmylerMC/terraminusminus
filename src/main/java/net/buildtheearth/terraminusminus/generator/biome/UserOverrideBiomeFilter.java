package net.buildtheearth.terraminusminus.generator.biome;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.collect.ImmutableSet;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import net.buildtheearth.terraminusminus.TerraConstants;
import net.buildtheearth.terraminusminus.generator.ChunkBiomesBuilder;
import net.buildtheearth.terraminusminus.generator.GeneratorDatasets;
import net.buildtheearth.terraminusminus.projection.GeographicProjection;
import net.buildtheearth.terraminusminus.projection.OutOfProjectionBoundsException;
import net.buildtheearth.terraminusminus.substitutes.Biome;
import net.buildtheearth.terraminusminus.substitutes.ChunkPos;
import net.buildtheearth.terraminusminus.util.CornerBoundingBox2d;
import net.buildtheearth.terraminusminus.util.bvh.BVH;
import net.buildtheearth.terraminusminus.util.bvh.Bounds2d;
import net.buildtheearth.terraminusminus.util.http.Disk;
import net.daporkchop.lib.common.function.io.IOFunction;
import net.daporkchop.lib.common.function.throwing.EFunction;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public class UserOverrideBiomeFilter implements IEarthBiomeFilter<UserOverrideBiomeFilter.BiomeBoundingBox> {
    protected final BVH<BiomeBoundingBox> bvh;


    @SneakyThrows(IOException.class)
    public UserOverrideBiomeFilter(@NonNull GeographicProjection projection) {
        List<URL> configSources = new ArrayList<>();
        configSources.add(UserOverrideBiomeFilter.class.getResource("biome_overrides.json5"));

        try(Stream<Path> stream = Files.list(Files.createDirectories(Disk.configFile("biome_overrides")))) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().matches(".*\\.json5?$"))
                    .map(Path::toUri).map((EFunction<URI, URL>) URI::toURL)
                    .forEach(configSources::add);
        }

        this.bvh = BVH.of(configSources.stream()
                .map((IOFunction<URL, BiomeBoundingBox[]>) url -> TerraConstants.JSON_MAPPER.readValue(url, BiomeBoundingBox[].class))
                .flatMap(Arrays::stream)
                .toArray(BiomeBoundingBox[]::new));
    }

    @Override
    public CompletableFuture<BiomeBoundingBox> requestData(ChunkPos pos, GeneratorDatasets datasets, Bounds2d bounds, CornerBoundingBox2d boundsGeo) throws OutOfProjectionBoundsException {
        return CompletableFuture.supplyAsync(() -> this.bvh.getAllIntersecting(boundsGeo).stream()
                .max(Comparator.naturalOrder())
                .orElse(null));
    }

    @Override
    public void bake(ChunkPos pos, ChunkBiomesBuilder builder, BiomeBoundingBox bbox) {
        if(bbox == null)
            return;

        final Biome biome = Biome.parse(bbox.biome);

        if(biome == null)
            return;

        if(bbox.replace == null)
            Arrays.fill(builder.state(), biome);
        else {
            for(int x = 0; x <  16; x++) {
                for (int z = 0; z < 16; z++) {
                    if(bbox.replace.contains(builder.get(x, z).identifier().toString())) {
                        builder.set(x, z, biome);
                    }
                }
            }
        }
    }

    @JsonDeserialize
    @JsonSerialize
    @Getter
    public static class BiomeBoundingBox implements Bounds2d, Comparable<BiomeBoundingBox> {
        protected final Set<String> replace;
        protected final String biome;

        protected final double minX;
        protected final double maxX;
        protected final double minZ;
        protected final double maxZ;

        @Getter(onMethod_ = { @JsonGetter })
        protected final double priority;

        @JsonCreator
        public BiomeBoundingBox(
            @JsonProperty(value = "replace", required = false) String[] replace,
            @JsonProperty(value = "biome", required = true) @NonNull String biome,
            @JsonProperty(value = "bounds", required = true) @NonNull Bounds2d bounds2d,
            @JsonProperty(value = "propery", defaultValue = "0.0") double priority) {
            this.replace = replace != null ? ImmutableSet.copyOf(replace) : null;
            this.biome = biome;
            this.priority = priority;

            this.minX = bounds2d.minX();
            this.maxX = bounds2d.maxX();
            this.minZ = bounds2d.minZ();
            this.maxZ = bounds2d.maxZ();
        }

        @Override
        public int compareTo(BiomeBoundingBox b) {
            return -Double.compare(this.priority, b.priority);
        }

        @JsonGetter
        public Bounds2d bounds2d() {
            return Bounds2d.of(this.minX, this.maxX, this.minZ, this.maxZ);
        }
    }
}
