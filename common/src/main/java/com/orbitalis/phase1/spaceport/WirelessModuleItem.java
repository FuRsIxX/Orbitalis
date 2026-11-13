package com.orbitalis.phase1.spaceport;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.orbitalis.core.registry.ModBlocks;
import com.orbitalis.core.research.ResearchManager;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Module sans fil : la seule façon de relier sans câble. Un clic droit sur ce qu'on veut relier (un pupitre de
 * lancement ou d'essai, un mur d'écrans, une station de poursuite), puis un second sur ce à quoi le relier :
 * <ul>
 * <li>un pupitre ou un mur d'écrans, puis un pas de tir (sa table, un port ou une dalle) : il le suit à distance ;</li>
 * <li>un pupitre et un mur d'écrans, dans un sens ou dans l'autre : l'écran suit le pas de tir du pupitre ;</li>
 * <li>une station de poursuite et un mur d'écrans : l'écran montre les satellites de l'agence.</li>
 * </ul>
 * Le module s'installe dans la liaison : il est consommé quand elle est faite. Sans lui, un clic droit ne relie rien ;
 * un câble de données relié au port de données d'un pas de tir fait le même travail.
 */
public class WirelessModuleItem extends Item {
    /** Ce qu'un module peut relier en premier. */
    private enum Source {
        CONSOLE, DISPLAY, STATION
    }

    private record Pending(ResourceKey<Level> dimension, BlockPos pos, Source source) {
    }

    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();

    public WirelessModuleItem(Properties properties) {
        super(properties);
    }

