package net.buildtheearth.terraminusminus.dataset.building;


import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import lombok.NonNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Cinnazeyy
 */
public final class BuildingShellDataset {
    private static final Gson GSON = new GsonBuilder().create();

    private final String id;
    private final Path tileDirectory;
    private final List<TileInfo> tiles;

    public BuildingShellDataset(@NonNull String id, @NonNull Path tileDirectory) {
        this.id = id;
        this.tileDirectory = tileDirectory;
        this.tiles = this.loadTileIndex();
    }

    public BuildingShell findNearestBuilding(double lon, double lat, double radius) {
        QueryBuildingShellBounds queryBuildingShellBounds = createRadiusBuildingShellBounds(lon, lat, radius);
        List<TileInfo> candidates = this.findCandidateTiles(queryBuildingShellBounds.minLon, queryBuildingShellBounds.minLat, queryBuildingShellBounds.maxLon, queryBuildingShellBounds.maxLat);
        if (candidates.isEmpty()) return null;

        BuildingShell nearest = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        double nearestCentroidDistance = Double.POSITIVE_INFINITY;

        for (TileInfo tile : candidates) {
            for (BuildingShell shell : this.readTile(tile)) {
                BuildingShellBounds bounds = shell.bounds();
                if (!bounds.intersects2D(queryBuildingShellBounds.minLon, queryBuildingShellBounds.minLat, queryBuildingShellBounds.maxLon, queryBuildingShellBounds.maxLat)) continue;

                double distance = bounds.distanceTo(lon, lat);
                if (distance > radius) continue;

                double[] centroid = shell.centroid();
                double centroidDistance = BuildingShellGeoMath.haversine(lat, lon, centroid[1], centroid[0]);

                if (distance > nearestDistance) continue;
                if (distance == nearestDistance && centroidDistance >= nearestCentroidDistance) continue;

                nearestDistance = distance;
                nearestCentroidDistance = centroidDistance;
                nearest = shell;
            }
        }

        return nearest;
    }

    public List<BuildingShell> findBuildingsIntersecting(double minLon, double minLat, double maxLon, double maxLat) {
        List<TileInfo> candidates = this.findCandidateTiles(minLon, minLat, maxLon, maxLat);
        if (candidates.isEmpty()) return Collections.emptyList();

        List<BuildingShell> matches = new ArrayList<>();
        for (TileInfo tile : candidates) {
            for (BuildingShell shell : this.readTile(tile)) {
                if (!shell.bounds().intersects2D(minLon, minLat, maxLon, maxLat)) continue;
                matches.add(shell);
            }
        }
        return matches;
    }

    private List<TileInfo> loadTileIndex() {
        List<TileInfo> result = new ArrayList<>();
        Path indexFile = this.tileDirectory.resolve("tile_index.json");
        if (!Files.exists(indexFile)) return result;

        try (JsonReader reader = new JsonReader(Files.newBufferedReader(indexFile))) {
            reader.beginObject();
            while (reader.hasNext()) {
                String name = reader.nextName();
                if (!"tiles".equals(name)) {
                    reader.skipValue();
                    continue;
                }

                reader.beginArray();
                while (reader.hasNext()) {
                    JsonObject tileObj = GSON.fromJson(reader, JsonObject.class);
                    String tileName = tileObj.get("name").getAsString();
                    JsonObject bounds = tileObj.getAsJsonObject("bounds");
                    result.add(new TileInfo(
                            tileName,
                            bounds.get("minLon").getAsDouble(),
                            bounds.get("minLat").getAsDouble(),
                            bounds.get("maxLon").getAsDouble(),
                            bounds.get("maxLat").getAsDouble()
                    ));
                }
                reader.endArray();
            }
            reader.endObject();
        } catch (IOException ignored) {
            return Collections.emptyList();
        }

        return result;
    }

    private List<TileInfo> findCandidateTiles(double minLon, double minLat, double maxLon, double maxLat) {
        List<TileInfo> candidates = new ArrayList<>();
        for (TileInfo tile : this.tiles) {
            if (!tile.intersects(minLon, minLat, maxLon, maxLat)) continue;
            candidates.add(tile);
        }
        return candidates;
    }

