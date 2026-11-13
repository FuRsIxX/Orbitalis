package com.orbitalis.phase1.machines;

import com.orbitalis.core.energy.MachineEnergyStorage;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import com.orbitalis.platform.nbt.ValueInput;
import com.orbitalis.platform.nbt.ValueOutput;
import com.orbitalis.platform.transfer.ResourceHandler;
import com.orbitalis.platform.transfer.fluid.FluidResource;

/**
 * Base de toutes les machines : une réserve d'énergie, un inventaire, la sauvegarde
 * et l'ouverture de l'interface. Chaque machine définit son comportement dans {@link #serverTick}.
 */
public abstract class MachineBlockEntity extends BlockEntity implements MenuProvider, com.orbitalis.platform.Compat.RemovalAware {
    /** Capacité par défaut de la réserve d'énergie d'une machine, en FE. */
    public static final int ENERGY_CAPACITY = 20_000;
    /** Débit maximal d'énergie reçu ou fourni par une machine, en FE par tick : de quoi nourrir la plus gourmande (400 FE/t). */
    public static final int ENERGY_TRANSFER = 400;

    protected final MachineEnergyStorage energy;
    protected final MachineInventory inventory;
    /** Emplacement du module de surcadençage (seulement sur les machines qui le permettent, voir {@link #supportsOverclock()}). */
    protected final MachineInventory upgrades = upgradeInventory(this::onUpgradeChanged);
    /** Vitesse choisie sur l'écran : 1, 2, 4 ou 8, jamais plus que ce que permet le module. */
    private int speed = 1;
    /** Vrai pendant les ticks où la machine travaille réellement. */
    private boolean working;

