package com.randomcraft;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.util.text.StringTextComponent;

public class RandomCraftCommand {
    public static void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(Commands.literal("randomcraft").requires(s -> s.hasPermission(2))
            .then(Commands.literal("shuffle").executes(ctx -> {
                CommandSource src = ctx.getSource();
                try {
                    long seed = System.currentTimeMillis();
                    int c = RecipeShuffler.shuffle(src.getServer(), seed);
                    RandomCraft.broadcast(src.getServer(),
                        "§6[RandomCraft] §eRecipes force-shuffled! §7(" + c + " recipes, seed=" + seed + ")");
                    RandomCraft.resetTimer();
                    return c;
                } catch (Throwable t) {
                    src.sendFailure(new StringTextComponent("Shuffle failed: " + t.getMessage()));
                    return 0;
                }
            }))
            .then(Commands.literal("next").executes(ctx -> {
                long sec = Math.max(0, RandomCraft.ticksUntilNextShuffle() / 20);
                ctx.getSource().sendSuccess(new StringTextComponent(
                    "§6[RandomCraft] §eNext shuffle in §a" + sec + "s §e(" + (sec/60) + "m " + (sec%60) + "s)"), false);
                return 1;
            })));
    }
}
