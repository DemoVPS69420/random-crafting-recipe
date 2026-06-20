package com.randomcraft;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Config INSTANCE;

    public int shuffleIntervalSeconds = 600;
    public boolean shuffleOnServerStart = true;
    public boolean broadcastMessage = true;
    public boolean includeModdedRecipes = true;
    public List<String> recipeBlacklist = new ArrayList<>();
    public List<String> itemBlacklist = new ArrayList<>(List.of("minecraft:crafting_table", "minecraft:chest"));

    public static Config get() {
        if (INSTANCE == null) INSTANCE = load();
        return INSTANCE;
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("randomcraft.json");
    }

    private static Config load() {
        Path p = path();
        try {
            if (Files.exists(p)) {
                String json = Files.readString(p);
                Config c = GSON.fromJson(json, Config.class);
                if (c != null) return c;
            }
        } catch (Exception e) {
            RandomCraftFabric.LOGGER.error("Failed to load config, using defaults", e);
        }
        Config def = new Config();
        def.save();
        return def;
    }

    public void save() {
        try {
            Files.createDirectories(path().getParent());
            Files.writeString(path(), GSON.toJson(this));
        } catch (IOException e) {
            RandomCraftFabric.LOGGER.error("Failed to save config", e);
        }
    }
}
