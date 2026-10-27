package com.orbitalis.phase1.rocket;

import java.util.List;

import com.orbitalis.core.network.StudyPayload;
import com.orbitalis.core.registry.ModBlocks;
import com.orbitalis.core.research.EngineeringStudies;
import com.orbitalis.phase1.spaceport.Workstation;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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
 * Établi d'ingénieur : le meuble où l'agence met ses technologies en pratique. C'est une table d'architecte, deux blocs
 * de large sur deux de profondeur, haute d'un bloc : une planche à dessin inclinée, sa dalle lumineuse, ses plans.
 * <p>
 * Un clic droit ouvre la liste des études ({@link EngineeringStudies}) : chaque pièce de fusée et chaque modèle de
 * lanceur s'y étudie, avec la technologie voulue et les matériaux d'un prototype. Le bloc ne stocke rien : les
 * études appartiennent à l'agence, pas à l'établi.
 * <p>
 * Le bloc de l'établi est celui de devant, à gauche quand on lui fait face : c'est lui qui dessine tout le meuble. Les
 * trois autres cases sont invisibles et lui donnent sa solidité.
 */
public class EngineerWorkbenchBlock extends Block implements Workstation.Host {
    /** Direction vers laquelle l'établi est tourné. */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** Distance maximale entre le joueur et un établi pour y faire une étude, en blocs. */
    private static final int REACH = 8;

    /** Ouvre l'écran de l'établi, côté client. */
    public static Runnable screenOpener = () -> {
    };

    public EngineerWorkbenchBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** Les trois autres cases : la table fait deux blocs de large sur deux de profondeur. */
    @Override
    public List<BlockPos> wingsOf(BlockPos pos, Direction facing) {
        BlockPos beside = pos.relative(facing.getClockWise());
        Direction back = facing.getOpposite();
        return List.of(beside, pos.relative(back), beside.relative(back));
    }

    /** Il faut la place de tout le meuble. Il fait face au joueur qui le pose. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = Workstation.placement(context, this);
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
     * Le joueur demande une étude. Le serveur vérifie lui-même qu'il est près d'un établi, que son agence maîtrise la
     * technologie, et qu'il a les matériaux : le client ne décide de rien.
     */
    public static void study(ServerPlayer player, StudyPayload payload) {
        ServerLevel level = player.level();
        boolean near = false;
        for (BlockPos pos : BlockPos.betweenClosed(player.blockPosition().offset(-REACH, -3, -REACH), player.blockPosition().offset(REACH, 3, REACH))) {
            if (level.getBlockState(pos).is(ModBlocks.ENGINEER_WORKBENCH.get())) {
                near = true;
                break;
            }
        }
        if (!near) {
            return;
        }
        EngineeringStudies.Study study = EngineeringStudies.get(payload.id());
        EngineeringStudies.Problem problem = EngineeringStudies.complete(player, payload.id());
        if (problem == EngineeringStudies.Problem.NONE && EngineeringStudies.isArchive(study)) {
            level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1.0F, 0.9F);
            player.sendSystemMessage(Component.translatable("engineering.orbitalis.archive.done", study.name(), EngineeringStudies.archivePoints(study))
                    .withStyle(ChatFormatting.GREEN));
        } else if (problem == EngineeringStudies.Problem.NONE) {
            level.playSound(null, player.blockPosition(), SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 1.0F, 1.1F);
            player.sendSystemMessage(Component.translatable("engineering.orbitalis.done", study.name()).withStyle(ChatFormatting.GREEN));
        } else if (problem != EngineeringStudies.Problem.UNKNOWN) {
            player.sendSystemMessage(Component.translatable("engineering.orbitalis.problem." + problem.name().toLowerCase(java.util.Locale.ROOT))
                    .withStyle(ChatFormatting.GOLD));
            // L'écran du joueur se remet d'accord avec le serveur.
            com.orbitalis.core.research.ResearchManager.sync(player);
        }
    }
}
