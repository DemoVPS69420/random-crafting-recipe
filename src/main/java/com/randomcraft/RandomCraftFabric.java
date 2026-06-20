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
    public static final Logger LOGGER = LoggerFactory.getLogger("randomcraft");
    private static MinecraftServer server;
    private static long nextShuffleTick = Long.MAX_VALUE;
    private static long lastSeed = 0L;

    @Override
    public void onInitialize() {
        Config.get();
        ServerLifecycleEvents.SERVER_STARTED.register(s -> {
            server = s; resetTimer();
            if (Config.get().shuffleOnServerStart) runShuffle();
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> { server = null; nextShuffleTick = Long.MAX_VALUE; });
        ServerTickEvents.END_SERVER_TICK.register(s -> {
            if (server == null) return;
            if (s.getTickCount() >= nextShuffleTick) { runShuffle(); resetTimer(); }
        });
        CommandRegistrationCallback.EVENT.register((d, reg, env) -> RandomCraftCommand.register(d));
    }

    private static void runShuffle() {
        if (server == null) return;
        long seed = System.currentTimeMillis(); lastSeed = seed;
        try {
            int c = RecipeShuffler.shuffle(server, seed);
            LOGGER.info("[RandomCraft] Shuffled {} recipes (seed={}).", c, seed);
            if (Config.get().broadcastMessage) {
                server.getPlayerList().broadcastSystemMessage(
                    Component.literal("§6[RandomCraft] §eCrafting recipes have been shuffled!"), false);
            }
        } catch (Throwable t) { LOGGER.error("Shuffle failed", t); }
    }

    public static void resetTimer() {
        if (server == null) return;
        long t = Math.max(20L, (long) Config.get().shuffleIntervalSeconds * 20L);
        nextShuffleTick = server.getTickCount() + t;
    }
    public static long ticksUntilNextShuffle() { return server == null ? 0 : Math.max(0, nextShuffleTick - server.getTickCount()); }
    public static long getLastSeed() { return lastSeed; }
    public static MinecraftServer getServer() { return server; }
}
