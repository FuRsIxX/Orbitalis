package com.orbitalis.core.network;

import java.util.ArrayList;
import java.util.List;

import com.orbitalis.Orbitalis;
import com.orbitalis.phase1.rocket.RocketDraft;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → serveur : le joueur demande à l'ordinateur de conception d'imprimer le plan de la fusée qu'il a réglée.
 * Les pièces voyagent par leur rang dans le registre des objets ; le serveur revérifie tout ({@link RocketDraft#isValid}).
 *
 * @param payload      rang de la charge utile (celui de l'air pour aucune)
 * @param engines      rang du moteur de chaque étage
 * @param engineCounts nombre de moteurs de chaque étage
 * @param tanks        rang du réservoir de chaque étage (celui de l'air pour un étage sans réservoir)
 * @param tankCounts   nombre de réservoirs de chaque étage
 * @param boosters     boosters latéraux du premier étage : 0, ou une paire
 */
public record DesignPrintPayload(int payload, List<Integer> engines, List<Integer> engineCounts, List<Integer> tanks, List<Integer> tankCounts,
        int boosters) implements CustomPacketPayload {
    public static final Type<DesignPrintPayload> TYPE = new Type<>(Orbitalis.id("design_print"));

    public static final StreamCodec<ByteBuf, DesignPrintPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, DesignPrintPayload::payload,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(3)), DesignPrintPayload::engines,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(3)), DesignPrintPayload::engineCounts,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(3)), DesignPrintPayload::tanks,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(3)), DesignPrintPayload::tankCounts,
            ByteBufCodecs.VAR_INT, DesignPrintPayload::boosters,
            DesignPrintPayload::new);

    /** Le message qui porte ce brouillon. */
    public static DesignPrintPayload of(RocketDraft draft) {
        List<Integer> engines = new ArrayList<>();
        List<Integer> engineCounts = new ArrayList<>();
        List<Integer> tanks = new ArrayList<>();
        List<Integer> tankCounts = new ArrayList<>();
        for (RocketDraft.Stage stage : draft.stages()) {
            engines.add(BuiltInRegistries.ITEM.getId(stage.engine()));
            engineCounts.add(stage.engines());
            tanks.add(BuiltInRegistries.ITEM.getId(stage.tank()));
            tankCounts.add(stage.tanks());
        }
        return new DesignPrintPayload(BuiltInRegistries.ITEM.getId(draft.payload()), engines, engineCounts, tanks, tankCounts, draft.boosters());
    }

    /** Le brouillon que porte ce message ; à revérifier avant usage. */
    public RocketDraft draft() {
        List<RocketDraft.Stage> stages = new ArrayList<>();
        int count = Math.min(Math.min(engines.size(), engineCounts.size()), Math.min(tanks.size(), tankCounts.size()));
        for (int i = 0; i < count; i++) {
            stages.add(new RocketDraft.Stage(BuiltInRegistries.ITEM.byId(engines.get(i)), engineCounts.get(i),
                    BuiltInRegistries.ITEM.byId(tanks.get(i)), tankCounts.get(i)));
        }
        return new RocketDraft(BuiltInRegistries.ITEM.byId(payload), List.copyOf(stages), boosters);
    }

    @Override
    public Type<DesignPrintPayload> type() {
        return TYPE;
    }
}
