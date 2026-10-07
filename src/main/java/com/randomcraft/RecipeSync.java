package com.randomcraft;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import java.util.*;

/** 1.12 has no vanilla recipe packet. Send authoritative changed outputs in bounded chunks. */
public final class RecipeSync {
    private static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel("randomcraft");
    private static final Map<ResourceLocation, ItemStack> ORIGINALS = new LinkedHashMap<>();
    private static final Map<ResourceLocation, ItemStack> OUTPUTS = new LinkedHashMap<>();

    public static void initialize() {
        CHANNEL.registerMessage(Handler.class, Snapshot.class, 0, Side.CLIENT);
    }

    public static void record(IRecipe recipe, ItemStack previous, ItemStack result) {
        ResourceLocation id = recipe.getRegistryName();
        if (id == null) return;
        ORIGINALS.putIfAbsent(id, previous.copy());
        OUTPUTS.put(id, result.copy());
    }

    public static void restore() {
        ORIGINALS.forEach((id, output) -> {
            IRecipe recipe = ForgeRegistries.RECIPES.getValue(id);
            if (recipe != null) RecipeShuffler.trySetResult(recipe, output.copy());
        });
        ORIGINALS.clear();
        OUTPUTS.clear();
    }

    public static void sync(MinecraftServer server) {
        for (EntityPlayerMP player : server.getPlayerList().getPlayers()) send(player);
    }

    public static void send(EntityPlayerMP player) {
        List<Map.Entry<ResourceLocation, ItemStack>> entries = new ArrayList<>(OUTPUTS.entrySet());
        for (int offset = 0; offset < Math.max(1, entries.size()); offset += 128) {
            int end = Math.min(offset + 128, entries.size());
            Snapshot packet = new Snapshot();
            packet.first = offset == 0;
            packet.last = end == entries.size();
            for (int i = offset; i < end; i++) packet.outputs.put(entries.get(i).getKey(), entries.get(i).getValue().copy());
            CHANNEL.sendTo(packet, player);
        }
    }

    public static class Snapshot implements IMessage {
        public boolean first, last;
        public final Map<ResourceLocation, ItemStack> outputs = new LinkedHashMap<>();

        @Override public void toBytes(ByteBuf buffer) {
            buffer.writeBoolean(first).writeBoolean(last).writeInt(outputs.size());
            outputs.forEach((id, stack) -> {
                ByteBufUtils.writeUTF8String(buffer, id.toString());
                ByteBufUtils.writeItemStack(buffer, stack);
            });
        }

        @Override public void fromBytes(ByteBuf buffer) {
            first = buffer.readBoolean();
            last = buffer.readBoolean();
            int size = buffer.readInt();
            if (size < 0 || size > 128) throw new IllegalArgumentException("Invalid recipe snapshot size");
            for (int i = 0; i < size; i++) outputs.put(new ResourceLocation(ByteBufUtils.readUTF8String(buffer)), ByteBufUtils.readItemStack(buffer));
        }
    }

    public static class Handler implements IMessageHandler<Snapshot, IMessage> {
        @Override public IMessage onMessage(Snapshot message, MessageContext context) {
            RandomCraft.PROXY.acceptSnapshot(message);
            return null;
        }
    }
}
