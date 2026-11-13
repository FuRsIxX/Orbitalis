package com.orbitalis.platform;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import io.netty.buffer.ByteBuf;
import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Les petites différences entre l'API de Minecraft 26 et celle de 1.21.1, réunies ici : le code du mod les appelle comme il
 * appelait les méthodes de 26.
 */
public final class Compat {
    private Compat() {
    }

    /** Une entité de bloc qui a quelque chose à faire avant d'être retirée (rendre son contenu) : appelée par le mixin de BlockBehaviour. */
    public interface RemovalAware {
        void preRemoveSideEffects(BlockPos pos, BlockState state);
    }

    /** Un type d'entité de bloc, pour ces blocs. */
    public static <T extends BlockEntity> BlockEntityType<T> blockEntityType(BlockEntityType.BlockEntitySupplier<T> factory, Block... blocks) {
        return BlockEntityType.Builder.of(factory, blocks).build(null);
    }

    /** Le résultat d'un clic droit avec un objet, à la façon de 1.21.1 (l'objet tenu, et ce qui s'est passé). */
    public static InteractionResultHolder<ItemStack> holder(InteractionResult result, ItemStack stack, Level level) {
        return switch (result) {
            case SUCCESS, SUCCESS_NO_ITEM_USED -> InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
            case CONSUME, CONSUME_PARTIAL -> InteractionResultHolder.consume(stack);
            case FAIL -> InteractionResultHolder.fail(stack);
            default -> InteractionResultHolder.pass(stack);
        };
    }

    /** Le résultat d'un clic droit sur un bloc avec un objet en main, à la façon de 1.21.1. */
    public static ItemInteractionResult itemResult(InteractionResult result) {
        return switch (result) {
            case SUCCESS, SUCCESS_NO_ITEM_USED -> ItemInteractionResult.SUCCESS;
            case CONSUME -> ItemInteractionResult.CONSUME;
            case CONSUME_PARTIAL -> ItemInteractionResult.CONSUME_PARTIAL;
            case FAIL -> ItemInteractionResult.FAIL;
            default -> ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        };
    }

    /** Une colonne centrée, de {@code width} pixels de côté, de {@code minY} à {@code maxY} pixels de haut. */
    public static VoxelShape column(double width, double minY, double maxY) {
        double half = width / 2.0;
        return Block.box(8.0 - half, minY, 8.0 - half, 8.0 + half, maxY, 8.0 + half);
    }

    /** Une colonne centrée, de {@code widthX} sur {@code widthZ} pixels. */
    public static VoxelShape column(double widthX, double widthZ, double minY, double maxY) {
        return Block.box(8.0 - widthX / 2.0, minY, 8.0 - widthZ / 2.0, 8.0 + widthX / 2.0, maxY, 8.0 + widthZ / 2.0);
    }

    /** Un point de l'espace, sur le réseau. */
    public static final StreamCodec<ByteBuf, Vec3> VEC3 = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, Vec3::x, ByteBufCodecs.DOUBLE, Vec3::y, ByteBufCodecs.DOUBLE, Vec3::z, Vec3::new);

    /** Les postures imposées aux joueurs (en apesanteur) : le mixin de Player les applique à la place de la posture calculée. */
    private static final Map<UUID, Pose> FORCED_POSES = new ConcurrentHashMap<>();

    public static void setForcedPose(Player player, @Nullable Pose pose) {
        if (pose == null) {
            FORCED_POSES.remove(player.getUUID());
        } else {
            FORCED_POSES.put(player.getUUID(), pose);
        }
    }

    public static @Nullable Pose forcedPose(Player player) {
        return FORCED_POSES.get(player.getUUID());
    }

    /** Les trois rangées de l'inventaire du joueur, puis sa barre d'objets, 58 pixels plus bas, comme dans toutes les interfaces. */
    public static void addStandardInventorySlots(Function<Slot, Slot> addSlot, Inventory inventory, int x, int y) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot.apply(new Slot(inventory, column + row * 9 + 9, x + column * 18, y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot.apply(new Slot(inventory, column, x + column * 18, y + 58));
        }
    }
}
