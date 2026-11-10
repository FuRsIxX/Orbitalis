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
    /** Données de l'écran : celles des machines, puis l'entrée et la sortie moyennes, en FE par tick, et le réglage. */
    public static final int DATA_COUNT = MachineMenu.BASE_DATA_COUNT + 3;

    /** Le réglage : en charge, il prend l'énergie par toutes ses faces ; en décharge, il la donne par toutes ses faces. */
    public enum Mode {
        CHARGE, DISCHARGE
    }
    /** Durée sur laquelle on fait la moyenne des échanges affichés, en ticks. */
    private static final int AVERAGE = 20;

    private final AccumulatorBlock.Tier tier;
    private final MachineEnergyStorage energy;
    private final EnergyHandler faces;
    private Mode mode = Mode.CHARGE;
    /** Vrai pendant que l'accumulateur pousse son énergie : il refuse alors ce qui lui revient. */
    private boolean pushing;
    /** La face par laquelle commencer à donner, au prochain tick : chacune son tour d'être servie la première. */
    private int nextFace;
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
        this.faces = new View();
    }

    public AccumulatorBlock.Tier tier() {
        return tier;
    }

    public MachineEnergyStorage getEnergy() {
        return energy;
    }

    /** L'énergie vue d'une face : toutes les faces font de même, recevoir en charge, donner en décharge. */
    public EnergyHandler handler(Direction side) {
        return side == null ? energy : faces;
    }

    public Mode mode() {
        return mode;
    }

    /** Change le réglage ; les câbles voisins revoient alors ce que chaque face accepte. */
    public void setMode(Mode mode) {
        if (this.mode != mode) {
            this.mode = mode;
            setChanged();
            if (level != null) {
                level.invalidateCapabilities(worldPosition);
            }
        }
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
        if (mode == Mode.DISCHARGE && energy.getEnergy() > 0) {
            // En décharge : il donne par toutes ses faces, dans la limite de son débit, chacune son tour la première.
            Direction[] sides = Direction.values();
            pushing = true;
            try {
                for (int k = 0; k < sides.length && moved < tier.transfer; k++) {
                    Direction side = sides[(nextFace + k) % sides.length];
                    EnergyHandler target = level.getCapability(Capabilities.Energy.BLOCK, pos.relative(side), side.getOpposite());
                    if (target != null) {
                        moved += EnergyHandlerUtil.move(energy, target, tier.transfer - moved, null);
                    }
                }
            } finally {
                pushing = false;
            }
            nextFace = (nextFace + 1) % sides.length;
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

    /** Une face de l'accumulateur : en charge, elle ne fait que recevoir ; en décharge, elle ne fait que donner. */
    private final class View implements EnergyHandler {
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
            return mode == Mode.CHARGE && !pushing ? energy.insert(amount, transaction) : 0;
        }

        @Override
        public int extract(int amount, TransactionContext transaction) {
            return mode == Mode.DISCHARGE ? energy.extract(amount, transaction) : 0;
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
                case 7 -> mode.ordinal();
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
        out.putString("mode", mode.name());
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        energy.deserialize(in.childOrEmpty("energy"));
        lastEnergy = energy.getEnergy();
        mode = "DISCHARGE".equals(in.getStringOr("mode", "CHARGE")) ? Mode.DISCHARGE : Mode.CHARGE;
    }
}
