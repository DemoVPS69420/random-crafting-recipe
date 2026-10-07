package com.randomcraft;

import net.minecraft.network.play.server.SUpdateRecipesPacket;
import net.minecraft.server.MinecraftServer;

/** A full vanilla recipe snapshot also tells JEI to rebuild its recipe index. */
public final class RecipeSync {
    private RecipeSync() {}

    public static void sync(MinecraftServer server) {
        server.getPlayerList().broadcastAll(new SUpdateRecipesPacket(server.getRecipeManager().getRecipes()));
    }
}
