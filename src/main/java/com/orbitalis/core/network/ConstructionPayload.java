package com.orbitalis.core.network;

import java.util.ArrayList;
import java.util.List;

import com.orbitalis.Orbitalis;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/**
 * Serveur → client : l'état du chantier d'une fusée sur son pas de tir. Le client ouvre l'écran de chantier (ou met à
 * jour celui qui est ouvert) ; un plan vide le ferme, la fusée étant formée ou le plan repris.
 * <p>
 * Le plan lui-même est envoyé : le client en recalcule les blocs avec les mêmes règles que le serveur, et ne reçoit
 * que ce qu'il ne peut pas savoir, ce qui est déjà posé.
 *
 * @param open   vrai pour ouvrir l'écran, faux pour mettre à jour celui qui est ouvert
 * @param table  la table de lancement
 * @param plan   le plan de la fusée, ou une pile vide pour fermer l'écran
 * @param facing sens du carneau du pas de tir (rang horizontal), qui oriente la couronne d'une fusée large
 * @param placed pour chaque bloc du plan, dans son ordre, vrai s'il est en place
 * @param ports  les raccords de ravitaillement qui manquent (rang de leur ergol)
 */
public record ConstructionPayload(boolean open, BlockPos table, ItemStack plan, int facing, List<Boolean> placed, List<Integer> ports)
        implements CustomPacketPayload {
    public static final Type<ConstructionPayload> TYPE = new Type<>(Orbitalis.id("construction"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConstructionPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeBoolean(payload.open);
                buffer.writeBlockPos(payload.table);
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, payload.plan);
                buffer.writeVarInt(payload.facing);
                buffer.writeVarInt(payload.placed.size());
                for (boolean done : payload.placed) {
                    buffer.writeBoolean(done);
                }
                buffer.writeVarInt(payload.ports.size());
                for (int port : payload.ports) {
                    buffer.writeVarInt(port);
                }
            },
            buffer -> {
                boolean open = buffer.readBoolean();
                BlockPos table = buffer.readBlockPos();
                ItemStack plan = ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer);
                int facing = buffer.readVarInt();
                int count = Math.min(buffer.readVarInt(), 4096);
                List<Boolean> placed = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    placed.add(buffer.readBoolean());
                }
                int portCount = Math.min(buffer.readVarInt(), 8);
                List<Integer> ports = new ArrayList<>();
                for (int i = 0; i < portCount; i++) {
                    ports.add(buffer.readVarInt());
                }
                return new ConstructionPayload(open, table, plan, facing, List.copyOf(placed), List.copyOf(ports));
            });

    @Override
    public Type<ConstructionPayload> type() {
        return TYPE;
    }
}
