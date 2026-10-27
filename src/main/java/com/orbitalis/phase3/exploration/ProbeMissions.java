package com.orbitalis.phase3.exploration;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import com.orbitalis.core.registry.ModBlocks;
import com.orbitalis.core.registry.ModItems;
import com.orbitalis.core.research.AgencyResearch;
import com.orbitalis.core.research.ResearchManager;
import com.orbitalis.phase1.rocket.RocketStats;
import com.orbitalis.phase1.satellite.CelestialBody;
import com.orbitalis.phase2.survival.SpaceRules;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Les missions robotiques vers la Lune et Mars : des charges utiles sans équipage, lancées comme les satellites.
 * <ul>
 * <li>Une sonde se met en orbite autour de l'astre et le cartographie.</li>
 * <li>Un atterrisseur robotique se pose à sa surface, dans sa dimension, à quelques dizaines de blocs à l'est de
 * l'aplomb du pas de tir : on peut aller le voir sur place. Ses coordonnées sont annoncées.</li>
 * <li>Une mission de retour d'échantillons se pose, prélève le sol et le rapporte sur Terre, où il est analysé.</li>
 * </ul>
 * La première mission de chaque sorte vers chaque astre rapporte une grosse prime de recherche ; les suivantes, un
 * peu de science en plus.
 */
public final class ProbeMissions {
    /** Écart, vers l'est, entre l'aplomb du pas de tir et l'endroit où se pose un atterrisseur, en blocs. */
    private static final int LANDING_OFFSET = 32;

    /**
     * Une mission.
     *
     * @param item       la charge utile
     * @param body       l'astre visé
     * @param dimension  sa dimension, ou {@code null} pour un astre où l'on ne se pose pas
     * @param reach      ce que doit atteindre le lanceur pour l'y envoyer
     * @param kind       sonde, atterrisseur ou retour d'échantillons
     * @param first      points de la première mission de cette sorte vers cet astre
     * @param repeat     points des suivantes
     */
    public record Mission(Supplier<? extends Item> item, CelestialBody body, ResourceKey<Level> dimension, RocketStats.Reach reach,
            Kind kind, int first, int repeat) {
        /** Vrai pour un atterrisseur, qui se pose pour de vrai. */
        public boolean lands() {
            return kind == Kind.LANDER;
        }

        /** Le jalon de recherche que franchit la première mission de cette sorte vers cet astre. */
        public String milestone() {
            return kind.prefix + body.id();
        }
    }

    /** Les familles de missions. */
    public enum Kind {
        ORBITER("mapped_"),
        LANDER("landed_"),
        SAMPLES("returned_"),
        /** Une sonde qui plonge dans l'atmosphère de l'astre et le mesure jusqu'au bout de sa descente. */
        ENTRY("entered_");

        private final String prefix;

        Kind(String prefix) {
            this.prefix = prefix;
        }
    }

    /** Ce qu'a donné une mission : les points gagnés, ce qu'il faut annoncer, et où s'est posé l'atterrisseur. */
    public record Outcome(int points, List<Component> messages, @Nullable BlockPos landedAt) {
    }

    private static List<Mission> missions;

    private ProbeMissions() {
    }

    public static List<Mission> all() {
        if (missions == null) {
            missions = List.of(
                    new Mission(ModItems.LUNAR_PROBE, CelestialBody.MOON, SpaceRules.MOON, RocketStats.Reach.LUNAR, Kind.ORBITER, 60, 10),
                    new Mission(ModItems.MARS_PROBE, CelestialBody.MARS, SpaceRules.MARS, RocketStats.Reach.MARS, Kind.ORBITER, 100, 15),
                    new Mission(ModItems.LUNAR_ROBOTIC_LANDER, CelestialBody.MOON, SpaceRules.MOON, RocketStats.Reach.LUNAR, Kind.LANDER, 100, 20),
                    new Mission(ModItems.MARS_ROBOTIC_LANDER, CelestialBody.MARS, SpaceRules.MARS, RocketStats.Reach.MARS, Kind.LANDER, 150, 25),
                    new Mission(ModItems.LUNAR_SAMPLE_RETURN, CelestialBody.MOON, SpaceRules.MOON, RocketStats.Reach.LUNAR, Kind.SAMPLES, 120, 25),
                    new Mission(ModItems.MARS_SAMPLE_RETURN, CelestialBody.MARS, SpaceRules.MARS, RocketStats.Reach.MARS, Kind.SAMPLES, 200, 40),
                    // Les planètes du système solaire : des sondes, sans dimension où se poser.
                    new Mission(ModItems.VENUS_ORBITER, CelestialBody.VENUS, null, RocketStats.Reach.VENUS, Kind.ORBITER, 140, 20),
                    new Mission(ModItems.VENUS_LANDER, CelestialBody.VENUS, null, RocketStats.Reach.VENUS, Kind.ENTRY, 180, 25),
                    new Mission(ModItems.MERCURY_ORBITER, CelestialBody.MERCURY, null, RocketStats.Reach.MERCURY, Kind.ORBITER, 260, 35),
                    new Mission(ModItems.JUPITER_ORBITER, CelestialBody.JUPITER, null, RocketStats.Reach.JUPITER, Kind.ORBITER, 250, 35),
                    new Mission(ModItems.JUPITER_ENTRY_PROBE, CelestialBody.JUPITER, null, RocketStats.Reach.JUPITER, Kind.ENTRY, 280, 40),
                    new Mission(ModItems.SATURN_ORBITER, CelestialBody.SATURN, null, RocketStats.Reach.SATURN, Kind.ORBITER, 320, 45),
                    new Mission(ModItems.TITAN_ENTRY_PROBE, CelestialBody.TITAN, null, RocketStats.Reach.TITAN, Kind.ENTRY, 380, 50),
                    new Mission(ModItems.EUROPA_ORBITER, CelestialBody.EUROPA, null, RocketStats.Reach.EUROPA, Kind.ORBITER, 350, 45));
        }
        return missions;
    }

