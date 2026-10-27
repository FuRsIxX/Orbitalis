package com.orbitalis.core.network;

import com.orbitalis.Orbitalis;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → serveur : demande faite depuis l'écran d'une station de poursuite.
 * Le serveur vérifie que le joueur se trouve bien devant cette station avant d'agir.
 *
 * @param station position de la station
 * @param action  {@link #REFRESH} ou {@link #DOWNLOAD}
 */
public record SatelliteCommandPayload(BlockPos station, int action) implements CustomPacketPayload {
    /** Renvoyer l'état des satellites, pour actualiser l'écran. */
    public static final int REFRESH = 0;
    /** Recevoir au sol les données en mémoire des satellites. */
    public static final int DOWNLOAD = 1;
    /** Demander un relevé de prospection aux satellites d'observation. */
    public static final int PROSPECT = 2;
    /** Demander l'état de l'observatoire. */
    public static final int OBSERVATORY = 3;
    /** Pointer les instruments sur un astre : ce code, plus le rang de l'astre. */
    public static final int OBSERVE = 1000;
    /** Recevoir au sol l'image d'un astre découvert : ce code, plus le rang de l'astre. */
    public static final int RECEIVE = 2000;

    public static final Type<SatelliteCommandPayload> TYPE = new Type<>(Orbitalis.id("satellite_command"));

    public static final StreamCodec<ByteBuf, SatelliteCommandPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SatelliteCommandPayload::station,
            ByteBufCodecs.VAR_INT, SatelliteCommandPayload::action,
            SatelliteCommandPayload::new);

    @Override
    public Type<SatelliteCommandPayload> type() {
        return TYPE;
    }
}
