package com.orbitalis.client;

import com.orbitalis.Orbitalis;
import com.orbitalis.core.registry.ModSounds;
import com.orbitalis.phase2.flight.CapsuleEntity;
import com.orbitalis.phase2.station.LifeSupportBlock;
import com.orbitalis.phase2.survival.SpaceRules;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Les bruits de fond d'une station : le ronronnement de chaque générateur d'air en service, que l'on entend en
 * s'en approchant, et l'ambiance discrète des pièces pressurisées, tant qu'on s'y tient.
 */
@EventBusSubscriber(modid = Orbitalis.MODID, value = Dist.CLIENT)
public final class StationSounds {
    private StationSounds() {
    }

    /** Un générateur d'air, vu par le client : son ronronnement commence, s'il ne joue pas déjà. */
    public static void hum(BlockPos pos) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        BlockPos at = pos.immutable();
        var dimension = minecraft.level.dimension();
        LoopSound.of("hum:" + dimension + ":" + at.asLong(), ModSounds.LIFE_SUPPORT_HUM.get()).at(() -> Vec3.atCenterOf(at), 2.0, 14.0)
                .volume(1.0F).fade(0.1F, 0.1F).play(() -> {
                    var level = Minecraft.getInstance().level;
                    if (level == null || level.dimension() != dimension) {
                        return false;
                    }
                    BlockState state = level.getBlockState(at);
                    return state.getBlock() instanceof LifeSupportBlock && state.getValue(LifeSupportBlock.ACTIVE);
                });
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.isPaused() || !inside(minecraft)) {
            return;
        }
        LoopSound.of("station:ambient", ModSounds.STATION_AMBIENT.get()).in(SoundSource.AMBIENT).volume(0.75F).fade(0.02F, 0.03F)
                .play(() -> inside(Minecraft.getInstance()));
    }

    /** Vrai si le joueur se tient dans une pièce pressurisée d'une station, hors de sa capsule. */
    private static boolean inside(Minecraft minecraft) {
        return minecraft.player != null && minecraft.level != null && SpaceRules.isOrbit(minecraft.level)
                && !(minecraft.player.getVehicle() instanceof CapsuleEntity) && SuitHud.breathable(minecraft.player);
    }
}
