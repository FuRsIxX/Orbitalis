package com.orbitalis.core.network;

import com.orbitalis.Orbitalis;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → serveur : le joueur ordonne le saut depuis l'écran du poste de pilotage. Le serveur vérifie tout de nouveau.
 *
 * @param helm le poste de pilotage
 */
public record ShipJumpPayload(BlockPos helm) implements CustomPacketPayload {
    public static final Type<ShipJumpPayload> TYPE = new Type<>(Orbitalis.id("ship_jump"));

    public static final StreamCodec<ByteBuf, ShipJumpPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ShipJumpPayload::helm,
            ShipJumpPayload::new);

    @Override
    public Type<ShipJumpPayload> type() {
        return TYPE;
    }
}
