package com.orbitalis.core.network;

import com.orbitalis.Orbitalis;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client → serveur : l'écran d'un terminal de station demande l'état de la station, pour s'actualiser. */
public record StationRequestPayload(BlockPos terminal) implements CustomPacketPayload {
    public static final Type<StationRequestPayload> TYPE = new Type<>(Orbitalis.id("station_request"));
    public static final StreamCodec<FriendlyByteBuf, StationRequestPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeBlockPos(payload.terminal),
            buffer -> new StationRequestPayload(buffer.readBlockPos()));

    @Override
    public Type<StationRequestPayload> type() {
        return TYPE;
    }
}
