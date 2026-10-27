package com.orbitalis.core.world;

import java.util.List;

import com.mojang.serialization.Codec;
import com.orbitalis.Orbitalis;
import com.orbitalis.core.registry.ModItems;
import com.orbitalis.core.registry.ModStructures;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Récupération : les équipements d'origine d'un centre spatial (pièces de fusée exposées, dalles, tranchée,
 * déflecteur et tour du pas de tir, postes de travail, écrans, panneaux solaires, projecteurs) sont hors d'usage.
 * Démontés, ils ne rendent que de la ferraille : un centre se fouille pour ses coffres, pas pour se dévaliser bloc
 * par bloc.
 * <p>
 * Ce qu'un joueur pose lui-même dans un centre reste à lui : sa place est retenue, et le bloc se récupère entier.
 */
@EventBusSubscriber(modid = Orbitalis.MODID)
public final class FacilitySalvage {
    /** Les blocs concernés. */
    public static final TagKey<Block> SALVAGE = TagKey.create(Registries.BLOCK, Orbitalis.id("facility_salvage"));

    private FacilitySalvage() {
    }

    /** Les places des blocs concernés que des joueurs ont posés eux-mêmes dans un centre, par monde. */
    public static final class Data extends SavedData {
        private static final Codec<Data> CODEC = Codec.LONG.listOf().xmap(Data::new, data -> List.copyOf(data.placed));
        public static final SavedDataType<Data> TYPE = new SavedDataType<>(Orbitalis.id("facility_salvage"), Data::new, CODEC);

        private final LongSet placed = new LongOpenHashSet();

        public Data() {
        }

        private Data(List<Long> placed) {
            this.placed.addAll(placed);
        }
    }

    private static Data data(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(Data.TYPE);
    }

    /** Vrai si cette place est dans un bâtiment ou sur le terrain d'un centre spatial. */
    public static boolean inFacility(ServerLevel level, BlockPos pos) {
        return level.structureManager().getStructureWithPieceAt(pos, holder -> holder.value().type() == ModStructures.SPACE_FACILITY.get()).isValid();
    }

    @SubscribeEvent
    static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof Player && event.getPlacedBlock().is(SALVAGE)
                && inFacility(level, event.getPos())) {
            Data data = data(level);
            if (data.placed.add(event.getPos().asLong())) {
                data.setDirty();
            }
        }
    }

    @SubscribeEvent
    static void onDrops(BlockDropsEvent event) {
        BlockState state = event.getState();
        ServerLevel level = event.getLevel();
        if (!state.is(SALVAGE) || !inFacility(level, event.getPos())) {
            return;
        }
        Data data = data(level);
        if (data.placed.remove(event.getPos().asLong())) {
            // Posé par un joueur : il le reprend tel quel.
            data.setDirty();
            return;
        }
        event.getDrops().clear();
        double x = event.getPos().getX() + 0.5;
        double y = event.getPos().getY() + 0.5;
        double z = event.getPos().getZ() + 0.5;
        int nuggets = 1 + level.getRandom().nextInt(3);
        event.getDrops().add(new ItemEntity(level, x, y, z, new ItemStack(Items.IRON_NUGGET, nuggets)));
        if (level.getRandom().nextFloat() < 0.2F) {
            event.getDrops().add(new ItemEntity(level, x, y, z, new ItemStack(ModItems.STEEL_PLATE.get())));
        }
    }
}
