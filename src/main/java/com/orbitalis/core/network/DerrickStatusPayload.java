package com.orbitalis.core.network;

import com.orbitalis.Orbitalis;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Serveur → client : l'état d'un derrick, pour son écran.
 *
 * @param pos          le derrick
 * @param open         vrai pour ouvrir l'écran, faux pour actualiser celui qui est ouvert
 * @param status       état ({@code DerrickBlockEntity.Status})
 * @param missing      blocs de la tour qui manquent encore
 * @param running      vrai si le forage est lancé
 * @param energy       énergie en réserve, et sa capacité, en FE
 * @param water        eau de refroidissement, et sa capacité, en mB
 * @param heat         chaleur de la tête, en millièmes
 * @param radius       demi-côté du carré foré
 * @param backfill     vrai si le remblai est en marche
 * @param filter       sortes à extraire, un bit par sorte ({@code QuarryKinds})
 * @param layer        couche en cours ; {@code top} le pied du derrick, {@code bottom} le fond du monde
 * @param mined        blocs extraits depuis la pose
 * @param stored       objets en attente dans la réserve
 * @param core         avancement du carottage en cours, de 0 à 100, ou -1
 * @param coreBands    résultat du dernier carottage : un bit par sorte présente, une valeur par couche de seize blocs
 * @param observers    satellites d'observation de l'agence en service
 * @param surveySide   côté de la carte du relevé satellite ; 0 sans relevé
 * @param surveyCells  sorte dominante de chaque colonne, plus un (0 : rien), rangées du nord au sud puis d'ouest en est
 * @param surveyCounts blocs de chaque sorte sous tout le carré
 */
public record DerrickStatusPayload(BlockPos pos, boolean open, int status, int missing, boolean running, int energy, int energyCapacity,
        int water, int waterCapacity, int heat, int radius, boolean backfill, int filter, int layer, int top, int bottom, int mined,
        int stored, int core, int[] coreBands, int observers, int surveySide, int[] surveyCells, int[] surveyCounts)
        implements CustomPacketPayload {
    public static final Type<DerrickStatusPayload> TYPE = new Type<>(Orbitalis.id("derrick_status"));

    public static final StreamCodec<FriendlyByteBuf, DerrickStatusPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeBlockPos(payload.pos);
                buffer.writeBoolean(payload.open);
                buffer.writeVarInt(payload.status);
                buffer.writeVarInt(payload.missing);
                buffer.writeBoolean(payload.running);
                buffer.writeVarInt(payload.energy);
                buffer.writeVarInt(payload.energyCapacity);
                buffer.writeVarInt(payload.water);
                buffer.writeVarInt(payload.waterCapacity);
                buffer.writeVarInt(payload.heat);
                buffer.writeVarInt(payload.radius);
                buffer.writeBoolean(payload.backfill);
                buffer.writeVarInt(payload.filter);
                buffer.writeInt(payload.layer);
                buffer.writeInt(payload.top);
                buffer.writeInt(payload.bottom);
                buffer.writeVarInt(payload.mined);
                buffer.writeVarInt(payload.stored);
                buffer.writeInt(payload.core);
                buffer.writeVarIntArray(payload.coreBands);
                buffer.writeVarInt(payload.observers);
                buffer.writeVarInt(payload.surveySide);
                buffer.writeVarIntArray(payload.surveyCells);
                buffer.writeVarIntArray(payload.surveyCounts);
            },
            buffer -> new DerrickStatusPayload(buffer.readBlockPos(), buffer.readBoolean(), buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readBoolean(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readVarInt(), buffer.readBoolean(), buffer.readVarInt(), buffer.readInt(), buffer.readInt(), buffer.readInt(),
                    buffer.readVarInt(), buffer.readVarInt(), buffer.readInt(), buffer.readVarIntArray(), buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readVarIntArray(), buffer.readVarIntArray()));

    @Override
    public Type<DerrickStatusPayload> type() {
        return TYPE;
    }
}