    private List<BuildingShell> readTile(TileInfo tile) {
        Path buildingsFile = this.tileDirectory.resolve(tile.name()).resolve("buildings.jsonl");
        if (!Files.exists(buildingsFile)) return Collections.emptyList();

        List<BuildingShell> shells = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(buildingsFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                BuildingShell shell = this.parseBuilding(line);
                if (shell == null) continue;
                shells.add(shell);
            }
        } catch (IOException ignored) {
            return Collections.emptyList();
        }
        return shells;
    }

    private BuildingShell parseBuilding(String jsonLine) {
        try {
            JsonObject obj = GSON.fromJson(jsonLine, JsonObject.class);
            String buildingId = obj.get("id").getAsString();
            JsonObject properties = obj.has("properties") && obj.get("properties").isJsonObject()
                    ? obj.getAsJsonObject("properties")
                    : new JsonObject();
            String uuid = properties.has("uuid") && !properties.get("uuid").isJsonNull() ? properties.get("uuid").getAsString() : null;

            JsonArray trianglesArray = obj.getAsJsonArray("triangles");
            List<BuildingShell.Triangle> triangles = new ArrayList<>(trianglesArray.size());
            for (int t = 0; t < trianglesArray.size(); t++) {
                JsonArray triArray = trianglesArray.get(t).getAsJsonArray();
                BuildingShell.Vertex[] vertices = new BuildingShell.Vertex[3];
                for (int v = 0; v < 3; v++) {
                    JsonArray ptArray = triArray.get(v).getAsJsonArray();
                    vertices[v] = new BuildingShell.Vertex(
                            ptArray.get(0).getAsDouble(),
                            ptArray.get(1).getAsDouble(),
                            ptArray.get(2).getAsDouble()
                    );
                }
                triangles.add(new BuildingShell.Triangle(vertices));
            }

            BuildingShellBounds bounds = parseBuildingShellBounds(obj.get("bounds"));
            BuildingShell.Footprint footprint = parseFootprint(obj.get("footprint"), bounds);
            return new BuildingShell(this.id, buildingId, uuid, triangles, bounds, footprint);
        } catch (Exception e) {
            return null;
        }
    }

    private static BuildingShellBounds parseBuildingShellBounds(JsonElement element) {
        if (element == null || !element.isJsonObject()) return null;
        JsonObject obj = element.getAsJsonObject();
        return new BuildingShellBounds(
                obj.get("minLon").getAsDouble(),
                obj.get("minLat").getAsDouble(),
                obj.has("minZ") ? obj.get("minZ").getAsDouble() : 0.0d,
                obj.get("maxLon").getAsDouble(),
                obj.get("maxLat").getAsDouble(),
                obj.has("maxZ") ? obj.get("maxZ").getAsDouble() : 0.0d
        );
    }

    private static BuildingShell.Footprint parseFootprint(JsonElement element, BuildingShellBounds knownBuildingShellBounds) {
        if (element == null || !element.isJsonObject()) return null;
        JsonObject obj = element.getAsJsonObject();
        if (!obj.has("coordinates") || !obj.get("coordinates").isJsonArray()) return null;

        String type = obj.has("type") ? obj.get("type").getAsString() : "Polygon";
        JsonArray coordinates = obj.getAsJsonArray("coordinates");
        List<BuildingShell.Ring> rings = new ArrayList<>();
        if ("Polygon".equalsIgnoreCase(type)) {
            addPolygonRings(coordinates, rings);
        } else if ("MultiPolygon".equalsIgnoreCase(type)) {
            for (JsonElement polygon : coordinates) {
                if (polygon.isJsonArray()) addPolygonRings(polygon.getAsJsonArray(), rings);
            }
        }
        if (rings.isEmpty()) return null;

        BuildingShellBounds bounds = knownBuildingShellBounds != null ? knownBuildingShellBounds : boundsFromRings(rings);
        return new BuildingShell.Footprint(rings, bounds);
    }

    private static void addPolygonRings(JsonArray polygon, List<BuildingShell.Ring> rings) {
        if (polygon.isEmpty()) return;
        JsonElement outer = polygon.get(0);
        if (!outer.isJsonArray()) return;

        List<BuildingShell.Point> points = new ArrayList<>();
        for (JsonElement pointElement : outer.getAsJsonArray()) {
            if (!pointElement.isJsonArray()) continue;
            JsonArray pointArray = pointElement.getAsJsonArray();
            if (pointArray.size() < 2) continue;
            points.add(new BuildingShell.Point(pointArray.get(0).getAsDouble(), pointArray.get(1).getAsDouble()));
        }
        if (points.size() >= 3) rings.add(new BuildingShell.Ring(points));
    }

    private static BuildingShellBounds boundsFromRings(List<BuildingShell.Ring> rings) {
        double minLon = Double.POSITIVE_INFINITY;
        double minLat = Double.POSITIVE_INFINITY;
        double maxLon = Double.NEGATIVE_INFINITY;
        double maxLat = Double.NEGATIVE_INFINITY;
        for (BuildingShell.Ring ring : rings) {
            for (BuildingShell.Point point : ring.points()) {
                minLon = Math.min(minLon, point.lon());
                minLat = Math.min(minLat, point.lat());
                maxLon = Math.max(maxLon, point.lon());
                maxLat = Math.max(maxLat, point.lat());
            }
        }
        return new BuildingShellBounds(minLon, minLat, 0.0d, maxLon, maxLat, 0.0d);
    }

    private static final double METERS_PER_DEGREE_LAT = 111_000.0d;
    private static final double MIN_COS_LATITUDE = 1.0e-12d;

    private static QueryBuildingShellBounds createRadiusBuildingShellBounds(double lon, double lat, double radius) {
        if (radius < 0.0d) {
            throw new IllegalArgumentException("radius must be non-negative: " + radius);
        }

        double latDelta = radius / METERS_PER_DEGREE_LAT;
        double cosLat = Math.max(Math.abs(Math.cos(Math.toRadians(lat))), MIN_COS_LATITUDE);
        double lonDelta = radius / (METERS_PER_DEGREE_LAT * cosLat);
        return new QueryBuildingShellBounds(lon - lonDelta, lat - latDelta, lon + lonDelta, lat + latDelta);
    }

    private static final class TileInfo {
        private final String name;
        private final double minLon;
        private final double minLat;
        private final double maxLon;
        private final double maxLat;

        TileInfo(String name, double minLon, double minLat, double maxLon, double maxLat) {
            this.name = name;
            this.minLon = minLon;
            this.minLat = minLat;
            this.maxLon = maxLon;
            this.maxLat = maxLat;
        }

        String name() {
            return this.name;
        }

        boolean intersects(double minLon, double minLat, double maxLon, double maxLat) {
            return this.minLon <= maxLon && this.maxLon >= minLon && this.minLat <= maxLat && this.maxLat >= minLat;
        }
    }

    private static final class QueryBuildingShellBounds {
        private final double minLon;
        private final double minLat;
        private final double maxLon;
        private final double maxLat;

        QueryBuildingShellBounds(double minLon, double minLat, double maxLon, double maxLat) {
            this.minLon = minLon;
            this.minLat = minLat;
            this.maxLon = maxLon;
            this.maxLat = maxLat;
        }
    }
}