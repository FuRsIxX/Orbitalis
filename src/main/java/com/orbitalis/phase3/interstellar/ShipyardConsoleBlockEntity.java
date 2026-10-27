package com.orbitalis.phase3.interstellar;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.orbitalis.core.registry.ModBlockEntities;
import com.orbitalis.core.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Le pupitre du chantier naval : il tient le plan du vaisseau interstellaire qui se bâtit dans le berceau, sous la
 * verrière de la salle de contrôle.
 * <p>
 * Le pupitre regarde la salle ; le vaisseau, lui, part de trois blocs derrière lui, à hauteur d'yeux, et s'étend vers le
 * large. Il n'est relié à un chantier que si le berceau est bien là : posé ailleurs, il ne montre rien.
 * <p>
 * Côté client, il relève à chaque demi-seconde les blocs du plan qui manquent encore, pour les montrer en fantômes ;
 * côté serveur, il tourne dans le bon sens les blocs déjà posés, et pose ceux qu'on lui demande avec ce que le joueur a
 * sur lui.
 */
public class ShipyardConsoleBlockEntity extends BlockEntity {
    /** Bloc à montrer en fantôme : sa position par rapport au pupitre, et son aspect. */
    public record Ghost(BlockPos offset, BlockState state) {
    }

    /** Avancement d'une section : blocs posés, blocs en tout. */
    public record Progress(int placed, int total) {
        public boolean done() {
            return placed >= total;
        }
    }

    /** Côté client : les blocs qui manquent, ceux de la section en cours d'abord ({@link #focus} premiers). */
    private List<Ghost> ghosts = List.of();
    private int focus;
    private int ticks;

