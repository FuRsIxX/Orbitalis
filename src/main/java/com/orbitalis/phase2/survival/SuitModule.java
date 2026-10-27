package com.orbitalis.phase2.survival;

import java.util.List;
import java.util.function.Supplier;

import com.mojang.serialization.Codec;
import com.orbitalis.core.registry.ModItems;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;

/**
 * Modules de la combinaison avancée : chacun se fixe sur une pièce précise (le casque, le torse ou les jambes), une
 * seule fois par pièce. La combinaison orange n'en reçoit aucun.
 */
public enum SuitModule implements StringRepresentable {
    /** Carte de proximité, sur le côté de la visière : les murs et les sols de quelques blocs alentour. */
    MAP("map", EquipmentSlot.HEAD, () -> ModItems.SUIT_MODULE_MAP.get()),
    /** Capteur d'environnement : pression et nature de l'air, pesanteur, température. */
    SENSOR("sensor", EquipmentSlot.HEAD, () -> ModItems.SUIT_MODULE_SENSOR.get()),
    /** Réservoir d'oxygène supplémentaire, sur le sac dorsal. */
    OXYGEN_TANK("oxygen_tank", EquipmentSlot.CHEST, () -> ModItems.SUIT_MODULE_OXYGEN_TANK.get()),
    /** Petits propulseurs à azote, sur le sac dorsal : on se déplace bien plus vite en apesanteur. */
    THRUSTERS("thrusters", EquipmentSlot.CHEST, () -> ModItems.SUIT_MODULE_THRUSTERS.get()),
    /** Amortisseurs des genoux et des bottes : les chutes font bien moins mal. */
    SHOCK_ABSORBERS("shock_absorbers", EquipmentSlot.LEGS, () -> ModItems.SUIT_MODULE_SHOCK_ABSORBERS.get()),
    /** Blindage anti-radiations, sur le torse : sans lui, les radiations de Jupiter tuent en quelques minutes sur Europe. */
    RADIATION_SHIELD("radiation_shield", EquipmentSlot.CHEST, () -> ModItems.SUIT_MODULE_RADIATION_SHIELD.get());

    public static final Codec<SuitModule> CODEC = StringRepresentable.fromEnum(SuitModule::values);
    public static final StreamCodec<ByteBuf, SuitModule> STREAM_CODEC = ByteBufCodecs.idMapper(id -> values()[id], SuitModule::ordinal);
    /** Liste des modules d'une pièce, telle qu'elle est gardée sur l'objet. */
    public static final Codec<List<SuitModule>> LIST_CODEC = CODEC.listOf();
    public static final StreamCodec<ByteBuf, List<SuitModule>> LIST_STREAM_CODEC = STREAM_CODEC.apply(ByteBufCodecs.list());

    private final String id;
    private final EquipmentSlot slot;
    private final Supplier<Item> item;

    SuitModule(String id, EquipmentSlot slot, Supplier<Item> item) {
        this.id = id;
        this.slot = slot;
        this.item = item;
    }

    public String id() {
        return id;
    }

    /** La pièce de la combinaison qui reçoit ce module. */
    public EquipmentSlot slot() {
        return slot;
    }

    /** L'objet de ce module, tel qu'on le fabrique et le récupère. */
    public Item item() {
        return item.get();
    }

    public String nameKey() {
        return "suit.orbitalis.module." + id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
