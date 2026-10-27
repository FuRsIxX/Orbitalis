package com.orbitalis.core.network;

import java.util.List;

import com.orbitalis.Orbitalis;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Panneau de commande de l'ascenseur de la tour, dans les deux sens.
 * <ul>
 * <li>Serveur → client : ouvre le panneau, avec la liste des arrêts et celui où se trouve la cabine
 * ({@code target} vaut alors -1).</li>
 * <li>Client → serveur : le joueur a choisi un arrêt ({@code target} est son rang dans la liste). Le serveur
 * vérifie qu'il est bien dans la cabine.</li>
 * </ul>
 *
 * @param cabin   identifiant de la cabine
 * @param stops   niveau de chaque arrêt, du plus bas au plus haut
 * @param current rang de l'arrêt où se trouve la cabine
 * @param target  rang de l'arrêt demandé, ou -1
 */
public record ElevatorPayload(int cabin, List<Integer> stops, int current, int target) implements CustomPacketPayload {
    public static final Type<ElevatorPayload> TYPE = new Type<>(Orbitalis.id("elevator"));

    public static final StreamCodec<ByteBuf, ElevatorPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ElevatorPayload::cabin,
            ByteBufCodecs.INT.apply(ByteBufCodecs.list()), ElevatorPayload::stops,
            ByteBufCodecs.VAR_INT, ElevatorPayload::current,
            ByteBufCodecs.INT, ElevatorPayload::target,
            ElevatorPayload::new);

    @Override
    public Type<ElevatorPayload> type() {
        return TYPE;
    }
}
