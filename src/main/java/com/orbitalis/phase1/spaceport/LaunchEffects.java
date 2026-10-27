package com.orbitalis.phase1.spaceport;

import com.orbitalis.core.config.ClientConfig;
import com.orbitalis.core.registry.ModParticles;
import com.orbitalis.core.registry.ModSounds;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

/**
 * Effets visuels et sonores d'un lancement, côté client. Ils ne changent rien au déroulement du lancement : ils
 * lisent seulement où en est la table (compte à rebours, vol) et habillent chaque moment.
 * <ol>
 * <li><b>Préparation</b> (dès T-30) : alarme, purge des lignes, dégazage des réservoirs cryogéniques.</li>
 * <li><b>Retrait des bras</b> (T-15) : hydraulique, bouffées froides à chaque débranchement.</li>
 * <li><b>Déluge</b> (T-6) : les arroseurs noient le tablier, un brouillard se lève.</li>
 * <li><b>Allumage</b> (T-3) : éclair, flammes, le carneau crache du feu puis un mur de vapeur blanche et de
 * fumée ; escarbilles, voile de chaleur.</li>
 * <li><b>Décollage</b> : tout à pleine puissance, et le souffle soulève la poussière autour du pas de tir.</li>
 * <li><b>Retour au calme</b> : pendant une demi-minute, le carneau fume encore et l'air tremble au-dessus de lui.</li>
 * </ol>
 * Au repos, rien n'est émis. Les grosses volutes (fumée, vapeur, feu) se voient de loin ; les petits effets
 * (gouttes, escarbilles, voile de chaleur) ne sont émis que pour les joueurs proches, le jeu s'en charge.
 */
final class LaunchEffects {
    /** Durée du retour au calme après la fin du vol, en ticks. */
    private static final int CALM_TICKS = 15 * 20;
    /** Moments du compte à rebours, en secondes avant le décollage. */
    private static final float ARMS_AT = 15.0F;
    private static final float DELUGE_AT = 6.0F;
    private static final float IGNITION_AT = LaunchTableBlockEntity.IGNITION_TICKS / 20.0F;

    private LaunchTableBlockEntity.Phase lastPhase = LaunchTableBlockEntity.Phase.IDLE;
    private long lastTicks = -1L;
    private int calm;

    // Repères du pas de tir, relevés à chaque tick : son centre au niveau du dessus du tablier, le sens du
    // carneau (dx, dz) et sa perpendiculaire (px, pz), sa taille, la demi-largeur de la fusée et la puissance
    // des effets qui en découle.
    private Level level;
    private RandomSource random;
    private double x;
    private double y;
    private double z;
    private double dx;
    private double dz;
    private double px;
    private double pz;
    private int radius;
    private float half;
    private double power;

    void tick(LaunchTableBlockEntity table, Level level) {
        LaunchTableBlockEntity.Phase phase = table.getPhase();
        long ticks = table.phaseTicks();
        if (phase != lastPhase) {
            if (lastPhase == LaunchTableBlockEntity.Phase.FLIGHT && phase == LaunchTableBlockEntity.Phase.IDLE) {
                calm = CALM_TICKS;
            }
            lastPhase = phase;
            // Un allumage commandé à la main entre dans le compte à rebours à T-3 s : ce qui précède ne doit pas
            // sonner d'un coup. Seul ce que l'horloge franchit désormais se fait entendre.
            lastTicks = ticks > 40 ? ticks - 1 : -1L;
        }
        if (phase == LaunchTableBlockEntity.Phase.IDLE && calm <= 0) {
            // Au repos, la séquence se voit quand même : les réservoirs sous pression dégazent, le déluge coule.
            if (table.hasRocket() && (table.isPressurized() || table.isDelugeOn())) {
                locate(table, level);
                if (table.isPressurized() && table.isLiquid()) {
                    venting(20.0F, Math.max(1, table.rocketHeight()));
                }
                if (table.isDelugeOn()) {
                    deluge(1.0F);
                }
            }
            return;
        }
        locate(table, level);
        switch (phase) {
            case COUNTDOWN -> countdown(table, ticks);
            case FLIGHT -> flight(table, ticks);
            default -> aftermath(--calm / (float) CALM_TICKS * 0.5F);
        }
        lastTicks = ticks;
    }

