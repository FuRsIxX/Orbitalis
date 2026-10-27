package com.orbitalis.core.research;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import com.orbitalis.Orbitalis;
import com.orbitalis.core.network.ResearchSyncPayload;
import com.orbitalis.phase1.rocket.RocketStats;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.scores.PlayerTeam;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Recherche côté serveur : retrouve l'agence d'un joueur, applique les changements,
 * les enregistre et les envoie aux joueurs concernés.
 * <p>
 * Une agence correspond à une équipe du jeu ({@code /team}) : tous ses membres partagent les mêmes points
 * et les mêmes technologies. Un joueur sans équipe a sa propre agence.
 */
@EventBusSubscriber(modid = Orbitalis.MODID)
public final class ResearchManager {
    /** Intervalle entre deux vérifications des jalons et de l'agence d'un joueur, en ticks. */
    private static final int CHECK_INTERVAL = 40;
    /** Points gagnés à chaque vol qui emporte une charge utile scientifique dans l'espace (données de haute altitude). */
    private static final int PAYLOAD_DATA_POINTS = 5;
    /** La télémétrie, et les points que rapportent les mesures d'un vol qui atteint l'espace. */
    public static final String TELEMETRY = "telemetry";
    private static final int TELEMETRY_POINTS = 3;

    /** Dernière agence connue de chaque joueur connecté, pour détecter un changement d'équipe. */
    private static final Map<UUID, String> KNOWN_AGENCY = new ConcurrentHashMap<>();

    /**
     * Recherche connue du client. Renseigné par le client au démarrage : cette classe est aussi chargée
     * sur un serveur dédié, où cette copie n'existe pas.
     */
    public static Supplier<AgencyResearch> clientView = AgencyResearch::new;

    private ResearchManager() {
    }

    /** Clé de l'agence d'un joueur : son équipe s'il en a une, sinon lui-même. */
    public static String agencyKey(ServerPlayer player) {
        PlayerTeam team = player.getTeam();
        return team != null ? "team:" + team.getName() : "player:" + player.getUUID();
    }

    private static ResearchSavedData data(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(ResearchSavedData.TYPE);
    }

    /** Recherche de l'agence d'un joueur. Après une modification, appeler {@link #changed}. */
    public static AgencyResearch of(ServerPlayer player) {
        return data(player.level().getServer()).get(agencyKey(player));
    }

    /** À appeler après toute modification : enregistre et renvoie l'état à tous les membres de l'agence. */
    public static void changed(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        data(server).setDirty();
        String key = agencyKey(player);
        for (ServerPlayer member : server.getPlayerList().getPlayers()) {
            if (agencyKey(member).equals(key)) {
                sync(member);
            }
        }
    }

    /** Envoie à un joueur l'état de la recherche de son agence. */
    public static void sync(ServerPlayer player) {
        AgencyResearch research = of(player);
        // Les études de l'établi d'ingénieur voyagent avec les technologies : le client les lit de la même façon.
        List<String> known = new java.util.ArrayList<>(research.unlocked());
        for (String milestone : research.milestones()) {
            // Les autres jalons sont les objectifs atteints : l'arbre de compétences les montre.
            known.add(milestone.startsWith(EngineeringStudies.SAVED_PREFIX) ? milestone : ResearchGoals.SAVED_PREFIX + milestone);
        }
        com.orbitalis.core.network.ModNetwork.send(player, new ResearchSyncPayload(research.points(), known));
    }

    /** Ajoute des points de recherche à l'agence d'un joueur. */
    public static void addPoints(ServerPlayer player, int amount) {
        of(player).addPoints(amount);
        changed(player);
    }

    /** Recherche d'une agence désignée par sa clé (utile quand aucun joueur n'est sous la main). */
    public static AgencyResearch forAgency(MinecraftServer server, String agencyKey) {
        return data(server).get(agencyKey);
    }

    /** Enregistre la recherche d'une agence et la renvoie à tous ses membres connectés. */
    public static void changed(MinecraftServer server, String agencyKey) {
        data(server).setDirty();
        for (ServerPlayer member : server.getPlayerList().getPlayers()) {
            if (agencyKey(member).equals(agencyKey)) {
                sync(member);
            }
        }
    }

