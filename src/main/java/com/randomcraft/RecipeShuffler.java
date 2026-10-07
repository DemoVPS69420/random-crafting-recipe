package com.randomcraft;

import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.lang.reflect.Field;
import java.util.*;

public class RecipeShuffler {
    public static int shuffle(MinecraftServer server, long seed) {
        Set<ResourceLocation> recipeBL = new HashSet<>();
        for (String s : RandomCraft.recipeBlacklist) { try { recipeBL.add(new ResourceLocation(s)); } catch (Throwable ignored) {} }
        Set<ResourceLocation> itemBL = new HashSet<>();
        for (String s : RandomCraft.itemBlacklist) { try { itemBL.add(new ResourceLocation(s)); } catch (Throwable ignored) {} }
        boolean incMod = RandomCraft.includeModded;

        List<IRecipe> candidates = new ArrayList<>();
        List<ItemStack> results = new ArrayList<>();

        for (IRecipe recipe : ForgeRegistries.RECIPES) {
            ResourceLocation id = recipe.getRegistryName();
            if (id == null) continue;
            if (recipeBL.contains(id)) continue;
            if (!incMod && !id.getNamespace().equals("minecraft")) continue;
            ItemStack r;
            try { r = recipe.getRecipeOutput(); } catch (Throwable t) { continue; }
            if (r == null || r.isEmpty()) continue;
            ResourceLocation iid = r.getItem().getRegistryName();
            if (iid != null && itemBL.contains(iid)) continue;
            candidates.add(recipe); results.add(r.copy());
        }
        if (candidates.size() < 2) return 0;

        List<ItemStack> shuffled = new ArrayList<>(results);
        Collections.shuffle(shuffled, new Random(seed));
        int changed = 0;
        for (int i = 0; i < candidates.size(); i++) {
            if (ItemStack.areItemStacksEqual(results.get(i), shuffled.get(i))) continue;
            ItemStack result = shuffled.get(i).copy();
            result.setCount(Math.min(result.getCount(), result.getMaxStackSize()));
            if (trySetResult(candidates.get(i), result)) {
                RecipeSync.record(candidates.get(i), results.get(i), result);
                changed++;
            }
        }
        if (changed > 0) RecipeSync.sync(server);
        return changed;
    }

    static boolean trySetResult(IRecipe recipe, ItemStack ns) {
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
