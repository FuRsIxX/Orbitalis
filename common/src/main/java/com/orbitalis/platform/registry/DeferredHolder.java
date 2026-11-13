package com.orbitalis.platform.registry;

import java.util.function.Supplier;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/**
 * Une entrée de registre du mod, prête dès l'enregistrement fait : la même forme que le {@code DeferredHolder} de NeoForge,
 * pour que le code du mod reste inchangé sous Fabric comme sous NeoForge.
 *
 * @param <R> le type du registre
 * @param <T> le type de l'entrée
 */
public class DeferredHolder<R, T extends R> implements Supplier<T> {
    private final ResourceKey<R> key;
    private final Supplier<T> value;

    DeferredHolder(ResourceKey<R> key, Supplier<T> value) {
        this.key = key;
        this.value = value;
    }

    @Override
    public T get() {
        return value.get();
    }

    public T value() {
        return value.get();
    }

    public ResourceLocation getId() {
        return key.location();
    }

    public ResourceKey<R> getKey() {
        return key;
    }

    /** L'entrée telle qu'Architectury la tient (pour ses fluides, ses seaux, ses blocs liquides). */
    @SuppressWarnings("unchecked")
    public <X> dev.architectury.registry.registries.RegistrySupplier<X> registrySupplier() {
        return (dev.architectury.registry.registries.RegistrySupplier<X>) value;
    }

    /** L'entrée sous la forme d'un {@link Holder} du registre (sons, effets, attributs…). */
    @SuppressWarnings("unchecked")
    public Holder<R> asHolder() {
        Registry<R> registry = (Registry<R>) BuiltInRegistries.REGISTRY.get(key.registry());
        return registry.getHolderOrThrow(key);
    }
}
