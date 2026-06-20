package com.randomcraft;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

import java.util.ArrayList;

/**
 * A server-wide boss bar showing a countdown to the next recipe shuffle.
 * Disabled by default; toggled via config (showTimerBossBar) or the
 * "/randomcraft timer on|off" command. Updated once per second.
 */
public class TimerBossBar {

    private static ServerBossEvent bar;

    private static ServerBossEvent bar() {
        if (bar == null) {
            bar = new ServerBossEvent(
                Component.literal("RandomCraft"),
                BossEvent.BossBarColor.GREEN,
                BossEvent.BossBarOverlay.PROGRESS);
        }
        return bar;
    }

    /** Called once per second from the server tick loop. */
    public static void tick(MinecraftServer server) {
        if (server == null || !Config.get().showTimerBossBar) return;

        long ticksLeft = RandomCraftFabric.ticksUntilNextShuffle();
        long totalTicks = Math.max(20L, (long) Config.get().shuffleIntervalSeconds * 20L);
        float progress = Math.max(0f, Math.min(1f, (float) ticksLeft / (float) totalTicks));
        long sec = ticksLeft / 20;

        ServerBossEvent b = bar();
        b.setName(Component.literal("§6RandomCraft §7| §eNext shuffle in §a" + format(sec)));
        b.setProgress(progress);
        syncPlayers(server);
    }

    /** Enable/disable: add or remove all online players from the bar. */
    public static void setEnabled(MinecraftServer server, boolean enabled) {
        if (enabled) {
            if (server != null) {
                for (ServerPlayer p : server.getPlayerList().getPlayers()) bar().addPlayer(p);
            }
        } else {
            bar().removeAllPlayers();
        }
    }

    /** Keep the bar's viewer set in sync with who is online. addPlayer is idempotent. */
    private static void syncPlayers(MinecraftServer server) {
        ServerBossEvent b = bar();
        var online = server.getPlayerList().getPlayers();
        for (ServerPlayer p : new ArrayList<>(b.getPlayers())) {
            if (!online.contains(p)) b.removePlayer(p);
        }
        for (ServerPlayer p : online) b.addPlayer(p);
    }

    private static String format(long totalSeconds) {
        long m = totalSeconds / 60;
        long s = totalSeconds % 60;
        return m + "m " + s + "s";
    }
}
