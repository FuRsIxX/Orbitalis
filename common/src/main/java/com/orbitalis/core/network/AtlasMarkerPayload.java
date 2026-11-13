package com.orbitalis.core.network;

import com.orbitalis.Orbitalis;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → serveur : depuis l'atlas d'un pupitre, un joueur pose, renomme ou retire un repère de son agence (voir
 * {@code AtlasMarkers}). Le serveur renvoie aussitôt les installations et les repères à jour.
 *
 * @param source le pupitre
 * @param action {@link #ADD}, {@link #RENAME} ou {@link #REMOVE}
 * @param id     le repère visé (renommer, retirer)
 * @param x      la place d'un repère posé
 * @param y      son altitude
 * @param z      la place d'un repère posé
 * @param name   le nouveau nom (renommer)
 */
public record AtlasMarkerPayload(BlockPos source, int action, int id, int x, int y, int z, String name) implements CustomPacketPayload {
    public static final Type<AtlasMarkerPayload> TYPE = new Type<>(Orbitalis.id("atlas_marker"));
    public static final int ADD = 0;
    public static final int RENAME = 1;
    public static final int REMOVE = 2;

    public static final StreamCodec<FriendlyByteBuf, AtlasMarkerPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeBlockPos(payload.source);
                buffer.writeByte(payload.action);
                buffer.writeVarInt(payload.id);
                buffer.writeInt(payload.x);
                buffer.writeInt(payload.y);
                buffer.writeInt(payload.z);
                buffer.writeUtf(payload.name, 64);
            },
            buffer -> new AtlasMarkerPayload(buffer.readBlockPos(), buffer.readByte(), buffer.readVarInt(), buffer.readInt(), buffer.readInt(),
                    buffer.readInt(), buffer.readUtf(64)));

    @Override
    public Type<AtlasMarkerPayload> type() {
        return TYPE;
    }
}
