package com.orbitalis.core.registry;

import com.mojang.serialization.MapCodec;
import com.orbitalis.Orbitalis;
import com.orbitalis.core.world.ArchiveBlueprintFunction;
import com.orbitalis.core.world.FacilityGroundPiece;
import com.orbitalis.core.world.FacilityPiece;
import com.orbitalis.core.world.SpaceFacilityStructure;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Les structures que le mod pose dans le monde (les centres spatiaux), leurs pièces, et la fonction de butin de leurs coffres. */
public final class ModStructures {
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, Orbitalis.MODID);
    public static final DeferredRegister<StructurePieceType> PIECES = DeferredRegister.create(Registries.STRUCTURE_PIECE, Orbitalis.MODID);
    public static final DeferredRegister<MapCodec<? extends LootItemFunction>> LOOT_FUNCTIONS = DeferredRegister.create(Registries.LOOT_FUNCTION_TYPE,
            Orbitalis.MODID);

    /** Un centre spatial : station météorologique, centre de recherche, d'observation, ou de contrôle et son pas de tir. */
    public static final DeferredHolder<StructureType<?>, StructureType<SpaceFacilityStructure>> SPACE_FACILITY = STRUCTURE_TYPES.register(
            "space_facility", () -> () -> SpaceFacilityStructure.CODEC);
    public static final DeferredHolder<StructurePieceType, StructurePieceType> FACILITY_BUILDING = PIECES.register("facility_building",
            () -> FacilityPiece::new);
    public static final DeferredHolder<StructurePieceType, StructurePieceType> FACILITY_GROUND = PIECES.register("facility_ground",
            () -> FacilityGroundPiece::new);
    /** Fait d'une feuille un plan de fusée d'archive. */
    public static final DeferredHolder<MapCodec<? extends LootItemFunction>, MapCodec<ArchiveBlueprintFunction>> ARCHIVE_BLUEPRINT = LOOT_FUNCTIONS.register(
            "archive_blueprint", () -> ArchiveBlueprintFunction.CODEC);

    private ModStructures() {
    }

    public static void register(IEventBus modEventBus) {
        STRUCTURE_TYPES.register(modEventBus);
        PIECES.register(modEventBus);
        LOOT_FUNCTIONS.register(modEventBus);
    }
}
