package com.orbitalis.phase1.machines;

import com.orbitalis.core.registry.ModBlocks;
import com.orbitalis.core.registry.ModParticles;
import java.util.function.Supplier;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;

/**
 * Bloc commun à toutes les machines : orienté vers le joueur à la pose, allumé quand la machine
 * fonctionne, et ouvrant l'interface au clic droit. Le comportement est porté par l'entité de bloc.
 */
public class MachineBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    private final Supplier<? extends BlockEntityType<? extends MachineBlockEntity>> blockEntityType;
    private final MapCodec<MachineBlock> codec;

    public MachineBlock(BlockBehaviour.Properties properties,
            Supplier<? extends BlockEntityType<? extends MachineBlockEntity>> blockEntityType) {
        super(properties);
        this.blockEntityType = blockEntityType;
        this.codec = simpleCodec(p -> new MachineBlock(p, blockEntityType));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false));
    }

    @Override
    protected MapCodec<MachineBlock> codec() {
        return codec;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    /** La face avant regarde le joueur qui pose le bloc. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return blockEntityType.get().create(pos, state);
    }

    /**
     * Effets d'une machine en marche, côté client : chaque machine a ses particules, émises à l'endroit du modèle
     * où elles ont un sens (cheminée, creuset, cylindres...), et parfois un son.
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) {
            return;
        }
        Direction facing = state.getValue(FACING);
        // Petite vitesse de côté tirée au hasard, pour les étincelles et les bouffées.
        double drift = (random.nextDouble() - 0.5) * 0.08;
        if (this == ModBlocks.COMBUSTION_GENERATOR.get()) {
            // Suie à la cheminée, étincelles qui s'échappent du foyer, crépitement.
            particle(level, pos, facing, ModParticles.SOOT.get(), 12.5, 16.3, 11, drift * 0.2, 0.035);
            if (random.nextInt(3) == 0) {
                particle(level, pos, facing, ModParticles.SPARK.get(), 5.5 + random.nextInt(6), 6.5, 0.2, drift, 0.06);
            }
            if (random.nextInt(10) == 0) {
                level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.FURNACE_FIRE_CRACKLE,
                        SoundSource.BLOCKS, 1.0F, 1.0F, false);
            }
        } else if (this == ModBlocks.ARC_FOUNDRY.get()) {
            // Gerbes d'étincelles devant la fenêtre, où l'arc frappe le bain, et la chaleur qui monte au-dessus du bloc.
            for (int i = 0; i < 2; i++) {
                particle(level, pos, facing, ModParticles.SPARK.get(), random.nextBoolean() ? 6.25 : 9.75, 6 + random.nextInt(4), -0.3,
                        (random.nextDouble() - 0.5) * 0.14, 0.1 + random.nextDouble() * 0.1);
            }
            if (random.nextInt(4) == 0) {
                particle(level, pos, facing, ModParticles.VAPOR.get(), 5 + random.nextInt(6), 16.2, 5 + random.nextInt(6), drift * 0.2, 0.03);
            }
        } else if (this == ModBlocks.ROLLING_MILL.get()) {
            // La tôle chaude fume en sortant par la fente du dessus ; les cylindres jettent parfois une étincelle par la vitre.
            if (random.nextInt(2) == 0) {
                particle(level, pos, facing, ModParticles.VAPOR.get(), 4 + random.nextInt(8), 16.2, 3 + random.nextInt(10), drift * 0.2, 0.02);
            }
            if (random.nextInt(5) == 0) {
                particle(level, pos, facing, ModParticles.SPARK.get(), 4 + random.nextInt(8), 7, -0.3, drift, 0.05);
            }
        } else if (this == ModBlocks.CASIMIR_CHAMBER.get()) {
            // Entre les deux miroirs, le vide crépite : de petites étincelles qui tombent à peine.
            if (random.nextInt(3) == 0) {
                particle(level, pos, facing, ModParticles.SPARK.get(), 4.5 + random.nextInt(7), 5 + random.nextInt(5), -0.3, drift * 0.3, 0.01);
            }
        } else if (this == ModBlocks.ELECTROLYZER.get()) {
            // Un peu de gaz fuit au dôme de chaque bouteille, et l'eau de la cuve bouillonne derrière son hublot.
            particle(level, pos, facing, ModParticles.VAPOR.get(), random.nextBoolean() ? 4 : 12, 16.1, 11, drift * 0.1, 0.02);
            if (random.nextInt(3) == 0) {
                particle(level, pos, facing, ModParticles.VAPOR.get(), 5 + random.nextInt(7), 5 + random.nextInt(4), -0.3, drift * 0.1, 0.015);
            }
        } else if (this == ModBlocks.CRYOGENIC_LIQUEFIER.get()) {
            // Une brume glacée coule du serpentin, en façade.
            if (random.nextInt(2) == 0) {
                particle(level, pos, facing, ModParticles.CRYO_MIST.get(), 4 + random.nextInt(8), 5 + random.nextInt(5), -0.3, drift * 0.3, -0.005);
            }
        } else if (this == ModBlocks.REFINERY.get()) {
            // Vapeurs au sommet de la colonne, étincelles au brûleur.
            particle(level, pos, facing, ModParticles.SOOT.get(), 8, 16.3, 8, drift * 0.2, 0.03);
            if (random.nextInt(4) == 0) {
                particle(level, pos, facing, ModParticles.SPARK.get(), 5.5 + random.nextInt(5), 4.8, 0.2, drift, 0.05);
            }
        } else if (this == ModBlocks.CRYOGENIC_TANK.get()) {
            // La cuve refroidie laisse couler un peu de brume le long de ses flancs.
            if (random.nextInt(3) == 0) {
                boolean left = random.nextBoolean();
                particle(level, pos, facing, ModParticles.CRYO_MIST.get(), left ? -0.3 : 16.3, 4 + random.nextInt(8), 3 + random.nextInt(10),
                        left ? -0.01 : 0.01, -0.005);
            }
        }
    }

    /**
     * Émet une particule à un point du modèle. Les coordonnées sont celles du modèle (en pixels, face avant au nord) :
     * elles sont tournées comme le bloc.
     *
     * @param speedY vitesse verticale de la particule
     */
    private static void particle(Level level, BlockPos pos, Direction facing, ParticleOptions type,
            double x, double y, double z, double speedX, double speedY) {
        double dx = x / 16.0 - 0.5;
        double dz = z / 16.0 - 0.5;
        // Même rotation que le modèle : un quart de tour par orientation, dans le sens des aiguilles d'une montre vu de dessus.
        double rotatedX = switch (facing) {
            case EAST -> -dz;
            case SOUTH -> -dx;
            case WEST -> dz;
            default -> dx;
        };
        double rotatedZ = switch (facing) {
            case EAST -> dx;
            case SOUTH -> -dz;
            case WEST -> -dx;
            default -> dz;
        };
        level.addParticle(type, pos.getX() + 0.5 + rotatedX, pos.getY() + y / 16.0, pos.getZ() + 0.5 + rotatedZ, speedX, speedY, 0.0);
    }

    /** La logique des machines ne tourne que côté serveur. */
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof MachineBlockEntity machine && tickLevel instanceof ServerLevel serverLevel) {
                machine.serverTick(serverLevel, pos, tickState);
            }
        };
    }

    /** Avec un seau en main : vide ou remplit le seau dans le réservoir de la machine, si elle en a un. */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hitResult) {
        if (level.getBlockEntity(pos) instanceof MachineBlockEntity machine && machine.getBucketFluidHandler() != null
                && FluidUtil.interactWithFluidHandler(player, hand, pos, machine.getBucketFluidHandler())) {
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof MachineBlockEntity machine) {
            player.openMenu(machine, machine::writeMenuData);
        }
        return InteractionResult.SUCCESS;
    }
}
