package com.orbitalis.core.network;

import java.util.List;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.orbitalis.Orbitalis;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * Serveur → client : une fusée se disloque. Le serveur a décidé de l'accident et de ses dégâts ; chaque client le
 * met en scène (tronçons projetés, boule de feu, onde de choc, secousse : voir {@code BreakupFx}).
 *
 * @param base      pied de la fusée au moment de l'accident, ou point d'impact d'une épave
 * @param parts     tronçons de la fusée, du bas vers le haut (rangs dans {@code RocketBlock.Part}) ; vide pour un
 *                  simple impact
 * @param size      diamètre de la fusée (rang dans {@code RocketBlock.Size})
 * @param origin    rang du tronçon qui cède le premier
 * @param radius    rayon de l'onde de choc, en blocs
 * @param cryogenic vrai si la fusée porte de l'hydrogène liquide
 * @param shock     vrai si l'accident a lieu au sol : une onde de choc en part
 * @param attitude  orientation de la fusée quand elle cède : elle éclate penchée comme elle l'était
 * @param pivot     hauteur, au-dessus de son pied, du point autour duquel elle tournait
 * @param spin      sa vitesse de rotation : un vecteur porté par l'axe, long de l'angle par tick (en radians)
 */
public record BreakupPayload(Vec3 base, List<Integer> parts, int size, int origin, float radius, boolean cryogenic, boolean shock,
        Quaternionf attitude, float pivot, Vector3f spin) implements CustomPacketPayload {
    public static final Type<BreakupPayload> TYPE = new Type<>(Orbitalis.id("breakup"));

    private static final StreamCodec<ByteBuf, List<Integer>> PARTS = ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list());

    public static final StreamCodec<ByteBuf, BreakupPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                Vec3.STREAM_CODEC.encode(buffer, payload.base);
                PARTS.encode(buffer, payload.parts);
                ByteBufCodecs.VAR_INT.encode(buffer, payload.size);
                ByteBufCodecs.VAR_INT.encode(buffer, payload.origin);
                buffer.writeFloat(payload.radius);
                buffer.writeBoolean(payload.cryogenic);
                buffer.writeBoolean(payload.shock);
                ByteBufCodecs.QUATERNIONF.encode(buffer, payload.attitude);
                buffer.writeFloat(payload.pivot);
                ByteBufCodecs.VECTOR3F.encode(buffer, payload.spin);
            },
            buffer -> new BreakupPayload(Vec3.STREAM_CODEC.decode(buffer), PARTS.decode(buffer), ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer), buffer.readFloat(), buffer.readBoolean(), buffer.readBoolean(),
                    new Quaternionf(ByteBufCodecs.QUATERNIONF.decode(buffer)), buffer.readFloat(),
                    new Vector3f(ByteBufCodecs.VECTOR3F.decode(buffer))));

    @Override
    public Type<BreakupPayload> type() {
        return TYPE;
    }
}
