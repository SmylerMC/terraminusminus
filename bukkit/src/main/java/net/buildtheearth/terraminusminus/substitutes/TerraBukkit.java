package net.buildtheearth.terraminusminus.substitutes;

import lombok.experimental.UtilityClass;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.data.BlockData;
import org.jetbrains.annotations.Nullable;

import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.*;


/**
 * Compatibility methods to translate between Terra-- internal Minecraft objects into Bukkit API objects.
 *
 * @author Smyler
 */
@UtilityClass
public final class TerraBukkit {

    /**
     * Translates internal Terra-- {@link Identifier} to Bukkit API's {@link NamespacedKey}.
     *
     * @param identifier the Terra-- {@link Identifier}
     * @return the Bukkit API {@link NamespacedKey}
     */
    public static @Nullable NamespacedKey toBukkitNamespacedKey(Identifier identifier) {
        if (identifier == null) return null;
        return NamespacedKey.fromString(identifier.toString());
    }

    /**
     * Translate Bukkit API's {@link NamespacedKey} to Terra-- internal {@link Identifier}.
     *
     * @param namespacedKey the Bukkit API {@link NamespacedKey}
     * @return the Terra-- {@link Identifier}
     */
    public static @Nullable Identifier fromBukkitNamespacedKey(NamespacedKey namespacedKey) {
        if (namespacedKey == null) return null;
        return new Identifier(namespacedKey.getNamespace(), namespacedKey.getKey());
    }

    /**
     * Translates an internal Terra-- {@link BlockPos} to Bukkit API's {@link Location}.
     *
     * @param blockPos the Terra-- {@link BlockPos}
     * @return the Bukkit API {@link Location}
     */
    public static @Nullable Location toBukkitLocation(@Nullable BlockPos blockPos) {
        if (blockPos == null) return null;
        return new Location(null, blockPos.x, blockPos.y, blockPos.z);
    }

    /**
     * Translate a Bukkit API {@link Location} to an internal Terra-- {@link BlockPos}.
     *
     * @param location the Bukkit API {@link Location}
     * @return the internal Terra-- {@link BlockPos}
     */
    public static @Nullable BlockPos fromBukkitLocation(@Nullable Location location) {
        if (location == null) return null;
        return new BlockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    /**
     * Translates internal Terra-- {@link BlockState blockstates} to Bukkit API's BlockData.
     * <br>
     * Takes advantage of an internal cache for optimized conversions.
     *
     * @param state the Terra-- {@link BlockState}
     * @return the Bukkit API {@link BlockData}
     */
    public static @Nullable BlockData toBukkitBlockData(@Nullable BlockState state) {
        if (state == null) return null;
        BlockStateBuilder.BlockStateImplementation implementation = null;
        if (state instanceof BlockStateBuilder.BlockStateImplementation) {
            BlockStateBuilder.BlockStateImplementation imp = (BlockStateBuilder.BlockStateImplementation) state;
            if (imp.bukkitBlockData != null) return (BlockData) imp.bukkitBlockData;
            implementation = imp;
        }
        Material material = Material.matchMaterial(state.getBlock().toString());
        if (material == null) return null;
        BlockData data = material.createBlockData(getPropertiesString(state));
        if (implementation != null) implementation.bukkitBlockData = data;
        return data;
    }

    /**
     * Translates Bukkit API {@link BlockData} to Terra-- internal {@link BlockState}.
     *
     * @param data the Bukkit API {@link BlockData}
     * @return the Terra-- {@link BlockState}
     */
    public static @Nullable BlockState fromBukkitBlockData(@Nullable BlockData data) {
        if (data == null) return null;
        String serializedData = data.getAsString();
        return BlockState.parse(serializedData);
    }

    private static String getPropertiesString(BlockState state) {
        return "[" + state.getProperties().entrySet().stream().map(
                    entry -> entry.getKey() + "=" + entry.getValue().getAsString()
                ).collect(Collectors.joining(",")) + ']';
    }

    /**
     * Translates Terra-- internal {@link Biome} into Bukkit API {@link org.bukkit.block.Biome}.
     *
     * @param biome the Terra-- {@link Biome}
     * @return the Bukkit API {@link org.bukkit.block.Biome}
     */
    public static @Nullable org.bukkit.block.Biome toBukkitBiome(@Nullable Biome biome) {
        if (biome == null) return null;
        if (biome.bukkitBiome == null) {
            NamespacedKey key = toBukkitNamespacedKey(biome.biomeId);
            checkState(key != null);
            biome.bukkitBiome = Registry.BIOME.get(key);
        }
        return (org.bukkit.block.Biome) biome.bukkitBiome;
    }

    /**
     * Translates Bukkit {@link org.bukkit.block.Biome} into Terra-- internal {@link Biome}.
     *
     * @param biome the Bukkit {@link org.bukkit.block.Biome}
     * @return the Terra-- {@link Biome}
     */
    public static Biome fromBukkitBiome(org.bukkit.block.Biome biome) {
        NamespacedKey key = biome.getKeyOrNull();
        if (key == null) {
            return Biome.UNKNOWN;
        }
        Biome terraBiome = Biome.byId(fromBukkitNamespacedKey(key));
        if (terraBiome == null) {
            return Biome.UNKNOWN;
        }
        return terraBiome;
    }

}
