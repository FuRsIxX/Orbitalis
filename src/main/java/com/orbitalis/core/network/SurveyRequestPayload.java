package com.orbitalis.core.network;

import com.orbitalis.Orbitalis;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → serveur : la carte de navigation de ce joueur n'a pas encore les relevés lointains de cette table de
 * lancement, et les demande (voir {@link SurveyPayload}) ; ou l'écran d'une station de poursuite demande le relevé du
 * pays autour d'elle. Le serveur les envoie s'ils sont prêts.
 *
 * @param table position de la table, ou de la station
 */
public record SurveyRequestPayload(BlockPos table) implements CustomPacketPayload {
    public static final Type<SurveyRequestPayload> TYPE = new Type<>(Orbitalis.id("survey_request"));

    public static final StreamCodec<ByteBuf, SurveyRequestPayload> STREAM_CODEC =
            StreamCodec.composite(BlockPos.STREAM_CODEC, SurveyRequestPayload::table, SurveyRequestPayload::new);

    @Override
    public Type<SurveyRequestPayload> type() {
        return TYPE;
    }
}
