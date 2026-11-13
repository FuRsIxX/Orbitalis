package com.orbitalis.core.network;

import com.orbitalis.Orbitalis;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Serveur → client : une tuile de terrain de l'atlas (voir {@code AtlasTiles}).
 *
 * @param lod   l'échelle, de 0 à 8 (une case vaut de 1 à 256 blocs, en doublant à chaque échelle)
 * @param tx    colonne de la tuile
 * @param tz    ligne de la tuile
 * @param cells les cases de la tuile et de sa marge ({@code AtlasTiles.SIDE} de côté), rangées par ligne (du nord au sud) puis par colonne (d'ouest en est) : la hauteur du
 *              terrain dans l'octet du haut (comptée depuis le niveau de la mer, décalée de {@code MAP_HEIGHT_BIAS}),
 *              puis sa couleur ; un tableau vide pour une tuile non relevée
 */
public record AtlasTilePayload(int lod, int tx, int tz, int[] cells) implements CustomPacketPayload {
    public static final Type<AtlasTilePayload> TYPE = new Type<>(Orbitalis.id("atlas_tile"));

    public static final StreamCodec<FriendlyByteBuf, AtlasTilePayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarInt(payload.lod);
                buffer.writeInt(payload.tx);
                buffer.writeInt(payload.tz);
                buffer.writeVarInt(payload.cells.length);
                for (int cell : payload.cells) {
                    buffer.writeInt(cell);
                }
            },
            buffer -> {
                int lod = buffer.readVarInt();
                int tx = buffer.readInt();
                int tz = buffer.readInt();
                int[] cells = new int[Math.min(buffer.readVarInt(), 1 << 12)];
                for (int i = 0; i < cells.length; i++) {
                    cells[i] = buffer.readInt();
                }
                return new AtlasTilePayload(lod, tx, tz, cells);
            });

    @Override
    public Type<AtlasTilePayload> type() {
        return TYPE;
    }
}
