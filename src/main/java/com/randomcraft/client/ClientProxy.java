package com.randomcraft.client;

import com.randomcraft.CommonProxy;
import com.randomcraft.RecipeSync;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import java.util.*;

public class ClientProxy extends CommonProxy {
    public static final Map<ResourceLocation, ItemStack> OUTPUTS = new LinkedHashMap<>();
    private final Map<ResourceLocation, ItemStack> pending = new LinkedHashMap<>();
    public static Runnable onChanged = () -> {};

    @Override public void initialize() { MinecraftForge.EVENT_BUS.register(this); }

    @Override public void acceptSnapshot(RecipeSync.Snapshot snapshot) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            if (snapshot.first) pending.clear();
            pending.putAll(snapshot.outputs);
            if (snapshot.last) {
                OUTPUTS.clear();
                OUTPUTS.putAll(pending);
                pending.clear();
                onChanged.run();
            }
        });
    }

    @SubscribeEvent public void disconnected(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            OUTPUTS.clear();
            pending.clear();
            onChanged.run();
        });
    }
}
