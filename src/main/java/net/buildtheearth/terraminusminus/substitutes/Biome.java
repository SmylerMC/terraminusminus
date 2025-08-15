package net.buildtheearth.terraminusminus.substitutes;

/**
 * All vanilla Minecraft 1.21.4 biomes.
 * <br>
 * Terra++'s biomes are not data-driven yet, so this is a simple enum.
 * 
 * @author SmylerMC
 *
 */
public enum Biome {

    OCEAN("ocean"),
    DEEP_OCEAN("deep_ocean"),
    FROZEN_OCEAN("frozen_ocean"),
    DEEP_FROZEN_OCEAN("deep_frozen_ocean"),
    COLD_OCEAN("cold_ocean"),
    DEEP_COLD_OCEAN("deep_cold_ocean"),
    LUKEWARM_OCEAN("lukewarm_ocean"),
    DEEP_LUKEWARM_OCEAN("deep_lukewarm_ocean"),
    WARM_OCEAN("warm_ocean"),
    RIVER("river"),
    FROZEN_RIVER("frozen_river"),
    BEACH("beach"),
    STONY_SHORE("stony_shore"),
    SNOWY_BEACH("snowy_beach"),
    FOREST("forest"),
    FLOWER_FOREST("flower_forest"),
    BIRCH_FOREST("birch_forest"),
    OLD_GROWTH_BIRCH_FOREST("old_growth_birch_forest"),
    DARK_FOREST("dark_forest"),
    PALE_GARDEN("pale_garden"),
    JUNGLE("jungle"),
    SPARSE_JUNGLE("sparse_jungle"),
    BAMBOO_JUNGLE("bamboo_jungle"),
    TAIGA("taiga"),
    SNOWY_TAIGA("snowy_taiga"),
    OLD_GROWTH_PINE_TAIGA("old_growth_pine_taiga"),
    OLD_GROWTH_SPRUCE_TAIGA("old_growth_spruce_taiga"),
    MUSHROOM_FIELDS("mushroom_fields"),
    SWAMP("swamp"),
    MANGROVE_SWAMP("mangrove_swamp"),
    SAVANNA("savanna"),
    SAVANNA_PLATEAU("savanna_plateau"),
    WINDSWEPT_SAVANNA("windswept_savanna"),
    PLAINS("plains"),
    SUNFLOWER_PLAINS("sunflower_plains"),
    DESERT("desert"),
    SNOWY_PLAINS("snowy_plains"),
    ICE_SPIKES("ice_spikes"),
    WINDSWEPT_HILLS("windswept_hills"),
    WINDSWEPT_FOREST("windswept_forest"),
    WINDSWEPT_GRAVELLY_HILLS("windswept_gravelly_hills"),
    BADLANDS("badlands"),
    WOODED_BADLANDS("wooded_badlands"),
    ERODED_BADLANDS("eroded_badlands"),
    JAGGED_PEAKS("jagged_peaks"),
    FROZEN_PEAKS("frozen_peaks"),
    STONY_PEAKS("stony_peaks"),
    MEADOW("meadow"),
    GROVE("grove"),
    SNOWY_SLOPES("snowy_slopes"),
    CHERRY_GROVE("cherry_grove"),
    DRIPSTONE_CAVES("dripstone_caves"),
    LUSH_CAVES("lush_caves"),
    DEEP_DARK("deep_dark"),
    NETHER_WASTES("nether_wastes"),
    SOUL_SAND_VALLEY("soul_sand_valley"),
    CRIMSON_FOREST("crimson_forest"),
    WARPED_FOREST("warped_forest"),
    BASALT_DELTAS("basalt_deltas"),
    THE_END("the_end"),
    SMALL_END_ISLANDS("small_end_islands"),
    END_MIDLANDS("end_midlands"),
    END_HIGHLANDS("end_highlands"),
    END_BARRENS("end_barrens"),
    THE_VOID("the_void"),
    UNKNOWN("unknown");
    
    public final Identifier biomeId;

    Object bukkitBiome;

    Biome(String biomeId) {
    	this.biomeId = new Identifier("minecraft:" + biomeId);
    }
    
    public static Biome byId(Identifier biomeId) {
    	for(Biome b: values()) {
    		if(b.biomeId.equals(biomeId)) return b;
    	}
    	return null;
    }
    
    public static Biome getDefault() {
    	return OCEAN;
    }

}