    /**
     * Récompense un vol réussi : une prime la première fois que l'agence atteint chaque palier
     * (vol atmosphérique, espace, orbite...), plus quelques points à chaque vol qui emporte une charge utile
     * scientifique jusque dans l'espace.
     *
     * @return les points de recherche gagnés
     */
    public static int awardFlight(MinecraftServer server, String agencyKey, RocketStats.Reach reach, boolean hasPayload) {
        AgencyResearch research = forAgency(server, agencyKey);
        int points = 0;
        for (RocketStats.Reach step : RocketStats.Reach.values()) {
            int reward = firstFlightReward(step);
            if (step.ordinal() <= reach.ordinal() && reward > 0 && research.completeMilestone("flight_" + step.name().toLowerCase(Locale.ROOT))) {
                points += reward;
            }
        }
        if (hasPayload && reach.ordinal() >= RocketStats.Reach.SUBORBITAL.ordinal()) {
            points += PAYLOAD_DATA_POINTS;
        }
        // Télémétrie : la fusée transmet ses mesures au sol ; tout vol qui atteint l'espace en rapporte.
        if (research.isUnlocked(TELEMETRY) && reach.ordinal() >= RocketStats.Reach.SUBORBITAL.ordinal()) {
            points += TELEMETRY_POINTS;
        }
        research.addFlight();
        research.addPoints(points);
        changed(server, agencyKey);
        return points;
    }

    /** Prime de recherche la première fois qu'une agence atteint ce palier (voir {@link ResearchGoals}). */
    private static int firstFlightReward(RocketStats.Reach reach) {
        return ResearchGoals.flightReward(reach);
    }

    /** Tente de rechercher une technologie pour l'agence d'un joueur. */
    public static AgencyResearch.UnlockResult unlock(ServerPlayer player, String nodeId) {
        AgencyResearch.UnlockResult result = of(player).unlock(nodeId);
        if (result == AgencyResearch.UnlockResult.SUCCESS) {
            changed(player);
        }
        return result;
    }

    /**
     * Vrai si l'agence du joueur maîtrise cette technologie. Sert à conditionner le contenu du mod.
     * Côté serveur, la réponse vient de la sauvegarde ; côté client, de la copie envoyée par le serveur.
     */
    /**
     * Vrai si ce joueur peut poser le bloc que demande cette technologie. Sinon, il est prévenu ; le serveur seul
     * décide (le client laisse faire, et le bloc refusé disparaît aussitôt). Le mode créatif passe toujours.
     */
    public static boolean mayPlace(net.minecraft.world.item.context.BlockPlaceContext context, String nodeId) {
        Player player = context.getPlayer();
        if (player == null || player.getAbilities().instabuild || context.getLevel().isClientSide() || isUnlocked(player, nodeId)) {
            return true;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("spaceport.orbitalis.research_required",
                    net.minecraft.network.chat.Component.translatable("research.orbitalis.node." + nodeId)).withStyle(net.minecraft.ChatFormatting.RED));
        }
        return false;
    }

    public static boolean isUnlocked(Player player, String nodeId) {
        if (player instanceof ServerPlayer serverPlayer) {
            return of(serverPlayer).isUnlocked(nodeId);
        }
        return clientView.get().isUnlocked(nodeId);
    }

    // ------------------------------------------------------------------
    // Événements
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            KNOWN_AGENCY.put(player.getUUID(), agencyKey(player));
            sync(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        KNOWN_AGENCY.remove(event.getEntity().getUUID());
    }

    /** Régulièrement : récompense les jalons atteints, et renvoie l'état si le joueur a changé d'équipe. */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % CHECK_INTERVAL != 0) {
            return;
        }
        String key = agencyKey(player);
        if (!key.equals(KNOWN_AGENCY.put(player.getUUID(), key))) {
            sync(player);
        }
        checkMilestones(player);
    }

    /** Un jalon est atteint quand un membre de l'agence a pour la première fois l'objet dans son inventaire. */
    private static void checkMilestones(ServerPlayer player) {
        AgencyResearch research = of(player);
        for (ResearchMilestones.Milestone milestone : ResearchMilestones.ALL) {
            if (research.hasMilestone(milestone.id())) {
                continue;
            }
            Item item = milestone.item().get().asItem();
            if (player.getInventory().hasAnyMatching(stack -> stack.is(item))) {
                research.completeMilestone(milestone.id());
                research.addPoints(milestone.points());
                changed(player);
                player.sendSystemMessage(Component.translatable("research.orbitalis.milestone",
                        item.getName(item.getDefaultInstance()), milestone.points()));
            }
        }
    }
}
