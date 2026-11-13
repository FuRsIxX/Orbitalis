package com.orbitalis.core.network;

import com.orbitalis.Orbitalis;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → serveur : le joueur prend la navette depuis l'écran du poste de pilotage, le vaisseau en orbite de Proxima b.
 *
 * @param helm le poste de pilotage
 */
public record ShipShuttlePayload(BlockPos helm) implements CustomPacketPayload {
    public static final Type<ShipShuttlePayload> TYPE = new Type<>(Orbitalis.id("ship_shuttle"));

    public static final StreamCodec<ByteBuf, ShipShuttlePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ShipShuttlePayload::helm,
            ShipShuttlePayload::new);

    @Override
    public Type<ShipShuttlePayload> type() {
        return TYPE;
    }
}
