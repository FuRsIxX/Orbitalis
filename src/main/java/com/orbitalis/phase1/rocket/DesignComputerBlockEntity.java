package com.orbitalis.phase1.rocket;

import java.util.List;

import com.orbitalis.core.network.DesignEditPayload;
import com.orbitalis.core.network.DesignPrintPayload;
import com.orbitalis.core.network.DesignStatePayload;
import com.orbitalis.core.network.ModNetwork;
import com.orbitalis.core.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Mémoire de l'ordinateur de conception : le brouillon qu'on y réglait, le modèle de départ et l'étage choisi. Celui
 * qui revient le retrouve tel quel, et en multijoueur tous ceux qui s'en servent voient le même brouillon, réglage
 * après réglage.
 */
public class DesignComputerBlockEntity extends BlockEntity {
    /** Distance au-delà de laquelle un joueur ne règle plus l'ordinateur, en blocs. */
    private static final double REACH = 10.0;
    /** Les joueurs à cette distance voient les réglages des autres en direct, si leur écran est ouvert, en blocs. */
    private static final double WATCH_RANGE = 24.0;

    private RocketDraft draft;
    private int preset;
    private int selected;

    public DesignComputerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DESIGN_COMPUTER.get(), pos, state);
    }

    /** Le brouillon gardé, ou {@code null} si personne n'a encore rien réglé. */
    public RocketDraft draft() {
        return draft;
    }

    /** Ouvre l'écran du joueur sur le brouillon gardé. */
    public void open(ServerPlayer player) {
        ModNetwork.send(player, state(true));
    }

    private DesignStatePayload state(boolean open) {
        RocketDraft shown = draft != null ? draft : new RocketDraft(Items.AIR, List.of());
        return new DesignStatePayload(worldPosition, open, draft != null, DesignPrintPayload.of(shown), preset, selected);
    }

    /** Un joueur a changé un réglage : l'ordinateur le garde et le montre aux autres. */
    public void edit(ServerPlayer player, DesignEditPayload edit) {
        if (!(level instanceof ServerLevel serverLevel) || player.level() != level
                || player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(worldPosition)) > REACH * REACH) {
            return;
        }
        RocketDraft received = edit.draft().draft();
        if (!received.isValid()) {
            return;
        }
        draft = received;
        preset = edit.preset();
        selected = Math.clamp(edit.selected(), 0, received.stages().size() - 1);
        setChanged();
        DesignStatePayload update = state(false);
        for (ServerPlayer other : serverLevel.players()) {
            if (other != player && other.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(worldPosition)) <= WATCH_RANGE * WATCH_RANGE) {
                ModNetwork.send(other, update);
            }
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.storeNullable("draft", RocketDraft.CODEC, draft);
        output.putInt("preset", preset);
        output.putInt("selected", selected);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        draft = input.read("draft", RocketDraft.CODEC).filter(RocketDraft::isValid).orElse(null);
        preset = input.getIntOr("preset", 0);
        selected = input.getIntOr("selected", 0);
    }
}
