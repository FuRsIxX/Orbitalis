package com.orbitalis.core.network;

import java.util.List;

import com.orbitalis.Orbitalis;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Serveur → client : état de la recherche de l'agence du joueur.
 *
 * @param points   points de recherche disponibles
 * @param unlocked identifiants des technologies recherchées
 */
public record ResearchSyncPayload(int points, List<String> unlocked) implements CustomPacketPayload {
    public static final Type<ResearchSyncPayload> TYPE = new Type<>(Orbitalis.id("research_sync"));

    public static final StreamCodec<ByteBuf, ResearchSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ResearchSyncPayload::points,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), ResearchSyncPayload::unlocked,
            ResearchSyncPayload::new);

    @Override
    public Type<ResearchSyncPayload> type() {
        return TYPE;
    }
}
