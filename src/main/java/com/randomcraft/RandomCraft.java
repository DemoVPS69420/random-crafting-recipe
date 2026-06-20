package com.randomcraft;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod(RandomCraft.MODID)
public class RandomCraft {
    public static final String MODID = "randomcraft";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static MinecraftServer server;
    private static long nextShuffleTick = Long.MAX_VALUE;
    private static long lastSeed = 0L;

    public RandomCraft(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, Config.SPEC, "randomcraft-common.toml");
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        server = event.getServer();
        resetTimer();
        if (Config.SHUFFLE_ON_SERVER_START.get()) runShuffle();
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        TimerBossBar.setEnabled(server, false);
        server = null;
        nextShuffleTick = Long.MAX_VALUE;
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (server == null) return;
        if (server.getTickCount() >= nextShuffleTick) {
            runShuffle();
            resetTimer();
        }
        if (server.getTickCount() % 20 == 0) TimerBossBar.tick(server);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        RandomCraftCommand.register(event.getDispatcher());
    }

    private void runShuffle() {
        if (server == null) return;
        long seed = System.currentTimeMillis();
        lastSeed = seed;
        try {
            int changed = RecipeShuffler.shuffle(server, seed);
            LOGGER.info("[RandomCraft] Shuffled {} recipes (seed={}).", changed, seed);
            if (Config.BROADCAST_MESSAGE.get()) {
                server.getPlayerList().broadcastSystemMessage(
                    net.minecraft.network.chat.Component.literal("§6[RandomCraft] §eCrafting recipes have been shuffled!"), false);
            }
        } catch (Throwable t) {
            LOGGER.error("[RandomCraft] Shuffle failed", t);
        }
    }

    public static void resetTimer() {
        if (server == null) return;
        long t = Math.max(20L, Config.SHUFFLE_INTERVAL_SECONDS.get() * 20L);
        nextShuffleTick = server.getTickCount() + t;
    }

    public static long ticksUntilNextShuffle() {
        if (server == null) return 0;
        return Math.max(0, nextShuffleTick - server.getTickCount());
    }

    public static long getLastSeed() { return lastSeed; }
}