    public ShipyardConsoleBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SHIPYARD_CONSOLE.get(), pos, state);
    }

    /** Direction de la proue : à l'opposé de la face du pupitre. */
    public Direction out() {
        BlockState state = getBlockState();
        return state.hasProperty(HorizontalDirectionalBlock.FACING) ? state.getValue(HorizontalDirectionalBlock.FACING).getOpposite() : Direction.NORTH;
    }

    /** Le centre de la poupe du vaisseau. */
    public BlockPos shipOrigin() {
        return worldPosition.relative(out(), 3).above();
    }

    /** Vrai si le berceau du chantier est là, derrière la verrière : un coin de son premier cadre suffit à le dire. */
    public boolean linked() {
        if (level == null) {
            return false;
        }
        Direction out = out();
        BlockPos corner = shipOrigin().offset(ShipPlan.local(out, ShipPlan.RIBS[0], ShipPlan.CRADLE, ShipPlan.CRADLE));
        // Jamais de chargement forcé : un tronçon de berceau qui n'est pas chargé ne se lit pas.
        return level.isLoaded(corner) && level.getBlockState(corner).is(ModBlocks.STATION_TRUSS.get());
    }

    public List<ShipPlan.Piece> pieces() {
        return ShipPlan.pieces(out());
    }

    private boolean placed(ShipPlan.Piece piece) {
        BlockPos pos = shipOrigin().offset(piece.offset());
        return level != null && level.isLoaded(pos) && level.getBlockState(pos).is(piece.state().getBlock());
    }

    /** L'avancement de chaque section, de la poupe à la proue. */
    public Map<ShipPlan.Section, Progress> progress() {
        Map<ShipPlan.Section, int[]> counts = new EnumMap<>(ShipPlan.Section.class);
        for (ShipPlan.Section section : ShipPlan.Section.values()) {
            counts.put(section, new int[2]);
        }
        for (ShipPlan.Piece piece : pieces()) {
            int[] count = counts.get(piece.section());
            count[1]++;
            if (placed(piece)) {
                count[0]++;
            }
        }
        Map<ShipPlan.Section, Progress> progress = new EnumMap<>(ShipPlan.Section.class);
        counts.forEach((section, count) -> progress.put(section, new Progress(count[0], count[1])));
        return progress;
    }

    /** La première section, de la poupe à la proue, où il manque encore un bloc ; {@code null} si la coque est achevée. */
    public ShipPlan.Section currentSection() {
        for (Map.Entry<ShipPlan.Section, Progress> entry : progress().entrySet()) {
            if (!entry.getValue().done()) {
                return entry.getKey();
            }
        }
        return null;
    }

    /** Les blocs qui manquent dans une section, par objet à fournir. */
    public Map<Item, Integer> missing(ShipPlan.Section section) {
        Map<Item, Integer> missing = new java.util.LinkedHashMap<>();
        for (ShipPlan.Piece piece : pieces()) {
            if (piece.section() == section && !placed(piece) && !ShipPlan.free(piece)) {
                missing.merge(piece.state().getBlock().asItem(), 1, Integer::sum);
            }
        }
        return missing;
    }

    public List<Ghost> ghosts() {
        return ghosts;
    }

    /** Nombre de fantômes de la section en cours, en tête de {@link #ghosts()}. */
    public int focus() {
        return focus;
    }

    public void clientTick() {
        if (level == null || ticks++ % 10 != 0) {
            return;
        }
        if (!linked()) {
            ghosts = List.of();
            focus = 0;
            return;
        }
        ShipPlan.Section current = null;
        List<Ghost> first = new ArrayList<>();
        List<Ghost> rest = new ArrayList<>();
        BlockPos origin = shipOrigin();
        for (ShipPlan.Piece piece : pieces()) {
            if (placed(piece)) {
                continue;
            }
            if (current == null) {
                current = piece.section();
            }
            Ghost ghost = new Ghost(origin.offset(piece.offset()).subtract(worldPosition), piece.state());
            (piece.section() == current ? first : rest).add(ghost);
        }
        focus = first.size();
        first.addAll(rest);
        ghosts = List.copyOf(first);
    }

    public void serverTick() {
        if (level == null || ticks++ % 20 != 0 || !linked()) {
            return;
        }
        // Seul le bloc compte pour le joueur : le pupitre tourne de lui-même un revêtement ou un caisson posé de travers.
        // Il ne touche qu'à l'orientation : la charge du cœur de distorsion ou les cultures d'un bac restent ce qu'elles sont.
        BlockPos origin = shipOrigin();
        for (ShipPlan.Piece piece : pieces()) {
            BlockPos pos = origin.offset(piece.offset());
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState there = level.getBlockState(pos);
            if (there.is(piece.state().getBlock())) {
                BlockState turned = orient(there, piece.state());
                if (turned != there) {
                    level.setBlock(pos, turned, Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    /** L'état {@code there}, tourné comme le veut le plan (orientation et moitié), sans rien changer d'autre. */
    private static BlockState orient(BlockState there, BlockState plan) {
        BlockState state = there;
        for (net.minecraft.world.level.block.state.properties.Property<?> property : List.of(HorizontalDirectionalBlock.FACING,
                net.minecraft.world.level.block.DirectionalBlock.FACING, TwoHighBlock.HALF)) {
            if (state.hasProperty(property) && plan.hasProperty(property)) {
                state = copy(state, plan, property);
            }
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState copy(BlockState state, BlockState plan, net.minecraft.world.level.block.state.properties.Property<T> property) {
        return state.setValue(property, plan.getValue(property));
    }

    /**
     * Pose, avec ce que le joueur a sur lui (tout, en créatif), les blocs qui manquent dans la section en cours, ou dans
     * toutes les sections.
     *
     * @return le nombre de blocs posés
     */
    public int build(Player player, boolean all) {
        if (level == null || !linked()) {
            return 0;
        }
        ShipPlan.Section current = currentSection();
        if (current == null) {
            return 0;
        }
        int placed = 0;
        BlockPos origin = shipOrigin();
        for (ShipPlan.Piece piece : pieces()) {
            if (!all && piece.section() != current) {
                continue;
            }
            BlockPos pos = origin.offset(piece.offset());
            if (!level.isLoaded(pos) || placed(piece) || !level.getBlockState(pos).canBeReplaced() || ShipPlan.free(piece)) {
                continue;
            }
            // Un meuble de deux blocs se pose d'un seul objet, ses deux moitiés à la fois.
            boolean tall = piece.state().hasProperty(TwoHighBlock.HALF);
            if (tall && !level.getBlockState(pos.above()).canBeReplaced()) {
                continue;
            }
            if (take(player, piece.state().getBlock().asItem())) {
                if (tall) {
                    TwoHighBlock.place(level, pos, piece.state());
                    placed += 2;
                } else {
                    level.setBlock(pos, piece.state(), Block.UPDATE_ALL);
                    placed++;
                }
            }
        }
        return placed;
    }

    /** Prend un objet dans l'inventaire du joueur ; en créatif, rien n'est prélevé. */
    private static boolean take(Player player, Item item) {
        if (player.isCreative() || player.getAbilities().instabuild) {
            return true;
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).is(item)) {
                inventory.getItem(slot).shrink(1);
                return true;
            }
        }
        return false;
    }
}
