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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import com.orbitalis.platform.event.PlayerTickEvent;

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
public final class SpaceRules {
    /** Dimension d'orbite : un vide noir autour de la planète, où arrivent les capsules. */
    public static final ResourceKey<Level> ORBIT = ResourceKey.create(Registries.DIMENSION, Orbitalis.id("orbit"));
    /** L'orbite de Proxima b, à 4,2 années-lumière : le vide, comme l'orbite de la planète, sous une naine rouge. */
    public static final ResourceKey<Level> PROXIMA_ORBIT = ResourceKey.create(Registries.DIMENSION, Orbitalis.id("proxima_orbit"));
    /** Proxima b : une planète rocheuse un peu plus massive que la Terre, à l'air irrespirable. */
    public static final ResourceKey<Level> PROXIMA_B = ResourceKey.create(Registries.DIMENSION, Orbitalis.id("proxima_b"));
    public static final double PROXIMA_GRAVITY = 1.1;
    private static final int REWARD_PROXIMAWALK = 2500;
    /** L'orbite de TRAPPIST-1 e, à 39 années-lumière : le vide, sous une petite étoile rouge et ses six autres planètes. */
    public static final ResourceKey<Level> TRAPPIST_ORBIT = ResourceKey.create(Registries.DIMENSION, Orbitalis.id("trappist_orbit"));
    /** TRAPPIST-1 e : un monde-océan un peu plus petit que la Terre, semé d'îles, à l'air irrespirable. */
    public static final ResourceKey<Level> TRAPPIST_1E = ResourceKey.create(Registries.DIMENSION, Orbitalis.id("trappist_1e"));
    public static final double TRAPPIST_GRAVITY = 0.93;
    private static final int REWARD_TRAPPISTWALK = 3000;
    /** L'orbite de Kepler-452b, à 1 400 années-lumière, sous une étoile semblable au Soleil. */
    public static final ResourceKey<Level> KEPLER_ORBIT = ResourceKey.create(Registries.DIMENSION, Orbitalis.id("kepler_orbit"));
    /** Kepler-452b : une super-Terre de mers et de plaines, une fois et demie plus pesante, à l'air irrespirable. */
    public static final ResourceKey<Level> KEPLER_452B = ResourceKey.create(Registries.DIMENSION, Orbitalis.id("kepler_452b"));
    public static final double KEPLER_GRAVITY = 1.5;
    private static final int REWARD_KEPLERWALK = 3500;
    /** L'orbite de Tarentule b, dans le Grand Nuage de Magellan, à 160 000 années-lumière : hors de notre Galaxie. */
    public static final ResourceKey<Level> TARANTULA_ORBIT = ResourceKey.create(Registries.DIMENSION, Orbitalis.id("tarantula_orbit"));
    /** Tarentule b : une planète de poussière rose et de cristaux, sous un air mince, au bord de la Tarentule. */
    public static final ResourceKey<Level> TARANTULA_B = ResourceKey.create(Registries.DIMENSION, Orbitalis.id("tarantula_b"));
    public static final double TARANTULA_GRAVITY = 0.8;
    private static final int REWARD_TARANTULAWALK = 5000;
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

    private static final ResourceLocation MICROGRAVITY = Orbitalis.id("microgravity");
    /** Les joueurs à qui l'apesanteur a prêté le droit de voler. */
    private static final java.util.Set<java.util.UUID> FLOAT_GRANTED = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final ResourceLocation ZERO_GRAVITY = Orbitalis.id("zero_gravity");
    private static final ResourceLocation LOW_GRAVITY = Orbitalis.id("low_gravity");
    /** Pesanteur à la surface de la Lune, en part de celle de la planète. */
    public static final double MOON_GRAVITY = 0.165;
    /** Prime de recherche des premiers pas sur la Lune d'une agence. */
    private static final int REWARD_MOONWALK = 300;

    private SpaceRules() {
    }