    protected MachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.energy = createEnergy();
        this.inventory = createInventory();
    }

    protected abstract MachineEnergyStorage createEnergy();

    protected abstract MachineInventory createInventory();

    /** Logique de la machine, exécutée une fois par tick côté serveur uniquement. */
    public abstract void serverTick(ServerLevel level, BlockPos pos, BlockState state);

    /** Vrai si la machine accepte un module de surcadençage. */
    public boolean supportsOverclock() {
        return false;
    }

    /** L'emplacement du module de surcadençage : un seul module, rien d'autre. Le menu du client en crée un pareil. */
    public static MachineInventory upgradeInventory(Runnable onChanged) {
        return new MachineInventory(1, (slot, stack) -> stack.getItem() instanceof OverclockModuleItem, slot -> false, onChanged) {
            @Override
            protected int getCapacity(int index, com.orbitalis.platform.transfer.item.ItemResource resource) {
                return 1;
            }
        };
    }

    public MachineInventory getUpgrades() {
        return upgrades;
    }

    /** Vitesse la plus haute que permet le module posé ; 1 sans module. */
    public int maxSpeed() {
        return supportsOverclock() && upgrades.getStack(0).getItem() instanceof OverclockModuleItem module ? module.speed() : 1;
    }

    /** Vitesse de la machine : combien de ticks de travail elle abat par tick. */
    public int speed() {
        return Math.min(speed, maxSpeed());
    }

    /** Choisit la vitesse (une puissance de deux, ramenée sous le plafond du module). */
    public void setSpeed(int wanted) {
        int chosen = 1;
        while (chosen * 2 <= Math.min(wanted, maxSpeed())) {
            chosen *= 2;
        }
        speed = chosen;
        applyOverclock();
        setChanged();
    }

    /** Consommation surcadencée : aller N fois plus vite coûte N² fois plus. */
    protected int overclockEnergy(int perTick) {
        int s = speed();
        return perTick * s * s;
    }

    /** Valeurs synchronisées du surcadençage, après celles de la machine : la vitesse, puis le plafond du module. */
    protected int overclockData(int local) {
        return local == 0 ? speed() : maxSpeed();
    }

    private void onUpgradeChanged() {
        // Sans module (ou avec un plus petit), la vitesse redescend d'elle-même ; elle remonte si on le repose.
        applyOverclock();
        setChanged();
    }

    /** La machine accepte d'autant plus d'énergie qu'elle en consomme. */
    private void applyOverclock() {
        if (supportsOverclock()) {
            int s = speed();
            energy.setMaxInsert(ENERGY_TRANSFER * s * s);
        }
    }

    /** Données supplémentaires envoyées au client à l'ouverture de l'interface. */
    public void writeMenuData(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(worldPosition);
    }

    public MachineEnergyStorage getEnergy() {
        return energy;
    }

    public MachineInventory getInventory() {
        return inventory;
    }

    /**
     * Réservoir dans lequel un joueur peut vider ou remplir un seau en cliquant sur le bloc ;
     * {@code null} si la machine ne contient pas de fluide.
     */
    public ResourceHandler<FluidResource> getBucketFluidHandler() {
        return null;
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    /**
     * Valeurs communes à toutes les machines, synchronisées vers l'interface du joueur.
     * Chaque valeur tient sur 16 bits : l'énergie et la capacité sont donc découpées en deux moitiés.
     */
    protected int baseData(int index) {
        return switch (index) {
            case 0 -> energy.getEnergy() & 0xFFFF;
            case 1 -> energy.getEnergy() >>> 16;
            case 2 -> energy.getCapacity() & 0xFFFF;
            case 3 -> energy.getCapacity() >>> 16;
            case 4 -> working ? 1 : 0;
            default -> 0;
        };
    }

    /** Indique si la machine travaille : allume ou éteint sa face avant et sa lumière. */
    protected void setLit(boolean lit) {
        working = lit;
        BlockState state = getBlockState();
        if (level != null && state.getValue(MachineBlock.LIT) != lit) {
            level.setBlock(worldPosition, state.setValue(MachineBlock.LIT, lit), 3);
        }
    }

    /** Cassée, la machine garde son énergie et son contenu sur son objet ; reposée, elle les retrouve. */
    @Override
    protected void collectImplicitComponents(net.minecraft.core.component.DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        energy.storeIn(components);
        inventory.storeIn(components);
        if (supportsOverclock() && (maxSpeed() > 1 || speed > 1)) {
            components.set(com.orbitalis.core.registry.ModDataComponents.OVERCLOCK.get(), new OverclockModuleItem.Setting(maxSpeed(), speed));
        }
    }

    @Override
    protected void applyImplicitComponents(net.minecraft.world.level.block.entity.BlockEntity.DataComponentInput components) {
        super.applyImplicitComponents(components);
        energy.restoreFrom(components::get);
        inventory.restoreFrom(components::get);
        OverclockModuleItem.Setting overclock = components.get(com.orbitalis.core.registry.ModDataComponents.OVERCLOCK.get());
        if (overclock != null && supportsOverclock()) {
            upgrades.setStack(0, OverclockModuleItem.forSpeed(overclock.module()));
            speed = Math.max(1, overclock.speed());
            applyOverclock();
        }
    }

    @Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag$, net.minecraft.core.HolderLookup.Provider registries$) {
        ValueOutput output = ValueOutput.of(tag$, registries$);
        super.saveAdditional(output.tag(), output.lookup());
        energy.serialize(output.child("energy"));
        inventory.serialize(output.child("inventory"));
        if (supportsOverclock()) {
            upgrades.serialize(output.child("upgrades"));
            output.putInt("speed", speed);
        }
    }

    @Override
    protected void loadAdditional(net.minecraft.nbt.CompoundTag tag$, net.minecraft.core.HolderLookup.Provider registries$) {
        ValueInput input = ValueInput.of(tag$, registries$);
        super.loadAdditional(input.tag(), input.lookup());
        energy.deserialize(input.childOrEmpty("energy"));
        inventory.deserialize(input.childOrEmpty("inventory"));
        if (supportsOverclock()) {
            upgrades.deserialize(input.childOrEmpty("upgrades"));
            speed = Math.max(1, input.getIntOr("speed", 1));
            applyOverclock();
        }
    }

    /**
     * Le contenu ne tombe plus au sol quand le bloc disparaît : il part avec l'objet de la machine (voir
     * {@link MachineDrops}). Le lâcher en plus le doublerait.
     */
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
    }
}
