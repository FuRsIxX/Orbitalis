package com.orbitalis.phase2.station;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Bac de culture hydroponique : la serre d'une station. On y plante des graines de blé ou de betterave, une carotte
 * ou une pomme de terre ; sous tension, les plants poussent en quatre stades, sans terre ni soleil, sous les rampes
 * de diodes du bac. Mûrs, un clic droit les récolte, et le bac se replante de lui-même. Un clic droit accroupi, main
 * vide, arrache la culture.
 * <p>
 * L'air traverse le bac ; dans une pièce pressurisée, ses plantes recyclent l'air et allègent le générateur.
 */
public class HydroponicTrayBlock extends Block implements EntityBlock, AirPermeable {
    public static final MapCodec<HydroponicTrayBlock> CODEC = simpleCodec(HydroponicTrayBlock::new);
    public static final EnumProperty<Crop> CROP = EnumProperty.create("crop", Crop.class);
    /** Stade de croissance : 3, la culture est mûre. */
    public static final IntegerProperty AGE = IntegerProperty.create("age", 0, 3);
    public static final int MAX_AGE = 3;
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 7, 16);

    /** Les cultures d'un bac : ce qu'on y plante, et ce qu'on en récolte. */
    public enum Crop implements StringRepresentable {
        NONE("none"),
        WHEAT("wheat"),
        CARROT("carrot"),
        POTATO("potato"),
        BEETROOT("beetroot");

        private final String name;

        Crop(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        /** La culture qu'on plante avec cet objet, ou {@code NONE}. */
        public static Crop of(ItemStack stack) {
            if (stack.is(Items.WHEAT_SEEDS)) {
                return WHEAT;
            } else if (stack.is(Items.CARROT)) {
                return CARROT;
            } else if (stack.is(Items.POTATO)) {
                return POTATO;
            } else if (stack.is(Items.BEETROOT_SEEDS)) {
                return BEETROOT;
            }
            return NONE;
        }

        /** Ce qu'on a planté : on le rend quand on arrache la culture. */
        public Item seed() {
            return switch (this) {
                case WHEAT -> Items.WHEAT_SEEDS;
                case CARROT -> Items.CARROT;
                case POTATO -> Items.POTATO;
                case BEETROOT -> Items.BEETROOT_SEEDS;
                case NONE -> Items.AIR;
            };
        }

        /** Une récolte : la culture se replante d'elle-même, la graine est déjà comptée. */
        public List<ItemStack> harvest() {
            return switch (this) {
                case WHEAT -> List.of(new ItemStack(Items.WHEAT, 2), new ItemStack(Items.WHEAT_SEEDS, 1));
                case CARROT -> List.of(new ItemStack(Items.CARROT, 3));
                case POTATO -> List.of(new ItemStack(Items.POTATO, 3));
                case BEETROOT -> List.of(new ItemStack(Items.BEETROOT, 2), new ItemStack(Items.BEETROOT_SEEDS, 1));
                case NONE -> List.of();
            };
        }
    }

    public HydroponicTrayBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(CROP, Crop.NONE).setValue(AGE, 0));
    }

    @Override
    protected MapCodec<HydroponicTrayBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CROP, AGE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HydroponicTrayBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof HydroponicTrayBlockEntity tray && tickLevel instanceof ServerLevel serverLevel) {
                tray.serverTick(serverLevel, pos, tickState);
            }
        };
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hitResult) {
        Crop crop = Crop.of(stack);
        if (crop == Crop.NONE || state.getValue(CROP) != Crop.NONE) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!level.isClientSide()) {
            level.setBlock(pos, state.setValue(CROP, crop).setValue(AGE, 0), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1.0F, 1.0F);
            stack.consume(1, player);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        Crop crop = state.getValue(CROP);
        if (crop == Crop.NONE) {
            serverPlayer.sendSystemMessage(Component.translatable("station.orbitalis.tray.empty").withStyle(ChatFormatting.GRAY));
        } else if (player.isShiftKeyDown()) {
            // Arracher la culture : on récupère ce qu'on avait planté.
            level.setBlock(pos, state.setValue(CROP, Crop.NONE).setValue(AGE, 0), Block.UPDATE_ALL);
            give(player, new ItemStack(crop.seed()));
        } else if (state.getValue(AGE) >= MAX_AGE) {
            level.setBlock(pos, state.setValue(AGE, 0), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.CROP_BREAK, SoundSource.BLOCKS, 1.0F, 1.1F);
            crop.harvest().forEach(item -> give(player, item.copy()));
        } else if (level.getBlockEntity(pos) instanceof HydroponicTrayBlockEntity tray) {
            serverPlayer.sendSystemMessage(tray.powered()
                    ? Component.translatable("station.orbitalis.tray.growing", tray.growthPercent(state)).withStyle(ChatFormatting.GREEN)
                    : Component.translatable("station.orbitalis.tray.no_power", HydroponicTrayBlockEntity.ENERGY_PER_TICK).withStyle(ChatFormatting.RED));
        }
        return InteractionResult.SUCCESS;
    }

    private static void give(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    /** Vrai si ce bac porte des plantes vivantes : il recycle l'air de la pièce. */
    public static boolean growing(BlockState state) {
        return state.getBlock() instanceof HydroponicTrayBlock && state.getValue(CROP) != Crop.NONE;
    }
}