    private void locate(LaunchTableBlockEntity table, Level level) {
        BlockPos pos = table.getBlockPos();
        Direction trench = table.getTrenchDirection();
        this.level = level;
        random = level.getRandom();
        x = pos.getX() + 0.5;
        y = pos.getY() + 1.0;
        z = pos.getZ() + 0.5;
        dx = trench.getStepX();
        dz = trench.getStepZ();
        px = -dz;
        pz = dx;
        radius = Math.max(1, table.getComplex().radius());
        half = Math.max(0.25F, table.rocketHalfWidth());
        // Un petit pas de tir et une fusée fine donnent un lancement modeste ; un grand, un spectacle.
        power = (0.55 + half) * (0.7 + 0.15 * radius) * ClientConfig.PARTICLE_DENSITY.get();
    }

    // ------------------------------------------------------------------
    // Compte à rebours
    // ------------------------------------------------------------------

    private void countdown(LaunchTableBlockEntity table, long ticks) {
        if (!table.hasRocket()) {
            return;
        }
        float remaining = (LaunchTableBlockEntity.COUNTDOWN_TICKS - ticks) / 20.0F;
        boolean liquid = table.needsArm();
        int height = Math.max(1, table.rocketHeight());

        if (reached(ticks, 1)) {
            sound(ModSounds.PAD_PURGE.get(), 2.0F, 1.0F);
        }
        if (reached(ticks, seconds(20.0F))) {
            sound(ModSounds.PAD_VALVE.get(), 2.0F, 1.0F);
        }
        if (reached(ticks, seconds(ARMS_AT))) {
            sound(ModSounds.PAD_HYDRAULIC.get(), 2.5F, 1.0F);
        }
        if (reached(ticks, seconds(13.0F))) {
            sound(ModSounds.PAD_CREAK.get(), 2.5F, 0.9F);
        }
        if (reached(ticks, seconds(DELUGE_AT))) {
            // L'eau elle-même s'entend en boucle tant qu'elle coule (voir DelugeSound).
            sound(ModSounds.PAD_VALVE.get(), 2.0F, 0.8F);
        }
        if (reached(ticks, seconds(IGNITION_AT))) {
            // Le grondement des moteurs est une boucle que le client tient tant qu'ils brûlent (voir EngineSound).
            far(ModParticles.LAUNCH_FLASH.get(), x, y + 0.6, z, 0.0, 0.0, 0.0);
        }

        if (liquid) {
            venting(remaining, height);
        }
        // Débranchement des bras : une bouffée froide jaillit du flanc de la fusée, là où un flexible se retire.
        if (liquid && remaining < ARMS_AT && remaining > ARMS_AT - 5.0F && random.nextInt(4) == 0) {
            double angle = random.nextDouble() * Math.PI * 2;
            double h = y + 0.5 + random.nextDouble() * height * 0.8;
            for (int i = 0; i < 4; i++) {
                near(ModParticles.CRYO_MIST.get(), x + Math.cos(angle) * (half + 0.1), h, z + Math.sin(angle) * (half + 0.1),
                        Math.cos(angle) * 0.08 + spread(0.03), -0.01, Math.sin(angle) * 0.08 + spread(0.03));
            }
        }
        if (table.isDelugeOn()) {
            deluge(1.0F);
        }
        if (remaining < IGNITION_AT) {
            // Les moteurs montent en puissance pendant les trois secondes qui précèdent le décollage.
            float thrust = Math.min(1.0F, (IGNITION_AT - remaining) / 1.5F);
            engines(table, 0.0F);
            blast(thrust, false);
            if (ticks % 6 == 0) {
                far(ModParticles.LAUNCH_FLASH.get(), x + spread(half), y + 0.4, z + spread(half), 0.0, 0.0, 0.0);
            }
        }
    }

