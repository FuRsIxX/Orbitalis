package com.orbitalis.core.network;

import com.orbitalis.Orbitalis;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → serveur : un bouton de l'écran de chantier. Le serveur pose, avec ce que le joueur a sur lui, la couche
 * suivante de la fusée ({@link #LAYER}) ou tout ce qu'il peut ({@link #ALL}), puis renvoie l'état du chantier.
 *
 * @param table  la table de lancement
 * @param action {@link #LAYER} ou {@link #ALL}
 */
public record ConstructionActionPayload(BlockPos table, int action) implements CustomPacketPayload {
    public static final int LAYER = 0;
    public static final int ALL = 1;

    public static final Type<ConstructionActionPayload> TYPE = new Type<>(Orbitalis.id("construction_action"));

    public static final StreamCodec<ByteBuf, ConstructionActionPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ConstructionActionPayload::table,
            ByteBufCodecs.VAR_INT, ConstructionActionPayload::action,
            ConstructionActionPayload::new);

    @Override
    public Type<ConstructionActionPayload> type() {
        return TYPE;
    }
}
