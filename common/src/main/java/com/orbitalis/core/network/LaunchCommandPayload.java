package com.orbitalis.core.network;

import com.orbitalis.Orbitalis;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → serveur : demande faite depuis l'écran d'un pupitre de lancement, ou depuis la cabine d'une fusée.
 * Le serveur vérifie que le joueur se trouve bien devant ce pupitre, ou à bord de cette fusée, avant d'agir.
 *
 * @param console position du pupitre ; depuis une cabine, celle de la table de lancement de la fusée
 * @param action  {@link #REFRESH}, {@link #START} ou {@link #ABORT}
 */
public record LaunchCommandPayload(BlockPos console, int action) implements CustomPacketPayload {
    /** Renvoyer l'état du complexe, pour actualiser l'écran. */
    public static final int REFRESH = 0;
    /** Démarrer le compte à rebours. */
    public static final int START = 1;
    /** Annuler le compte à rebours. */
    public static final int ABORT = 2;
    /** Faire une étape de la séquence de lancement : {@code STEP} plus le rang de l'étape dans {@code LaunchStep}. */
    public static final int STEP = 10;

    public static final Type<LaunchCommandPayload> TYPE = new Type<>(Orbitalis.id("launch_command"));

    public static final StreamCodec<ByteBuf, LaunchCommandPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, LaunchCommandPayload::console,
            ByteBufCodecs.VAR_INT, LaunchCommandPayload::action,
            LaunchCommandPayload::new);

    @Override
    public Type<LaunchCommandPayload> type() {
        return TYPE;
    }
}
