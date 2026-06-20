package com.randomcraft;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
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

    public RandomCraft() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC, "randomcraft-common.toml");
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        server = event.getServer();
        long intervalTicks = Math.max(20L, Config.SHUFFLE_INTERVAL_SECONDS.get() * 20L);
        nextShuffleTick = server.getTickCount() + intervalTicks;

        if (Config.SHUFFLE_ON_SERVER_START.get()) {
            runShuffle();
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        TimerBossBar.setEnabled(server, false);
        server = null;
        nextShuffleTick = Long.MAX_VALUE;
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || server == null) return;
        if (server.getTickCount() >= nextShuffleTick) {
            runShuffle();
            long intervalTicks = Math.max(20L, Config.SHUFFLE_INTERVAL_SECONDS.get() * 20L);
            nextShuffleTick = server.getTickCount() + intervalTicks;
        }
        if (server.getTickCount() % 20 == 0) TimerBossBar.tick(server);
    }

    private void runShuffle() {
        if (server == null) return;
        long seed = System.currentTimeMillis();
        lastSeed = seed;
        try {
            int changed = RecipeShuffler.shuffle(server, seed);
            LOGGER.info("[RandomCraft] Shuffled {} crafting recipes (seed={}).", changed, seed);
            if (Config.BROADCAST_MESSAGE.get()) {
                server.getPlayerList().broadcastSystemMessage(
                        net.minecraft.network.chat.Component.literal("§6[RandomCraft] §eCrafting recipes have been shuffled!"),
                        false);
            }
        } catch (Throwable t) {
            LOGGER.error("[RandomCraft] Failed to shuffle recipes", t);
        }
    }

    public static long getLastSeed() { return lastSeed; }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        RandomCraftCommand.register(event.getDispatcher());
    }

    public static void resetTimer() {
        if (server == null) return;
        long intervalTicks = Math.max(20L, Config.SHUFFLE_INTERVAL_SECONDS.get() * 20L);
        nextShuffleTick = server.getTickCount() + intervalTicks;
    }

    public static long ticksUntilNextShuffle() {
        if (server == null) return 0;
        return Math.max(0, nextShuffleTick - server.getTickCount());
    }
}
