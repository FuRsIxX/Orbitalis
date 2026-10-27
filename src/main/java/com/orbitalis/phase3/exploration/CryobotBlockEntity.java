package com.orbitalis.phase3.exploration;

import java.util.UUID;

import com.orbitalis.core.registry.ModBlockEntities;
import com.orbitalis.core.research.AgencyResearch;
import com.orbitalis.core.research.ResearchManager;
import com.orbitalis.phase2.survival.SpaceRules;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Le cryobot au travail. Sur Europe, sa tête chauffée par son générateur à radio-isotopes fond la glace sous elle, et la
 * sonde s'enfonce, en déroulant son câble : vingt kilomètres de banquise, en dix minutes de jeu. Au fond, l'océan. Il y
 * cherche la vie : la première fois qu'une agence l'atteint, il y trouve des molécules organiques et les signes d'une
 * vie microbienne, la plus grande découverte du jeu ; les fois suivantes, des mesures de plus.
 * <p>
 * Ailleurs que sur Europe, il ne fait rien : il n'y a pas d'océan sous la glace.
 */
public class CryobotBlockEntity extends BlockEntity {
    /** Durée de la descente, en ticks (dix minutes), et épaisseur de la banquise, en kilomètres. */
    public static final int DRILL_TICKS = 12_000;
    public static final int ICE_KM = 20;
    /** Points de recherche : la découverte de la vie (une fois par agence), puis chaque plongée suivante. */
    public static final int OCEAN_POINTS = 2000;
    public static final int REPEAT_POINTS = 100;

    private int progress;
    private boolean done;
    private String agency = "";
    private UUID owner;

    public CryobotBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CRYOBOT.get(), pos, state);
    }

    void setOwner(ServerPlayer player) {
        owner = player.getUUID();
        agency = ResearchManager.agencyKey(player);
        setChanged();
    }

    /** Part de la descente accomplie, de 0 à 1. */
    public float progress() {
        return done ? 1.0F : Math.min(1.0F, progress / (float) DRILL_TICKS);
    }

    public boolean isDone() {
        return done;
    }

    public void serverTick(ServerLevel level, BlockPos pos) {
        if (done || !SpaceRules.isEuropa(level)) {
            return;
        }
        progress++;
        if (progress % 10 == 0) {
            // La vapeur de la glace fondue s'échappe du trou de forage.
            level.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, 2, 0.15, 0.05, 0.15, 0.01);
        }
        if (progress % 200 == 0) {
            setChanged();
        }
        if (progress >= DRILL_TICKS) {
            reachOcean(level);
        }
    }

    /** Pour les essais : la descente touche presque au but. */
    public void advanceForTest(int ticks) {
        progress = Math.min(DRILL_TICKS - 1, progress + ticks);
    }

    /** L'océan est atteint : la découverte revient à l'agence de celui qui a posé le cryobot. */
    private void reachOcean(ServerLevel level) {
        done = true;
        setChanged();
        ServerPlayer player = owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);
        if (agency.isEmpty()) {
            return;
        }
        AgencyResearch research = ResearchManager.forAgency(level.getServer(), agency);
        boolean first = research.completeMilestone("europa_ocean");
        int points = first ? OCEAN_POINTS : REPEAT_POINTS;
        research.addPoints(points);
        ResearchManager.changed(level.getServer(), agency);
        if (player != null) {
            player.sendSystemMessage(Component.translatable(first ? "exploration.orbitalis.cryobot.life" : "exploration.orbitalis.cryobot.ocean", points)
                    .withStyle(first ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.AQUA));
        }
    }

    /** Ce qu'en dit un clic droit : où il en est, ou pourquoi il ne fait rien. */
    public Component status(ServerLevel level) {
        if (!SpaceRules.isEuropa(level)) {
            return Component.translatable("exploration.orbitalis.cryobot.wrong_world").withStyle(ChatFormatting.GOLD);
        }
        if (done) {
            return Component.translatable("exploration.orbitalis.cryobot.done").withStyle(ChatFormatting.AQUA);
        }
        int depth = Math.round(progress() * ICE_KM);
        int minutes = (int) Math.ceil((DRILL_TICKS - progress) / 1200.0);
        return Component.translatable("exploration.orbitalis.cryobot.drilling", depth, ICE_KM, minutes).withStyle(ChatFormatting.AQUA);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("progress", progress);
        output.putBoolean("done", done);
        output.putString("agency", agency);
        if (owner != null) {
            output.putString("owner", owner.toString());
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        progress = input.getIntOr("progress", 0);
        done = input.getBooleanOr("done", false);
        agency = input.getStringOr("agency", "");
        String id = input.getStringOr("owner", "");
        owner = id.isEmpty() ? null : UUID.fromString(id);
    }
}
