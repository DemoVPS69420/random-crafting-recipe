package com.randomcraft;

import net.minecraftforge.common.ForgeConfigSpec;
import java.util.Arrays;
import java.util.List;

public class Config {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue SHUFFLE_INTERVAL_SECONDS;
    public static final ForgeConfigSpec.BooleanValue SHUFFLE_ON_SERVER_START;
    public static final ForgeConfigSpec.BooleanValue BROADCAST_MESSAGE;
    public static final ForgeConfigSpec.BooleanValue INCLUDE_MODDED_RECIPES;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> RECIPE_BLACKLIST;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ITEM_BLACKLIST;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("general");
        SHUFFLE_INTERVAL_SECONDS = b.defineInRange("shuffleIntervalSeconds", 600, 20, Integer.MAX_VALUE);
        SHUFFLE_ON_SERVER_START = b.define("shuffleOnServerStart", true);
        BROADCAST_MESSAGE = b.define("broadcastMessage", true);
        INCLUDE_MODDED_RECIPES = b.define("includeModdedRecipes", true);
        RECIPE_BLACKLIST = b.defineList("recipeBlacklist", Arrays.asList(), o -> o instanceof String);
        ITEM_BLACKLIST = b.defineList("itemBlacklist",
                Arrays.asList("minecraft:crafting_table","minecraft:chest"), o -> o instanceof String);
        b.pop();
        SPEC = b.build();
    }
}
