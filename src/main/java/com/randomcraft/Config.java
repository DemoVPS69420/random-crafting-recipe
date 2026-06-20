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
    public static final ForgeConfigSpec.BooleanValue SHOW_TIMER_BOSS_BAR;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> RECIPE_BLACKLIST;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ITEM_BLACKLIST;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.push("general");

        SHUFFLE_INTERVAL_SECONDS = b
                .comment("Interval between shuffles, in seconds. Default: 600 (10 minutes).")
                .defineInRange("shuffleIntervalSeconds", 600, 20, Integer.MAX_VALUE);

        SHUFFLE_ON_SERVER_START = b
                .comment("If true, shuffle recipes once when the server starts.")
                .define("shuffleOnServerStart", true);

        BROADCAST_MESSAGE = b
                .comment("If true, broadcast a chat message every time recipes are shuffled.")
                .define("broadcastMessage", true);

        INCLUDE_MODDED_RECIPES = b
                .comment("If true, crafting recipes from other mods are also shuffled.",
                         "Only recipes of type minecraft:crafting (crafting table) are affected.")
                .define("includeModdedRecipes", true);

        SHOW_TIMER_BOSS_BAR = b
                .comment("If true, show a boss bar counting down to the next shuffle. Default: false.")
                .define("showTimerBossBar", false);

        RECIPE_BLACKLIST = b
                .comment("Recipe IDs (ResourceLocation) to never shuffle. Example: \"minecraft:stick\".")
                .defineList("recipeBlacklist", Arrays.asList(), o -> o instanceof String);

        ITEM_BLACKLIST = b
                .comment("Item IDs whose recipes (by output) will never be shuffled. Example: \"minecraft:crafting_table\".")
                .defineList("itemBlacklist", Arrays.asList("minecraft:crafting_table", "minecraft:chest"),
                        o -> o instanceof String);

        b.pop();
        SPEC = b.build();
    }
}
