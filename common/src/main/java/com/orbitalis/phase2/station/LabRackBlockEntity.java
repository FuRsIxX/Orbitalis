package com.orbitalis.phase2.station;

import com.orbitalis.core.energy.MachineEnergyStorage;
import com.orbitalis.core.registry.ModBlockEntities;
import com.orbitalis.core.research.AgencyResearch;
import com.orbitalis.core.research.ResearchManager;
import com.orbitalis.phase2.survival.SpaceRules;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import com.orbitalis.platform.nbt.ValueInput;
import com.orbitalis.platform.nbt.ValueOutput;

/**
 * Baie d'expériences. Toutes les secondes, elle vérifie qu'elle est en orbite, que l'air de la pièce est respirable
 * devant elle et qu'elle a de quoi s'alimenter (sa propre réserve, remplie par câble, ou le réseau de la station) ;
 * alors son expérience avance. Une expérience menée à bien rapporte {@link #POINTS_PER_EXPERIMENT} point de
 * recherche à l'agence qui possède la station.
 * <p>
 * On peut aussi lui confier une boîte d'échantillons (de la Lune, de Mars ou d'une exoplanète) : elle l'analyse en
 * {@link #ANALYSIS_TICKS} ticks, en même temps que son expérience, et l'analyse en apesanteur vaut la moitié de plus
 * qu'au laboratoire de la Terre.
 */
public class LabRackBlockEntity extends BlockEntity implements com.orbitalis.platform.Compat.RemovalAware {
    /** Ce que fait la baie. */
    public enum Status {
        /** Pas en orbite : l'apesanteur manque. */
        NOT_IN_ORBIT,
        /** La pièce n'est pas pressurisée. */
        NO_AIR,
        /** Pas assez d'électricité. */
        NO_POWER,
        /** Une expérience est en cours. */
        RUNNING
    }

    /** Consommation, en FE par tick. */
    public static final int ENERGY_PER_TICK = 40;
    /** Durée d'une expérience, en ticks : une orbite, cinq minutes. */
    public static final int EXPERIMENT_TICKS = StationPower.ORBIT_PERIOD;
    public static final int POINTS_PER_EXPERIMENT = 1;
    /** Prime de la première expérience menée à bien par une agence. */
    private static final int REWARD_FIRST_EXPERIMENT = 50;
    private static final int STEP = 20;
    /** Durée de l'analyse d'une boîte d'échantillons, en ticks : une minute. */
    public static final int ANALYSIS_TICKS = 1200;
    /** L'analyse en apesanteur vaut la moitié de plus que sur Terre : ses points, en pour cent de ceux de la Terre. */
    public static final int ANALYSIS_BONUS = 150;

    private final MachineEnergyStorage energy = new MachineEnergyStorage(20_000, 400, 0, this::setChanged);
    private final StationPower.Link link = new StationPower.Link();
    private Status status = Status.NOT_IN_ORBIT;
    private int progress;
    private int experiments;
    private int ticks;
    /** La boîte d'échantillons en analyse, ou rien. */
    private ItemStack sample = ItemStack.EMPTY;
    private int analysis;

