package com.orbitalis.core.network;

import com.orbitalis.Orbitalis;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Serveur → client : le brouillon que garde un ordinateur de conception. Il arrive quand le joueur ouvre l'écran, puis
 * chaque fois qu'un autre joueur, devant le même ordinateur, change un réglage.
 *
 * @param pos      l'ordinateur
 * @param open     vrai pour ouvrir l'écran, faux pour actualiser celui qui est ouvert
 * @param saved    vrai si l'ordinateur garde un brouillon ; sinon l'écran part du premier modèle connu
 * @param draft    le brouillon gardé
 * @param preset   le modèle de départ (voir l'écran de conception)
 * @param selected l'étage qu'on réglait
 */
public record DesignStatePayload(BlockPos pos, boolean open, boolean saved, DesignPrintPayload draft, int preset, int selected)
        implements CustomPacketPayload {
    public static final Type<DesignStatePayload> TYPE = new Type<>(Orbitalis.id("design_state"));

    public static final StreamCodec<ByteBuf, DesignStatePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, DesignStatePayload::pos,
            ByteBufCodecs.BOOL, DesignStatePayload::open,
            ByteBufCodecs.BOOL, DesignStatePayload::saved,
            DesignPrintPayload.STREAM_CODEC, DesignStatePayload::draft,
            ByteBufCodecs.VAR_INT, DesignStatePayload::preset,
            ByteBufCodecs.VAR_INT, DesignStatePayload::selected,
            DesignStatePayload::new);

    @Override
    public Type<DesignStatePayload> type() {
        return TYPE;
    }
}
