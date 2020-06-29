/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.core.sync.network;

import net.minecraft.client.MinecraftClient;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.Packet;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.network.INetHandler;
import net.minecraft.network.ThreadQuickExitException;
import net.minecraft.network.play.ServerPlayNetHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.world.dimension.DimensionType;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.LogicalSidedProvider;
import net.minecraftforge.fml.network.NetworkEvent;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.event.EventNetworkChannel;

import appeng.core.sync.BasePacket;

public class NetworkHandler {
    private static NetworkHandler instance;

    private final Identifier myChannelName;

    private final IPacketHandler clientHandler;
    private final IPacketHandler serverHandler;

    public NetworkHandler(final Identifier channelName) {
        EventNetworkChannel ec = NetworkRegistry.ChannelBuilder.named(myChannelName = channelName)
                .networkProtocolVersion(() -> "1").clientAcceptedVersions(s -> true).serverAcceptedVersions(s -> true)
                .eventNetworkChannel();
        ec.registerObject(this);

        this.clientHandler = DistExecutor.unsafeRunForDist(() -> ClientPacketHandler::new, () -> () -> null);
        this.serverHandler = this.createServerSide();
    }

    public static void init(final Identifier channelName) {
        instance = new NetworkHandler(channelName);
    }

    public static NetworkHandler instance() {
        return instance;
    }

    private IPacketHandler createServerSide() {
        try {
            return new ServerPacketHandler();
        } catch (final Throwable t) {
            return null;
        }
    }

    @SubscribeEvent
    public void serverPacket(final NetworkEvent.ClientCustomPayloadEvent ev) {
        if (this.serverHandler != null) {
            try {
                NetworkEvent.Context ctx = ev.getSource().get();
                ServerPlayNetHandler netHandler = (ServerPlayNetHandler) ctx.getNetworkManager().getNetHandler();
                ctx.setPacketHandled(true);
                ctx.enqueueWork(
                        () -> this.serverHandler.onPacketData(null, netHandler, ev.getPayload(), netHandler.player));

            } catch (final ThreadQuickExitException ignored) {

            }
        }
    }

    @SubscribeEvent
    public void clientPacket(final NetworkEvent.ServerCustomPayloadEvent ev) {
        if (ev instanceof NetworkEvent.ServerCustomPayloadLoginEvent) {
            return;
        }
        if (this.clientHandler != null) {
            try {
                NetworkEvent.Context ctx = ev.getSource().get();
                INetHandler netHandler = ctx.getNetworkManager().getNetHandler();
                ctx.setPacketHandled(true);
                ctx.enqueueWork(() -> this.clientHandler.onPacketData(null, netHandler, ev.getPayload(), null));
            } catch (final ThreadQuickExitException ignored) {

            }
        }
    }

    public Identifier getChannel() {
        return this.myChannelName;
    }

    public void sendToAll(final BasePacket message) {
        getServer().getPlayerList().sendPacketToAllPlayers(message.toPacket(NetworkSide.PLAY_TO_CLIENT));
    }

    public void sendTo(final BasePacket message, final ServerPlayerEntity player) {
        player.connection.sendPacket(message.toPacket(NetworkSide.PLAY_TO_CLIENT));
    }

    public void sendToAllAround(final BasePacket message, final TargetPoint point) {
        Packet<?> pkt = message.toPacket(NetworkSide.PLAY_TO_CLIENT);
        getServer().getPlayerList().sendToAllNearExcept(point.excluded, point.x, point.y, point.z, point.r2, point.dim,
                pkt);
    }

    public void sendToDimension(final BasePacket message, final DimensionType dim) {
        getServer().getPlayerList().sendPacketToAllPlayersInDimension(message.toPacket(NetworkSide.PLAY_TO_CLIENT),
                dim);
    }

    public void sendToServer(final BasePacket message) {
        MinecraftClient.getInstance().getConnection().sendPacket(message.toPacket(NetworkSide.PLAY_TO_SERVER));
    }

    private MinecraftServer getServer() {
        return LogicalSidedProvider.INSTANCE.get(LogicalSide.SERVER);
    }
}
