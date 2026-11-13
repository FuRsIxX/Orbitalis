package com.orbitalis.core.network;

import com.orbitalis.Orbitalis;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → serveur : le joueur demande une étude depuis l'établi d'ingénieur.
 * Le serveur vérifie lui-même la technologie, les études préalables et les matériaux : le client ne décide de rien.
 *
 * @param id identifiant de l'étude (voir {@code EngineeringStudies})
 */
public record StudyPayload(String id) implements CustomPacketPayload {
    public static final Type<StudyPayload> TYPE = new Type<>(Orbitalis.id("study"));

    public static final StreamCodec<ByteBuf, StudyPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(64), StudyPayload::id,
            StudyPayload::new);

    @Override
    public Type<StudyPayload> type() {
        return TYPE;
    }
}
