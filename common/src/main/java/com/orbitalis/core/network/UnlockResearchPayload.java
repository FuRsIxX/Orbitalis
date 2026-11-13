package com.orbitalis.core.network;

import com.orbitalis.Orbitalis;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → serveur : le joueur demande à rechercher une technologie depuis le terminal.
 * Le serveur vérifie lui-même les prérequis et les points : le client ne décide de rien.
 *
 * @param nodeId identifiant de la technologie
 */
public record UnlockResearchPayload(String nodeId) implements CustomPacketPayload {
    public static final Type<UnlockResearchPayload> TYPE = new Type<>(Orbitalis.id("unlock_research"));

    public static final StreamCodec<ByteBuf, UnlockResearchPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(64), UnlockResearchPayload::nodeId,
            UnlockResearchPayload::new);

    @Override
    public Type<UnlockResearchPayload> type() {
        return TYPE;
    }
}