    /** Dégazage : les réservoirs cryogéniques laissent échapper de petites bouffées, de plus en plus souvent. */
    private void venting(float remaining, int height) {
        if (random.nextInt(remaining < 10.0F ? 2 : 3) == 0) {
            double angle = random.nextDouble() * Math.PI * 2;
            near(ModParticles.CRYO_MIST.get(), x + Math.cos(angle) * (half + 0.1), y + random.nextDouble() * height,
                    z + Math.sin(angle) * (half + 0.1), Math.cos(angle) * 0.02, -0.01, Math.sin(angle) * 0.02);
        }
        // Soupape de tête : toutes les quatre secondes, un jet de vapeur file à l'horizontale près du sommet.
        long beat = Math.round(remaining * 20.0F) % 80L;
        if (beat < 12L) {
            double h = y + height * 0.82;
            near(ModParticles.VAPOR.get(), x + px * (half + 0.1), h, z + pz * (half + 0.1),
                    px * 0.22 + spread(0.02), 0.01 + spread(0.01), pz * 0.22 + spread(0.02));
        }
    }

    // ------------------------------------------------------------------
    // Vol
    // ------------------------------------------------------------------

    private void flight(LaunchTableBlockEntity table, long ticks) {
        float seconds = ticks / 20.0F;
        float ascent = table.ascent(0.0F);
        if (reached(ticks, 1)) {
            // Décollage : deux éclairs, sous la fusée et à la bouche du carneau.
            far(ModParticles.LAUNCH_FLASH.get(), x, y + 0.6, z, 0.0, 0.0, 0.0);
            far(ModParticles.LAUNCH_FLASH.get(), x + dx * radius, y, z + dz * radius, 0.0, 0.0, 0.0);
        }
        // Les flammes d'une fusée habitée sont l'affaire de sa capsule, qui la suit partout.
        if (ascent < LaunchTableBlockEntity.ASCENT_LIMIT && !table.carriesCabin() && !table.enginesOut()) {
            engines(table, table.renderAscent(0.0F));
        }
        if (seconds < 4.0F && table.isDelugeOn()) {
            deluge(1.0F - seconds / 4.0F);
        }
        // Tant que la fusée est basse, ses gaz frappent le pas de tir ; puis le sol se calme peu à peu.
        float ground = Math.max(0.0F, 1.0F - seconds / 7.0F);
        if (ground > 0.0F) {
            blast(ground, true);
        } else {
            aftermath(Math.max(0.3F, 1.0F - (seconds - 7.0F) / 18.0F));
        }
    }

