package com.randomcraft;

import net.fabricmc.fabric.api.recipe.v1.sync.RecipeSynchronization;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.core.registries.BuiltInRegistries;

/** Server-owned recipe synchronization; JEI remains an optional client mod. */
public final class RecipeSync {
    private RecipeSync() {}

    public static void initialize() {
        // Register only vanilla serializers. Other mods opt their own serializers in.
        for (RecipeSerializer<?> serializer : BuiltInRegistries.RECIPE_SERIALIZER) {
            String id = BuiltInRegistries.RECIPE_SERIALIZER.getKey(serializer).toString();
            if (id.equals("minecraft:crafting_shaped") || id.equals("minecraft:crafting_shapeless")) {
                RecipeSynchronization.synchronizeRecipeSerializer(serializer);
            }
        }
    }

    public static void sync(MinecraftServer server) {
        // Rebuild cached vanilla recipe-book displays from the changed results.
        server.getRecipeManager().finalizeRecipeLoading(server.getWorldData().enabledFeatures());
        // Fabric's PlayerList hook sends negotiated full recipes BEFORE the vanilla
        // update packet. JEI consumes that snapshot and rebuilds its output index.
        // This also refreshes recipe books and preserves normal join/reload handling.
        server.getPlayerList().reloadResources();
    }
}