    public static boolean isOrbit(Level level) {
        return level.dimension() == ORBIT || level.dimension() == PROXIMA_ORBIT || level.dimension() == TRAPPIST_ORBIT
                || level.dimension() == KEPLER_ORBIT || level.dimension() == TARANTULA_ORBIT;
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

    /** Vrai en orbite de TRAPPIST-1 e. */
    public static boolean isTrappistOrbit(Level level) {
        return level.dimension() == TRAPPIST_ORBIT;
    }

    /** Vrai sur TRAPPIST-1 e. */
    public static boolean isTrappist(Level level) {
        return level.dimension() == TRAPPIST_1E;
    }

    /** Vrai en orbite de Kepler-452b. */
    public static boolean isKeplerOrbit(Level level) {
        return level.dimension() == KEPLER_ORBIT;
    }

    /** Vrai sur Kepler-452b. */
    public static boolean isKepler(Level level) {
        return level.dimension() == KEPLER_452B;
    }

    /** Vrai en orbite de Tarentule b. */
    public static boolean isTarantulaOrbit(Level level) {
        return level.dimension() == TARANTULA_ORBIT;
    }

    /** Vrai sur Tarentule b. */
    public static boolean isTarantula(Level level) {
        return level.dimension() == TARANTULA_B;
    }

    /** Vrai si l'on ne peut pas respirer dans ce monde sans combinaison : le vide, l'air de Mars ou celui de Titan. */
    public static boolean isUnbreathable(Level level) {
        return isVacuum(level) || isMars(level) || isTitan(level) || isProxima(level) || isTrappist(level) || isKepler(level)
                || isTarantula(level);
    }

    /** Pesanteur de ce monde, en part de celle de la planète. */
    public static double gravityOf(Level level) {
        return isMoon(level) ? MOON_GRAVITY : isMars(level) ? MARS_GRAVITY : isTitan(level) ? TITAN_GRAVITY
                : isEuropa(level) ? EUROPA_GRAVITY : isProxima(level) ? PROXIMA_GRAVITY : isTrappist(level) ? TRAPPIST_GRAVITY
                : isKepler(level) ? KEPLER_GRAVITY : isTarantula(level) ? TARANTULA_GRAVITY : 1.0;
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
        boolean onTrappist = isTrappist(player.level());
        boolean onKepler = isKepler(player.level());
        boolean onTarantula = isTarantula(player.level());
        // À bord d'un vaisseau dont l'anneau tourne, l'équipage retrouve la pesanteur.
        updateMicrogravity(player, inOrbit && !com.orbitalis.phase3.interstellar.RingDriveBlockEntity.gravityAt(player));
        updateLowGravity(player, gravityOf(player.level()));
        if (player.tickCount % 20 != 0) {
            return;
        }
        ItemStack suit = player.getItemBySlot(EquipmentSlot.CHEST);
        boolean outdoors = inOrbit || onMoon || onMars || onTitan || onEuropa || onProxima || onTrappist || onKepler || onTarantula;
        // La capsule est pressurisée, comme les pièces d'une station dont le générateur d'air tourne.
        boolean sheltered = outdoors && (player.getVehicle() instanceof CapsuleEntity
                || StationAir.isBreathable(player.level(), BlockPos.containing(player.getEyePosition())));
        // Les capteurs du casque : son affichage dit si l'on respire dehors ou sur la réserve. Sans casque, l'ambiance
        // des pièces pressurisées s'en sert aussi.
        com.orbitalis.platform.network.Network.sendToPlayer(player, new SuitStatusPayload(!outdoors || sheltered));
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
            player.hurt(player.damageSources().magic(), RADIATION_DAMAGE);
        }
        EquipmentSlot missing = SpaceSuits.missing(player);
        if (missing == null && oxygen(suit) > 0) {
            addOxygen(suit, -1);
            int left = oxygen(suit);
            // L'affichage du casque tient le compte ; la dernière minute, une alerte toutes les dix secondes.
            if (irradiated) {
                player.displayClientMessage(Component.translatable("space.orbitalis.radiation").withStyle(ChatFormatting.RED), true);
            } else if (left <= 60 && left % 10 == 0) {
                player.displayClientMessage(Component.translatable("space.orbitalis.oxygen", left / 60, String.format("%02d", left % 60))
                        .withStyle(ChatFormatting.RED), true);
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
            } else if (onTrappist) {
                rewardTrappistwalk(player);
            } else if (onKepler) {
                rewardKeplerwalk(player);
            } else if (onTarantula) {
                rewardTarantulawalk(player);
            } else {
                rewardSpacewalk(player);
            }
        } else {
            Component warning = !isSuit(suit) ? Component.translatable("space.orbitalis.no_suit")
                    : missing != null ? Component.translatable("space.orbitalis.suit_open", Component.translatable("suit.orbitalis.piece." + missing.getName()))
                    : Component.translatable("space.orbitalis.oxygen_empty");
            player.displayClientMessage(warning.copy().withStyle(ChatFormatting.RED), true);
            // Les dégâts du vide ont leur propre type : la blessure ordinaire, et non le bruit de noyade.
            player.hurt(new net.minecraft.world.damagesource.DamageSource(player.level().registryAccess()
                    .lookupOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).getOrThrow(VACUUM)), VACUUM_DAMAGE);
        }
    }

    /**
     * En orbite, le joueur flotte : plus de pesanteur, et il nage comme dans l'eau (son client calcule la nage). Le
     * droit de voler lui est laissé, mais jamais le vol lui-même : sans lui, un serveur prendrait ce joueur qui ne
     * touche plus rien pour un tricheur et l'expulserait. Ailleurs, il retrouve la pesanteur.
     */
    private static void updateMicrogravity(ServerPlayer player, boolean inOrbit) {
        AttributeInstance gravity = player.getAttribute(Attributes.GRAVITY);
        if (gravity == null) {
            return;
        }
        boolean floating = inOrbit && !player.isCreative() && !player.isSpectator();
        if (floating) {
            // Le droit de voler, prêté le temps de l'apesanteur (en 26, un attribut de NeoForge le donnait).
            if (FLOAT_GRANTED.add(player.getUUID()) && !player.getAbilities().mayfly) {
                player.getAbilities().mayfly = true;
                player.onUpdateAbilities();
            } else if (!player.getAbilities().mayfly) {
                player.getAbilities().mayfly = true;
                player.onUpdateAbilities();
            }
            if (!gravity.hasModifier(ZERO_GRAVITY)) {
                gravity.addTransientModifier(new AttributeModifier(ZERO_GRAVITY, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
            if (player.getAbilities().flying) {
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
            }
            com.orbitalis.platform.Compat.setForcedPose(player, Microgravity.applies(player) ? Microgravity.pose(player) : null);
            player.resetFallDistance();
            return;
        }
        gravity.removeModifier(ZERO_GRAVITY);
        if (FLOAT_GRANTED.remove(player.getUUID())) {
            if (!player.isCreative() && !player.isSpectator()) {
                player.getAbilities().mayfly = false;
            }
            com.orbitalis.platform.Compat.setForcedPose(player, null);
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
        // Plus légère ou plus lourde que celle de la planète : sur Kepler-452b, on saute moins haut et l'on tombe plus vite.
        boolean other = Math.abs(share - 1.0) > 1.0E-6;
        // Rien à faire si le joueur porte déjà la pesanteur de ce monde.
        if (other ? current != null && Math.abs(current.amount() - (share - 1.0)) < 1.0E-6 : current == null) {
            return;
        }
        gravity.removeModifier(LOW_GRAVITY);
        safeFall.removeModifier(LOW_GRAVITY);
        if (other) {
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

    /** Premiers pas sur TRAPPIST-1 e d'une agence : sur une île du monde-océan, hors du vaisseau, en combinaison. */
    private static void rewardTrappistwalk(ServerPlayer player) {
        AgencyResearch research = ResearchManager.of(player);
        if (research.completeMilestone("first_trappistwalk")) {
            research.addPoints(REWARD_TRAPPISTWALK);
            ResearchManager.changed(player);
            player.sendSystemMessage(Component.translatable("space.orbitalis.first_trappistwalk", REWARD_TRAPPISTWALK).withStyle(ChatFormatting.AQUA));
        }
    }

    /** Premiers pas sur Kepler-452b d'une agence : sous une pesanteur une fois et demie plus forte, en combinaison. */
    private static void rewardKeplerwalk(ServerPlayer player) {
        AgencyResearch research = ResearchManager.of(player);
        if (research.completeMilestone("first_keplerwalk")) {
            research.addPoints(REWARD_KEPLERWALK);
            ResearchManager.changed(player);
            player.sendSystemMessage(Component.translatable("space.orbitalis.first_keplerwalk", REWARD_KEPLERWALK).withStyle(ChatFormatting.AQUA));
        }
    }

    /** Premiers pas sur Tarentule b d'une agence : le premier monde d'une autre galaxie, en combinaison. */
    private static void rewardTarantulawalk(ServerPlayer player) {
        AgencyResearch research = ResearchManager.of(player);
        if (research.completeMilestone("first_tarantulawalk")) {
            research.addPoints(REWARD_TARANTULAWALK);
            ResearchManager.changed(player);
            player.sendSystemMessage(Component.translatable("space.orbitalis.first_tarantulawalk", REWARD_TARANTULAWALK).withStyle(ChatFormatting.AQUA));
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
