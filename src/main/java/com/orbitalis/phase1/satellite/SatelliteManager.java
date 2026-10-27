package com.orbitalis.phase1.satellite;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.orbitalis.Orbitalis;
import com.orbitalis.core.config.ServerConfig;
import com.orbitalis.core.network.SatelliteCommandPayload;
import com.orbitalis.core.network.SatelliteStatusPayload;
import com.orbitalis.core.registry.ModBlocks;
import com.orbitalis.core.research.AgencyResearch;
import com.orbitalis.core.research.ResearchManager;
import com.orbitalis.phase1.rocket.RocketStats;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.WeatherData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Satellites côté serveur : mise en orbite à la fin d'un vol, réception des données, relais par les satellites
 * de télécommunications, et état envoyé aux stations de poursuite.
 * <p>
 * Comme la recherche, les satellites appartiennent à l'agence (l'équipe du joueur, ou le joueur seul).
 */
@EventBusSubscriber(modid = Orbitalis.MODID)
public final class SatelliteManager {
    /** Intervalle entre deux relais automatiques des données, en ticks. */
    private static final int RELAY_INTERVAL = 200;
    /** Distance maximale entre un joueur et la station de poursuite qu'il utilise, en blocs. */
    private static final double STATION_REACH = 8.0;

    /**
     * Résultat de la fin d'un vol qui emportait un satellite.
     *
     * @param satellite satellite mis en orbite, ou {@code null} si l'orbite n'a pas été atteinte
     * @param points    points de recherche gagnés (premier satellite de ce type, constellation)
     * @param messages  ce qu'il faut annoncer aux joueurs
     */
    public record Deployment(Satellite satellite, int points, List<Component> messages) {
    }

    private SatelliteManager() {
    }

    private static SatelliteSavedData data(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(SatelliteSavedData.TYPE);
    }

    /** Satellites d'une agence. Après une modification, appeler {@link #changed}. */
    public static AgencySatellites of(MinecraftServer server, String agencyKey) {
        return data(server).get(agencyKey);
    }

    public static void changed(MinecraftServer server) {
        data(server).setDirty();
    }

    /** Date courante, en ticks de jeu : elle ne recule jamais, même si l'heure du jour est modifiée. */
    /** Les grandes antennes de suivi : elles ouvrent l'observatoire et la prospection de la station de poursuite. */
    public static final String TRACKING_STATIONS = "tracking_stations";
    /** Les générateurs à radio-isotopes : les satellites lancés ensuite durent deux fois plus longtemps. */
    public static final String RTG = "rtg";

    public static long now(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    /** Vrai si les satellites ont une durée de vie (réglage du serveur). */
    public static boolean limited() {
        return ServerConfig.SATELLITE_LIFETIME.get();
    }

    /**
     * Fin d'un vol réussi qui emportait un satellite : il est mis en orbite si le lanceur pouvait atteindre
     * son orbite de travail, sinon il est perdu.
     */
    public static Deployment onFlight(MinecraftServer server, String agencyKey, SatelliteType type, RocketStats.Reach reach) {
        List<Component> messages = new ArrayList<>();
        Component typeName = Component.translatable(type.nameKey());
        Component orbitName = Component.translatable(type.orbit().nameKey());
        if (!type.orbit().isReachedBy(reach)) {
            messages.add(Component.translatable("satellite.orbitalis.lost", typeName, orbitName).withStyle(ChatFormatting.RED));
            return new Deployment(null, 0, messages);
        }

        long now = now(server);
        AgencySatellites satellites = of(server, agencyKey);
        Satellite satellite = satellites.deploy(type, now, ResearchManager.forAgency(server, agencyKey).isUnlocked(RTG));
        changed(server);
        messages.add(Component.translatable("satellite.orbitalis.deployed", typeName, satellite.number(), orbitName,
                type.orbit().altitudeKm()).withStyle(ChatFormatting.GREEN));

        AgencyResearch research = ResearchManager.forAgency(server, agencyKey);
        int points = 0;
        if (research.completeMilestone("satellite_" + type.id())) {
            points += type.firstReward();
            messages.add(Component.translatable("satellite.orbitalis.first", type.firstReward()).withStyle(ChatFormatting.AQUA));
        }
        if (type == SatelliteType.NAVIGATION
                && satellites.active(SatelliteType.NAVIGATION, now, limited()) >= SatelliteType.NAVIGATION_CONSTELLATION
                && research.completeMilestone("constellation_navigation")) {
            points += SatelliteType.CONSTELLATION_REWARD;
            messages.add(Component.translatable("satellite.orbitalis.constellation", SatelliteType.CONSTELLATION_REWARD)
                    .withStyle(ChatFormatting.AQUA));
        }
        if (points > 0) {
            research.addPoints(points);
            ResearchManager.changed(server, agencyKey);
        }
        return new Deployment(satellite, points, messages);
    }

    /**
     * Reçoit au sol les données en mémoire des satellites d'une agence et les convertit en points de recherche.
     *
     * @return les points de recherche gagnés
     */
    public static int collect(MinecraftServer server, String agencyKey) {
        int points = of(server, agencyKey).collect(now(server), limited());
        if (points > 0) {
            changed(server);
            ResearchManager.forAgency(server, agencyKey).addPoints(points);
            ResearchManager.changed(server, agencyKey);
        }
        return points;
    }

    /** Régulièrement : les agences qui ont un satellite de télécommunications reçoivent leurs données sans rien faire. */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        // Les observations avancent, toutes les secondes.
        if (server.getTickCount() % Observatory.STEP == 0) {
            Observatory.tick(server, data(server).agencies(), now(server), limited());
        }
        if (server.getTickCount() % RELAY_INTERVAL != 0) {
            return;
        }
        long now = now(server);
        boolean limited = limited();
        for (Map.Entry<String, AgencySatellites> agency : data(server).agencies().entrySet()) {
            if (agency.getValue().hasRelay(now, limited) && agency.getValue().pending(now, limited) > 0) {
                collect(server, agency.getKey());
            }
        }
    }

