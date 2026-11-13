package com.orbitalis.core.network;

import com.orbitalis.Orbitalis;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → serveur : les commandes que tient le pilote d'une cabine, une par bit. Le client l'envoie quand elles
 * changent, et le confirme quatre fois par seconde ; le serveur les relâche de lui-même s'il n'en a plus de nouvelles.
 * <p>
 * Ces commandes ont leurs propres touches, réglables dans les options du jeu (voir {@code PilotControls}) : elles
 * ne dépendent pas des touches de déplacement.
 *
 * @param bits commandes tenues
 */
public record PilotInputPayload(int bits) implements CustomPacketPayload {
    /** Tangage : piquer (le nez part vers le sens du tir) et cabrer. */
    public static final int PITCH_DOWN = 1;
    public static final int PITCH_UP = 1 << 1;
    public static final int YAW_LEFT = 1 << 2;
    public static final int YAW_RIGHT = 1 << 3;
    public static final int ROLL_LEFT = 1 << 4;
    public static final int ROLL_RIGHT = 1 << 5;
    /** Faire l'étape du vol qui vient de s'allumer. */
    public static final int STEP = 1 << 6;
    /** Ouvrir la séquence de lancement, ou redécoller de la Lune. */
    public static final int LAUNCH = 1 << 7;
    /** Quitter l'orbite et rentrer. */
    public static final int RETURN = 1 << 8;
    /** Partir de l'orbite vers la Lune. */
    public static final int MOON = 1 << 9;
    /** Armer les parachutes pendant la rentrée. */
    public static final int CHUTE = 1 << 10;

    public static final Type<PilotInputPayload> TYPE = new Type<>(Orbitalis.id("pilot_input"));

    public static final StreamCodec<ByteBuf, PilotInputPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, PilotInputPayload::bits, PilotInputPayload::new);

    @Override
    public Type<PilotInputPayload> type() {
        return TYPE;
    }
}
