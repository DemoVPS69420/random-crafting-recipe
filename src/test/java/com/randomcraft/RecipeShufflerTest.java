package com.randomcraft;

import net.minecraft.SharedConstants;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.fabricmc.fabric.impl.recipe.sync.ClientboundRecipeSyncPayload;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class RecipeShufflerTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        RecipeSync.initialize();
        // In 26.3 default item components bind after dynamic registries load.
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(VanillaRegistries.createWorldLookup())
            .forEach(DataComponentInitializers.PendingComponents::apply);
    }

    private RecipeHolder<?> recipe(String name, Item item, int count, boolean shaped) {
        var common = new Recipe.CommonInfo(true);
        var book = new CraftingRecipe.CraftingBookInfo(CraftingBookCategory.MISC, "");
        var result = new ItemStackTemplate(item, count);
        CraftingRecipe recipe = shaped
            ? new ShapedRecipe(common, book, ShapedRecipePattern.of(Map.of('#', Ingredient.of(Items.DIRT)), "#"), result)
            : new ShapelessRecipe(common, book, result, List.of(Ingredient.of(Items.DIRT)));
        return new RecipeHolder<>(ResourceKey.create(Registries.RECIPE, Identifier.parse(name)), recipe);
    }

    private Item output(RecipeHolder<?> holder) {
        return ((CraftingRecipe) holder.value()).assemble(CraftingInput.EMPTY).getItem();
    }

    private List<RecipeHolder<?>> candidates() {
        return List.of(recipe("minecraft:a", Items.STICK, 4, true),
            recipe("minecraft:b", Items.DIAMOND, 3, false),
            recipe("minecraft:c", Items.IRON_SWORD, 1, true),
            recipe("minecraft:d", Items.APPLE, 2, false));
    }

    @Test
    void shuffledVanillaRecipesActuallyCraftTheNewItems() {
        var recipes = candidates();
        var original = recipes.stream().map(this::output).toList();
        assertTrue(RecipeShuffler.shuffleRecipes(recipes, new Config(), 42) > 0);
        var after = recipes.stream().map(this::output).toList();
        assertNotEquals(original, after);
        assertEquals(new HashSet<>(original), new HashSet<>(after));
        for (var holder : recipes) {
            var stack = ((CraftingRecipe) holder.value()).assemble(CraftingInput.EMPTY);
            assertTrue(stack.getCount() >= 1 && stack.getCount() <= stack.getMaxStackSize());
        }
    }

    @Test
    void sameSeedProducesSameOutputs() {
        var first = candidates();
        var second = candidates();
        RecipeShuffler.shuffleRecipes(first, new Config(), 42);
        RecipeShuffler.shuffleRecipes(second, new Config(), 42);
        assertEquals(first.stream().map(this::output).toList(), second.stream().map(this::output).toList());
    }

    @Test
    void blacklistedItemsAndRecipeIdsStayUntouched() {
        var protectedItem = recipe("minecraft:protected", Items.CRAFTING_TABLE, 1, true);
        var protectedId = recipe("minecraft:keep", Items.EMERALD, 1, false);
        var recipes = new ArrayList<>(candidates());
        recipes.add(protectedItem);
        recipes.add(protectedId);
        var config = new Config();
        config.recipeBlacklist.add("minecraft:keep");
        RecipeShuffler.shuffleRecipes(recipes, config, 42);
        assertSame(Items.CRAFTING_TABLE, output(protectedItem));
        assertSame(Items.EMERALD, output(protectedId));
    }

    @Test
    void moddedNamespacesRespectConfig() {
        var modded = recipe("example:custom", Items.EMERALD, 1, false);
        var recipes = new ArrayList<>(candidates());
        recipes.add(modded);
        var config = new Config();
        config.includeModdedRecipes = false;
        RecipeShuffler.shuffleRecipes(recipes, config, 42);
        assertSame(Items.EMERALD, output(modded));
        config.includeModdedRecipes = true;
        boolean moved = false;
        for (int seed = 0; seed < 20; seed++) {
            RecipeShuffler.shuffleRecipes(recipes, config, seed);
            moved |= output(modded) != Items.EMERALD;
        }
        assertTrue(moved);
    }

    @Test
    void emptyAndSingleCandidateAreUnchanged() {
        assertEquals(0, RecipeShuffler.shuffleRecipes(List.of(), new Config(), 42));
        var single = recipe("minecraft:only", Items.STICK, 4, false);
        assertEquals(0, RecipeShuffler.shuffleRecipes(List.of(single), new Config(), 42));
        assertSame(Items.STICK, output(single));
    }

    @Test
    void fabricRecipeSnapshotCarriesShuffledOutputsAndCounts() {
        var recipes = candidates();
        RecipeShuffler.shuffleRecipes(recipes, new Config(), 42);
        var groups = new LinkedHashMap<RecipeSerializer<?>, List<RecipeHolder<?>>>();
        for (var recipe : recipes) {
            groups.computeIfAbsent(recipe.value().getSerializer(), ignored -> new ArrayList<>()).add(recipe);
        }
        var entries = groups.entrySet().stream()
            .map(entry -> new ClientboundRecipeSyncPayload.Entry(entry.getKey(), entry.getValue())).toList();
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(),
            RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        try {
            ClientboundRecipeSyncPayload.CODEC.encode(buffer, new ClientboundRecipeSyncPayload(entries));
            var decoded = ClientboundRecipeSyncPayload.CODEC.decode(buffer);
            var received = new HashMap<Object, RecipeHolder<?>>();
            decoded.entries().forEach(entry -> entry.recipes().forEach(recipe -> received.put(recipe.id(), recipe)));
            assertEquals(recipes.size(), received.size());
            for (var original : recipes) {
                var expected = ((CraftingRecipe) original.value()).assemble(CraftingInput.EMPTY);
                var actual = ((CraftingRecipe) received.get(original.id()).value()).assemble(CraftingInput.EMPTY);
                assertTrue(net.minecraft.world.item.ItemStack.matches(expected, actual));
            }
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    void refinalizingRecipesRefreshesCachedRecipeBookOutputs() {
        var recipes = candidates();
        var lookup = new RegistrySetBuilder().add(Registries.RECIPE,
            context -> recipes.forEach(recipe -> context.register(recipe.id(), recipe.value())))
            .build(VanillaRegistries.createWorldLookup());
        var manager = new RecipeManager(lookup);
        manager.finalizeRecipeLoading(FeatureFlags.DEFAULT_FLAGS);
        var before = displayOutputs(manager, recipes);
        RecipeShuffler.shuffleRecipes(recipes, new Config(), 42);
        manager.finalizeRecipeLoading(FeatureFlags.DEFAULT_FLAGS);
        var after = displayOutputs(manager, recipes);
        assertNotEquals(before, after);
        for (var recipe : recipes) {
            assertEquals(recipe.value().display().getFirst().result(), after.get(recipe.id()));
        }
    }

    private Map<Object, SlotDisplay> displayOutputs(RecipeManager manager, List<RecipeHolder<?>> recipes) {
        Map<Object, SlotDisplay> outputs = new HashMap<>();
        for (var recipe : recipes) {
            manager.listDisplaysForRecipe(recipe.id(), entry -> outputs.put(recipe.id(), entry.display().result()));
        }
        assertEquals(recipes.size(), outputs.size());
        return outputs;
    }
}
