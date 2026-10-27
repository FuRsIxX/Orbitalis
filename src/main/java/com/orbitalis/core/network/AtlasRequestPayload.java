package com.orbitalis.core.network;

import com.orbitalis.Orbitalis;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → serveur : l'atlas de ce joueur demande une tuile de terrain (voir {@code AtlasTiles}). Le serveur répond
 * par un {@link AtlasTilePayload}, vide si la tuile est hors de ce que le centre de contrôle connaît.
 *
 * @param source le pupitre du centre de contrôle depuis lequel l'atlas est consulté
 * @param lod    l'échelle, de 0 à 8 (une case vaut de 1 à 256 blocs, en doublant à chaque échelle)
 * @param tx     colonne de la tuile
 * @param tz     ligne de la tuile
 */
public record AtlasRequestPayload(BlockPos source, int lod, int tx, int tz) implements CustomPacketPayload {
    public static final Type<AtlasRequestPayload> TYPE = new Type<>(Orbitalis.id("atlas_request"));

    public static final StreamCodec<ByteBuf, AtlasRequestPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, AtlasRequestPayload::source,
            ByteBufCodecs.VAR_INT, AtlasRequestPayload::lod,
            ByteBufCodecs.INT, AtlasRequestPayload::tx,
            ByteBufCodecs.INT, AtlasRequestPayload::tz,
            AtlasRequestPayload::new);

    @Override
    public Type<AtlasRequestPayload> type() {
        return TYPE;
    }
}
