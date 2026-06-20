package com.randomcraft;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;

import java.lang.reflect.Field;
import java.util.*;

public class RecipeShuffler {

    public static int shuffle(MinecraftServer server, long seed) {
        RecipeManager mgr = server.getRecipeManager();

        Config cfg = Config.get();
        Set<Identifier> recipeBlacklist = new HashSet<>();
        for (String s : cfg.recipeBlacklist) {
            Identifier id = Identifier.tryParse(s);
            if (id != null) recipeBlacklist.add(id);
        }
        Set<Identifier> itemBlacklist = new HashSet<>();
        for (String s : cfg.itemBlacklist) {
            Identifier id = Identifier.tryParse(s);
            if (id != null) itemBlacklist.add(id);
        }
        boolean includeModded = cfg.includeModdedRecipes;

        List<Recipe<?>> candidates = new ArrayList<>();
        List<Item> originalItems = new ArrayList<>();

        for (RecipeHolder<?> holder : mgr.getRecipes()) {
            Recipe<?> recipe = holder.value();
            if (!(recipe instanceof CraftingRecipe)) continue;

            Identifier id = holder.id().identifier();
            if (recipeBlacklist.contains(id)) continue;
            if (!includeModded && !id.getNamespace().equals("minecraft")) continue;

            Item item = readResultItem(recipe);
            if (item == null) continue;

            Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
            if (itemId != null && itemBlacklist.contains(itemId)) continue;

            candidates.add(recipe);
            originalItems.add(item);
        }

        if (candidates.size() < 2) return 0;

        List<Item> shuffled = new ArrayList<>(originalItems);
        Collections.shuffle(shuffled, new Random(seed));

        int changed = 0;
        for (int i = 0; i < candidates.size(); i++) {
            if (originalItems.get(i) == shuffled.get(i)) continue;
            if (writeResultItem(candidates.get(i), shuffled.get(i))) changed++;
        }
        return changed;
    }

    private static Item readResultItem(Recipe<?> recipe) {
        Class<?> cls = recipe.getClass();
        while (cls != null && cls != Object.class) {
            for (Field f : cls.getDeclaredFields()) {
                Class<?> t = f.getType();
                if (t == ItemStackTemplate.class) {
                    try {
                        f.setAccessible(true);
                        ItemStackTemplate tpl = (ItemStackTemplate) f.get(recipe);
                        if (tpl == null) continue;
                        Holder<Item> h = tpl.item();
                        if (h != null) return h.value();
                    } catch (Throwable ignored) {}
                } else if (t == ItemStack.class) {
                    try {
                        f.setAccessible(true);
                        ItemStack stack = (ItemStack) f.get(recipe);
                        if (stack != null && !stack.isEmpty()) return stack.getItem();
                    } catch (Throwable ignored) {}
                }
            }
            cls = cls.getSuperclass();
        }
        return null;
    }

    private static boolean writeResultItem(Recipe<?> recipe, Item newItem) {
        int maxStack = Math.max(1, newItem.getDefaultMaxStackSize());
        Class<?> cls = recipe.getClass();
        while (cls != null && cls != Object.class) {
            for (Field f : cls.getDeclaredFields()) {
                Class<?> t = f.getType();
                if (t == ItemStackTemplate.class) {
                    try {
                        f.setAccessible(true);
                        ItemStackTemplate old = (ItemStackTemplate) f.get(recipe);
                        int origCount = old != null ? old.count() : 1;
                        int count = Math.min(origCount, maxStack);
                        f.set(recipe, new ItemStackTemplate(newItem, count));
                        return true;
                    } catch (Throwable ignored) {}
                } else if (t == ItemStack.class) {
                    try {
                        f.setAccessible(true);
                        ItemStack old = (ItemStack) f.get(recipe);
                        int origCount = (old != null && !old.isEmpty()) ? old.getCount() : 1;
                        int count = Math.min(origCount, maxStack);
                        f.set(recipe, new ItemStack(newItem, count));
                        return true;
                    } catch (Throwable ignored) {}
                }
            }
            cls = cls.getSuperclass();
        }
        return false;
    }
}
