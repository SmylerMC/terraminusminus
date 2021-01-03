package io.github.terra121.dataset.osm;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterators;
import io.github.terra121.TerraConfig;
import io.github.terra121.dataset.TiledDataset;
import io.github.terra121.dataset.geojson.GeoJSON;
import io.github.terra121.dataset.geojson.GeoJSONObject;
import io.github.terra121.dataset.geojson.object.Reference;
import io.github.terra121.dataset.impl.Water;
import io.github.terra121.dataset.osm.poly.OSMPolygon;
import io.github.terra121.dataset.osm.segment.OSMSegment;
import io.github.terra121.dataset.osm.segment.SegmentType;
import io.github.terra121.projection.EquirectangularProjection;
import io.github.terra121.projection.GeographicProjection;
import io.github.terra121.projection.OutOfProjectionBoundsException;
import io.github.terra121.util.CornerBoundingBox2d;
import io.github.terra121.util.bvh.Bounds2d;
import io.github.terra121.util.http.Http;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import lombok.NonNull;
import net.minecraft.util.math.ChunkPos;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static net.daporkchop.lib.common.math.PMath.*;
import static net.daporkchop.lib.common.util.PorkUtil.*;

public class OpenStreetMap extends TiledDataset<OSMRegion> {
    protected static final String TILE_SUFFIX = "${x}/${z}.json";

    public static final double TILE_SIZE = 1 / 60.0;//250*(360.0/40075000.0);

    protected final GeographicProjection earthProjection;

    protected final LoadingCache<String, CompletableFuture<OSMBlob>> referencedBlobs = CacheBuilder.newBuilder()
            .softValues()
            .expireAfterAccess(5L, TimeUnit.MINUTES)
            .build(CacheLoader.from(location -> {
                //suffix urls with location
                String[] urls = Arrays.stream(TerraConfig.data.openstreetmap).map(s -> s + location).toArray(String[]::new);

                return Http.getFirst(urls, this::parseGeoJSON).thenApply(CompletableFuture::join);
            }));

    public final Water water;
    private final List<OSMSegment> allSegments = new ArrayList<>();
    private final List<OSMPolygon> allPolygons = new ArrayList<>();
    private final boolean doRoad;
    private final boolean doWater;
    private final boolean doBuildings;