    /**
     * Flammes sous la fusée et sillage de fumée derrière elle. Une fusée penchée crache ses gaz dans son axe, depuis
     * ses tuyères, là où elles se trouvent : le panache suit le nez du pilote.
     */
    private void engines(LaunchTableBlockEntity table, float ascent) {
        org.joml.Quaternionf attitude = table.renderAttitude(0.0F);
        float pivot = table.pivotHeight();
        // Les tuyères, par rapport à la fusée debout, et le sens dans lequel partent les gaz.
        float back = table.pivotBack();
        // Les flammes sortent des moteurs de l'étage allumé : après un largage, ce n'est plus le pied de la fusée.
        int lit = com.orbitalis.phase2.flight.CapsuleEntity.stageStart(table.rocketShape().parts(), table.stagesPassed());
        org.joml.Vector3f foot = attitude.transform(new org.joml.Vector3f(0.0F, lit - pivot, -back)).add(0.0F, pivot, back);
        org.joml.Vector3f exhaust = attitude.transform(new org.joml.Vector3f(0.0F, -1.0F, 0.0F));
        net.minecraft.world.phys.Vec3 drift = table.renderDrift(0.0F);
        double nx = x + drift.x + foot.x;
        double ny = y + ascent + foot.y;
        double nz = z + drift.z + foot.z;
        // Tout ce qui sort des tuyères part avec la vitesse de la fusée, plus celle des gaz dans son axe : les
        // flammes restent collées au dard dessiné sous les moteurs, puis l'air les freine. Au sol, la fusée ne bouge
        // pas encore : elles tombent droit dans le carneau.
        net.minecraft.world.phys.Vec3 next = table.renderDrift(1.0F);
        double mx = next.x - drift.x;
        double my = table.renderAscent(1.0F) - table.renderAscent(0.0F);
        double mz = next.z - drift.z;
        boolean aloft = ascent > 0.5F;
        if (!aloft) {
            // Au sol, la fusée ne bouge pas encore : flammes et boules de feu tombent droit dans le carneau.
            int flames = amount(3 + half * 8);
            for (int i = 0; i < flames; i++) {
                double ox = spread(half * 0.7F);
                double oz = spread(half * 0.7F);
                far(ModParticles.ENGINE_FLAME.get(), nx + ox + exhaust.x * 0.1, ny + exhaust.y * 0.1, nz + oz + exhaust.z * 0.1,
                        exhaust.x * 0.35 + ox * 0.05, exhaust.y * 0.35, exhaust.z * 0.35 + oz * 0.05);
            }
            int jet = amount(1 + half * 3);
            for (int i = 0; i < jet; i++) {
                far(ModParticles.TRENCH_FIRE.get(), nx + spread(half * 0.7) + exhaust.x * 0.2, ny + exhaust.y * 0.2, nz + spread(half * 0.7) + exhaust.z * 0.2,
                        exhaust.x * 0.45, exhaust.y * 0.45, exhaust.z * 0.45);
            }
        }
        if (ascent > 1.5F) {
            // En vol, la flamme est dessinée avec la fusée, attachée à ses tuyères. Ici ne naît que la fumée du
            // sillage : posée le long du chemin parcouru pendant ce tick, au bout du dard, elle dessine la trajectoire.
            double reach = half > 1.0F ? 10.0 : 5.0;
            int trail = amount(3 + half * 8);
            for (int i = 0; i < trail; i++) {
                double along = random.nextDouble();
                double behind = reach + random.nextDouble() * 2.0;
                double push = 0.12 + random.nextDouble() * 0.2;
                far(ModParticles.EXHAUST_SMOKE.get(), nx - mx * along + spread(half) + exhaust.x * behind, ny - my * along + exhaust.y * behind,
                        nz - mz * along + spread(half) + exhaust.z * behind, mx * 0.15 + spread(0.03) + exhaust.x * push, my * 0.15 + exhaust.y * push,
                        mz * 0.15 + spread(0.03) + exhaust.z * push);
            }
        }
    }

    // ------------------------------------------------------------------
    // Déluge
    // ------------------------------------------------------------------

    /**
     * Déluge d'eau : les arroseurs géants du tablier arrosent le pied de la fusée, des rideaux d'eau tombent sous
     * la table, et un brouillard se lève. Sur le plus petit pas de tir, sans arroseurs, il ne reste que les rideaux.
     */
    private void deluge(float strength) {
        if (radius >= 2) {
            for (int side = -2; side <= 2; side += 4) {
                // Les arroseurs sont sur les rampes, de part et d'autre de l'ouverture laissée aux boosters.
                for (int k = -3; k <= 3; k += 2) {
                    if (Math.abs(k) == 3 && radius < 4) {
                        continue;
                    }
                    double sx = x + px * side + dx * k;
                    double sz = z + pz * side + dz * k;
                    if (random.nextFloat() < 0.2F * strength) {
                        near(ModParticles.VAPOR.get(), sx, y + 1.6, sz, spread(0.03), 0.02, spread(0.03));
                    }
                    for (int i = 0; i < 4; i++) {
                        if (random.nextFloat() < strength) {
                            near(ModParticles.DELUGE_WATER.get(), sx, y + 0.85, sz,
                                    -px * side * 0.07 - dx * k * 0.05 + spread(0.05), 0.22 + random.nextDouble() * 0.1,
                                    -pz * side * 0.07 - dz * k * 0.05 + spread(0.05));
                        }
                    }
                }
            }
        }
        int curtain = amount(7 * strength);
        for (int i = 0; i < curtain; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = half + 0.3 + random.nextDouble() * 1.2;
            near(ModParticles.DELUGE_WATER.get(), x + Math.cos(angle) * distance, y + 0.5, z + Math.sin(angle) * distance,
                    Math.cos(angle) * 0.05, 0.05, Math.sin(angle) * 0.05);
            near(ParticleTypes.SPLASH, x + Math.cos(angle) * distance, y + 0.05, z + Math.sin(angle) * distance, 0.0, 0.2, 0.0);
        }
        if (random.nextFloat() < 0.5F * strength) {
            double angle = random.nextDouble() * Math.PI * 2;
            far(ModParticles.LAUNCH_STEAM.get(), x + Math.cos(angle) * (half + 0.6), y + 0.2, z + Math.sin(angle) * (half + 0.6),
                    Math.cos(angle) * 0.04, 0.03, Math.sin(angle) * 0.04);
        }
    }

