package com.orbitalis.phase1.fuel;

import java.util.ArrayList;
import java.util.List;

import com.orbitalis.core.registry.ModBlockEntities;
import com.orbitalis.core.registry.ModBlocks;
import com.orbitalis.core.registry.ModFluids;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;

/**
 * Un bloc de réservoir modulable.
 * <p>
 * Des blocs de réservoir posés les uns contre les autres forment un seul réservoir : un pavé à base carrée, d'un
 * à trois blocs de côté et d'un à douze blocs de haut. Tout le contenu est tenu par un seul bloc, le
 * <em>maître</em> : celui du coin le plus bas, au nord-ouest. Les autres ne font que renvoyer vers lui. C'est
 * {@link FluidTankStructure} qui assemble et défait les réservoirs quand on pose ou retire un bloc.
 * <p>
 * Le réservoir accepte n'importe quel liquide ou gaz, un seul à la fois. Il n'est pas refroidi : un ergol
 * cryogénique s'y évapore peu à peu, comme dans un réservoir cryogénique laissé sans courant. Il ne pousse rien de
 * lui-même : on le vide avec un tuyau en extraction, ou au seau.
 */
public class FluidTankBlockEntity extends BlockEntity {
    /** Contenance d'un bloc de réservoir, en mB : un réservoir de 3×3×12 tient 1 728 seaux. */
    public static final int CAPACITY_PER_BLOCK = 16_000;
    /** Plus grand réservoir : trois blocs de côté, douze de haut. */
    public static final int MAX_WIDTH = 3;
    public static final int MAX_HEIGHT = 12;
    /** Évaporation d'un ergol cryogénique : 1 mB par bloc de réservoir à chaque intervalle, soit 2,5 mB par seconde. */
    private static final int BOIL_OFF_INTERVAL = 8;
    /** Intervalle entre deux envois du niveau aux joueurs, en ticks. */
    private static final int SYNC_INTERVAL = 5;

    /** Contenu du réservoir : un seul fluide, dans la limite de la contenance de tout le réservoir. */
    private final class Storage extends FluidStacksResourceHandler {
        private Storage() {
            super(1, CAPACITY_PER_BLOCK);
        }

        @Override
        protected int getCapacity(int index, FluidResource resource) {
            return capacity();
        }

        @Override
        protected void onContentsChanged(int index, FluidStack previousContents) {
            setChanged();
            dirty = true;
        }
    }

    /** Le maître du réservoir, ou {@code null} si ce bloc l'est lui-même. */
    private BlockPos master;
    /** Côté et hauteur du réservoir, en blocs. N'ont de sens que sur le maître. */
    private int width = 1;
    private int height = 1;
    private final Storage storage = new Storage();
    private boolean dirty;
    private int ticks;

    public FluidTankBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FLUID_TANK.get(), pos, state);
    }

    // ------------------------------------------------------------------
    // Le réservoir dont ce bloc fait partie
    // ------------------------------------------------------------------

    /** Vrai si ce bloc tient le contenu du réservoir. */
    public boolean isMaster() {
        return master == null;
    }

    public BlockPos masterPos() {
        return master == null ? worldPosition : master;
    }

    /** Le bloc maître du réservoir, ou {@code null} s'il a disparu. */
    public FluidTankBlockEntity master() {
        if (master == null) {
            return this;
        }
        return level != null && level.getBlockEntity(master) instanceof FluidTankBlockEntity tank && tank.isMaster() ? tank : null;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    /** Contenance de tout le réservoir, en mB. */
    public int capacity() {
        return width * width * height * CAPACITY_PER_BLOCK;
    }

    /** Contenu de tout le réservoir. */
    public FluidStack fluid() {
        FluidTankBlockEntity tank = master();
        return tank == null ? FluidStack.EMPTY : FluidUtil.getStack(tank.storage, 0);
    }

    /** Contenance de tout le réservoir dont ce bloc fait partie, en mB. */
    public int tankCapacity() {
        FluidTankBlockEntity tank = master();
        return tank == null ? 0 : tank.capacity();
    }

    /** Ce que voient les tuyaux, les seaux et les autres mods : le contenu du réservoir entier, quel que soit le bloc touché. */
    public ResourceHandler<FluidResource> handler() {
        FluidTankBlockEntity tank = master();
        return tank == null ? null : tank.storage;
    }

    /** Les blocs du réservoir, d'après son maître : ceux qui existent encore et qui le reconnaissent. */
    List<FluidTankBlockEntity> members() {
        List<FluidTankBlockEntity> members = new ArrayList<>();
        if (level == null || !isMaster()) {
            return members;
        }
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                for (int z = 0; z < width; z++) {
                    BlockPos pos = worldPosition.offset(x, y, z);
                    if (level.getBlockState(pos).is(ModBlocks.FLUID_TANK.get()) && level.getBlockEntity(pos) instanceof FluidTankBlockEntity tank
                            && tank.masterPos().equals(worldPosition)) {
                        members.add(tank);
                    }
                }
            }
        }
        return members;
    }

    /** Fait de ce bloc un réservoir à lui seul, avec ce contenu. */
    void becomeSingle(FluidStack content) {
        becomeMaster(1, 1, content);
    }

    /** Fait de ce bloc le maître d'un réservoir de cette taille, avec ce contenu. */
    void becomeMaster(int newWidth, int newHeight, FluidStack content) {
        master = null;
        width = newWidth;
        height = newHeight;
        int amount = Math.min(content.getAmount(), capacity());
        storage.set(0, amount <= 0 ? FluidResource.EMPTY : FluidResource.of(content), Math.max(0, amount));
        sync();
    }

    /** Rattache ce bloc au réservoir de ce maître : il ne tient plus rien lui-même. */
    void follow(BlockPos newMaster) {
        master = newMaster.immutable();
        width = 1;
        height = 1;
        storage.set(0, FluidResource.EMPTY, 0);
        sync();
    }

    // ------------------------------------------------------------------
    // Vie du réservoir
    // ------------------------------------------------------------------

    public void serverTick(ServerLevel serverLevel) {
        if (!isMaster()) {
            return;
        }
        ticks++;
        FluidStack content = FluidUtil.getStack(storage, 0);
        // Sans refroidissement, un ergol cryogénique s'évapore.
        if (ticks % BOIL_OFF_INTERVAL == 0 && !content.isEmpty() && ModFluids.boilsOff(content.getFluid(), serverLevel)) {
            int remaining = content.getAmount() - width * width * height;
            storage.set(0, remaining <= 0 ? FluidResource.EMPTY : FluidResource.of(content), Math.max(0, remaining));
            setChanged();
            dirty = true;
        }
        if (dirty && ticks % SYNC_INTERVAL == 0) {
            dirty = false;
            serverLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** Un bloc du réservoir va disparaître : le réservoir se défait, et son contenu se partage entre les blocs qui restent. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level instanceof ServerLevel serverLevel) {
            FluidTankStructure.dissolve(serverLevel, this);
        }
    }

    private void sync() {
        setChanged();
        dirty = false;
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (master != null) {
            output.putLong("master", master.asLong());
        }
        output.putInt("width", width);
        output.putInt("height", height);
        storage.serialize(output.child("tank"));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        long packed = input.getLongOr("master", Long.MIN_VALUE);
        master = packed == Long.MIN_VALUE ? null : BlockPos.of(packed);
        width = Math.clamp(input.getIntOr("width", 1), 1, MAX_WIDTH);
        height = Math.clamp(input.getIntOr("height", 1), 1, MAX_HEIGHT);
        storage.deserialize(input.childOrEmpty("tank"));
    }
}