    // ------------------------------------------------------------------
    // Station de poursuite
    // ------------------------------------------------------------------

    /** Vrai si l'antenne de la station voit le ciel. */
    public static boolean isSkyClear(ServerLevel level, BlockPos station) {
        return level.canSeeSky(station.above());
    }

    /** État des satellites de l'agence d'un joueur, vu depuis une station de poursuite. */
    public static SatelliteStatusPayload status(ServerPlayer player, BlockPos station, boolean open) {
        MinecraftServer server = player.level().getServer();
        AgencySatellites satellites = of(server, ResearchManager.agencyKey(player));
        long now = now(server);
        boolean limited = limited();

        List<SatelliteStatusPayload.Entry> entries = new ArrayList<>();
        for (Satellite satellite : satellites.all()) {
            long remaining = !limited ? -1 : Math.max(0, AgencySatellites.endOfLife(satellite, true) - now);
            entries.add(new SatelliteStatusPayload.Entry(satellite.number(), satellite.type().ordinal(), now - satellite.launchTime(),
                    remaining, AgencySatellites.pending(satellite, now, limited)));
        }

        int[] forecast = forecast(server, ResearchManager.agencyKey(player));
        return new SatelliteStatusPayload(station, open, isSkyClear(player.level(), station), satellites.hasRelay(now, limited),
                satellites.active(SatelliteType.NAVIGATION, now, limited), forecast[0], forecast[1], entries);
    }

    /**
     * Prévision météo d'une agence : elle n'en a une que si l'un de ses satellites météo est en service.
     *
     * @return le temps qu'il fait ({@code SatelliteStatusPayload.FORECAST_*}), et la durée pendant laquelle il doit
     *         encore durer, en ticks
     */
    public static int[] forecast(MinecraftServer server, String agencyKey) {
        if (of(server, agencyKey).active(SatelliteType.WEATHER, now(server), limited()) <= 0) {
            return new int[] {SatelliteStatusPayload.FORECAST_NONE, 0};
        }
        WeatherData weather = server.getWeatherData();
        if (weather.isThundering()) {
            return new int[] {SatelliteStatusPayload.FORECAST_THUNDER, Math.max(0, weather.getThunderTime())};
        }
        if (weather.isRaining()) {
            return new int[] {SatelliteStatusPayload.FORECAST_RAIN, Math.max(0, weather.getRainTime())};
        }
        // Par beau temps, le jeu compte soit la durée du beau temps, soit le délai avant la prochaine pluie.
        return new int[] {SatelliteStatusPayload.FORECAST_CLEAR,
                Math.max(0, weather.getClearWeatherTime() > 0 ? weather.getClearWeatherTime() : weather.getRainTime())};
    }

    /** Vrai si un satellite de télécommunications de cette agence est en service : il relaie aussi les pupitres. */
    public static boolean hasRelay(MinecraftServer server, String agencyKey) {
        return of(server, agencyKey).hasRelay(now(server), limited());
    }