    // ------------------------------------------------------------------
    // Souffle des moteurs sur le pas de tir
    // ------------------------------------------------------------------

    /**
     * Les gaz des moteurs frappent le déflecteur et filent dans le carneau : le feu en sort d'abord, puis un mur
     * de vapeur blanche (l'eau du déluge, vaporisée), puis la fumée. Autour de la table, un nuage roule sur le
     * tablier. L'air tremble, des escarbilles volent, et au décollage le souffle soulève la poussière alentour.
     *
     * @param strength puissance des moteurs, de 0 à 1
     * @param liftoff  vrai une fois la fusée partie : le souffle balaie aussi le sol autour du pas de tir
     */
    private void blast(float strength, boolean liftoff) {
        double mouthX = x + dx * (radius + 0.6);
        double mouthZ = z + dz * (radius + 0.6);

        // Feu dans le carneau : des boules de feu filent de la table vers la bouche.
        int fire = amount((2.5 + radius * 1.0) * strength);
        for (int i = 0; i < fire; i++) {
            double along = 0.6 + random.nextDouble() * radius;
            double speed = 0.45 + random.nextDouble() * 0.5;
            far(ModParticles.TRENCH_FIRE.get(), x + dx * along + px * spread(0.3), y - 0.55 + random.nextDouble() * 0.3,
                    z + dz * along + pz * spread(0.3), dx * speed, 0.01 + random.nextDouble() * 0.03, dz * speed);
        }
        // Vapeur et fumée à la bouche du carneau : un jet horizontal, large, qui s'élève en s'éloignant.
        int jet = amount((8.0 + half * 12.0) * strength * power);
        for (int i = 0; i < jet; i++) {
            double speed = 0.45 + random.nextDouble() * 0.75;
            double side = spread(0.35);
            ParticleOptions cloud = i % 3 == 0 ? ModParticles.LAUNCH_SMOKE.get() : ModParticles.LAUNCH_STEAM.get();
            far(cloud, mouthX + px * spread(0.5), y - 0.6 + random.nextDouble() * 0.8, mouthZ + pz * spread(0.5),
                    dx * speed + px * side, 0.03 + random.nextDouble() * 0.12, dz * speed + pz * side);
        }
        // Vapeur qui monte à travers le caillebotis, tout le long du carneau.
        int grating = amount(radius * 1.2 * strength);
        for (int i = 0; i < grating; i++) {
            double along = 0.8 + random.nextDouble() * (radius - 0.3);
            far(ModParticles.LAUNCH_STEAM.get(), x + dx * along + px * spread(0.4), y, z + dz * along + pz * spread(0.4),
                    dx * 0.08, 0.14 + random.nextDouble() * 0.12, dz * 0.08);
        }
        // Nuage au pied de la fusée : fumée dorée par les flammes, mêlée de vapeur, qui roule vers l'extérieur.
        int ring = amount((10.0 + half * 22.0) * strength * power);
        for (int i = 0; i < ring; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double speed = 0.18 + random.nextDouble() * 0.4;
            ParticleOptions cloud = i % 4 == 0 ? ModParticles.LAUNCH_STEAM.get() : ModParticles.LAUNCH_SMOKE.get();
            far(cloud, x + Math.cos(angle) * (half + 0.3), y + 0.2 + random.nextDouble() * 0.6, z + Math.sin(angle) * (half + 0.3),
                    Math.cos(angle) * speed, 0.02 + random.nextDouble() * 0.09, Math.sin(angle) * speed);
        }
        // Escarbilles arrachées au carneau, et voile de chaleur au-dessus de lui et autour de la table.
        int embers = amount(2.0 * strength);
        for (int i = 0; i < embers; i++) {
            double speed = 0.3 + random.nextDouble() * 0.5;
            near(ModParticles.LAUNCH_EMBER.get(), mouthX, y - 0.3, mouthZ, dx * speed + px * spread(0.25),
                    0.1 + random.nextDouble() * 0.25, dz * speed + pz * spread(0.25));
        }
        heat(strength);
        // Souffle : un anneau de poussière part du bord du pas de tir, au ras du sol.
        if (liftoff) {
            int dust = amount((5.0 + 2.5 * radius) * strength);
            for (int i = 0; i < dust; i++) {
                double angle = random.nextDouble() * Math.PI * 2;
                double distance = radius + 1.2 + random.nextDouble();
                far(ModParticles.LAUNCH_DUST.get(), x + Math.cos(angle) * distance, y - 0.8 + random.nextDouble() * 0.4,
                        z + Math.sin(angle) * distance, Math.cos(angle) * 0.35, 0.02 + random.nextDouble() * 0.04, Math.sin(angle) * 0.35);
            }
        }
    }

