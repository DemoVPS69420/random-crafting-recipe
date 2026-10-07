package com.randomcraft;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;

public class RandomCraftCommand extends CommandBase {
    public static void register(FMLServerStartingEvent event) {
        event.registerServerCommand(new RandomCraftCommand());
    }

    @Override public String getName() { return "randomcraft"; }
    @Override public String getUsage(ICommandSender s) { return "/randomcraft <shuffle|next|sync>"; }
    @Override public int getRequiredPermissionLevel() { return 2; }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        if (args.length == 0) { sender.sendMessage(new TextComponentString(getUsage(sender))); return; }
        if ("shuffle".equalsIgnoreCase(args[0])) {
            try {
                long seed = System.currentTimeMillis();
                int c = RecipeShuffler.shuffle(server, seed);
                server.getPlayerList().sendMessage(
                    new TextComponentString("§6[RandomCraft] §eRecipes force-shuffled! §7(" + c + " recipes, seed=" + seed + ")"));
                RandomCraft.resetTimer();
            } catch (Throwable t) {
                sender.sendMessage(new TextComponentString("§cShuffle failed: " + t.getMessage()));
            }
        } else if ("sync".equalsIgnoreCase(args[0])) {
            RecipeSync.sync(server);
        } else if ("next".equalsIgnoreCase(args[0])) {
            long sec = Math.max(0, RandomCraft.ticksUntilNextShuffle() / 20);
            sender.sendMessage(new TextComponentString(
                "§6[RandomCraft] §eNext shuffle in §a" + sec + "s §e(" + (sec/60) + "m " + (sec%60) + "s)"));
        } else {
            sender.sendMessage(new TextComponentString(getUsage(sender)));
        }
    }
}
