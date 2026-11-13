package com.orbitalis.platform.transfer.fluid;

import java.util.HashMap;
import java.util.Map;

import com.orbitalis.platform.fluid.FluidStack;
import com.orbitalis.platform.transfer.ResourceHandler;
import com.orbitalis.platform.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/** Les gestes courants avec les fluides : lire une cuve, remplir ou vider une cuve au seau. */
public final class FluidUtil {
    /** Un seau plein vaut 1000 mB. */
    public static final int BUCKET = 1000;

    private static Map<Item, Fluid> bucketFluids;

    private FluidUtil() {
    }

    /** Le contenu de cet emplacement, en pile. */
    public static FluidStack getStack(ResourceHandler<FluidResource> handler, int index) {
        FluidResource resource = handler.getResource(index);
        return resource.isEmpty() ? FluidStack.EMPTY : resource.toStack(handler.getAmountAsInt(index));
    }

    /** Le fluide que contient ce seau plein, ou le vide. */
    public static Fluid fluidOfBucket(ItemStack stack) {
        if (bucketFluids == null) {
            Map<Item, Fluid> map = new HashMap<>();
            for (Fluid fluid : BuiltInRegistries.FLUID) {
                Item bucket = fluid.getBucket();
                if (bucket != Items.AIR && fluid.isSource(fluid.defaultFluidState())) {
                    map.putIfAbsent(bucket, fluid);
                }
            }
            bucketFluids = map;
        }
        return bucketFluids.getOrDefault(stack.getItem(), Fluids.EMPTY);
    }

    /**
     * Le joueur clique la cuve avec ce qu'il tient : un seau plein s'y vide, un seau vide s'y remplit d'un fluide qu'on peut
     * mettre en seau. Rien d'autre.
     *
     * @return vrai si quelque chose s'est fait
     */
    public static boolean interactWithFluidHandler(Player player, InteractionHand hand, @Nullable BlockPos pos, ResourceHandler<FluidResource> handler) {
        ItemStack held = player.getItemInHand(hand);
        Fluid full = fluidOfBucket(held);
        if (full != Fluids.EMPTY) {
            FluidResource resource = FluidResource.of(full);
            try (Transaction transaction = Transaction.openRoot()) {
                if (handler.insert(resource, BUCKET, transaction) != BUCKET) {
                    return false;
                }
                transaction.commit();
            }
            if (!player.getAbilities().instabuild) {
                player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, new ItemStack(Items.BUCKET)));
            }
            player.level().playSound(null, pos == null ? player.blockPosition() : pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
            return true;
        }
        if (!held.is(Items.BUCKET)) {
            return false;
        }
        for (int index = 0; index < handler.size(); index++) {
            FluidResource resource = handler.getResource(index);
            if (resource.isEmpty() || resource.getFluid().getBucket() == Items.AIR) {
                continue;
            }
            try (Transaction transaction = Transaction.openRoot()) {
                if (handler.extract(index, resource, BUCKET, transaction) != BUCKET) {
                    continue;
                }
                transaction.commit();
            }
            player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, new ItemStack(resource.getFluid().getBucket())));
            player.level().playSound(null, pos == null ? player.blockPosition() : pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
            return true;
        }
        return false;
    }
}
