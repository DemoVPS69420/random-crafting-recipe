package com.randomcraft;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;

import java.lang.reflect.Field;
import java.util.*;

public class RecipeShuffler {

    public static int shuffle(MinecraftServer server, long seed) {
        RecipeManager mgr = server.getRecipeManager();
        RegistryAccess access = server.registryAccess();

        Config cfg = Config.get();
        Set<ResourceLocation> recipeBlacklist = new HashSet<>();
        for (String s : cfg.recipeBlacklist) {
            ResourceLocation rl = ResourceLocation.tryParse(s);
            if (rl != null) recipeBlacklist.add(rl);
        }
        Set<ResourceLocation> itemBlacklist = new HashSet<>();
        for (String s : cfg.itemBlacklist) {
            ResourceLocation rl = ResourceLocation.tryParse(s);
            if (rl != null) itemBlacklist.add(rl);
        }
        boolean includeModded = cfg.includeModdedRecipes;

        List<RecipeHolder<?>> candidates = new ArrayList<>();
        List<ItemStack> results = new ArrayList<>();

        for (RecipeHolder<CraftingRecipe> holder : mgr.getAllRecipesFor(RecipeType.CRAFTING)) {
            ResourceLocation id = holder.id();
            if (recipeBlacklist.contains(id)) continue;
            if (!includeModded && !id.getNamespace().equals("minecraft")) continue;

            ItemStack result;
            try {
                result = holder.value().getResultItem(access);
            } catch (Throwable t) {
                continue;
            }
            if (result == null || result.isEmpty()) continue;

            ResourceLocation itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(result.getItem());
            if (itemBlacklist.contains(itemId)) continue;

            candidates.add(holder);
            results.add(result.copy());
        }

        if (candidates.size() < 2) return 0;

        List<ItemStack> shuffled = new ArrayList<>(results);
        Collections.shuffle(shuffled, new Random(seed));

        int changed = 0;
        for (int i = 0; i < candidates.size(); i++) {
            Recipe<?> recipe = candidates.get(i).value();
            ItemStack newResult = shuffled.get(i);
            if (ItemStack.matches(results.get(i), newResult)) continue;
            if (trySetResult(recipe, clampCount(newResult.copy()))) changed++;
        }

        if (changed > 0) {
            ClientboundUpdateRecipesPacket packet = new ClientboundUpdateRecipesPacket(mgr.getRecipes());
            server.getPlayerList().broadcastAll(packet);
        }
        return changed;
    }

    private static ItemStack clampCount(ItemStack stack) {
        int max = Math.max(1, stack.getMaxStackSize());
        if (stack.getCount() > max) stack.setCount(max);
        return stack;
    }

    private static boolean trySetResult(Recipe<?> recipe, ItemStack newResult) {
        Class<?> cls = recipe.getClass();
        while (cls != null && cls != Object.class) {
            for (Field f : cls.getDeclaredFields()) {
                if (f.getType() == ItemStack.class) {
                    try {
                        f.setAccessible(true);
                        f.set(recipe, newResult);
                        return true;
                    } catch (Throwable ignored) {}
                }
            }
            cls = cls.getSuperclass();
        }
        return false;
    }
}