    public OpenStreetMap(@NonNull GeographicProjection earthProjection, boolean doRoad, boolean doWater, boolean doBuildings) {
        super(new EquirectangularProjection(), TILE_SIZE);

        this.earthProjection = earthProjection;

        try {
            this.water = new Water(this);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        this.doRoad = doRoad;
        this.doWater = doWater;
        this.doBuildings = doBuildings;
    }

    @Override
    protected String[] urls(int tileX, int tileZ) {
        return Arrays.stream(TerraConfig.data.openstreetmap).map(s -> s + TILE_SUFFIX).toArray(String[]::new);
    }

    @Override
    @Deprecated
    protected OSMRegion decode(int tileX, int tileZ, @NonNull ByteBuf data) throws Exception {
        throw new UnsupportedOperationException("decode"); //this method shouldn't be being used
    }

    @Override
    @Deprecated
    public CompletableFuture<OSMRegion> load(@NonNull ChunkPos pos) throws Exception {
        String location = Http.formatUrl(ImmutableMap.of("x", String.valueOf(pos.x), "z", String.valueOf(pos.z)), TILE_SUFFIX);

        return this.referencedBlobs.getUnchecked(location).thenApply(blob -> this.toRegion(pos, blob));
    }

    protected CompletableFuture<OSMBlob> parseGeoJSON(@NonNull ByteBuf json) throws IOException {
        GeoJSONObject[] objects; //parse each line as a GeoJSON object
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteBufInputStream(json)))) {
            objects = reader.lines().map(GeoJSON::parse).toArray(GeoJSONObject[]::new);
        }

        if (Arrays.stream(objects).anyMatch(o -> o instanceof Reference)) { //resolve references
            List<CompletableFuture<OSMBlob>> referencedObjectFutures = new ArrayList<>();

            //add all non-reference objects as a single stream at once to make iteration faster
            referencedObjectFutures.add(CompletableFuture.completedFuture(OSMBlob.fromGeoJSON(
                    Arrays.stream(objects).filter(o -> !(o instanceof Reference)).toArray(GeoJSONObject[]::new))));

            for (GeoJSONObject object : objects) {
                if (object instanceof Reference) {
                    //suffix urls with location
                    String location = ((Reference) object).location();
                    String[] urls = Arrays.stream(TerraConfig.data.openstreetmap).map(s -> s + location).toArray(String[]::new);

                    //actually send request
                    referencedObjectFutures.add(Http.getFirst(urls, this::parseGeoJSON).thenApply(CompletableFuture::join));
                }
            }

            return CompletableFuture.allOf(referencedObjectFutures.toArray(new CompletableFuture[0]))
                    .thenApply(unused -> OSMBlob.merge(referencedObjectFutures.stream().map(CompletableFuture::join).toArray(OSMBlob[]::new)));
        }

        return CompletableFuture.completedFuture(OSMBlob.fromGeoJSON(objects));
    }

    protected OSMRegion toRegion(@NonNull ChunkPos pos, @NonNull OSMBlob blob) {
    }

    public ChunkPos getRegion(double lon, double lat) {
        return new ChunkPos(floorI(lon / TILE_SIZE), floorI(lat / TILE_SIZE));
    }

    public CompletableFuture<OSMRegion[]> getRegionsAsync(@NonNull CornerBoundingBox2d bounds) throws OutOfProjectionBoundsException {
        Bounds2d localBounds = bounds.fromGeo(this.projection).axisAlign();
        CompletableFuture<OSMRegion>[] futures = uncheckedCast(Arrays.stream(localBounds.toTiles(TILE_SIZE))
                .map(this::getTileAsync)
                .toArray(CompletableFuture[]::new));

        return CompletableFuture.allOf(futures)
                .thenApplyAsync(unused -> Arrays.stream(futures).map(CompletableFuture::join).toArray(OSMRegion[]::new));
    }

    protected void doGson(InputStream is, OSMRegion region) throws IOException {
        Data data = this.gson.fromJson(writer.toString(), Data.class);

        Map<Long, Element> allWays = new HashMap<>();
        Set<Element> unusedWays = new HashSet<>();
        Set<Long> ground = new HashSet<>();

        for (Element elem : data.elements) {
            Attributes attributes = Attributes.NONE;
            if (elem.type == EType.way) {
                allWays.put(elem.id, elem);

                if (elem.tags == null) {
                    unusedWays.add(elem);
                    continue;
                }

                String naturalv = null;
                String highway = null;
                String waterway = null;
                String building = null;
                String istunnel = null;
                String isbridge = null;

                if (this.doWater) {
                    naturalv = elem.tags.get("natural");
                    waterway = elem.tags.get("waterway");
                }

                if (this.doRoad) {
                    highway = elem.tags.get("highway");
                    istunnel = elem.tags.get("tunnel");
                    // to be implemented
                    isbridge = elem.tags.get("bridge");
                }

                if (this.doBuildings) {
                    building = elem.tags.get("building");
                }

                if ("coastline".equals(naturalv)) {
                    this.waterway(elem, -1, region);
                } else if (highway != null || building != null || ("river".equals(waterway) || "canal".equals(waterway) || "stream".equals(waterway))) { //TODO: fewer equals
                    SegmentType type = SegmentType.ROAD;

                    if (waterway != null) {
                        type = SegmentType.STREAM;
                        if ("river".equals(waterway) || "canal".equals(waterway)) {
                            type = SegmentType.RIVER;
                        }
                    }

                    if (building != null) {
                        type = SegmentType.BUILDING;
                    }

                    if ("yes".equals(istunnel)) {
                        attributes = Attributes.ISTUNNEL;
                        continue; //tunnels are never generated, don't bother adding them
                    } else if ("yes".equals(isbridge)) {
                        attributes = Attributes.ISBRIDGE;
                    }

                    if (highway != null) {
                        switch (highway) {
                            case "motorway":
                                type = SegmentType.FREEWAY;
                                break;
                            case "trunk":
                                type = SegmentType.LIMITEDACCESS;
                                break;
                            case "motorway_link":
                            case "trunk_link":
                                type = SegmentType.INTERCHANGE;
                                break;
                            case "primary":
                            case "raceway":
                                type = SegmentType.MAIN;
                                break;
                            case "tertiary":
                            case "residential":
                                type = SegmentType.MINOR;
                                break;
                            case "secondary":
                            case "primary_link":
                            case "secondary_link":
                            case "living_street":
                            case "bus_guideway":
                            case "service":
                            case "unclassified":
                                type = SegmentType.SIDE;
                                break;
                        }
                    }

                    //get lane number (default is 2)
                    String slanes = elem.tags.get("lanes");
                    String slayer = elem.tags.get("layer");
                    byte lanes = 2;
                    byte layer = 0;

                    if (slayer != null) {
                        try {
                            layer = Byte.parseByte(slayer);
                        } catch (NumberFormatException e) {
                        }
                    }

                    if (slanes != null) {
                        try {
                            lanes = Byte.parseByte(slanes);
                        } catch (NumberFormatException e) {
                        }
                    }

                    //prevent super high # of lanes to prevent ridiculous results (prly a mistake if its this high anyways)
                    if (lanes > 8) {
                        lanes = 8;
                    }

                    // an interchange that doesn't have any lane tag should be defaulted to 2 lanes
                    if (lanes < 2 && type == SegmentType.INTERCHANGE) {
                        lanes = 2;
                    }

                    // upgrade road type if many lanes (and the road was important enough to include a lanes tag)
                    if (lanes > 2 && type == SegmentType.MINOR) {
                        type = SegmentType.MAIN;
                    }

                    this.addWay(elem, type, lanes, region, attributes, layer);
                } else {
                    unusedWays.add(elem);
                }
            } else if (elem.type == EType.relation && elem.members != null && elem.tags != null) {
                if (this.doWater) {
                    String naturalv = elem.tags.get("natural");
                    String waterv = elem.tags.get("water");
                    String wway = elem.tags.get("waterway");

                    if (waterv != null || "water".equals(naturalv) || "riverbank".equals(wway)) {
                        for (Member member : elem.members) {
                            if (member.type == EType.way) {
                                Element way = allWays.get(member.ref);
                                if (way != null) {
                                    this.waterway(way, elem.id + 3600000000L, region);
                                    unusedWays.remove(way);
                                }
                            }
                        }
                        continue;
                    }
                }
                if (this.doBuildings && elem.tags.get("building") != null) {
                    for (Member member : elem.members) {
                        if (member.type == EType.way) {
                            Element way = allWays.get(member.ref);
                            if (way != null) {
                                this.addWay(way, SegmentType.BUILDING, (byte) 1, region, Attributes.NONE, (byte) 0);
                                unusedWays.remove(way);
                            }
                        }
                    }
                }

            } else if (elem.type == EType.area) {
                ground.add(elem.id);
            }
        }

        if (this.doWater) {
            for (Element way : unusedWays) {
                if (way.tags != null) {
                    String naturalv = way.tags.get("natural");
                    String waterv = way.tags.get("water");
                    String wway = way.tags.get("waterway");

                    if (waterv != null || "water".equals(naturalv) || "riverbank".equals(wway)) {
                        this.waterway(way, way.id + 2400000000L, region);
                    }
                }
            }

            if (this.water.grounding.state(region.coord.x, region.coord.z) == 0) {
                ground.add(-1L);
            }

            region.renderWater(ground);
        }
    }

    void addWay(Element elem, SegmentType type, byte lanes, OSMRegion region, Attributes attributes, byte layer) {
        double[] lastProj = null;
        if (elem.geometry != null) {
            for (Geometry geom : elem.geometry) {
                if (geom == null) {
                    lastProj = null;
                } else {
                    try {
                        double[] proj = this.earthProjection.fromGeo(geom.lon, geom.lat);

                        if (lastProj != null) { //register as a road edge
                            this.allSegments.add(new OSMSegment(lastProj[0], lastProj[1], proj[0], proj[1], type, lanes, region, attributes, layer));
                        }

                        lastProj = proj;
                    } catch (OutOfProjectionBoundsException e) { //projection is out of bounds, make this point unusable
                        lastProj = null;
                    }
                }
            }
        }
    }

    Geometry waterway(Element way, long id, OSMRegion region) {
        Geometry last = null;
        if (way.geometry != null) {
            this.allPolygons.add(new OSMPolygon(new double[][][]{
                    Arrays.stream(way.geometry).filter(Objects::nonNull).map(geom -> new double[]{ geom.lon, geom.lat }).toArray(double[][]::new)
            }));

            for (Geometry geom : way.geometry) {
                if (geom != null && last != null) {
                    region.addWaterEdge(last.lon, last.lat, geom.lon, geom.lat, id);
                }
                last = geom;
            }
        }
        return last;
    }

    public enum Attributes {
        ISBRIDGE, ISTUNNEL, NONE
    }

    public enum EType {
        invalid, node, way, relation, area
    }

    public static class Member {
        EType type;
        long ref;
        String role;
    }

    public static class Geometry {
        double lat;
        double lon;
    }

    public static class Element {
        EType type;
        long id;
        Map<String, String> tags;
        long[] nodes;
        Member[] members;
        Geometry[] geometry;
    }

    public static class Data {
        float version;
        String generator;
        Map<String, String> osm3s;
        List<Element> elements;
    }
}