    /** Voile de chaleur : l'air tremble au-dessus du carneau et autour de la table. */
    private void heat(float strength) {
        int count = amount(1.5 * strength);
        for (int i = 0; i < count; i++) {
            double along = random.nextDouble() * (radius + 1.5);
            near(ModParticles.HEAT_HAZE.get(), x + dx * along + px * spread(0.8), y + 0.2 + random.nextDouble() * 0.8,
                    z + dz * along + pz * spread(0.8), 0.0, 0.03, 0.0);
        }
    }

    /**
     * Retour au calme : le carneau fume encore, un reste de vapeur s'élève du tablier mouillé, l'air tremble.
     *
     * @param strength ce qu'il reste de chaleur, de 1 (la fusée vient de partir) à 0
     */
    private void aftermath(float strength) {
        if (strength <= 0.0F) {
            return;
        }
        if (random.nextFloat() < 0.35F * strength * (0.6F + 0.2F * radius)) {
            double along = 0.5 + random.nextDouble() * (radius + 0.6);
            far(random.nextInt(3) == 0 ? ModParticles.LAUNCH_SMOKE.get() : ModParticles.LAUNCH_STEAM.get(),
                    x + dx * along + px * spread(0.4), y - 0.2, z + dz * along + pz * spread(0.4),
                    dx * 0.03, 0.05 + random.nextDouble() * 0.06, dz * 0.03);
        }
        if (random.nextFloat() < 0.3F * strength) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = random.nextDouble() * (radius + 0.5);
            near(ModParticles.VAPOR.get(), x + Math.cos(angle) * distance, y + 0.05, z + Math.sin(angle) * distance, 0.0, 0.03, 0.0);
        }
        heat(strength * 0.6F);
    }

    // ------------------------------------------------------------------
    // Outils
    // ------------------------------------------------------------------

    /** Vrai au tick où le compteur de la phase atteint ou dépasse un seuil, une seule fois. */
    private boolean reached(long ticks, long threshold) {
        return lastTicks < threshold && ticks >= threshold;
    }

    /** Tick du compte à rebours qui correspond à un nombre de secondes avant le décollage. */
    private static long seconds(float beforeLiftoff) {
        return LaunchTableBlockEntity.COUNTDOWN_TICKS - Math.round(beforeLiftoff * 20.0F);
    }

    /** Nombre de particules à émettre ce tick : la partie entière, plus une de temps en temps pour le reste. */
    private int amount(double perTick) {
        int whole = (int) perTick;
        return whole + (random.nextDouble() < perTick - whole ? 1 : 0);
    }

    private double spread(double range) {
        return (random.nextDouble() - 0.5) * 2.0 * range;
    }

    /** Particule visible de loin : les grosses volutes, les flammes, les éclairs. */
    private void far(ParticleOptions particle, double px, double py, double pz, double vx, double vy, double vz) {
        level.addAlwaysVisibleParticle(particle, true, px, py, pz, vx, vy, vz);
    }

    /** Petite particule : elle n'existe que pour les joueurs proches. */
    private void near(ParticleOptions particle, double px, double py, double pz, double vx, double vy, double vz) {
        level.addParticle(particle, px, py, pz, vx, vy, vz);
    }

    private void sound(SoundEvent event, float volume, float pitch) {
        level.playLocalSound(x, y, z, event, SoundSource.BLOCKS, volume, pitch, true);
    }
}
