package com.orbitalis.phase1.power;

import com.orbitalis.core.energy.MachineEnergyStorage;
import com.orbitalis.core.registry.ModBlockEntities;
import com.orbitalis.phase1.machines.MachineMenu;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Réserve d'un accumulateur. Tout ce qu'on lui envoie par ses cinq faces d'entrée (l'avant, celui de la jauge, et
 * les autres) s'y range ; à chaque tick, il en pousse autant qu'il peut par son dos, dans ce qui le touche (un câble,
 * une machine, un autre accumulateur). Il ne se renvoie jamais sa propre énergie, même quand le câble de sa sortie
 * revient sur une de ses entrées.
 */
public class AccumulatorBlockEntity extends BlockEntity implements MenuProvider {
    /** Données de l'écran : celles des machines, puis l'entrée et la sortie moyennes, en FE par tick. */
    public static final int DATA_COUNT = MachineMenu.BASE_DATA_COUNT + 2;
    /** Durée sur laquelle on fait la moyenne des échanges affichés, en ticks. */
    private static final int AVERAGE = 20;

    private final AccumulatorBlock.Tier tier;
    private final MachineEnergyStorage energy;
    private final EnergyHandler input;
    private final EnergyHandler output;
    /** Vrai pendant que l'accumulateur pousse son énergie : ses entrées refusent alors ce qui leur revient. */
    private boolean pushing;
    private int lastEnergy;
    private int ticks;
    private long inSum;
    private long outSum;
    private int averageIn;
    private int averageOut;

    public AccumulatorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ACCUMULATOR.get(), pos, state);
        this.tier = state.getBlock() instanceof AccumulatorBlock block ? block.tier() : AccumulatorBlock.Tier.BASIC;
        this.energy = new MachineEnergyStorage(tier.capacity, tier.transfer, tier.transfer, this::setChanged);
        this.input = new View(true);
        this.output = new View(false);
    }

    public AccumulatorBlock.Tier tier() {
        return tier;
    }

    public MachineEnergyStorage getEnergy() {
        return energy;
    }

    /** L'énergie vue d'une face : le dos ne fait que donner, les autres faces ne font que recevoir. */
    public EnergyHandler handler(Direction side) {
        if (side == null) {
            return energy;
        }
        return side == outlet() ? output : input;
    }

    /** La sortie : le dos, à l'opposé de la jauge. */
    public Direction outlet() {
        return front().getOpposite();
    }

    public Direction front() {
        BlockState state = getBlockState();
        return state.hasProperty(HorizontalDirectionalBlock.FACING) ? state.getValue(HorizontalDirectionalBlock.FACING) : Direction.NORTH;
    }

    public int averageIn() {
        return averageIn;
    }

    public int averageOut() {
        return averageOut;
    }

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        // Ce qui est entré depuis le tick d'avant (ou, si l'on a puisé chez lui par l'avant, ce qui est sorti).
        int delta = energy.getEnergy() - lastEnergy;
        int moved = 0;
        if (energy.getEnergy() > 0) {
            Direction outlet = outlet();
            EnergyHandler target = level.getCapability(Capabilities.Energy.BLOCK, pos.relative(outlet), outlet.getOpposite());
            if (target != null) {
                pushing = true;
                try {
                    moved = EnergyHandlerUtil.move(energy, target, tier.transfer, null);
                } finally {
                    pushing = false;
                }
            }
        }
        lastEnergy = energy.getEnergy();
        inSum += Math.max(0, delta);
        outSum += moved + Math.max(0, -delta);
        if (++ticks % AVERAGE == 0) {
            averageIn = (int) (inSum / AVERAGE);
            averageOut = (int) (outSum / AVERAGE);
            inSum = 0;
            outSum = 0;
        }
        int charge = chargeLevel();
        if (state.getValue(AccumulatorBlock.CHARGE) != charge) {
            level.setBlock(pos, state.setValue(AccumulatorBlock.CHARGE, charge), Block.UPDATE_CLIENTS);
        }
    }

    /** Les crans de charge des flancs : aucun s'il est vide, un dès la première goutte, cinq quand il est plein. */
    public int chargeLevel() {
        if (energy.getEnergy() <= 0) {
            return 0;
        }
        return Math.clamp((int) Math.ceil(energy.getEnergy() * (double) AccumulatorBlock.MAX_CHARGE / tier.capacity), 1, AccumulatorBlock.MAX_CHARGE);
    }

    /** Une face de l'accumulateur : une entrée, qui ne fait que recevoir, ou la sortie, qui ne fait que donner. */
    private final class View implements EnergyHandler {
        private final boolean receives;

        View(boolean receives) {
            this.receives = receives;
        }

        @Override
        public long getAmountAsLong() {
            return energy.getEnergy();
        }

        @Override
        public long getCapacityAsLong() {
            return energy.getCapacity();
        }

        @Override
        public int insert(int amount, TransactionContext transaction) {
            return receives && !pushing ? energy.insert(amount, transaction) : 0;
        }

        @Override
        public int extract(int amount, TransactionContext transaction) {
            return receives ? 0 : energy.extract(amount, transaction);
        }
    }

    // ------------------------------------------------------------------
    // Écran
    // ------------------------------------------------------------------

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> energy.getEnergy() & 0xFFFF;
                case 1 -> energy.getEnergy() >>> 16;
                case 2 -> energy.getCapacity() & 0xFFFF;
                case 3 -> energy.getCapacity() >>> 16;
                case 4 -> tier.ordinal();
                case 5 -> averageIn & 0xFFFF;
                case 6 -> averageOut & 0xFFFF;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new AccumulatorMenu(containerId, this, data);
    }

    // ------------------------------------------------------------------
    // Sauvegarde : cassé, il garde son énergie sur son objet
    // ------------------------------------------------------------------

    @Override
    protected void collectImplicitComponents(net.minecraft.core.component.DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        energy.storeIn(components);
    }

    @Override
    protected void applyImplicitComponents(net.minecraft.core.component.DataComponentGetter components) {
        super.applyImplicitComponents(components);
        energy.restoreFrom(components);
        lastEnergy = energy.getEnergy();
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        energy.serialize(out.child("energy"));
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        energy.deserialize(in.childOrEmpty("energy"));
        lastEnergy = energy.getEnergy();
    }
}
