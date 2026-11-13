package com.orbitalis.neoforge;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.MapCodec;
import com.orbitalis.Orbitalis;
import com.orbitalis.core.Features;
import com.orbitalis.platform.capability.neoforge.NeoForgeTransfer;
import com.orbitalis.platform.event.Events;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Orbitalis sous NeoForge : le démarrage commun, ce qui est propre à NeoForge, puis le client s'il y en a un. */
@Mod(Orbitalis.MODID)
public final class OrbitalisNeoForge {
    /** La condition des recettes du contenu interstellaire : vraie quand ce contenu est montré. */
    private enum InterstellarCondition implements ICondition {
        INSTANCE;

        static final MapCodec<InterstellarCondition> CODEC = MapCodec.unit(INSTANCE);

        @Override
        public boolean test(IContext context) {
            return Features.interstellar();
        }

        @Override
        public MapCodec<? extends ICondition> codec() {
            return CODEC;
        }
    }

    private static final DeferredRegister<MapCodec<? extends ICondition>> CONDITIONS =
            DeferredRegister.create(NeoForgeRegistries.Keys.CONDITION_CODECS, Orbitalis.MODID);

    static {
        CONDITIONS.register(Features.INTERSTELLAR_CONDITION.getPath(), () -> InterstellarCondition.CODEC);
    }

    public OrbitalisNeoForge(IEventBus modBus) {
        Orbitalis.init();
        CONDITIONS.register(modBus);
        modBus.addListener(RegisterCapabilitiesEvent.class, NeoForgeTransfer::expose);
        NeoForge.EVENT_BUS.addListener(BlockDropsEvent.class, OrbitalisNeoForge::onDrops);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            Orbitalis.initClient();
        }
    }

    /** Ce que laisse tomber un bloc cassé passe par le mod (les centres spatiaux abandonnés rendent de la ferraille). */
    private static void onDrops(BlockDropsEvent event) {
        List<ItemStack> drops = new ArrayList<>();
        for (ItemEntity entity : event.getDrops()) {
            drops.add(entity.getItem());
        }
        List<ItemStack> before = List.copyOf(drops);
        Events.drops(new com.orbitalis.platform.event.BlockDropsEvent(event.getLevel(), event.getPos(), event.getState(),
                event.getBlockEntity(), event.getBreaker(), drops));
        if (drops.equals(before)) {
            return;
        }
        event.getDrops().clear();
        double x = event.getPos().getX() + 0.5;
        double y = event.getPos().getY() + 0.5;
        double z = event.getPos().getZ() + 0.5;
        for (ItemStack stack : drops) {
            if (!stack.isEmpty()) {
                event.getDrops().add(new ItemEntity(event.getLevel(), x, y, z, stack));
            }
        }
    }
}