    /** Le module agit avant le bloc : sans cela, un clic sur un pupitre ouvrirait son écran. */
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Level level = context.getLevel();
        if (!(level instanceof ServerLevel serverLevel) || !(context.getPlayer() instanceof ServerPlayer player)) {
            return isLinkable(level, context.getClickedPos()) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        if (state.is(ModBlocks.WORKSTATION_PART.get())) {
            BlockPos host = Workstation.findHost(level, pos);
            if (host != null) {
                pos = host;
                state = level.getBlockState(host);
            }
        }
        Source clicked = sourceOf(state);
        LaunchTableBlockEntity table = tableOf(serverLevel, pos, state);
        Pending pending = PENDING.get(player.getUUID());
        if (pending != null && pending.dimension() != level.dimension()) {
            pending = null;
        }
        if (pending != null && !pending.pos().equals(pos) && link(serverLevel, player, pending, pos, clicked, table)) {
            PENDING.remove(player.getUUID());
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            return InteractionResult.SUCCESS;
        }
        if (clicked != null) {
            PENDING.put(player.getUUID(), new Pending(level.dimension(), pos.immutable(), clicked));
            player.sendSystemMessage(Component.translatable("wireless.orbitalis.started." + clicked.name().toLowerCase(java.util.Locale.ROOT))
                    .withStyle(ChatFormatting.AQUA), true);
            return InteractionResult.SUCCESS;
        }
        if (table != null) {
            player.sendSystemMessage(Component.translatable("wireless.orbitalis.source_first").withStyle(ChatFormatting.GOLD), true);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    /** Fait la liaison entre ce qui attend et ce qu'on vient de viser ; vrai si elle est faite. */
    private static boolean link(ServerLevel level, ServerPlayer player, Pending pending, BlockPos pos, Source clicked, LaunchTableBlockEntity table) {
        String agency = ResearchManager.agencyKey(player);
        // Un pupitre ou un écran, puis un pas de tir.
        if (table != null && pending.source() == Source.CONSOLE) {
            PadLinks.linkWireless(level, pending.pos(), table.getBlockPos());
            player.sendSystemMessage(Component.translatable("wireless.orbitalis.console_linked").withStyle(ChatFormatting.GREEN));
            return true;
        }
        if (table != null && pending.source() == Source.DISPLAY) {
            linkDisplay(level, pending.pos(), table.getBlockPos());
            player.sendSystemMessage(Component.translatable("wireless.orbitalis.display_linked").withStyle(ChatFormatting.GREEN));
            return true;
        }
        // Un pupitre et un écran, dans un sens ou dans l'autre.
        BlockPos console = pending.source() == Source.CONSOLE && clicked == Source.DISPLAY ? pending.pos()
                : pending.source() == Source.DISPLAY && clicked == Source.CONSOLE ? pos : null;
        if (console != null) {
            LaunchTableBlockEntity linked = LaunchConsoleBlock.linkedTable(level, console, agency);
            if (linked == null) {
                player.sendSystemMessage(Component.translatable("spaceport.orbitalis.console.not_linked").withStyle(ChatFormatting.RED));
                return false;
            }
            linkDisplay(level, console.equals(pos) ? pending.pos() : pos, linked.getBlockPos());
            player.sendSystemMessage(Component.translatable("wireless.orbitalis.display_linked").withStyle(ChatFormatting.GREEN));
            return true;
        }
        // Une station de poursuite et un écran : les satellites de l'agence.
        BlockPos display = pending.source() == Source.STATION && clicked == Source.DISPLAY ? pos
                : pending.source() == Source.DISPLAY && clicked == Source.STATION ? pending.pos() : null;
        if (display != null) {
            MissionDisplayBlockEntity.Wall wall = MissionDisplayBlockEntity.wallOf(level, display);
            for (BlockPos block : wall.blocks()) {
                if (level.getBlockEntity(block) instanceof MissionDisplayBlockEntity screen) {
                    screen.setSatelliteLink(agency);
                    screen.refreshSatellites(level.getServer());
                }
            }
            player.sendSystemMessage(Component.translatable("display.orbitalis.satellites_linked", wall.width(), wall.height())
                    .withStyle(ChatFormatting.GREEN));
            return true;
        }
        return false;
    }

    /** Tout le mur d'écrans suit désormais ce pas de tir. */
    private static void linkDisplay(ServerLevel level, BlockPos display, BlockPos table) {
        MissionDisplayBlockEntity.Wall wall = MissionDisplayBlockEntity.wallOf(level, display);
        for (BlockPos block : wall.blocks()) {
            if (level.getBlockEntity(block) instanceof MissionDisplayBlockEntity screen) {
                screen.setLink(table);
            }
        }
    }

    private static Source sourceOf(BlockState state) {
        if (state.is(ModBlocks.LAUNCH_CONSOLE.get()) || state.is(ModBlocks.TEST_CONSOLE.get())) {
            return Source.CONSOLE;
        }
        if (state.is(ModBlocks.MISSION_DISPLAY.get())) {
            return Source.DISPLAY;
        }
        if (state.is(ModBlocks.TRACKING_STATION.get())) {
            return Source.STATION;
        }
        return null;
    }

    /** La table du pas de tir visé : la table elle-même, ou celle d'un port, d'une dalle ou du carneau. */
    private static LaunchTableBlockEntity tableOf(ServerLevel level, BlockPos pos, BlockState state) {
        if (level.getBlockEntity(pos) instanceof LaunchTableBlockEntity table) {
            return table;
        }
        if (state.is(ModBlocks.FUEL_PORT.get()) || state.is(ModBlocks.LAUNCH_PAD.get()) || state.is(ModBlocks.FLAME_TRENCH.get())) {
            return LaunchTableBlockEntity.nearest(level, pos, LaunchComplex.REACH + 2);
        }
        return null;
    }

    /** Côté client : vrai si le clic doit revenir au module plutôt qu'au bloc. */
    private static boolean isLinkable(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return sourceOf(state) != null || state.is(ModBlocks.WORKSTATION_PART.get()) || state.is(ModBlocks.LAUNCH_TABLE.get())
                || state.is(ModBlocks.FUEL_PORT.get()) || state.is(ModBlocks.LAUNCH_PAD.get()) || state.is(ModBlocks.FLAME_TRENCH.get());
    }
}
