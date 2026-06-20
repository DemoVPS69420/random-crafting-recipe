package com.randomcraft;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;

import java.lang.reflect.Field;
import java.util.*;

public class RecipeShuffler {
    public static int shuffle(MinecraftServer server, long seed) {
        RecipeManager mgr = server.getRecipeManager();
        RegistryAccess access = server.registryAccess();
        Config cfg = Config.get();

        Set<ResourceLocation> recipeBL = new HashSet<>();
        for (String s : cfg.recipeBlacklist) { ResourceLocation r = ResourceLocation.tryParse(s); if (r != null) recipeBL.add(r); }
        Set<ResourceLocation> itemBL = new HashSet<>();
        for (String s : cfg.itemBlacklist) { ResourceLocation r = ResourceLocation.tryParse(s); if (r != null) itemBL.add(r); }

        List<Recipe<?>> candidates = new ArrayList<>();
        List<ItemStack> results = new ArrayList<>();

        for (CraftingRecipe recipe : mgr.getAllRecipesFor(RecipeType.CRAFTING)) {
            ResourceLocation id = recipe.getId();
            if (recipeBL.contains(id)) continue;
            if (!cfg.includeModdedRecipes && !id.getNamespace().equals("minecraft")) continue;
            ItemStack r;
            try { r = recipe.getResultItem(access); } catch (Throwable t) { continue; }
            if (r == null || r.isEmpty()) continue;
            ResourceLocation iid = BuiltInRegistries.ITEM.getKey(r.getItem());
            if (itemBL.contains(iid)) continue;
            candidates.add(recipe); results.add(r.copy());
        }
        if (candidates.size() < 2) return 0;

        List<ItemStack> shuffled = new ArrayList<>(results);
        Collections.shuffle(shuffled, new Random(seed));
        int changed = 0;
        for (int i = 0; i < candidates.size(); i++) {
            if (ItemStack.matches(results.get(i), shuffled.get(i))) continue;
            if (trySetResult(candidates.get(i), clampCount(shuffled.get(i).copy()))) changed++;
        }
        if (changed > 0) {
            List<Recipe<?>> all = new ArrayList<>(mgr.getRecipes());
            server.getPlayerList().broadcastAll(new ClientboundUpdateRecipesPacket(all));
        }
        return changed;
    }

    private static ItemStack clampCount(ItemStack stack) {
        int max = Math.max(1, stack.getMaxStackSize());
        if (stack.getCount() > max) stack.setCount(max);
        return stack;
    }

    private static boolean trySetResult(Recipe<?> recipe, ItemStack ns) {
        Class<?> c = recipe.getClass();
        while (c != null && c != Object.class) {
            for (Field f : c.getDeclaredFields()) {
                if (f.getType() == ItemStack.class) {
                    try { f.setAccessible(true); f.set(recipe, ns); return true; } catch (Throwable ignored) {}
                }
            }
            c = c.getSuperclass();
        }
        return false;
    }
}
