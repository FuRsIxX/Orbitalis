package com.orbitalis.platform;

import java.util.function.Consumer;

import dev.architectury.registry.menu.MenuRegistry;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

/** Les interfaces qui reçoivent des données à l'ouverture (la position d'une machine…), pour Fabric comme pour NeoForge. */
public final class Menus {
    /** Ce qui crée l'interface côté client, d'après les données reçues. */
    @FunctionalInterface
    public interface Factory<T extends AbstractContainerMenu> {
        T create(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer);
    }

    private Menus() {
    }

    public static <T extends AbstractContainerMenu> MenuType<T> create(Factory<T> factory) {
        return MenuRegistry.ofExtended((id, inventory, buffer) -> factory.create(id, inventory, registryBuffer(buffer, inventory.player.registryAccess())));
    }

    /** Ouvre l'interface chez le joueur, en lui envoyant d'abord ce que {@code writer} écrit. */
    public static void open(ServerPlayer player, MenuProvider provider, Consumer<RegistryFriendlyByteBuf> writer) {
        MenuRegistry.openExtendedMenu(player, provider, buffer -> writer.accept(registryBuffer(buffer, player.registryAccess())));
    }

    private static RegistryFriendlyByteBuf registryBuffer(FriendlyByteBuf buffer, RegistryAccess access) {
        return buffer instanceof RegistryFriendlyByteBuf registry ? registry : new RegistryFriendlyByteBuf(buffer, access);
    }
}
