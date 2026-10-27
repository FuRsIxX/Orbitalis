package com.orbitalis.core.network;

import com.orbitalis.Orbitalis;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Serveur → client : le tableau de bord d'une station, vu de l'un de ses terminaux.
 *
 * @param terminal  le terminal d'où on la regarde
 * @param station   centre de son module central
 * @param open      vrai pour ouvrir l'écran, faux pour actualiser celui qui est ouvert
 * @param power     son réseau électrique et son orbite
 * @param air       ses générateurs d'air
 * @param lab       ses baies d'expériences et sa serre
 * @param crew      les noms des équipiers à bord
 * @param bunks     ses couchettes
 * @param cargo     ses casiers de fret : nombre, emplacements occupés
 * @param capsules  vaisseaux amarrés ou en attente près d'elle
 * @param modules   son plan
 */
public record StationStatusPayload(BlockPos terminal, BlockPos station, boolean open, Power power, Air air, Lab lab, List<String> crew,
        int bunks, int[] cargo, int capsules, List<Module> modules) implements CustomPacketPayload {
    public static final Type<StationStatusPayload> TYPE = new Type<>(Orbitalis.id("station_status"));

    /**
     * @param produced  production de la dernière seconde, en FE par tick
     * @param consumed  consommation de la dernière seconde, en FE par tick
     * @param stored    énergie en réserve, en FE
     * @param capacity  capacité de la réserve, en FE
     * @param batteries batteries raccordées
     * @param sunlit    vrai si la station est au soleil
     * @param switchIn  ticks avant l'éclipse, ou avant le retour au soleil
     */
    public record Power(int produced, int consumed, long stored, long capacity, int batteries, boolean sunlit, int switchIn) {
    }

    /**
     * @param generators générateurs d'air
     * @param working    générateurs qui pressurisent leur volume
     * @param leaks      générateurs dont le volume fuit
     * @param volume     volume pressurisé, en mètres cubes
     * @param plants     bacs plantés dans ce volume
     */
    public record Air(int generators, int working, int leaks, int volume, int plants) {
    }

    /**
     * @param racks       baies d'expériences
     * @param running     baies en marche
     * @param experiments expériences menées à bien
     * @param trays       bacs de culture
     * @param planted     bacs plantés
     * @param ripe        bacs mûrs, à récolter
     */
    public record Lab(int racks, int running, int experiments, int trays, int planted, int ripe) {
    }

    /** Un module du plan : son type, et son emprise relative au centre de la station (x, z ; minimum et maximum). */
    public record Module(int type, int x0, int z0, int x1, int z1) {
    }

    public static final StreamCodec<FriendlyByteBuf, StationStatusPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeBlockPos(payload.terminal);
                buffer.writeBlockPos(payload.station);
                buffer.writeBoolean(payload.open);
                Power p = payload.power;
                buffer.writeVarInt(p.produced);
                buffer.writeVarInt(p.consumed);
                buffer.writeVarLong(p.stored);
                buffer.writeVarLong(p.capacity);
                buffer.writeVarInt(p.batteries);
                buffer.writeBoolean(p.sunlit);
                buffer.writeVarInt(p.switchIn);
                Air a = payload.air;
                buffer.writeVarInt(a.generators);
                buffer.writeVarInt(a.working);
                buffer.writeVarInt(a.leaks);
                buffer.writeVarInt(a.volume);
                buffer.writeVarInt(a.plants);
                Lab l = payload.lab;
                buffer.writeVarInt(l.racks);
                buffer.writeVarInt(l.running);
                buffer.writeVarInt(l.experiments);
                buffer.writeVarInt(l.trays);
                buffer.writeVarInt(l.planted);
                buffer.writeVarInt(l.ripe);
                buffer.writeVarInt(payload.crew.size());
                payload.crew.forEach(name -> buffer.writeUtf(name, 64));
                buffer.writeVarInt(payload.bunks);
                buffer.writeVarInt(payload.cargo[0]);
                buffer.writeVarInt(payload.cargo[1]);
                buffer.writeVarInt(payload.capsules);
                buffer.writeVarInt(payload.modules.size());
                for (Module m : payload.modules) {
                    buffer.writeByte(m.type);
                    buffer.writeVarInt(m.x0);
                    buffer.writeVarInt(m.z0);
                    buffer.writeVarInt(m.x1);
                    buffer.writeVarInt(m.z1);
                }
            },
            buffer -> {
                BlockPos terminal = buffer.readBlockPos();
                BlockPos station = buffer.readBlockPos();
                boolean open = buffer.readBoolean();
                Power power = new Power(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarLong(), buffer.readVarLong(), buffer.readVarInt(),
                        buffer.readBoolean(), buffer.readVarInt());
                Air air = new Air(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
                Lab lab = new Lab(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                        buffer.readVarInt());
                int crewCount = Math.min(buffer.readVarInt(), 64);
                List<String> crew = new ArrayList<>(crewCount);
                for (int k = 0; k < crewCount; k++) {
                    crew.add(buffer.readUtf(64));
                }
                int bunks = buffer.readVarInt();
                int[] cargo = {buffer.readVarInt(), buffer.readVarInt()};
                int capsules = buffer.readVarInt();
                int count = Math.min(buffer.readVarInt(), 256);
                List<Module> modules = new ArrayList<>(count);
                for (int k = 0; k < count; k++) {
                    modules.add(new Module(buffer.readByte(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt()));
                }
                return new StationStatusPayload(terminal, station, open, power, air, lab, crew, bunks, cargo, capsules, modules);
            });

    @Override
    public Type<StationStatusPayload> type() {
        return TYPE;
    }
}
