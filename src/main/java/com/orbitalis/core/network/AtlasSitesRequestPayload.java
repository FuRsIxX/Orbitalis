package com.orbitalis.core.network;

import com.orbitalis.Orbitalis;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → serveur : l'atlas ouvert depuis ce pupitre demande les installations qu'il connaît, et leur état (voir
 * {@link AtlasSitesPayload}). Il redemande toutes les deux secondes tant qu'il est ouvert.
 *
 * @param source le pupitre
 */
public record AtlasSitesRequestPayload(BlockPos source) implements CustomPacketPayload {
    public static final Type<AtlasSitesRequestPayload> TYPE = new Type<>(Orbitalis.id("atlas_sites_request"));

    public static final StreamCodec<ByteBuf, AtlasSitesRequestPayload> STREAM_CODEC =
            StreamCodec.composite(BlockPos.STREAM_CODEC, AtlasSitesRequestPayload::source, AtlasSitesRequestPayload::new);

    @Override
    public Type<AtlasSitesRequestPayload> type() {
        return TYPE;
    }
}
