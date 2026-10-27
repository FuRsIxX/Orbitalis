package com.orbitalis.core.network;

import com.orbitalis.Orbitalis;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Serveur → client : la tablette de mission de ce joueur peut ouvrir l'atlas, centré sur l'endroit où il se trouve.
 *
 * @param source l'endroit où la tablette est ouverte : c'est de là que l'atlas est consulté
 */
public record AtlasOpenPayload(BlockPos source) implements CustomPacketPayload {
    public static final Type<AtlasOpenPayload> TYPE = new Type<>(Orbitalis.id("atlas_open"));

    public static final StreamCodec<ByteBuf, AtlasOpenPayload> STREAM_CODEC =
            StreamCodec.composite(BlockPos.STREAM_CODEC, AtlasOpenPayload::source, AtlasOpenPayload::new);

    @Override
    public Type<AtlasOpenPayload> type() {
        return TYPE;
    }
}
