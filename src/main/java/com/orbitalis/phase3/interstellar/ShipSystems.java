package com.orbitalis.phase3.interstellar;

import java.util.ArrayList;
import java.util.List;

import com.orbitalis.core.registry.ModBlocks;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * Le bilan du vaisseau, tel que le lit le poste de pilotage : sa coque, et chacun de ses systèmes, d'après le plan.
 *
 * @param hull      blocs de coque posés, et en tout
 * @param charge    matière exotique dans le cœur de distorsion (0 s'il n'est pas là)
 * @param ringDrive vrai si le moteur de l'anneau est en place
 * @param pods      caissons d'hibernation en place, et en tout
 * @param trays     bacs de culture de la serre en place, et en tout
 * @param helm      vrai si le poste de pilotage est en place
 */
public record ShipSystems(int[] hull, boolean core, int charge, boolean ringDrive, int[] pods, int[] trays, boolean helm) {
    /** Part de la coque qui doit être posée pour qu'on se dise à bord d'un vaisseau. */
    private static final float ABOARD = 0.5F;

    /** Fait le bilan du vaisseau dont la poupe est en {@code origin} et la proue regarde {@code out}. */
    public static ShipSystems check(Level level, BlockPos origin, Direction out) {
        int[] hull = new int[2];
        int[] pods = new int[2];
        int[] trays = new int[2];
        boolean core = false;
        int charge = 0;
        boolean ringDrive = false;
        boolean helm = false;
        for (ShipPlan.Piece piece : ShipPlan.pieces(out)) {
            BlockPos pos = origin.offset(piece.offset());
            BlockState there = level.isLoaded(pos) ? level.getBlockState(pos) : null;
            Block block = piece.state().getBlock();
            boolean placed = there != null && there.is(block);
            if (block == ModBlocks.WARP_CORE.get()) {
                if (lower(piece) && placed) {
                    core = true;
                    charge = there.getValue(WarpCoreBlock.CHARGE);
                }
            } else if (block == ModBlocks.HIBERNATION_POD.get()) {
                if (lower(piece)) {
                    pods[1]++;
                    pods[0] += placed ? 1 : 0;
                }
            } else if (block == ModBlocks.HYDROPONIC_TRAY.get()) {
                trays[1]++;
                trays[0] += placed ? 1 : 0;
            } else if (block == ModBlocks.RING_DRIVE.get()) {
                ringDrive = placed;
            } else if (block == ModBlocks.SHIP_HELM.get()) {
                helm = placed;
            } else {
                hull[1]++;
                hull[0] += placed ? 1 : 0;
            }
        }
        return new ShipSystems(hull, core, charge, ringDrive, pods, trays, helm);
    }

    private static boolean lower(ShipPlan.Piece piece) {
        return !piece.state().hasProperty(TwoHighBlock.HALF) || piece.state().getValue(TwoHighBlock.HALF) == DoubleBlockHalf.LOWER;
    }

    /** Vrai si l'on est à bord d'un vaisseau : sa coque est au moins à moitié posée. */
    public boolean aboard() {
        return hull[1] > 0 && hull[0] >= hull[1] * ABOARD;
    }

    /** Vrai si tous les systèmes sont prêts : coque achevée, cœur chargé, anneau, caissons, serre et poste de pilotage. */
    public boolean ready() {
        return hull[0] >= hull[1] && core && charge >= WarpCoreBlock.MAX_CHARGE && ringDrive && pods[0] >= pods[1] && trays[0] >= trays[1] && helm;
    }

    /** Le bilan en lignes : une par système, en vert s'il est prêt, puis le verdict. */
    public List<Component> report() {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("ship.orbitalis.report.title").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
        lines.add(line("ship.orbitalis.report.hull", hull[0] >= hull[1], hull[0], hull[1]));
        lines.add(core ? line("ship.orbitalis.report.core", charge >= WarpCoreBlock.MAX_CHARGE, charge, WarpCoreBlock.MAX_CHARGE)
                : line("ship.orbitalis.report.core_missing", false));
        lines.add(line(ringDrive ? "ship.orbitalis.report.ring" : "ship.orbitalis.report.ring_missing", ringDrive));
        lines.add(line("ship.orbitalis.report.pods", pods[0] >= pods[1], pods[0], pods[1]));
        lines.add(line("ship.orbitalis.report.trays", trays[0] >= trays[1], trays[0], trays[1]));
        lines.add(ready() ? Component.translatable("ship.orbitalis.report.ready").withStyle(ChatFormatting.GREEN)
                : Component.translatable("ship.orbitalis.report.not_ready").withStyle(ChatFormatting.GOLD));
        return lines;
    }

    private static Component line(String key, boolean ok, Object... args) {
        return Component.literal(ok ? "✔ " : "✘ ").append(Component.translatable(key, args))
                .withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED);
    }
}
