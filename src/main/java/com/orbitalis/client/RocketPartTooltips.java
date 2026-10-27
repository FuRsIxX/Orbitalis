package com.orbitalis.client;

import java.util.List;
import java.util.Locale;

import com.orbitalis.Orbitalis;
import com.orbitalis.client.research.ClientResearch;
import com.orbitalis.core.research.ResearchNode;
import com.orbitalis.core.research.ResearchNodes;
import com.orbitalis.core.registry.ModItems;
import com.orbitalis.phase1.rocket.RocketItem;
import com.orbitalis.phase1.rocket.RocketParts;
import com.orbitalis.phase1.rocket.RocketStats;
import com.orbitalis.phase1.satellite.SatelliteType;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Ajoute à l'infobulle des pièces de fusée leurs caractéristiques (poussée, impulsion spécifique, masse...)
 * et la technologie nécessaire pour les installer dans l'atelier d'assemblage.
 */
@EventBusSubscriber(modid = Orbitalis.MODID, value = Dist.CLIENT)
public final class RocketPartTooltips {
    private RocketPartTooltips() {
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        List<Component> lines = event.getToolTip();
        if (event.getItemStack().is(ModItems.ROCKET.get())) {
            RocketStats stats = RocketStats.of(RocketItem.design(event.getItemStack()));
            lines.add(Component.translatable(stats.reach().nameKey()).withStyle(stats.isFlightReady() ? ChatFormatting.GREEN : ChatFormatting.RED));
            lines.add(spec("rocket.orbitalis.tooltip.rocket", number(stats.liftoffMass()), number(stats.totalDeltaV())));
            // Satellite à bord : son orbite de travail, en vert si le lanceur peut l'atteindre.
            SatelliteType payload = SatelliteType.of(RocketItem.design(event.getItemStack()).payload());
            if (payload != null) {
                boolean reached = payload.orbit().isReachedBy(stats.reach());
                lines.add(Component.translatable(reached ? "satellite.orbitalis.tooltip.payload" : "satellite.orbitalis.tooltip.payload_short",
                        Component.translatable(payload.nameKey()), Component.translatable(payload.orbit().nameKey()))
                        .withStyle(reached ? ChatFormatting.GREEN : ChatFormatting.RED));
            }
            lines.add(Component.translatable("rocket.orbitalis.tooltip.disassemble").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        if (event.getItemStack().is(ModItems.MISSION_TABLET.get())) {
            lines.add(Component.translatable("tablet.orbitalis.tooltip").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (event.getItemStack().is(ModItems.RECOVERY_REMOTE.get())) {
            lines.add(Component.translatable("recovery.orbitalis.tooltip").withStyle(ChatFormatting.GRAY));
            return;
        }
        RocketParts.Part part = RocketParts.get(event.getItemStack());
        if (part == null) {
            return;
        }
        if (part.engine() != null) {
            RocketParts.Engine engine = part.engine();
            lines.add(spec("rocket.orbitalis.tooltip.propellant", Component.translatable(engine.propellant().nameKey())));
            if (engine.propellant() == com.orbitalis.phase1.rocket.Propellant.SUNLIGHT) {
                lines.add(spec("rocket.orbitalis.tooltip.sail", number(RocketStats.SAIL_IMPULSE / 1000.0)));
            } else if (engine.propellant().isLowThrust()) {
                // Une poussée de moins d'un kilonewton : on la donne en newtons.
                lines.add(spec("rocket.orbitalis.tooltip.thrust_low", String.format(Locale.ROOT, "%.1f", engine.thrustVacuum() * 1000.0)));
                lines.add(spec("rocket.orbitalis.tooltip.isp_vacuum", number(engine.ispVacuum())));
            } else {
                lines.add(spec("rocket.orbitalis.tooltip.thrust", number(engine.thrustSeaLevel()), number(engine.thrustVacuum())));
                lines.add(spec("rocket.orbitalis.tooltip.isp", number(engine.ispSeaLevel()), number(engine.ispVacuum())));
            }
            if (engine.solidLoad() > 0) {
                lines.add(spec(engine.propellant().isLowThrust() ? "rocket.orbitalis.tooltip.xenon_load" : "rocket.orbitalis.tooltip.solid_load",
                        number(engine.solidLoad())));
            }
            if (engine.propellant().isLowThrust()) {
                lines.add(Component.translatable("rocket.orbitalis.tooltip.low_thrust").withStyle(ChatFormatting.DARK_AQUA));
            }
        }
        if (part.tank() != null) {
            lines.add(spec("rocket.orbitalis.tooltip.volume", number(part.tank().volume())));
        }
        lines.add(spec("rocket.orbitalis.tooltip.mass", number(part.mass())));
        SatelliteType satellite = SatelliteType.of(event.getItemStack());
        if (satellite != null) {
            lines.add(spec("satellite.orbitalis.tooltip.orbit", Component.translatable(satellite.orbit().nameKey()),
                    satellite.orbit().altitudeKm()));
            lines.add(spec("satellite.orbitalis.tooltip.delta_v", number(satellite.orbit().requiredDeltaV())));
            if (satellite.pointsPerDay() > 0) {
                lines.add(spec("satellite.orbitalis.tooltip.data", satellite.pointsPerDay()));
            }
            lines.add(spec("satellite.orbitalis.tooltip.lifetime", satellite.lifetimeDays()));
            lines.add(Component.translatable("satellite.orbitalis.role." + satellite.id()).withStyle(ChatFormatting.DARK_AQUA));
        }

        // Technologie requise : en vert si l'agence la maîtrise, en rouge sinon.
        ResearchNode node = part.research() == null ? null : ResearchNodes.get(part.research());
        if (node != null && !node.isStartingKnowledge()) {
            boolean unlocked = ClientResearch.get().isUnlocked(node.id());
            lines.add(Component.translatable("rocket.orbitalis.tooltip.research", Component.translatable(node.nameKey()))
                    .withStyle(unlocked ? ChatFormatting.GREEN : ChatFormatting.RED));
        }
    }

    private static Component spec(String key, Object... values) {
        return Component.translatable(key, values).withStyle(ChatFormatting.GRAY);
    }

    private static String number(double value) {
        return String.format(Locale.ROOT, "%.0f", value);
    }
}
