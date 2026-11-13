package com.orbitalis.platform.registry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import dev.architectury.registry.registries.RegistrySupplier;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Un registre du mod, sur le modèle du {@code DeferredRegister} de NeoForge (ses blocs, ses objets, ses composants), posé sur
 * celui d'Architectury : il marche sous Fabric comme sous NeoForge. {@link #register()} se fait au démarrage commun.
 *
 * @param <T> le type du registre
 */
public class DeferredRegister<T> {
    private final dev.architectury.registry.registries.DeferredRegister<T> inner;
    private final ResourceKey<? extends Registry<T>> registry;
    private final String modid;
    private final List<DeferredHolder<T, ? extends T>> entries = new ArrayList<>();

    protected DeferredRegister(ResourceKey<? extends Registry<T>> registry, String modid) {
        @SuppressWarnings("unchecked")
        ResourceKey<Registry<T>> key = (ResourceKey<Registry<T>>) (ResourceKey<?>) registry;
        this.inner = dev.architectury.registry.registries.DeferredRegister.create(modid, key);
        this.registry = registry;
        this.modid = modid;
    }

    public static <T> DeferredRegister<T> create(ResourceKey<? extends Registry<T>> registry, String modid) {
        return new DeferredRegister<>(registry, modid);
    }

    public static Blocks createBlocks(String modid) {
        return new Blocks(modid);
    }

    public static Items createItems(String modid) {
        return new Items(modid);
    }

    public static DataComponents createDataComponents(ResourceKey<? extends Registry<DataComponentType<?>>> registry, String modid) {
        return new DataComponents(modid);
    }

    protected ResourceKey<T> key(String name) {
        return ResourceKey.create(registry, ResourceLocation.fromNamespaceAndPath(modid, name));
    }

    protected <I extends T> Supplier<I> add(String name, Supplier<? extends I> supplier) {
        RegistrySupplier<I> entry = inner.register(name, supplier);
        return entry;
    }

    public <I extends T> DeferredHolder<T, I> register(String name, Supplier<? extends I> supplier) {
        DeferredHolder<T, I> holder = new DeferredHolder<>(key(name), add(name, supplier));
        entries.add(holder);
        return holder;
    }

    public <I extends T> DeferredHolder<T, I> register(String name, Function<ResourceLocation, ? extends I> factory) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(modid, name);
        return register(name, () -> factory.apply(id));
    }

    /** Inscrit les entrées auprès du jeu ; à appeler une fois, au démarrage commun. */
    public void register() {
        inner.register();
    }

    public Collection<DeferredHolder<T, ? extends T>> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    /** Le registre des blocs. */
    public static final class Blocks extends DeferredRegister<Block> {
        private Blocks(String modid) {
            super(Registries.BLOCK, modid);
        }

        public <B extends Block> DeferredBlock<B> registerBlock(String name, Function<BlockBehaviour.Properties, ? extends B> factory,
                UnaryOperator<BlockBehaviour.Properties> properties) {
            return block(name, () -> factory.apply(properties.apply(BlockBehaviour.Properties.of())));
        }

        public <B extends Block> DeferredBlock<B> registerBlock(String name, Function<BlockBehaviour.Properties, ? extends B> factory) {
            return registerBlock(name, factory, UnaryOperator.identity());
        }

        public DeferredBlock<Block> registerSimpleBlock(String name, UnaryOperator<BlockBehaviour.Properties> properties) {
            return registerBlock(name, Block::new, properties);
        }

        public DeferredBlock<Block> registerSimpleBlock(String name) {
            return registerSimpleBlock(name, UnaryOperator.identity());
        }

        public <B extends Block> DeferredBlock<B> block(String name, Supplier<? extends B> supplier) {
            DeferredBlock<B> block = new DeferredBlock<>(key(name), add(name, supplier));
            return block;
        }
    }

    /** Le registre des objets. */
    public static final class Items extends DeferredRegister<Item> {
        private Items(String modid) {
            super(Registries.ITEM, modid);
        }

        public <I extends Item> DeferredItem<I> registerItem(String name, Function<Item.Properties, ? extends I> factory,
                UnaryOperator<Item.Properties> properties) {
            return item(name, () -> factory.apply(properties.apply(new Item.Properties())));
        }

        public <I extends Item> DeferredItem<I> registerItem(String name, Function<Item.Properties, ? extends I> factory,
                Supplier<Item.Properties> properties) {
            return item(name, () -> factory.apply(properties.get()));
        }

        public <I extends Item> DeferredItem<I> registerItem(String name, Function<Item.Properties, ? extends I> factory) {
            return registerItem(name, factory, UnaryOperator.identity());
        }

        public DeferredItem<Item> registerSimpleItem(String name, UnaryOperator<Item.Properties> properties) {
            return registerItem(name, Item::new, properties);
        }

        public DeferredItem<Item> registerSimpleItem(String name, Supplier<Item.Properties> properties) {
            return registerItem(name, Item::new, properties);
        }

        public DeferredItem<Item> registerSimpleItem(String name) {
            return registerSimpleItem(name, UnaryOperator.identity());
        }

        public DeferredItem<BlockItem> registerSimpleBlockItem(DeferredBlock<?> block) {
            return item(block.getId().getPath(), () -> new BlockItem(block.get(), new Item.Properties()));
        }

        public DeferredItem<BlockItem> registerSimpleBlockItem(DeferredBlock<?> block, UnaryOperator<Item.Properties> properties) {
            return item(block.getId().getPath(), () -> new BlockItem(block.get(), properties.apply(new Item.Properties())));
        }

        public <I extends Item> DeferredItem<I> item(String name, Supplier<? extends I> supplier) {
            return new DeferredItem<>(key(name), add(name, supplier));
        }

        @Override
        public <I extends Item> DeferredHolder<Item, I> register(String name, Supplier<? extends I> supplier) {
            return item(name, supplier);
        }
    }

    /** Le registre des composants de données. */
    public static final class DataComponents extends DeferredRegister<DataComponentType<?>> {
        private DataComponents(String modid) {
            super(Registries.DATA_COMPONENT_TYPE, modid);
        }

        public <D> DeferredHolder<DataComponentType<?>, DataComponentType<D>> registerComponentType(String name,
                UnaryOperator<DataComponentType.Builder<D>> builder) {
            return register(name, () -> builder.apply(DataComponentType.builder()).build());
        }
    }
}
