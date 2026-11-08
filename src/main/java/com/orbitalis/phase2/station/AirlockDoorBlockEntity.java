package com.orbitalis.phase2.station;

import com.orbitalis.core.registry.ModBlockEntities;
import com.orbitalis.core.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

/**
 * Porte de sas, côté moitié basse : elle mène la porte de bout en bout, une fois qu'on l'a ouverte ou fermée d'un clic
 * droit. Elle ne s'ouvre ni ne se ferme d'elle-même.
 * <ul>
 * <li>En cycle, elle met le sas à niveau ({@link #CYCLE_TICKS}), des bouffées d'air s'échappant des deux côtés.</li>
 * <li>Ouverte, l'écoutille remonte d'une position tous les {@link #STEP_TICKS} ticks, puis la porte reste ouverte.</li>
 * <li>En fermeture, l'écoutille redescend ; si quelqu'un se tient dans le passage, elle remonte au lieu de se refermer
 * sur lui.</li>
 * </ul>
 */
public class AirlockDoorBlockEntity extends BlockEntity {
    /** Durée du cycle de mise à niveau, en ticks. */
    public static final int CYCLE_TICKS = 40;
    /** Durée de chaque position de l'écoutille, en ticks. */
    public static final int STEP_TICKS = 3;

    private int cycle;
    private int timer;
    /** Vrai si le code tient la porte ouverte (essais, aperçus). */
    private boolean held;

    public AirlockDoorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.AIRLOCK_DOOR.get(), pos, state);
    }

    public void startCycle() {
        cycle = CYCLE_TICKS;
        setChanged();
    }

    void setHeld(boolean held) {
        this.held = held;
        setChanged();
    }

    /** Un joueur vient d'ouvrir ou de fermer la porte d'un clic droit : elle n'est plus tenue par le code. */
    void operatedByHand(boolean closed) {
        held = false;
        setChanged();
    }

    /** Le passage de la porte : sa baie, et un peu de chaque côté. */
    private static AABB doorway(BlockPos pos, Direction facing) {
        boolean alongX = facing.getAxis() == Direction.Axis.X;
        return new AABB(pos).expandTowards(0.0, 1.0, 0.0).inflate(alongX ? 0.35 : 0.0, 0.0, alongX ? 0.0 : 0.35);
    }

    /** Vrai si un joueur (hors spectateur) se tient dans le passage : la porte ne doit pas se refermer sur lui. */
    private static boolean someoneInside(ServerLevel level, BlockPos pos, Direction facing) {
        AABB doorway = doorway(pos, facing);
        return !level.getEntitiesOfClass(Player.class, doorway, player -> !player.isSpectator()).isEmpty();
    }

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        AirlockDoorBlock.Phase phase = state.getValue(AirlockDoorBlock.PHASE);
        Direction facing = state.getValue(HorizontalDirectionalBlock.FACING);
        int step = state.getValue(AirlockDoorBlock.STEP);
        switch (phase) {
            case CLOSED -> {
                if (step != 0) {
                    AirlockDoorBlock.setStep(level, pos, 0);
                }
            }
            case CYCLING -> {
                if (cycle <= 0) {
                    // Une porte restée en cycle (monde rechargé au mauvais moment) reprend son cycle.
                    cycle = CYCLE_TICKS;
                }
                cycle--;
                setChanged();
                if (cycle % 5 == 0) {
                    // Des bouffées d'air, devant et derrière la porte, à mi-hauteur de l'ouverture.
                    for (int side : new int[] {1, -1}) {
                        double x = pos.getX() + 0.5 + facing.getStepX() * 0.6 * side;
                        double z = pos.getZ() + 0.5 + facing.getStepZ() * 0.6 * side;
                        level.sendParticles(ParticleTypes.CLOUD, x, pos.getY() + 1.0, z, 3, 0.25, 0.5, 0.25, 0.01);
                    }
                }
                if (cycle == 0) {
                    AirlockDoorBlock.setPhase(level, pos, AirlockDoorBlock.Phase.OPEN);
                    timer = STEP_TICKS;
                    level.playSound(null, pos.above(), ModSounds.AIRLOCK_HATCH_OPEN.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
                }
            }
            case OPEN -> {
                // L'écoutille remonte, une position après l'autre, puis la porte reste ouverte.
                if (step < AirlockDoorBlock.MAX_STEP && --timer <= 0) {
                    AirlockDoorBlock.setStep(level, pos, step + 1);
                    timer = STEP_TICKS;
                }
            }
            case CLOSING -> {
                if (step > 0 && someoneInside(level, pos, facing)) {
                    // Quelqu'un se tient dans le passage : l'écoutille remonte, sans jamais se refermer sur lui.
                    AirlockDoorBlock.setPhase(level, pos, AirlockDoorBlock.Phase.OPEN);
                    timer = STEP_TICKS;
                } else if (--timer <= 0) {
                    timer = STEP_TICKS;
                    if (step > 0) {
                        AirlockDoorBlock.setStep(level, pos, step - 1);
                    } else {
                        held = false;
                        setChanged();
                        AirlockDoorBlock.setPhase(level, pos, AirlockDoorBlock.Phase.CLOSED);
                        level.playSound(null, pos.above(), ModSounds.PAD_VALVE.get(), SoundSource.BLOCKS, 0.5F, 1.4F);
                    }
                }
            }
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("cycle", cycle);
        output.putBoolean("held", held);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        cycle = input.getIntOr("cycle", 0);
        held = input.getBooleanOr("held", false);
    }
}
