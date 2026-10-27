package com.orbitalis.phase2.station;

import com.orbitalis.core.registry.ModBlockEntities;
import com.orbitalis.core.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Porte de sas, côté moitié basse : elle guette qui veut passer, et mène la porte de bout en bout.
 * <p>
 * Veut passer qui se tient dans le passage, ou s'avance vers la porte en la regardant (à moins de {@link #REACH} blocs).
 * Le regard compte : au milieu d'un sas, on est près des deux portes, mais on n'en regarde qu'une ; c'est celle-là qui
 * s'ouvre, et l'autre se referme derrière soi.
 * <ul>
 * <li>Fermée, elle s'ouvre pour qui veut passer, si l'autre porte du sas est close.</li>
 * <li>En cycle, elle met le sas à niveau ({@link #CYCLE_TICKS}), des bouffées d'air s'échappant des deux côtés.</li>
 * <li>Ouverte, l'écoutille remonte d'une position tous les {@link #STEP_TICKS} ticks ; puis la porte se referme quand plus
 * personne ne veut passer depuis {@link #HOLD_TICKS} ticks (sauf si le code la tient ouverte).</li>
 * <li>En fermeture, l'écoutille redescend ; si quelqu'un revient entre-temps, elle remonte.</li>
 * </ul>
 * Fermée d'un clic droit, elle ne se rouvre pas d'elle-même tant que celui qui l'a fermée reste devant à la regarder.
 */
public class AirlockDoorBlockEntity extends BlockEntity {
    /** Durée du cycle de mise à niveau, en ticks. */
    public static final int CYCLE_TICKS = 40;
    /** Durée de chaque position de l'écoutille, en ticks. */
    public static final int STEP_TICKS = 3;
    /** Temps que la porte reste ouverte une fois que plus personne ne veut passer, en ticks. */
    public static final int HOLD_TICKS = 20;
    /** Distance à laquelle regarder la porte suffit à l'ouvrir, en blocs (depuis les yeux jusqu'au centre de la porte). */
    private static final double REACH = 3.2;
    /** À quel point il faut viser la porte : le cosinus de l'angle entre le regard et la porte. */
    private static final double AIM = 0.8;
    /**
     * Une fois ouverte, la porte reste ouverte pour qui en est un peu plus loin ou la regarde un peu de biais : sans
     * cette marge, un léger mouvement de tête au bord de la zone la refermait et la rouvrait sans fin.
     */
    private static final double KEEP_REACH = 4.5;
    private static final double KEEP_AIM = 0.55;

    private int cycle;
    private int timer;
    private int empty;
    private int warned;
    /** Vrai si le code tient la porte ouverte : elle ne se referme pas d'elle-même. */
    private boolean held;
    /** Vrai si on vient de la fermer à la main : elle attend que plus personne ne la veuille pour se rouvrir. */
    private boolean shut;

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

    /** Un joueur vient d'ouvrir ou de fermer la porte d'un clic droit. */
    void operatedByHand(boolean closed) {
        held = false;
        shut = closed;
        empty = 0;
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

    /**
     * Vrai si un joueur (hors spectateur) se tient dans le passage, ou s'avance vers la porte en la regardant, à moins
     * de {@code reach} blocs et en la visant à {@code aim} près.
     */
    private static boolean someoneWants(ServerLevel level, BlockPos pos, Direction facing, double reach, double aim) {
        AABB doorway = doorway(pos, facing);
        Vec3 center = Vec3.atCenterOf(pos.above()).subtract(0.0, 0.5, 0.0);
        for (Player player : level.getEntitiesOfClass(Player.class, new AABB(pos).expandTowards(0.0, 1.0, 0.0).inflate(reach),
                player -> !player.isSpectator())) {
            if (player.getBoundingBox().intersects(doorway)) {
                return true;
            }
            Vec3 toDoor = center.subtract(player.getEyePosition());
            double distance = toDoor.length();
            if (distance <= reach && (distance < 0.6 || player.getLookAngle().dot(toDoor.scale(1.0 / distance)) >= aim)) {
                return true;
            }
        }
        return false;
    }

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        AirlockDoorBlock.Phase phase = state.getValue(AirlockDoorBlock.PHASE);
        Direction facing = state.getValue(HorizontalDirectionalBlock.FACING);
        int step = state.getValue(AirlockDoorBlock.STEP);
        boolean open = phase == AirlockDoorBlock.Phase.OPEN || phase == AirlockDoorBlock.Phase.CLOSING;
        boolean wanted = open ? someoneWants(level, pos, facing, KEEP_REACH, KEEP_AIM) : someoneWants(level, pos, facing, REACH, AIM);
        // Quelqu'un dans le passage ne compte que pour une porte ouverte : elle ne doit pas se refermer sur lui.
        boolean inside = open && someoneInside(level, pos, facing);
        if (!wanted) {
            shut = false;
        }
        boolean calls = wanted && !shut;
        if (warned > 0) {
            warned--;
        }
        switch (phase) {
            case CLOSED -> {
                if (step != 0) {
                    AirlockDoorBlock.setStep(level, pos, 0);
                }
                if (calls && level.getGameTime() % 5 == 0) {
                    Component problem = AirlockDoorBlock.operate(level, pos);
                    if (problem != null && warned == 0) {
                        // L'autre porte du sas est ouverte : on le dit, une fois de temps en temps.
                        warned = 60;
                        for (Player player : level.getEntitiesOfClass(Player.class, new AABB(pos).inflate(REACH))) {
                            player.sendOverlayMessage(problem.copy().withStyle(ChatFormatting.GOLD));
                        }
                    }
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
                if (step < AirlockDoorBlock.MAX_STEP) {
                    // L'écoutille remonte, une position après l'autre.
                    if (--timer <= 0) {
                        AirlockDoorBlock.setStep(level, pos, step + 1);
                        timer = STEP_TICKS;
                    }
                    empty = 0;
                } else if (wanted || held || inside) {
                    empty = 0;
                } else if (++empty >= HOLD_TICKS) {
                    // Plus personne ne veut passer : elle se referme.
                    AirlockDoorBlock.operate(level, pos);
                    timer = STEP_TICKS;
                    empty = 0;
                }
            }
            case CLOSING -> {
                if ((calls || inside) && step > 0) {
                    // Quelqu'un revient, ou se tient encore dans le passage : l'écoutille remonte, sans jamais se
                    // refermer sur lui.
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
