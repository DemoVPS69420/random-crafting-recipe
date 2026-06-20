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
import net.minecraft.network.chat.TextComponent;
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

    @SubscribeEvent public void onServerStarted(ServerStartedEvent e) {
        server = e.getServer(); resetTimer();
        if (Config.SHUFFLE_ON_SERVER_START.get()) runShuffle();
    }
    @SubscribeEvent public void onServerStopping(ServerStoppingEvent e) { TimerBossBar.setEnabled(server, false); server = null; nextShuffleTick = Long.MAX_VALUE; }
    @SubscribeEvent public void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || server == null) return;
        if (server.getTickCount() >= nextShuffleTick) { runShuffle(); resetTimer(); }
        if (server.getTickCount() % 20 == 0) TimerBossBar.tick(server);
    }
    @SubscribeEvent public void onRegisterCommands(RegisterCommandsEvent e) { RandomCraftCommand.register(e.getDispatcher()); }

    private void runShuffle() {
        if (server == null) return;
        long seed = System.currentTimeMillis(); lastSeed = seed;
        try {
            int c = RecipeShuffler.shuffle(server, seed);
            LOGGER.info("[RandomCraft] Shuffled {} recipes (seed={}).", c, seed);
            if (Config.BROADCAST_MESSAGE.get()) {
                server.getPlayerList().broadcastMessage(
                    new TextComponent("§6[RandomCraft] §eCrafting recipes have been shuffled!"),
                    net.minecraft.network.chat.ChatType.SYSTEM, net.minecraft.Util.NIL_UUID);
            }
        } catch (Throwable t) { LOGGER.error("Shuffle failed", t); }
    }

    public static void resetTimer() {
        if (server == null) return;
        long t = Math.max(20L, Config.SHUFFLE_INTERVAL_SECONDS.get() * 20L);
        nextShuffleTick = server.getTickCount() + t;
    }
    public static long ticksUntilNextShuffle() { return server == null ? 0 : Math.max(0, nextShuffleTick - server.getTickCount()); }
    public static long getLastSeed() { return lastSeed; }
}
