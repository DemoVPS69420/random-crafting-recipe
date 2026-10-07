package com.randomcraft.client;

import mezz.jei.api.*;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.ingredients.VanillaTypes;
import mezz.jei.api.recipe.*;
import mezz.jei.api.recipe.wrapper.ICraftingRecipeWrapper;
import mezz.jei.api.recipe.wrapper.IShapedCraftingRecipeWrapper;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.crafting.IShapedRecipe;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import java.util.*;

/** Optional JEI 4 API plugin. Dynamic lookups avoid stale output indexes and recipe accumulation. */
@JEIPlugin
public class RandomCraftJeiPlugin implements IModPlugin, IRecipeRegistryPlugin {
    private IRecipeRegistry registry;
    private final Map<ResourceLocation, IRecipeWrapper> hidden = new HashMap<>();

    @Override public void register(IModRegistry registry) { registry.addRecipeRegistryPlugin(this); }

    @Override public void onRuntimeAvailable(IJeiRuntime runtime) {
        registry = runtime.getRecipeRegistry();
        hidden.clear();
        ClientProxy.onChanged = this::refresh;
        refresh();
    }

    private void refresh() {
        if (registry == null) return;
        Iterator<Map.Entry<ResourceLocation, IRecipeWrapper>> it = hidden.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<ResourceLocation, IRecipeWrapper> entry = it.next();
            if (!ClientProxy.OUTPUTS.containsKey(entry.getKey())) {
                registry.unhideRecipe(entry.getValue(), VanillaRecipeCategoryUid.CRAFTING);
                it.remove();
            }
        }
        ClientProxy.OUTPUTS.forEach((id, stack) -> {
            if (hidden.containsKey(id)) return;
            IRecipe recipe = ForgeRegistries.RECIPES.getValue(id);
            if (recipe == null) return;
            IRecipeWrapper original = registry.getRecipeWrapper(recipe, VanillaRecipeCategoryUid.CRAFTING);
            if (original != null) {
                registry.hideRecipe(original, VanillaRecipeCategoryUid.CRAFTING);
                hidden.put(id, original);
            }
        });
    }

    @Override public <V> List<String> getRecipeCategoryUids(IFocus<V> focus) {
        return matching(focus).isEmpty() ? Collections.emptyList() : Collections.singletonList(VanillaRecipeCategoryUid.CRAFTING);
    }

    @Override @SuppressWarnings("unchecked")
    public <T extends IRecipeWrapper, V> List<T> getRecipeWrappers(IRecipeCategory<T> category, IFocus<V> focus) {
        return VanillaRecipeCategoryUid.CRAFTING.equals(category.getUid()) ? (List<T>) (List<?>) matching(focus) : Collections.emptyList();
    }

    @Override @SuppressWarnings("unchecked")
    public <T extends IRecipeWrapper> List<T> getRecipeWrappers(IRecipeCategory<T> category) {
        return VanillaRecipeCategoryUid.CRAFTING.equals(category.getUid()) ? (List<T>) (List<?>) matching(null) : Collections.emptyList();
    }

    private List<IRecipeWrapper> matching(IFocus<?> focus) {
        List<IRecipeWrapper> wrappers = new ArrayList<>();
        if (focus != null && !(focus.getValue() instanceof ItemStack)) return wrappers;
        ClientProxy.OUTPUTS.forEach((id, output) -> {
            if (!hidden.containsKey(id)) return;
            IRecipe recipe = ForgeRegistries.RECIPES.getValue(id);
            if (recipe == null || output.isEmpty()) return;
            if (focus != null) {
                ItemStack stack = (ItemStack) focus.getValue();
                if (focus.getMode() == IFocus.Mode.OUTPUT) {
                    if (!ItemStack.areItemsEqual(stack, output) || !ItemStack.areItemStackTagsEqual(stack, output)) return;
                } else if (recipe.getIngredients().stream().noneMatch(ingredient -> ingredient.apply(stack))) return;
            }
            wrappers.add(recipe instanceof IShapedRecipe ? new Shaped(recipe, output) : new Wrapper(recipe, output));
        });
        return wrappers;
    }

    private static class Wrapper implements ICraftingRecipeWrapper {
        final IRecipe recipe;
        final ItemStack output;
        Wrapper(IRecipe recipe, ItemStack output) { this.recipe = recipe; this.output = output.copy(); }
        @Override public ResourceLocation getRegistryName() { return recipe.getRegistryName(); }
        @Override public void getIngredients(IIngredients ingredients) {
            List<List<ItemStack>> inputs = new ArrayList<>();
            for (Ingredient ingredient : recipe.getIngredients()) inputs.add(Arrays.asList(ingredient.getMatchingStacks()));
            ingredients.setInputLists(VanillaTypes.ITEM, inputs);
            ingredients.setOutput(VanillaTypes.ITEM, output);
        }
    }

    private static final class Shaped extends Wrapper implements IShapedCraftingRecipeWrapper {
        Shaped(IRecipe recipe, ItemStack output) { super(recipe, output); }
        @Override public int getWidth() { return ((IShapedRecipe) recipe).getRecipeWidth(); }
        @Override public int getHeight() { return ((IShapedRecipe) recipe).getRecipeHeight(); }
    }
}
