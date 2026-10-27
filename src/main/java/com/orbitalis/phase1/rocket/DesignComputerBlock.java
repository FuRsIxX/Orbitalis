package com.orbitalis.phase1.rocket;

import java.util.List;

import com.orbitalis.core.network.DesignPrintPayload;
import com.orbitalis.core.registry.ModBlocks;
import com.orbitalis.core.research.ResearchManager;
import com.orbitalis.phase1.spaceport.Workstation;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Ordinateur de conception : le grand poste où l'on choisit et règle ses lanceurs. C'est un poste de travail de
 * trois blocs de large, deux de haut et deux de profondeur ({@link Workstation}).
 * <p>
 * Un clic droit ouvre son écran : le catalogue des lanceurs, une vue en 3D de celui qu'on règle, ses
 * caractéristiques, et le bouton qui imprime son plan. Imprimer coûte une feuille de papier, et la charge utile
 * choisie : elle part avec le plan, et sera à bord de la fusée une fois bâtie.
 */
public class DesignComputerBlock extends Block implements Workstation.Host {
    /** Direction vers laquelle les écrans sont tournés. */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** Distance maximale entre le joueur et un ordinateur pour lui faire imprimer un plan, en blocs. */
    private static final int REACH = 8;

    /** Ouvre l'écran de conception, côté client. */
    public static Runnable screenOpener = () -> {
    };

    public DesignComputerBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** Il faut la place de tout le meuble. Les écrans font face au joueur qui le pose. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = Workstation.placement(context);
        return facing == null ? null : defaultBlockState().setValue(FACING, facing);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this)) {
            Workstation.placeWings(level, pos, state);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        Workstation.removeWings(level, pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) {
            screenOpener.run();
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Le joueur demande un plan. Le serveur revérifie le brouillon (chaque pièce à sa place, chaque nombre dans sa
     * plage), vérifie que l'agence du joueur maîtrise et a étudié toutes ses pièces, prélève une feuille de papier et la
     * charge utile, puis remet le plan.
     */
    public static void print(ServerPlayer player, DesignPrintPayload payload) {
        ServerLevel level = player.level();
        boolean near = false;
        for (BlockPos pos : BlockPos.betweenClosed(player.blockPosition().offset(-REACH, -3, -REACH), player.blockPosition().offset(REACH, 3, REACH))) {
            if (level.getBlockState(pos).is(ModBlocks.DESIGN_COMPUTER.get())) {
                near = true;
                break;
            }
        }
        RocketDraft draft = payload.draft();
        if (!near || !draft.isValid()) {
            return;
        }
        NonNullList<ItemStack> parts = draft.slots();
        RocketStats stats = RocketStats.of(RocketDesign.fromSlots(parts::get));
        if (!stats.isFlightReady()) {
            player.sendSystemMessage(Component.translatable("design.orbitalis.print.not_ready").withStyle(ChatFormatting.GOLD));
            return;
        }
        boolean creative = player.getAbilities().instabuild;
        if (!creative) {
            String missing = lockedResearch(player, parts);
            if (missing != null) {
                player.sendSystemMessage(Component.translatable("spaceport.orbitalis.research_required",
                        Component.translatable("research.orbitalis.node." + missing)).withStyle(ChatFormatting.RED));
                return;
            }
            // L'établi d'ingénieur : chacune de ses pièces doit avoir été étudiée.
            com.orbitalis.core.research.EngineeringStudies.Study lacking = com.orbitalis.core.research.EngineeringStudies.firstUnstudied(player, parts);
            if (lacking != null) {
                player.sendSystemMessage(Component.translatable("engineering.orbitalis.required", lacking.name()).withStyle(ChatFormatting.RED));
                return;
            }
            ItemStack cargo = parts.get(RocketDesign.SLOT_PAYLOAD);
            Inventory inventory = player.getInventory();
            if (count(inventory, Items.PAPER) < 1 || (!cargo.isEmpty() && count(inventory, cargo.getItem()) < 1)) {
                player.sendSystemMessage((cargo.isEmpty() ? Component.translatable("design.orbitalis.print.need_paper")
                        : Component.translatable("design.orbitalis.print.need_payload", cargo.getHoverName())).withStyle(ChatFormatting.GOLD));
                return;
            }
            take(inventory, Items.PAPER);
            if (!cargo.isEmpty()) {
                take(inventory, cargo.getItem());
            }
        }
        Component name = planName(draft);
        ItemStack blueprint = RocketBlueprintItem.create(parts, Component.translatable("item.orbitalis.rocket_blueprint.named", name));
        if (!player.getInventory().add(blueprint)) {
            player.drop(blueprint, false);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1.0F, 0.8F);
        player.sendSystemMessage(Component.translatable("design.orbitalis.print.done", name).withStyle(ChatFormatting.GREEN));
    }

    /**
     * Nom d'un plan : celui du modèle du catalogue s'il en reprend un tel quel, sinon un lanceur sur mesure, nommé par
     * son nombre d'étages et sa charge utile.
     */
    public static Component planName(RocketDraft draft) {
        for (RocketCatalog.Template template : RocketCatalog.all()) {
            for (int p = 0; p < template.payloads().size(); p++) {
                if (RocketDraft.of(RocketCatalog.build(template, p, template.presets())).equals(draft)) {
                    return Component.translatable(template.nameKey());
                }
            }
        }
        Component cargo = draft.payload() == Items.AIR ? Component.translatable("design.orbitalis.no_payload")
                : new ItemStack(draft.payload()).getHoverName();
        return Component.translatable("design.orbitalis.custom_name", draft.stages().size(), cargo);
    }

    /** La technologie qui permet d'assembler des lanceurs à trois étages. */
    public static final String VEHICLE_ASSEMBLY = "vehicle_assembly";

    /**
     * Première technologie que l'agence du joueur ne maîtrise pas parmi celles des pièces (ou, pour un lanceur à
     * trois étages, celle du bâtiment d'assemblage), ou {@code null}.
     */
    public static String lockedResearch(Player player, List<ItemStack> parts) {
        // Un lanceur à trois étages s'assemble à l'abri, dans le bâtiment d'assemblage.
        if (parts.size() == RocketDesign.SLOT_COUNT && !ResearchManager.isUnlocked(player, VEHICLE_ASSEMBLY)) {
            RocketDesign design = RocketDesign.fromSlots(parts::get);
            long stages = design.stages().stream().filter(stage -> !stage.isEmpty()).count();
            if (stages >= RocketDesign.MAX_STAGES) {
                return VEHICLE_ASSEMBLY;
            }
        }
        for (ItemStack stack : parts) {
            RocketParts.Part part = RocketParts.get(stack);
            if (part != null && part.research() != null && !part.research().isEmpty() && !ResearchManager.isUnlocked(player, part.research())) {
                return part.research();
            }
        }
        return null;
    }

    private static int count(Inventory inventory, Item item) {
        int count = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).is(item)) {
                count += inventory.getItem(slot).getCount();
            }
        }
        return count;
    }

    private static void take(Inventory inventory, Item item) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).is(item)) {
                inventory.getItem(slot).shrink(1);
                return;
            }
        }
    }
}
