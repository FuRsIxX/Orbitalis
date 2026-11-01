package com.orbitalis.core.network;

import com.orbitalis.Orbitalis;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Serveur → client : l'état d'une foreuse, pour son écran (les jauges, elles, suivent le menu à chaque tick).
 *
 * @param pos          la foreuse
 * @param status       état ({@code DerrickBlockEntity.Status})
 * @param missing      blocs qui manquent encore à la tour
 * @param running      vrai si le forage est lancé
 * @param mode         {@code DerrickBlockEntity.MODE_*} : filons ou tout miner
 * @param chunk        vrai si la zone est un tronçon entier du monde
 * @param levels       étages de mâts de la tour
 * @param side         côté de la zone forée
 * @param maxSide      côté le plus grand que permet la tour
 * @param depth        profondeur que permet la tour, en blocs ; 0 : jusqu'au fond du monde
 * @param replace      vrai si les blocs extraits sont remplacés
 * @param filter       sortes à extraire en mode filons, un bit par sorte ({@code QuarryKinds})
 * @param layer        couche en cours ; {@code top} la surface, {@code bottom} le fond foré
 * @param mined        blocs extraits depuis la pose
 * @param stored       objets en attente dans la réserve
 * @param core         avancement du carottage en cours, de 0 à 100, ou -1
 * @param coreBands    résultat du dernier carottage : un bit par sorte présente, une valeur par couche de seize blocs
 * @param observers    satellites d'observation de l'agence en service
 * @param surveyWidth  largeur (ouest-est) de la carte du relevé satellite ; 0 sans relevé
 * @param surveyCells  sorte dominante de chaque colonne, plus un (0 : rien), rangées du nord au sud puis d'ouest en est
 * @param surveyCounts blocs de chaque sorte sous toute la zone
 */
public record DerrickStatusPayload(BlockPos pos, int status, int missing, boolean running, int mode, boolean chunk, int levels, int side,
        int maxSide, int depth, boolean replace, int filter, int layer, int top, int bottom, int mined, int stored, int core, int[] coreBands,
        int observers, int surveyWidth, int[] surveyCells, int[] surveyCounts) implements CustomPacketPayload {
    public static final Type<DerrickStatusPayload> TYPE = new Type<>(Orbitalis.id("derrick_status"));

    public static final StreamCodec<FriendlyByteBuf, DerrickStatusPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeBlockPos(payload.pos);
                buffer.writeVarInt(payload.status);
                buffer.writeVarInt(payload.missing);
                buffer.writeBoolean(payload.running);
                buffer.writeVarInt(payload.mode);
                buffer.writeBoolean(payload.chunk);
                buffer.writeVarInt(payload.levels);
                buffer.writeVarInt(payload.side);
                buffer.writeVarInt(payload.maxSide);
                buffer.writeVarInt(payload.depth);
                buffer.writeBoolean(payload.replace);
                buffer.writeVarInt(payload.filter);
                buffer.writeInt(payload.layer);
                buffer.writeInt(payload.top);
                buffer.writeInt(payload.bottom);
                buffer.writeVarInt(payload.mined);
                buffer.writeVarInt(payload.stored);
                buffer.writeInt(payload.core);
                buffer.writeVarIntArray(payload.coreBands);
                buffer.writeVarInt(payload.observers);
                buffer.writeVarInt(payload.surveyWidth);
                buffer.writeVarIntArray(payload.surveyCells);
                buffer.writeVarIntArray(payload.surveyCounts);
            },
            buffer -> new DerrickStatusPayload(buffer.readBlockPos(), buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean(),
                    buffer.readVarInt(), buffer.readBoolean(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readBoolean(), buffer.readVarInt(), buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readVarInt(),
                    buffer.readVarInt(), buffer.readInt(), buffer.readVarIntArray(), buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readVarIntArray(), buffer.readVarIntArray()));

    @Override
    public Type<DerrickStatusPayload> type() {
        return TYPE;
    }
}
