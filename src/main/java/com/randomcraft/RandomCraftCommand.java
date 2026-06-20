package com.randomcraft;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class RandomCraftCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("randomcraft").requires(s -> s.hasPermission(2))
            .then(Commands.literal("shuffle").executes(ctx -> {
                CommandSourceStack src = ctx.getSource();
                try {
                    long seed = System.currentTimeMillis();
                    int changed = RecipeShuffler.shuffle(src.getServer(), seed);
                    src.getServer().getPlayerList().broadcastSystemMessage(
                        Component.literal("§6[RandomCraft] §eRecipes force-shuffled! §7(" + changed + " recipes, seed=" + seed + ")"), false);
                    RandomCraft.resetTimer();
                    return changed;
                } catch (Throwable t) {
                    src.sendFailure(Component.literal("Shuffle failed: " + t.getMessage()));
                    return 0;
                }
            }))
            .then(Commands.literal("next").executes(ctx -> {
                long sec = Math.max(0, RandomCraft.ticksUntilNextShuffle() / 20);
                ctx.getSource().sendSuccess(() -> Component.literal(
                    "§6[RandomCraft] §eNext shuffle in §a" + sec + "s §e(" + (sec/60) + "m " + (sec%60) + "s)"), false);
                return 1;
            }))
            .then(Commands.literal("timer")
                .then(Commands.literal("on").executes(ctx -> setTimer(ctx.getSource(), true)))
                .then(Commands.literal("off").executes(ctx -> setTimer(ctx.getSource(), false)))));
    }

    private static int setTimer(CommandSourceStack src, boolean enabled) {
        Config.SHOW_TIMER_BOSS_BAR.set(enabled);
        Config.SPEC.save();
        TimerBossBar.setEnabled(src.getServer(), enabled);
        src.sendSuccess(() -> Component.literal(
            "§6[RandomCraft] §eTimer boss bar " + (enabled ? "§aenabled" : "§cdisabled") + "§e."), true);
        return 1;
    }
}
