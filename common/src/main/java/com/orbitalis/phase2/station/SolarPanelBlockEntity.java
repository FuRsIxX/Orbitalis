package com.orbitalis.phase2.station;

import java.util.ArrayList;
import java.util.List;

import com.orbitalis.core.energy.MachineEnergyStorage;
import com.orbitalis.core.registry.ModBlockEntities;
import com.orbitalis.phase2.survival.SpaceRules;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import com.orbitalis.platform.nbt.ValueInput;
import com.orbitalis.platform.nbt.ValueOutput;
import com.orbitalis.platform.capability.Capabilities;
import com.orbitalis.platform.transfer.energy.EnergyHandler;
import com.orbitalis.platform.transfer.energy.EnergyHandlerUtil;

/**
 * Élément de panneau solaire : produit de l'électricité quand il voit le ciel. En orbite, hors de l'atmosphère et
 * sans nuit, il donne sa pleine puissance en permanence ; au sol, seulement de jour et quatre fois moins. Un élément
 * pris dans un carré de deux sur deux éléments raccordés produit un quart de plus.
 * <p>
 * Les éléments raccordés en un grand panneau mettent leur électricité en commun : chacun connaît toutes les
 * prises du panneau (les machines et les câbles qui touchent l'un de ses éléments) et y envoie sa production.
 * Cette liste n'est relevée que toutes les deux secondes : le reste du temps, un élément ne fait que produire
 * et, s'il a de quoi, livrer.
 */
public class SolarPanelBlockEntity extends BlockEntity {
    /** Réserve d'un élément, en ticks de sa production en orbite. */
    private static final int BUFFER_TICKS = 125;
    /** Un élément pris dans un carré de quatre produit un quart de plus. */
    private static final int GROUPED_BONUS_PERCENT = 25;
    /** Intervalle entre deux relevés des prises du grand panneau, et de l'ensoleillement, en ticks. */
    private static final int SCAN_INTERVAL = 40;

    /** Prise du grand panneau : un bloc qui accepte de l'électricité, et la face par laquelle on la lui envoie. */
    private record Outlet(BlockPos pos, Direction side) {
    }

    private final SolarPanelBlock.Tier tier;
    private final int transfer;
    private final MachineEnergyStorage energy;
    private final List<Outlet> outlets = new ArrayList<>();
    private int output;
    private int ticks;

    public SolarPanelBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SOLAR_PANEL.get(), pos, state);
        this.tier = state.getBlock() instanceof SolarPanelBlock panel ? panel.tier() : SolarPanelBlock.Tier.BASIC;
        // De quoi tout livrer, même avec la prime du carré de quatre.
        this.transfer = Math.max(200, tier.orbit * 2);
        this.energy = new MachineEnergyStorage(tier.orbit * BUFFER_TICKS, 0, transfer, this::setChanged);
    }

    public SolarPanelBlock.Tier tier() {
        return tier;
    }

    public MachineEnergyStorage getEnergy() {
        return energy;
    }

    private final StationPower.Link link = new StationPower.Link();

    /** Production à cet instant, en FE par tick : rien pendant l'éclipse, en orbite. */
    public int output(ServerLevel level, BlockPos pos) {
        if (!level.canSeeSky(pos.above())) {
            return 0;
        }
        int base;
        if (SpaceRules.isVacuum(level)) {
            base = StationPower.sunlit(level) ? tier.orbit : 0;
        } else {
            base = level.isDay() && !level.isRaining() ? tier.ground : 0;
        }
        return grouped(level, pos) ? base * (100 + GROUPED_BONUS_PERCENT) / 100 : base;
    }

    /** Vrai si l'élément forme, avec trois voisins raccordés, un carré de deux sur deux. */
    public static boolean grouped(net.minecraft.world.level.BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof SolarPanelBlock)) {
            return false;
        }
        // Le carré ne compte que des éléments de la même génération.
        for (Direction alongX : new Direction[] {Direction.EAST, Direction.WEST}) {
            for (Direction alongZ : new Direction[] {Direction.NORTH, Direction.SOUTH}) {
                if (!state.getValue(SolarPanelBlock.CONNECTIONS.get(alongX)) || !state.getValue(SolarPanelBlock.CONNECTIONS.get(alongZ))) {
                    continue;
                }
                // Le coin opposé doit être raccordé aux deux autres éléments du carré.
                BlockState corner = level.getBlockState(pos.relative(alongX).relative(alongZ));
                if (corner.getBlock() == state.getBlock() && corner.getValue(SolarPanelBlock.CONNECTIONS.get(alongX.getOpposite()))
                        && corner.getValue(SolarPanelBlock.CONNECTIONS.get(alongZ.getOpposite()))) {
                    return true;
                }
            }
        }
        return false;
    }

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        if (ticks++ % SCAN_INTERVAL == 0) {
            output = output(level, pos);
            scanOutlets(level, pos);
        }
        energy.generate(output);
        if (energy.getEnergy() <= 0) {
            return;
        }
        for (Outlet outlet : outlets) {
            EnergyHandler target = com.orbitalis.platform.capability.Caps.get(level, Capabilities.Energy.BLOCK, outlet.pos(), outlet.side());
            if (target != null) {
                EnergyHandlerUtil.move(energy, target, transfer, null);
            }
        }
        // Ce que les voisins n'ont pas pris part sur le réseau de la station, vers ses batteries et ses équipements.
        StationPower.Bus bus = link.bus(level, pos);
        if (bus != null && energy.getEnergy() > 0) {
            int sent = bus.offer(level, Math.min(energy.getEnergy(), transfer));
            energy.consume(sent);
        }
    }

    /** Relève les prises du grand panneau : tout ce qui touche l'un de ses éléments et accepte de l'électricité. */
    private void scanOutlets(ServerLevel level, BlockPos pos) {
        outlets.clear();
        for (BlockPos element : SolarPanelBlock.array(level, pos)) {
            for (Direction direction : Direction.values()) {
                BlockPos neighbour = element.relative(direction);
                // Un autre élément de panneau n'est pas une prise : il produit, il ne consomme pas.
                if (level.getBlockState(neighbour).getBlock() instanceof SolarPanelBlock) {
                    continue;
                }
                if (com.orbitalis.platform.capability.Caps.get(level, Capabilities.Energy.BLOCK, neighbour, direction.getOpposite()) != null) {
                    outlets.add(new Outlet(neighbour, direction.getOpposite()));
                }
            }
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
    }

    @Override
    protected void loadAdditional(net.minecraft.nbt.CompoundTag tag$, net.minecraft.core.HolderLookup.Provider registries$) {
        ValueInput input = ValueInput.of(tag$, registries$);
        super.loadAdditional(input.tag(), input.lookup());
        energy.deserialize(input.childOrEmpty("energy"));
    }
}
