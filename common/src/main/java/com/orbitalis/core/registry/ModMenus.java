package com.orbitalis.core.registry;

import com.orbitalis.Orbitalis;
import com.orbitalis.phase1.fuel.CryogenicTankMenu;
import com.orbitalis.phase1.fuel.FluidMachineMenu;
import com.orbitalis.phase1.machines.CombustionGeneratorMenu;
import com.orbitalis.phase1.machines.ProcessingMachineMenu;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import com.orbitalis.platform.registry.DeferredHolder;
import com.orbitalis.platform.registry.DeferredRegister;

/**
 * Registre des interfaces (menus) du mod.
 */
public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Orbitalis.MODID);

    /** Interface commune aux machines de transformation (fonderie à arc, laminoir). */
    public static final DeferredHolder<MenuType<?>, MenuType<ProcessingMachineMenu>> PROCESSING_MACHINE =
            MENUS.register("processing_machine", () -> com.orbitalis.platform.Menus.create(ProcessingMachineMenu::fromNetwork));

    public static final DeferredHolder<MenuType<?>, MenuType<CombustionGeneratorMenu>> COMBUSTION_GENERATOR =
            MENUS.register("combustion_generator", () -> com.orbitalis.platform.Menus.create(CombustionGeneratorMenu::fromNetwork));

    /** Interface commune aux machines à fluides (électrolyseur, liquéfacteur cryogénique, raffinerie). */
    public static final DeferredHolder<MenuType<?>, MenuType<FluidMachineMenu>> FLUID_MACHINE =
            MENUS.register("fluid_machine", () -> com.orbitalis.platform.Menus.create(FluidMachineMenu::fromNetwork));

    public static final DeferredHolder<MenuType<?>, MenuType<CryogenicTankMenu>> CRYOGENIC_TANK =
            MENUS.register("cryogenic_tank", () -> com.orbitalis.platform.Menus.create(CryogenicTankMenu::fromNetwork));

    /** Établi de combinaison : les quatre pièces et leurs modules. */
    public static final DeferredHolder<MenuType<?>, MenuType<com.orbitalis.phase2.survival.SuitWorkbenchMenu>> SUIT_WORKBENCH =
            MENUS.register("suit_workbench", () -> com.orbitalis.platform.Menus.create(
                    (id, inventory, data) -> new com.orbitalis.phase2.survival.SuitWorkbenchMenu(id, inventory)));

    /** Foreuse : le butin, le bloc de remplacement, les jauges. */
    public static final DeferredHolder<MenuType<?>, MenuType<com.orbitalis.phase1.quarry.DerrickMenu>> DRILL_DERRICK =
            MENUS.register("drill_derrick", () -> com.orbitalis.platform.Menus.create(com.orbitalis.phase1.quarry.DerrickMenu::fromNetwork));

    /** Accumulateur : sa charge et ses échanges. */
    public static final DeferredHolder<MenuType<?>, MenuType<com.orbitalis.phase1.power.AccumulatorMenu>> ACCUMULATOR =
            MENUS.register("accumulator", () -> com.orbitalis.platform.Menus.create(com.orbitalis.phase1.power.AccumulatorMenu::fromNetwork));

    private ModMenus() {
    }

    public static void register() {
        MENUS.register();
    }
}
