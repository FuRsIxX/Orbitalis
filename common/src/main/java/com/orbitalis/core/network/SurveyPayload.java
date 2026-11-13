package com.orbitalis.core.network;

import com.orbitalis.Orbitalis;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Serveur → client : un relevé lointain du terrain autour d'une table de lancement, pour la carte de navigation.
 * <p>
 * La table en calcule deux, à partir du générateur du monde : la région (un cube pour 6 blocs) et le pays (un cube
 * pour 32 blocs), chacun sur 256 cubes de côté, centré sur elle. Ils ne changent pas : ils sont envoyés une fois, à
 * qui les demande.
 *
 * @param table position de la table
 * @param lod   0 pour la région, 1 pour le pays
 * @param cells les cubes, rangés par ligne (du nord au sud) puis par colonne (d'ouest en est) : la hauteur du terrain
 *              dans l'octet du haut (comptée depuis le niveau de la mer, décalée de {@code MAP_HEIGHT_BIAS}), puis sa couleur ;
 *              0 pour un cube non relevé
 */
public record SurveyPayload(BlockPos table, int lod, int[] cells) implements CustomPacketPayload {
    public static final Type<SurveyPayload> TYPE = new Type<>(Orbitalis.id("survey"));

    public static final StreamCodec<FriendlyByteBuf, SurveyPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeBlockPos(payload.table);
                buffer.writeVarInt(payload.lod);
                buffer.writeVarInt(payload.cells.length);
                for (int cell : payload.cells) {
                    buffer.writeInt(cell);
                }
            },
            buffer -> {
                BlockPos table = buffer.readBlockPos();
                int lod = buffer.readVarInt();
                int[] cells = new int[Math.min(buffer.readVarInt(), 1 << 16)];
                for (int i = 0; i < cells.length; i++) {
                    cells[i] = buffer.readInt();
                }
                return new SurveyPayload(table, lod, cells);
            });

    @Override
    public Type<SurveyPayload> type() {
        return TYPE;
    }
}
