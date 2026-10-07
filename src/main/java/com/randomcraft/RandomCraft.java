package com.randomcraft;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.Logger;

import java.io.File;

@Mod(modid = RandomCraft.MODID, name = "RandomCraft", version = "1.2.0", acceptableRemoteVersions = "[1.2.0,1.3)")
public class RandomCraft {
    public static final String MODID = "randomcraft";
    public static Logger LOGGER;
    @net.minecraftforge.fml.common.SidedProxy(clientSide = "com.randomcraft.client.ClientProxy", serverSide = "com.randomcraft.CommonProxy")
    public static CommonProxy PROXY;

    private static MinecraftServer server;
    private static long nextShuffleTick = Long.MAX_VALUE;
    private static long lastSeed = 0L;

    public static int shuffleIntervalSeconds = 600;
    public static boolean shuffleOnServerStart = true;
    public static boolean broadcastMessage = true;
    public static boolean includeModded = true;
    public static String[] recipeBlacklist = new String[0];
    public static String[] itemBlacklist = new String[]{"minecraft:crafting_table", "minecraft:chest"};

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOGGER = event.getModLog();
        RecipeSync.initialize();
        PROXY.initialize();
        File cfgFile = new File(event.getModConfigurationDirectory(), "randomcraft.cfg");
        Configuration cfg = new Configuration(cfgFile);
        cfg.load();
        shuffleIntervalSeconds = cfg.getInt("shuffleIntervalSeconds", "general", 600, 20, Integer.MAX_VALUE,
                "Interval between shuffles in seconds. 600 = 10 min.");
        shuffleOnServerStart = cfg.getBoolean("shuffleOnServerStart", "general", true, "");
        broadcastMessage = cfg.getBoolean("broadcastMessage", "general", true, "");
        includeModded = cfg.getBoolean("includeModdedRecipes", "general", true, "");
        recipeBlacklist = cfg.getStringList("recipeBlacklist", "general", new String[0], "");
        itemBlacklist = cfg.getStringList("itemBlacklist", "general",
                new String[]{"minecraft:crafting_table","minecraft:chest"}, "");
        if (cfg.hasChanged()) cfg.save();
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Mod.EventHandler
    public void onServerStarting(FMLServerStartingEvent event) {
        RandomCraftCommand.register(event);
    }

    @Mod.EventHandler
    public void onServerStarted(FMLServerStartedEvent event) {
        server = net.minecraftforge.fml.common.FMLCommonHandler.instance().getMinecraftServerInstance();
        resetTimer();
        if (shuffleOnServerStart) runShuffle();
    }

    @Mod.EventHandler
    public void onServerStopping(FMLServerStoppingEvent event) { RecipeSync.restore(); server = null; nextShuffleTick = Long.MAX_VALUE; }

    @SubscribeEvent
    public void onPlayerLogin(net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof net.minecraft.entity.player.EntityPlayerMP) {
            RecipeSync.send((net.minecraft.entity.player.EntityPlayerMP) event.player);
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || server == null) return;
        if (server.getTickCounter() >= nextShuffleTick) { runShuffle(); resetTimer(); }
    }

    public static void runShuffle() {
        if (server == null) return;
        long seed = System.currentTimeMillis(); lastSeed = seed;
        try {
            int c = RecipeShuffler.shuffle(server, seed);
            if (LOGGER != null) LOGGER.info("[RandomCraft] Shuffled {} recipes (seed={}).", c, seed);
            if (broadcastMessage) {
                server.getPlayerList().sendMessage(
                    new TextComponentString("§6[RandomCraft] §eCrafting recipes have been shuffled!"));
            }
        } catch (Throwable t) { if (LOGGER != null) LOGGER.error("Shuffle failed", t); }
    }

    public static void resetTimer() {
        if (server == null) return;
        long t = Math.max(20L, (long) shuffleIntervalSeconds * 20L);
        nextShuffleTick = server.getTickCounter() + t;
    }
    public static long ticksUntilNextShuffle() { return server == null ? 0 : Math.max(0, nextShuffleTick - server.getTickCounter()); }
    public static MinecraftServer getServer() { return server; }
}
