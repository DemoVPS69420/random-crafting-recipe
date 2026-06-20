package com.randomcraft;

import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.ICraftingRecipe;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.IRecipeType;
import net.minecraft.item.crafting.RecipeManager;
import net.minecraft.network.play.server.SUpdateRecipesPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Field;
import java.util.*;

public class RecipeShuffler {
    public static int shuffle(MinecraftServer server, long seed) {
        RecipeManager mgr = server.getRecipeManager();

        Set<ResourceLocation> recipeBL = new HashSet<>();
        for (String s : Config.RECIPE_BLACKLIST.get()) { ResourceLocation r = tryParse(s); if (r != null) recipeBL.add(r); }
        Set<ResourceLocation> itemBL = new HashSet<>();
        for (String s : Config.ITEM_BLACKLIST.get()) { ResourceLocation r = tryParse(s); if (r != null) itemBL.add(r); }
        boolean incMod = Config.INCLUDE_MODDED_RECIPES.get();

        List<IRecipe<?>> candidates = new ArrayList<>();
        List<ItemStack> results = new ArrayList<>();

        for (ICraftingRecipe recipe : mgr.getAllRecipesFor(IRecipeType.CRAFTING)) {
            ResourceLocation id = recipe.getId();
            if (recipeBL.contains(id)) continue;
            if (!incMod && !id.getNamespace().equals("minecraft")) continue;
            ItemStack r;
            try { r = recipe.getResultItem(); } catch (Throwable t) { continue; }
            if (r == null || r.isEmpty()) continue;
            ResourceLocation iid = ForgeRegistries.ITEMS.getKey(r.getItem());
            if (iid != null && itemBL.contains(iid)) continue;
            candidates.add(recipe); results.add(r.copy());
        }
        if (candidates.size() < 2) return 0;

        List<ItemStack> shuffled = new ArrayList<>(results);
        Collections.shuffle(shuffled, new Random(seed));
        int changed = 0;
        for (int i = 0; i < candidates.size(); i++) {
            if (stacksEqual(results.get(i), shuffled.get(i))) continue;
            if (trySetResult(candidates.get(i), clampCount(shuffled.get(i).copy()))) changed++;
        }
        if (changed > 0) {
            List<IRecipe<?>> all = new ArrayList<>(mgr.getRecipes());
            server.getPlayerList().broadcastAll(new SUpdateRecipesPacket(all));
        }
        return changed;
    }

    private static ItemStack clampCount(ItemStack stack) {
        int max = Math.max(1, stack.getMaxStackSize());
        if (stack.getCount() > max) stack.setCount(max);
        return stack;
    }

    private static ResourceLocation tryParse(String s) {
        try { return new ResourceLocation(s); } catch (Throwable t) { return null; }
    }

    private static boolean stacksEqual(ItemStack a, ItemStack b) {
        return a.getItem() == b.getItem() && a.getCount() == b.getCount();
    }

    private static boolean trySetResult(IRecipe<?> recipe, ItemStack ns) {
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
