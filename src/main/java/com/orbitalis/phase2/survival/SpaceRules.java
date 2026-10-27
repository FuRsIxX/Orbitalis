package com.orbitalis.phase2.survival;

import com.orbitalis.Orbitalis;
import com.orbitalis.core.network.SuitStatusPayload;
import com.orbitalis.core.research.AgencyResearch;
import com.orbitalis.core.research.ResearchManager;
import com.orbitalis.phase2.flight.CapsuleEntity;
import net.minecraft.core.BlockPos;
import com.orbitalis.phase2.station.StationAir;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Règles de l'espace : dans la dimension d'orbite, il n'y a ni pesanteur ni air.
 * <ul>
 * <li>Apesanteur : tout joueur y flotte et s'y déplace comme dans l'eau (voir {@link Microgravity}).</li>
 * <li>Vide : hors de sa capsule, il faut une combinaison spatiale complète (casque, torse et jambes, voir
 * {@link SpaceSuits}), dont la réserve d'oxygène s'épuise. Sans combinaison, avec une combinaison incomplète, ou
 * réserve vide, le joueur suffoque.</li>
 * <li>La combinaison se recharge dans la capsule, et toute seule une fois revenu sur la planète.</li>
 * </ul>
 */
@EventBusSubscriber(modid = Orbitalis.MODID)
public final class SpaceRules {
    /** Dimension d'orbite : un vide noir autour de la planète, où arrivent les capsules. */
    public static final ResourceKey<Level> ORBIT = ResourceKey.create(Registries.DIMENSION, Orbitalis.id("orbit"));
    /** L'orbite de Proxima b, à 4,2 années-lumière : le vide, comme l'orbite de la planète, sous une naine rouge. */
    public static final ResourceKey<Level> PROXIMA_ORBIT = ResourceKey.create(Registries.DIMENSION, Orbitalis.id("proxima_orbit"));
    /** Proxima b : une planète rocheuse un peu plus massive que la Terre, à l'air irrespirable. */
    public static final ResourceKey<Level> PROXIMA_B = ResourceKey.create(Registries.DIMENSION, Orbitalis.id("proxima_b"));
    public static final double PROXIMA_GRAVITY = 1.1;
    private static final int REWARD_PROXIMAWALK = 2500;
    /** Hauteur à laquelle les capsules arrivent dans la dimension d'orbite. */
    public static final double PARKING_HEIGHT = 100.0;