    /** La mission que porte cette charge utile, ou {@code null} si ce n'est ni une sonde ni un atterrisseur. */
    public static @Nullable Mission of(ItemStack payload) {
        for (Mission mission : all()) {
            if (payload.is(mission.item().get())) {
                return mission;
            }
        }
        return null;
    }

    /**
     * Fin d'un vol réussi qui emportait une sonde ou un atterrisseur.
     *
     * @return ce qu'a donné la mission, ou {@code null} si la charge utile n'en est pas une
     */
    public static @Nullable Outcome onFlight(MinecraftServer server, String agencyKey, ItemStack payload, RocketStats.Reach reached,
            BlockPos launchSite) {
        Mission mission = of(payload);
        if (mission == null) {
            return null;
        }
        List<Component> messages = new ArrayList<>();
        Component body = Component.translatable(mission.body().nameKey());
        if (reached.ordinal() < mission.reach().ordinal()) {
            messages.add(Component.translatable("exploration.orbitalis.lost", payload.getHoverName(), body).withStyle(ChatFormatting.RED));
            return new Outcome(0, messages, null);
        }
        AgencyResearch research = ResearchManager.forAgency(server, agencyKey);
        boolean first = research.completeMilestone(mission.milestone());
        int points = first ? mission.first() : mission.repeat();
        research.addPoints(points);
        ResearchManager.changed(server, agencyKey);
        BlockPos landedAt = null;
        if (mission.lands()) {
            landedAt = land(server, mission.dimension(), launchSite);
            if (landedAt == null) {
                messages.add(Component.translatable("exploration.orbitalis.lander.landed_somewhere", body, points).withStyle(ChatFormatting.GREEN));
            } else {
                messages.add(Component.translatable("exploration.orbitalis.lander.landed", body, landedAt.getX(), landedAt.getY(), landedAt.getZ(), points)
                        .withStyle(ChatFormatting.GREEN));
            }
        } else if (mission.kind() == Kind.ENTRY) {
            messages.add(Component.translatable("exploration.orbitalis.entry.arrived", body, points).withStyle(ChatFormatting.GREEN));
        } else if (mission.kind() == Kind.SAMPLES) {
            messages.add(Component.translatable("exploration.orbitalis.samples.returned", body, points).withStyle(ChatFormatting.GREEN));
        } else {
            messages.add(Component.translatable("exploration.orbitalis.probe.arrived", body, points).withStyle(ChatFormatting.GREEN));
        }
        if (first) {
            String key = switch (mission.kind()) {
                case ORBITER -> "exploration.orbitalis.probe.first";
                case LANDER -> "exploration.orbitalis.lander.first";
                case SAMPLES -> "exploration.orbitalis.samples.first";
                case ENTRY -> "exploration.orbitalis.entry.first";
            };
            messages.add(Component.translatable(key, body).withStyle(ChatFormatting.AQUA));
        }
        return new Outcome(points, messages, landedAt);
    }

    /**
     * Pose un atterrisseur robotique à la surface de cette dimension, à l'est de l'aplomb du pas de tir, sur le sol.
     *
     * @return où il s'est posé, ou {@code null} si la dimension n'existe pas
     */
    public static @Nullable BlockPos land(MinecraftServer server, ResourceKey<Level> dimension, BlockPos launchSite) {
        ServerLevel level = server.getLevel(dimension);
        if (level == null) {
            return null;
        }
        int x = launchSite.getX() + LANDING_OFFSET;
        int z = launchSite.getZ();
        // Le terrain de l'endroit se génère s'il ne l'est pas encore : l'atterrisseur se pose sur le vrai sol.
        level.getChunk(x >> 4, z >> 4);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos pos = new BlockPos(x, y, z);
        level.setBlock(pos, ModBlocks.ROBOTIC_LANDER.get().defaultBlockState(), Block.UPDATE_ALL);
        return pos;
    }
}
