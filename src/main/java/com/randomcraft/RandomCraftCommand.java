package com.randomcraft;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class RandomCraftCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("randomcraft")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("shuffle").executes(ctx -> {
                    CommandSourceStack src = ctx.getSource();
                    try {
                        long seed = System.currentTimeMillis();
                        int changed = RecipeShuffler.shuffle(src.getServer(), seed);
                        src.getServer().getPlayerList().broadcastSystemMessage(
                            Component.literal("§6[RandomCraft] §eRecipes force-shuffled! §7(" + changed + " recipes, seed=" + seed + ")"),
                            false);
                        RandomCraft.resetTimer();
                        return changed;
                    } catch (Throwable t) {
                        src.sendFailure(Component.literal("RandomCraft shuffle failed: " + t.getMessage()));
                        RandomCraft.LOGGER.error("Force shuffle failed", t);
                        return 0;
                    }
                }))
                .then(Commands.literal("next").executes(ctx -> {
                    long ticksLeft = RandomCraft.ticksUntilNextShuffle();
                    long sec = Math.max(0, ticksLeft / 20);
                    ctx.getSource().sendSuccess(() -> Component.literal(
                        "§6[RandomCraft] §eNext shuffle in §a" + sec + "s §e(" + (sec / 60) + "m " + (sec % 60) + "s)"), false);
                    return 1;
                }))
        );
    }
}
