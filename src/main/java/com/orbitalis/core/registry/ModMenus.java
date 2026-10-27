package com.orbitalis.core.registry;

import com.orbitalis.Orbitalis;
import com.orbitalis.phase1.fuel.CryogenicTankMenu;
import com.orbitalis.phase1.fuel.FluidMachineMenu;
import com.orbitalis.phase1.machines.CombustionGeneratorMenu;
import com.orbitalis.phase1.machines.ProcessingMachineMenu;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registre des interfaces (menus) du mod.
 */
public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Orbitalis.MODID);

    /** Interface commune aux machines de transformation (fonderie à arc, laminoir). */
    public static final DeferredHolder<MenuType<?>, MenuType<ProcessingMachineMenu>> PROCESSING_MACHINE =
            MENUS.register("processing_machine", () -> IMenuTypeExtension.create(ProcessingMachineMenu::fromNetwork));

    public static final DeferredHolder<MenuType<?>, MenuType<CombustionGeneratorMenu>> COMBUSTION_GENERATOR =
            MENUS.register("combustion_generator", () -> IMenuTypeExtension.create(CombustionGeneratorMenu::fromNetwork));

    /** Interface commune aux machines à fluides (électrolyseur, liquéfacteur cryogénique, raffinerie). */
    public static final DeferredHolder<MenuType<?>, MenuType<FluidMachineMenu>> FLUID_MACHINE =
            MENUS.register("fluid_machine", () -> IMenuTypeExtension.create(FluidMachineMenu::fromNetwork));

    public static final DeferredHolder<MenuType<?>, MenuType<CryogenicTankMenu>> CRYOGENIC_TANK =
            MENUS.register("cryogenic_tank", () -> IMenuTypeExtension.create(CryogenicTankMenu::fromNetwork));

    /** Établi de combinaison : les quatre pièces et leurs modules. */
    public static final DeferredHolder<MenuType<?>, MenuType<com.orbitalis.phase2.survival.SuitWorkbenchMenu>> SUIT_WORKBENCH =
            MENUS.register("suit_workbench", () -> IMenuTypeExtension.create(
                    (id, inventory, data) -> new com.orbitalis.phase2.survival.SuitWorkbenchMenu(id, inventory)));

    private ModMenus() {
    }

    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }
}
