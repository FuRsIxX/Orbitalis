package com.orbitalis.core.network;

import com.orbitalis.Orbitalis;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → serveur : un bouton de l'écran du chantier naval. Le serveur pose, avec ce que le joueur a sur lui, la
 * section en cours du vaisseau ({@link #SECTION}) ou tout ce qu'il peut ({@link #ALL}).
 *
 * @param console le pupitre du chantier
 * @param action  {@link #SECTION} ou {@link #ALL}
 */
public record ShipyardActionPayload(BlockPos console, int action) implements CustomPacketPayload {
    public static final int SECTION = 0;
    public static final int ALL = 1;

    public static final Type<ShipyardActionPayload> TYPE = new Type<>(Orbitalis.id("shipyard_action"));

    public static final StreamCodec<ByteBuf, ShipyardActionPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ShipyardActionPayload::console,
            ByteBufCodecs.VAR_INT, ShipyardActionPayload::action,
            ShipyardActionPayload::new);

    @Override
    public Type<ShipyardActionPayload> type() {
        return TYPE;
    }
}