    /** Réserve d'oxygène d'une combinaison orange pleine, en secondes. */
    public static final int SUIT_OXYGEN = 600;
    /** Recharge de la combinaison, en secondes d'oxygène par seconde : dans la capsule, puis à l'air libre. */
    private static final int CAPSULE_REFILL = 10;
    private static final int PLANET_REFILL = 20;
    /** Dégâts par seconde dans le vide sans oxygène. */
    private static final float VACUUM_DAMAGE = 4.0F;
    /** Les dégâts du vide, sans combinaison complète ou sans oxygène (data/orbitalis/damage_type/vacuum.json). */
    public static final net.minecraft.resources.ResourceKey<net.minecraft.world.damagesource.DamageType> VACUUM =
            net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DAMAGE_TYPE, com.orbitalis.Orbitalis.id("vacuum"));
    /** Prime de recherche de la première sortie dans l'espace d'une agence. */
    private static final int REWARD_SPACEWALK = 100;

    private static final Identifier MICROGRAVITY = Orbitalis.id("microgravity");
    private static final Identifier ZERO_GRAVITY = Orbitalis.id("zero_gravity");
    private static final Identifier LOW_GRAVITY = Orbitalis.id("low_gravity");
    /** Pesanteur à la surface de la Lune, en part de celle de la planète. */
    public static final double MOON_GRAVITY = 0.165;
    /** Prime de recherche des premiers pas sur la Lune d'une agence. */
    private static final int REWARD_MOONWALK = 300;

    private SpaceRules() {
    }

    public static boolean isOrbit(Level level) {
        return level.dimension() == ORBIT || level.dimension() == PROXIMA_ORBIT;
    }

    /** Dimension de la Lune : un sol de régolithe, un sixième de la pesanteur, pas d'air. */
    public static final ResourceKey<Level> MOON = ResourceKey.create(Registries.DIMENSION, Orbitalis.id("moon"));

    public static boolean isMoon(Level level) {
        return level.dimension() == MOON;
    }

    /** Dimension de Mars : une poussière rouille sous un ciel ocre, 38 % de la pesanteur, un air trop mince pour respirer. */
    public static final ResourceKey<Level> MARS = ResourceKey.create(Registries.DIMENSION, Orbitalis.id("mars"));
    /** Pesanteur à la surface de Mars, en part de celle de la planète. */
    public static final double MARS_GRAVITY = 0.38;
    /** Prime de recherche des premiers pas sur Mars d'une agence. */
    private static final int REWARD_MARSWALK = 500;

    public static boolean isMars(Level level) {
        return level.dimension() == MARS;
    }

    /**
     * Dimension de Titan, la grande lune de Saturne : des dunes de tholines et des lacs de méthane sous une brume orange,
     * 14 % de la pesanteur, un air d'azote froid et quatre fois plus dense que celui de la planète.
     */
    public static final ResourceKey<Level> TITAN = ResourceKey.create(Registries.DIMENSION, Orbitalis.id("titan"));
    /** Pesanteur à la surface de Titan, en part de celle de la planète. */
    public static final double TITAN_GRAVITY = 0.14;
    /** Prime de recherche des premiers pas sur Titan d'une agence. */
    private static final int REWARD_TITANWALK = 700;

    public static boolean isTitan(Level level) {
        return level.dimension() == TITAN;
    }

    /**
     * Dimension d'Europe, la lune glacée de Jupiter : une banquise sans air sur un océan caché, 13 % de la pesanteur,
     * -160 °C, et les radiations de Jupiter.
     */
    public static final ResourceKey<Level> EUROPA = ResourceKey.create(Registries.DIMENSION, Orbitalis.id("europa"));
    /** Pesanteur à la surface d'Europe, en part de celle de la planète. */
    public static final double EUROPA_GRAVITY = 0.134;
    /** Prime de recherche des premiers pas sur Europe d'une agence. */
    private static final int REWARD_EUROPAWALK = 800;
    /** Dégâts des radiations de Jupiter, par seconde, sans blindage. */
    private static final float RADIATION_DAMAGE = 1.5F;

    public static boolean isEuropa(Level level) {
        return level.dimension() == EUROPA;
    }

    /** Vrai en orbite de Proxima b, loin du système solaire. */
    public static boolean isProximaOrbit(Level level) {
        return level.dimension() == PROXIMA_ORBIT;
    }

    /** Vrai sur Proxima b. */
    public static boolean isProxima(Level level) {
        return level.dimension() == PROXIMA_B;
    }

    /** Vrai si l'on ne peut pas respirer dans ce monde sans combinaison : le vide, l'air de Mars ou celui de Titan. */
    public static boolean isUnbreathable(Level level) {
        return isVacuum(level) || isMars(level) || isTitan(level) || isProxima(level);
    }

    /** Pesanteur de ce monde, en part de celle de la planète. */
    public static double gravityOf(Level level) {
        return isMoon(level) ? MOON_GRAVITY : isMars(level) ? MARS_GRAVITY : isTitan(level) ? TITAN_GRAVITY
                : isEuropa(level) ? EUROPA_GRAVITY : isProxima(level) ? PROXIMA_GRAVITY : 1.0;
    }

    /** Vrai s'il n'y a pas d'air dans ce monde : en orbite, sur la Lune et sur Europe. */
    public static boolean isVacuum(Level level) {
        return isOrbit(level) || isMoon(level) || isEuropa(level);
    }

    // ------------------------------------------------------------------
    // Combinaison spatiale : sa réserve d'oxygène est son « usure »
    // ------------------------------------------------------------------

    /** Vrai pour le torse d'une combinaison : celui qui porte le sac de survie et l'oxygène. */
    public static boolean isSuit(ItemStack stack) {
        return SpaceSuits.isPiece(stack, EquipmentSlot.CHEST);
    }

    /** Oxygène restant dans une combinaison, en secondes. */
    public static int oxygen(ItemStack suit) {
        return isSuit(suit) ? Math.max(0, suit.getMaxDamage() - suit.getDamageValue()) : 0;
    }

    /** Réserve d'une combinaison pleine, en secondes : selon le modèle, et son réservoir supplémentaire. */
    public static int capacity(ItemStack suit) {
        return isSuit(suit) ? suit.getMaxDamage() : 0;
    }

    /** Ajoute (ou retire, si la quantité est négative) de l'oxygène à une combinaison, sans dépasser sa réserve. */
    public static void addOxygen(ItemStack suit, int seconds) {
        if (isSuit(suit)) {
            suit.setDamageValue(Math.clamp(suit.getDamageValue() - seconds, 0, suit.getMaxDamage()));
        }
    }

    // ------------------------------------------------------------------
    // À chaque tick, pour chaque joueur
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        boolean inOrbit = isOrbit(player.level());
        boolean onMoon = isMoon(player.level());
        boolean onMars = isMars(player.level());
        boolean onTitan = isTitan(player.level());
        boolean onEuropa = isEuropa(player.level());
        boolean onProxima = isProxima(player.level());
        updateMicrogravity(player, inOrbit);
        updateLowGravity(player, gravityOf(player.level()));
        if (player.tickCount % 20 != 0) {
            return;
        }
        ItemStack suit = player.getItemBySlot(EquipmentSlot.CHEST);
        boolean outdoors = inOrbit || onMoon || onMars || onTitan || onEuropa || onProxima;
        // La capsule est pressurisée, comme les pièces d'une station dont le générateur d'air tourne.
        boolean sheltered = outdoors && (player.getVehicle() instanceof CapsuleEntity
                || StationAir.isBreathable(player.level(), BlockPos.containing(player.getEyePosition())));
        // Les capteurs du casque : son affichage dit si l'on respire dehors ou sur la réserve. Sans casque, l'ambiance
        // des pièces pressurisées s'en sert aussi.
        PacketDistributor.sendToPlayer(player, new SuitStatusPayload(!outdoors || sheltered));
        if (!outdoors) {
            // Sur la planète, la combinaison refait le plein toute seule.
            addOxygen(suit, PLANET_REFILL);
            return;
        }
        if (sheltered) {
            // On y respire, et la combinaison s'y recharge.
            addOxygen(suit, CAPSULE_REFILL);
            return;
        }
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        // Les radiations de Jupiter : sur Europe, hors d'un abri, seul le blindage de la combinaison en protège.
        boolean irradiated = onEuropa && !SpaceSuits.has(player, SuitModule.RADIATION_SHIELD);
        if (irradiated) {
            player.hurtServer(player.level(), player.damageSources().magic(), RADIATION_DAMAGE);
        }
        EquipmentSlot missing = SpaceSuits.missing(player);
        if (missing == null && oxygen(suit) > 0) {
            addOxygen(suit, -1);
            int left = oxygen(suit);
            // L'affichage du casque tient le compte ; la dernière minute, une alerte toutes les dix secondes.
            if (irradiated) {
                player.sendOverlayMessage(Component.translatable("space.orbitalis.radiation").withStyle(ChatFormatting.RED));
            } else if (left <= 60 && left % 10 == 0) {
                player.sendOverlayMessage(Component.translatable("space.orbitalis.oxygen", left / 60, String.format("%02d", left % 60))
                        .withStyle(ChatFormatting.RED));
            }
            if (onMoon) {
                rewardMoonwalk(player);
            } else if (onMars) {
                rewardMarswalk(player);
            } else if (onTitan) {
                rewardTitanwalk(player);
            } else if (onEuropa) {
                rewardEuropawalk(player);
            } else if (onProxima) {
                rewardProximawalk(player);
            } else {
                rewardSpacewalk(player);
            }
        } else {
            Component warning = !isSuit(suit) ? Component.translatable("space.orbitalis.no_suit")
                    : missing != null ? Component.translatable("space.orbitalis.suit_open", Component.translatable("suit.orbitalis.piece." + missing.getName()))
                    : Component.translatable("space.orbitalis.oxygen_empty");
            player.sendOverlayMessage(warning.copy().withStyle(ChatFormatting.RED));
            // Les dégâts du vide ont leur propre type : la blessure ordinaire, et non le bruit de noyade.
            player.hurtServer(player.level(), new net.minecraft.world.damagesource.DamageSource(player.level().registryAccess()
                    .lookupOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).getOrThrow(VACUUM)), VACUUM_DAMAGE);
        }
    }

    /**
     * En orbite, le joueur flotte : plus de pesanteur, et il nage comme dans l'eau (son client calcule la nage). Le
     * droit de voler lui est laissé, mais jamais le vol lui-même : sans lui, un serveur prendrait ce joueur qui ne
     * touche plus rien pour un tricheur et l'expulserait. Ailleurs, il retrouve la pesanteur.
     */
    private static void updateMicrogravity(ServerPlayer player, boolean inOrbit) {
        AttributeInstance flight = player.getAttribute(NeoForgeMod.CREATIVE_FLIGHT);
        AttributeInstance gravity = player.getAttribute(Attributes.GRAVITY);
        if (flight == null || gravity == null) {
            return;
        }
        boolean floating = inOrbit && !player.isCreative() && !player.isSpectator();
        if (floating) {
            if (!flight.hasModifier(MICROGRAVITY)) {
                flight.addTransientModifier(new AttributeModifier(MICROGRAVITY, 1.0, AttributeModifier.Operation.ADD_VALUE));
            }
            if (!gravity.hasModifier(ZERO_GRAVITY)) {
                gravity.addTransientModifier(new AttributeModifier(ZERO_GRAVITY, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
            if (player.getAbilities().flying) {
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
            }
            player.setForcedPose(Microgravity.applies(player) ? Microgravity.pose(player) : null);
            player.resetFallDistance();
            return;
        }
        gravity.removeModifier(ZERO_GRAVITY);
        if (flight.hasModifier(MICROGRAVITY)) {
            flight.removeModifier(MICROGRAVITY);
            player.setForcedPose(null);
            if (!player.getAbilities().mayfly && player.getAbilities().flying) {
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
            }
        }
    }

    /**
     * Sur la Lune et sur Mars, la pesanteur est plus faible que sur la planète : on saute plus haut, on retombe
     * lentement, et une chute de quelques blocs ne blesse pas. D'autant plus que le monde est léger.
     */
    private static void updateLowGravity(ServerPlayer player, double share) {
        AttributeInstance gravity = player.getAttribute(Attributes.GRAVITY);
        AttributeInstance safeFall = player.getAttribute(Attributes.SAFE_FALL_DISTANCE);
        if (gravity == null || safeFall == null) {
            return;
        }
        AttributeModifier current = gravity.getModifier(LOW_GRAVITY);
        boolean light = share < 1.0;
        // Rien à faire si le joueur porte déjà la pesanteur de ce monde.
        if (light ? current != null && Math.abs(current.amount() - (share - 1.0)) < 1.0E-6 : current == null) {
            return;
        }
        gravity.removeModifier(LOW_GRAVITY);
        safeFall.removeModifier(LOW_GRAVITY);
        if (light) {
            gravity.addTransientModifier(new AttributeModifier(LOW_GRAVITY, share - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            safeFall.addTransientModifier(new AttributeModifier(LOW_GRAVITY, 3.0 / share - 3.0, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    /** Premiers pas sur Proxima b d'une agence : le premier monde d'une autre étoile, hors du vaisseau, en combinaison. */
    private static void rewardProximawalk(ServerPlayer player) {
        AgencyResearch research = ResearchManager.of(player);
        if (research.completeMilestone("first_proximawalk")) {
            research.addPoints(REWARD_PROXIMAWALK);
            ResearchManager.changed(player);
            player.sendSystemMessage(Component.translatable("space.orbitalis.first_proximawalk", REWARD_PROXIMAWALK).withStyle(ChatFormatting.AQUA));
        }
    }

    /** Premiers pas sur Europe d'une agence : hors du vaisseau, en combinaison. */
    private static void rewardEuropawalk(ServerPlayer player) {
        AgencyResearch research = ResearchManager.of(player);
        if (research.completeMilestone("first_europawalk")) {
            research.addPoints(REWARD_EUROPAWALK);
            ResearchManager.changed(player);
            player.sendSystemMessage(Component.translatable("space.orbitalis.first_europawalk", REWARD_EUROPAWALK).withStyle(ChatFormatting.AQUA));
        }
    }

    /** Premiers pas sur Titan d'une agence : hors du vaisseau, en combinaison. */
    private static void rewardTitanwalk(ServerPlayer player) {
        AgencyResearch research = ResearchManager.of(player);
        if (research.completeMilestone("first_titanwalk")) {
            research.addPoints(REWARD_TITANWALK);
            ResearchManager.changed(player);
            player.sendSystemMessage(Component.translatable("space.orbitalis.first_titanwalk", REWARD_TITANWALK).withStyle(ChatFormatting.AQUA));
        }
    }

    /** Premiers pas sur Mars d'une agence : hors du vaisseau, en combinaison. */
    private static void rewardMarswalk(ServerPlayer player) {
        AgencyResearch research = ResearchManager.of(player);
        if (research.completeMilestone("first_marswalk")) {
            research.addPoints(REWARD_MARSWALK);
            ResearchManager.changed(player);
            player.sendSystemMessage(Component.translatable("space.orbitalis.first_marswalk", REWARD_MARSWALK).withStyle(ChatFormatting.AQUA));
        }
    }

    /** Premiers pas sur la Lune d'une agence : hors du vaisseau, en combinaison. */
    private static void rewardMoonwalk(ServerPlayer player) {
        AgencyResearch research = ResearchManager.of(player);
        if (research.completeMilestone("first_moonwalk")) {
            research.addPoints(REWARD_MOONWALK);
            ResearchManager.changed(player);
            player.sendSystemMessage(Component.translatable("space.orbitalis.first_moonwalk", REWARD_MOONWALK).withStyle(ChatFormatting.AQUA));
        }
    }

    /** Première sortie dans l'espace d'une agence : hors de la capsule, en combinaison. */
    private static void rewardSpacewalk(ServerPlayer player) {
        AgencyResearch research = ResearchManager.of(player);
        if (research.completeMilestone("first_spacewalk")) {
            research.addPoints(REWARD_SPACEWALK);
            ResearchManager.changed(player);
            player.sendSystemMessage(Component.translatable("space.orbitalis.first_spacewalk", REWARD_SPACEWALK).withStyle(ChatFormatting.AQUA));
        }
    }
}