    public LabRackBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LAB_RACK.get(), pos, state);
    }

    public MachineEnergyStorage getEnergy() {
        return energy;
    }

    public Status getStatus() {
        return status;
    }

    /** Expériences menées à bien depuis la pose de la baie. */
    public int getExperiments() {
        return experiments;
    }

    public int progressPercent() {
        return progress * 100 / EXPERIMENT_TICKS;
    }

    /** La boîte d'échantillons en analyse, ou une pile vide. */
    public ItemStack getSample() {
        return sample;
    }

    public int analysisPercent() {
        return Math.min(100, analysis * 100 / ANALYSIS_TICKS);
    }

    /** Confie une boîte d'échantillons à la baie : faux si elle en analyse déjà une. */
    public boolean insertSample(ItemStack stack) {
        if (!sample.isEmpty() || !(stack.getItem() instanceof com.orbitalis.phase3.exploration.SampleItem)) {
            return false;
        }
        sample = stack.copyWithCount(1);
        analysis = 0;
        setChanged();
        return true;
    }

    /** Points de l'analyse en apesanteur : ceux de la Terre, et la moitié de plus. */
    public static int orbitalPoints(com.orbitalis.phase3.exploration.SampleItem.Body body, boolean first) {
        return body.points(first) * ANALYSIS_BONUS / 100;
    }

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        if (ticks++ % STEP != 0) {
            return;
        }
        BlockPos front = pos.relative(state.getValue(LabRackBlock.FACING));
        StationPower.Bus bus = link.bus(level, pos);
        int need = ENERGY_PER_TICK * STEP;
        if (!SpaceRules.isOrbit(level)) {
            status = Status.NOT_IN_ORBIT;
        } else if (!StationAir.isBreathable(level, front)) {
            status = Status.NO_AIR;
        } else if (!energy.consume(need) && (bus == null || !bus.draw(level, need))) {
            status = Status.NO_POWER;
        } else {
            status = Status.RUNNING;
            progress += STEP;
            if (progress >= EXPERIMENT_TICKS) {
                progress = 0;
                experiments++;
                credit(level, link.station(level, pos));
            }
            if (!sample.isEmpty()) {
                analysis += STEP;
                if (analysis >= ANALYSIS_TICKS) {
                    analyse(level, pos, link.station(level, pos));
                }
            }
            setChanged();
        }
        boolean lit = status == Status.RUNNING;
        if (state.getValue(LabRackBlock.LIT) != lit) {
            level.setBlock(pos, state.setValue(LabRackBlock.LIT, lit), Block.UPDATE_ALL);
        }
    }

    /** L'analyse est finie : ses points vont à l'agence de la station, et l'équipage à bord l'apprend. */
    private void analyse(ServerLevel level, BlockPos pos, BlockPos station) {
        String agency = station == null ? null : StationManager.agencyOf(level.getServer(), station);
        if (agency == null || !(sample.getItem() instanceof com.orbitalis.phase3.exploration.SampleItem item)) {
            // Une station sans agence : l'analyse attend, finie, qu'une agence la reçoive.
            analysis = ANALYSIS_TICKS;
            return;
        }
        AgencyResearch research = ResearchManager.forAgency(level.getServer(), agency);
        boolean first = research.completeMilestone(item.body().milestone());
        int points = orbitalPoints(item.body(), first);
        research.addPoints(points);
        ResearchManager.changed(level.getServer(), agency);
        Component name = sample.getHoverName();
        sample = ItemStack.EMPTY;
        analysis = 0;
        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.BREWING_STAND_BREW, net.minecraft.sounds.SoundSource.BLOCKS, 0.8F, 1.3F);
        for (net.minecraft.server.level.ServerPlayer player : level.getEntitiesOfClass(net.minecraft.server.level.ServerPlayer.class,
                new net.minecraft.world.phys.AABB(pos).inflate(24.0))) {
            player.sendSystemMessage(Component.translatable(first ? "station.orbitalis.lab.analysed_first" : "station.orbitalis.lab.analysed",
                    name, points).withStyle(net.minecraft.ChatFormatting.GREEN));
        }
    }

    /** L'expérience est finie : ses résultats vont à l'agence de la station. */
    private static void credit(ServerLevel level, BlockPos station) {
        String agency = station == null ? null : StationManager.agencyOf(level.getServer(), station);
        if (agency == null) {
            return;
        }
        AgencyResearch research = ResearchManager.forAgency(level.getServer(), agency);
        research.addPoints(POINTS_PER_EXPERIMENT);
        if (research.completeMilestone("first_experiment")) {
            research.addPoints(REWARD_FIRST_EXPERIMENT);
        }
        ResearchManager.changed(level.getServer(), agency);
    }

    /** Cassée, la baie rend la boîte qu'elle analysait. */
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null && !sample.isEmpty()) {
            net.minecraft.world.Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, sample);
            sample = ItemStack.EMPTY;
        }
    }

    /** Cassée, la machine garde son énergie sur son objet ; reposée, elle la retrouve. */
    @Override
    protected void collectImplicitComponents(net.minecraft.core.component.DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        energy.storeIn(components);
    }

    @Override
    protected void applyImplicitComponents(net.minecraft.world.level.block.entity.BlockEntity.DataComponentInput components) {
        super.applyImplicitComponents(components);
        energy.restoreFrom(components::get);
    }

    @Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag$, net.minecraft.core.HolderLookup.Provider registries$) {
        ValueOutput output = ValueOutput.of(tag$, registries$);
        super.saveAdditional(output.tag(), output.lookup());
        energy.serialize(output.child("energy"));
        output.putInt("progress", progress);
        output.putInt("experiments", experiments);
        output.store("sample", ItemStack.OPTIONAL_CODEC, sample);
        output.putInt("analysis", analysis);
    }

    @Override
    protected void loadAdditional(net.minecraft.nbt.CompoundTag tag$, net.minecraft.core.HolderLookup.Provider registries$) {
        ValueInput input = ValueInput.of(tag$, registries$);
        super.loadAdditional(input.tag(), input.lookup());
        energy.deserialize(input.childOrEmpty("energy"));
        progress = input.getIntOr("progress", 0);
        experiments = input.getIntOr("experiments", 0);
        sample = input.read("sample", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        analysis = input.getIntOr("analysis", 0);
    }
}