    /**
     * Relevé de prospection : les satellites d'observation de l'agence comptent les minerais du sous-sol autour de
     * la station, tronçon par tronçon. Un satellite voit un carré de cinq tronçons de côté, deux en voient sept,
     * trois ou plus en voient neuf. Seul le terrain présent en mémoire peut être relevé.
     */
    public static com.orbitalis.core.network.ProspectPayload prospect(ServerPlayer player, BlockPos station) {
        MinecraftServer server = player.level().getServer();
        int kinds = ProspectOre.values().length;
        int observers = of(server, ResearchManager.agencyKey(player)).active(SatelliteType.OBSERVATION, now(server), limited());
        if (observers <= 0) {
            return new com.orbitalis.core.network.ProspectPayload(station, 0, 0, 0, kinds, new int[0]);
        }
        int radius = Math.min(4, 1 + observers);
        int size = 2 * radius + 1;
        int originX = (station.getX() >> 4) - radius;
        int originZ = (station.getZ() >> 4) - radius;
        int[] counts = new int[size * size * kinds];
        ServerLevel level = player.level();
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                net.minecraft.world.level.chunk.LevelChunk chunk = level.getChunkSource().getChunkNow(originX + column, originZ + row);
                if (chunk == null) {
                    continue;
                }
                int base = (row * size + column) * kinds;
                for (int kind = 0; kind < kinds; kind++) {
                    counts[base + kind] = 1;
                }
                for (net.minecraft.world.level.chunk.LevelChunkSection section : chunk.getSections()) {
                    // La plupart des tranches du monde n'ont aucun minerai : leur palette le dit sans les parcourir.
                    if (section.hasOnlyAir() || !section.maybeHas(state -> ProspectOre.of(state) != null)) {
                        continue;
                    }
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            for (int x = 0; x < 16; x++) {
                                ProspectOre ore = ProspectOre.of(section.getBlockState(x, y, z));
                                if (ore != null) {
                                    counts[base + ore.ordinal()]++;
                                }
                            }
                        }
                    }
                }
            }
        }
        return new com.orbitalis.core.network.ProspectPayload(station, size, originX, originZ, kinds, counts);
    }

    /** Demande venue de l'écran d'une station de poursuite. */
    public static void handleCommand(ServerPlayer player, SatelliteCommandPayload payload) {
        BlockPos station = payload.station();
        ServerLevel level = player.level();
        if (!level.isLoaded(station) || !level.getBlockState(station).is(ModBlocks.TRACKING_STATION.get())
                || !player.blockPosition().closerThan(station, STATION_REACH)) {
            return;
        }
        if (payload.action() == SatelliteCommandPayload.DOWNLOAD && isSkyClear(level, station)) {
            int points = collect(level.getServer(), ResearchManager.agencyKey(player));
            player.sendSystemMessage(points > 0
                    ? Component.translatable("satellite.orbitalis.station.downloaded", points).withStyle(ChatFormatting.GREEN)
                    : Component.translatable("satellite.orbitalis.station.nothing").withStyle(ChatFormatting.GRAY));
        }
        // L'observatoire et la prospection demandent les grandes antennes des stations de suivi.
        boolean tracking = player.getAbilities().instabuild || ResearchManager.isUnlocked(player, TRACKING_STATIONS);
        if (!tracking && (payload.action() == SatelliteCommandPayload.PROSPECT || payload.action() >= SatelliteCommandPayload.OBSERVE)) {
            player.sendSystemMessage(Component.translatable("spaceport.orbitalis.research_required",
                    Component.translatable("research.orbitalis.node." + TRACKING_STATIONS)).withStyle(ChatFormatting.RED));
        }
        if (tracking && payload.action() == SatelliteCommandPayload.PROSPECT) {
            PacketDistributor.sendToPlayer(player, prospect(player, station));
        }
        // L'observatoire : pointer les instruments, recevoir une image ; dans tous les cas, son état est renvoyé.
        CelestialBody[] bodies = CelestialBody.values();
        int action = payload.action();
        if (tracking && action >= SatelliteCommandPayload.OBSERVE && action < SatelliteCommandPayload.OBSERVE + bodies.length) {
            Observatory.observe(player, bodies[action - SatelliteCommandPayload.OBSERVE]);
        }
        if (tracking && action >= SatelliteCommandPayload.RECEIVE && action < SatelliteCommandPayload.RECEIVE + bodies.length) {
            Observatory.receive(player, station, bodies[action - SatelliteCommandPayload.RECEIVE]);
        }
        if (action == SatelliteCommandPayload.OBSERVATORY || action >= SatelliteCommandPayload.OBSERVE) {
            PacketDistributor.sendToPlayer(player, Observatory.status(player, station));
        }
        PacketDistributor.sendToPlayer(player, status(player, station, false));
    }
}
