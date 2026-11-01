package com.orbitalis.core.network;

import com.orbitalis.Orbitalis;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → serveur : le joueur a changé un réglage sur l'écran de conception. L'ordinateur garde le brouillon, pour
 * celui qui reviendra et pour les joueurs qui regardent le même écran.
 *
 * @param pos      l'ordinateur
 * @param draft    le brouillon tel qu'il est réglé
 * @param preset   le modèle de départ (voir l'écran de conception)
 * @param selected l'étage qu'on règle
 */
public record DesignEditPayload(BlockPos pos, DesignPrintPayload draft, int preset, int selected) implements CustomPacketPayload {
    public static final Type<DesignEditPayload> TYPE = new Type<>(Orbitalis.id("design_edit"));

    public static final StreamCodec<ByteBuf, DesignEditPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, DesignEditPayload::pos,
            DesignPrintPayload.STREAM_CODEC, DesignEditPayload::draft,
            ByteBufCodecs.VAR_INT, DesignEditPayload::preset,
            ByteBufCodecs.VAR_INT, DesignEditPayload::selected,
            DesignEditPayload::new);

    @Override
    public Type<DesignEditPayload> type() {
        return TYPE;
    }
}
