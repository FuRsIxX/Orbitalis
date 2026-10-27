package com.orbitalis.core.atlas;

import com.orbitalis.core.registry.ModItems;
import com.orbitalis.core.research.ResearchManager;
import com.orbitalis.phase1.satellite.MissionTabletItem;
import com.orbitalis.phase1.satellite.SatelliteManager;
import com.orbitalis.phase1.spaceport.LaunchConsoleBlock;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;

/**
 * Qui peut consulter l'atlas, et d'où.
 * <ul>
 * <li>Depuis le pupitre d'un centre de contrôle, à quelques pas de lui.</li>
 * <li>Partout à la surface, avec la tablette de mission en main, pour peu qu'un satellite de télécommunications de
 * l'agence relaie les données : l'atlas est alors centré sur l'endroit où l'on a ouvert la tablette.</li>
 * </ul>
 */
public final class AtlasAccess {
    /** Distance au pupitre en deçà de laquelle un joueur peut consulter son atlas, en blocs. */
    public static final double CONSOLE_REACH = 32.0;
    /** Distance que l'on peut parcourir, tablette ouverte, avant qu'elle ne perde le fil de l'endroit où on l'a ouverte. */
    public static final double TABLET_DRIFT = 256.0;

    private AtlasAccess() {
    }

    /** Vrai si ce joueur consulte l'atlas depuis ce pupitre, à sa portée. */
    public static boolean atConsole(ServerPlayer player, BlockPos source) {
        Level level = player.level();
        return level.isLoaded(source) && level.getBlockState(source).getBlock() instanceof LaunchConsoleBlock
                && player.blockPosition().closerThan(source, CONSOLE_REACH);
    }

    /** Vrai si ce joueur tient une tablette de mission, dans une main ou l'autre. */
    public static boolean holdsTablet(ServerPlayer player) {
        return player.getItemInHand(InteractionHand.MAIN_HAND).is(ModItems.MISSION_TABLET.get())
                || player.getItemInHand(InteractionHand.OFF_HAND).is(ModItems.MISSION_TABLET.get());
    }

    /**
     * Pourquoi la tablette de ce joueur ne peut pas ouvrir l'atlas, ou {@code null} si elle le peut : il faut la
     * technologie, être à la surface de la planète, et qu'un satellite de télécommunications de l'agence relaie.
     */
    public static Component tabletRefusal(ServerPlayer player) {
        if (!player.getAbilities().instabuild && !ResearchManager.isUnlocked(player, MissionTabletItem.RESEARCH)) {
            return Component.translatable("tablet.orbitalis.research").withStyle(ChatFormatting.GOLD);
        }
        if (player.level().dimension() != Level.OVERWORLD) {
            return Component.translatable("tablet.orbitalis.no_planet").withStyle(ChatFormatting.GOLD);
        }
        if (!SatelliteManager.hasRelay(player.level().getServer(), ResearchManager.agencyKey(player))) {
            return Component.translatable("tablet.orbitalis.no_relay").withStyle(ChatFormatting.GOLD);
        }
        return null;
    }

    /** Vrai si ce joueur consulte l'atlas avec sa tablette, ouverte à cet endroit, pas trop loin de lui. */
    public static boolean byTablet(ServerPlayer player, BlockPos source) {
        return holdsTablet(player) && tabletRefusal(player) == null && player.blockPosition().closerThan(source, TABLET_DRIFT);
    }

    /** Vrai si ce joueur peut consulter l'atlas de ce point : un pupitre à sa portée, ou sa tablette. */
    public static boolean allowed(ServerPlayer player, BlockPos source) {
        return player.level() instanceof ServerLevel && (atConsole(player, source) || byTablet(player, source));
    }
}
