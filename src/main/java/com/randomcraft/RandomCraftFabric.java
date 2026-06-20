package com.randomcraft;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RandomCraftFabric implements ModInitializer {
    public static final String MODID = "randomcraft";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    private static MinecraftServer server;
    private static long nextShuffleTick = Long.MAX_VALUE;
    private static long lastSeed = 0L;

    @Override
    public void onInitialize() {
        Config.get();

        ServerLifecycleEvents.SERVER_STARTED.register(s -> {
            server = s;
            resetTimer();
            if (Config.get().shuffleOnServerStart) runShuffle();
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
            TimerBossBar.setEnabled(s, false);
            server = null;
            nextShuffleTick = Long.MAX_VALUE;
        });

        ServerTickEvents.END_SERVER_TICK.register(s -> {
            if (server == null) return;
            if (s.getTickCount() >= nextShuffleTick) {
                runShuffle();
                resetTimer();
            }
            if (s.getTickCount() % 20 == 0) TimerBossBar.tick(s);
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, reg, env) ->
            RandomCraftCommand.register(dispatcher));
    }

    private static void runShuffle() {
        if (server == null) return;
        long seed = System.currentTimeMillis();
        lastSeed = seed;
        try {
            int changed = RecipeShuffler.shuffle(server, seed);
            LOGGER.info("[RandomCraft] Shuffled {} crafting recipes (seed={}).", changed, seed);
            if (Config.get().broadcastMessage) {
                server.getPlayerList().broadcastSystemMessage(
                    Component.literal("§6[RandomCraft] §eCrafting recipes have been shuffled!"), false);
            }
        } catch (Throwable t) {
            LOGGER.error("[RandomCraft] Failed to shuffle recipes", t);
        }
    }

    public static void forceShuffle() { runShuffle(); }

    public static void resetTimer() {
        if (server == null) return;
        long intervalTicks = Math.max(20L, (long) Config.get().shuffleIntervalSeconds * 20L);
        nextShuffleTick = server.getTickCount() + intervalTicks;
    }

    public static long ticksUntilNextShuffle() {
        if (server == null) return 0;
        return Math.max(0, nextShuffleTick - server.getTickCount());
    }

    public static long getLastSeed() { return lastSeed; }
    public static MinecraftServer getServer() { return server; }
}
