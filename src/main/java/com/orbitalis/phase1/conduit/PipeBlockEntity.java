package com.orbitalis.phase1.conduit;

import com.orbitalis.core.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * La couleur d'un tuyau. Elle est gardée ici plutôt que dans l'état du bloc : les raccordements, le contenu et
 * l'extraction donnent déjà des milliers d'états à chaque tuyau, et la couleur les aurait multipliés par neuf.
 */
public class PipeBlockEntity extends BlockEntity {
    private PipeColor color = PipeColor.NONE;

    public PipeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PIPE.get(), pos, state);
    }

    public PipeColor color() {
        return color;
    }

    /** Couleur du tuyau situé là ; aucune s'il n'y a pas de tuyau (ou pas encore son entité). */
    public static PipeColor colorAt(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof PipeBlockEntity pipe ? pipe.color : PipeColor.NONE;
    }

    /** Change la couleur, côté serveur : le tuyau se redessine chez les joueurs. */
    public void setColor(PipeColor color) {
        if (this.color == color) {
            return;
        }
        this.color = color;
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (color != PipeColor.NONE) {
            output.putString("color", color.name());
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        String name = input.getStringOr("color", PipeColor.NONE.name());
        PipeColor loaded;
        try {
            loaded = PipeColor.valueOf(name);
        } catch (IllegalArgumentException unknown) {
            loaded = PipeColor.NONE;
        }
        boolean changed = loaded != color;
        color = loaded;
        // Chez le joueur, une couleur reçue redessine le tuyau.
        if (changed && level != null && level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
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
}
