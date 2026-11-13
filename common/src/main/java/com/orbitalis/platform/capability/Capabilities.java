package com.orbitalis.platform.capability;

import com.orbitalis.platform.transfer.ResourceHandler;
import com.orbitalis.platform.transfer.energy.EnergyHandler;
import com.orbitalis.platform.transfer.fluid.FluidResource;
import com.orbitalis.platform.transfer.item.ItemResource;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

/** Les trois sortes d'échanges entre blocs : l'énergie, les fluides, les objets. */
public final class Capabilities {
    private Capabilities() {
    }

    public static final class Energy {
        public static final BlockCapability<EnergyHandler, Direction> BLOCK =
                BlockCapability.createSided(ResourceLocation.fromNamespaceAndPath("orbitalis", "energy"), EnergyHandler.class);

        private Energy() {
        }
    }

    public static final class Fluid {
        @SuppressWarnings({"unchecked", "rawtypes"})
        public static final BlockCapability<ResourceHandler<FluidResource>, Direction> BLOCK =
                BlockCapability.createSided(ResourceLocation.fromNamespaceAndPath("orbitalis", "fluid"), (Class) ResourceHandler.class);

        private Fluid() {
        }
    }

    public static final class Item {
        @SuppressWarnings({"unchecked", "rawtypes"})
        public static final BlockCapability<ResourceHandler<ItemResource>, Direction> BLOCK =
                BlockCapability.createSided(ResourceLocation.fromNamespaceAndPath("orbitalis", "item"), (Class) ResourceHandler.class);

        private Item() {
        }
    }
}
