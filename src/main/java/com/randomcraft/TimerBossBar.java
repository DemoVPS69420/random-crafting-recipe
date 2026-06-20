package com.randomcraft;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.BossInfo;
import net.minecraft.world.server.ServerBossInfo;

import java.util.ArrayList;
import java.util.Collection;

/**
 * A server-wide boss bar showing a countdown to the next recipe shuffle.
 * Disabled by default; toggled via config (showTimerBossBar) or the
 * "/randomcraft timer on|off" command. Updated once per second.
 */
public class TimerBossBar {

    private static ServerBossInfo bar;

    private static ServerBossInfo bar() {
        if (bar == null) {
            bar = new ServerBossInfo(
                new StringTextComponent("RandomCraft"),
                BossInfo.Color.GREEN,
                BossInfo.Overlay.PROGRESS);
        }
        return bar;
    }

    public static void tick(MinecraftServer server) {
        if (server == null || !Config.SHOW_TIMER_BOSS_BAR.get()) return;

        long ticksLeft = RandomCraft.ticksUntilNextShuffle();
        long totalTicks = Math.max(20L, Config.SHUFFLE_INTERVAL_SECONDS.get() * 20L);
        float progress = Math.max(0f, Math.min(1f, (float) ticksLeft / (float) totalTicks));
        long sec = ticksLeft / 20;

        ServerBossInfo b = bar();
        b.setName(new StringTextComponent("§6RandomCraft §7| §eNext shuffle in §a" + format(sec)));
        b.setPercent(progress);
        syncPlayers(server);
    }

    public static void setEnabled(MinecraftServer server, boolean enabled) {
        if (enabled) {
            if (server != null) {
                for (ServerPlayerEntity p : server.getPlayerList().getPlayers()) bar().addPlayer(p);
            }
        } else {
            bar().removeAllPlayers();
        }
    }

    private static void syncPlayers(MinecraftServer server) {
        ServerBossInfo b = bar();
        Collection<ServerPlayerEntity> online = server.getPlayerList().getPlayers();
        for (ServerPlayerEntity p : new ArrayList<>(b.getPlayers())) {
            if (!online.contains(p)) b.removePlayer(p);
        }
        for (ServerPlayerEntity p : online) b.addPlayer(p);
    }

    private static String format(long totalSeconds) {
        long m = totalSeconds / 60;
        long s = totalSeconds % 60;
        return m + "m " + s + "s";
    }
}
