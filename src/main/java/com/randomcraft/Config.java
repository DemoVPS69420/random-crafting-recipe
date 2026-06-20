package com.randomcraft;

import net.neoforged.neoforge.common.ModConfigSpec;
import java.util.Arrays;
import java.util.List;

public class Config {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue SHUFFLE_INTERVAL_SECONDS;
    public static final ModConfigSpec.BooleanValue SHUFFLE_ON_SERVER_START;
    public static final ModConfigSpec.BooleanValue BROADCAST_MESSAGE;
    public static final ModConfigSpec.BooleanValue INCLUDE_MODDED_RECIPES;
    public static final ModConfigSpec.BooleanValue SHOW_TIMER_BOSS_BAR;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> RECIPE_BLACKLIST;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ITEM_BLACKLIST;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("general");
        SHUFFLE_INTERVAL_SECONDS = b.comment("Interval between shuffles, in seconds. Default 600 (10 min).")
                .defineInRange("shuffleIntervalSeconds", 600, 20, Integer.MAX_VALUE);
        SHUFFLE_ON_SERVER_START = b.define("shuffleOnServerStart", true);
        BROADCAST_MESSAGE = b.define("broadcastMessage", true);
        INCLUDE_MODDED_RECIPES = b.define("includeModdedRecipes", true);
        SHOW_TIMER_BOSS_BAR = b.comment("Show a boss bar counting down to the next shuffle. Default off.")
                .define("showTimerBossBar", false);
        RECIPE_BLACKLIST = b.defineList("recipeBlacklist", Arrays.asList(), () -> "", o -> o instanceof String);
        ITEM_BLACKLIST = b.defineList("itemBlacklist",
                Arrays.asList("minecraft:crafting_table", "minecraft:chest"),
                () -> "minecraft:chest", o -> o instanceof String);
        b.pop();
        SPEC = b.build();
    }
}
