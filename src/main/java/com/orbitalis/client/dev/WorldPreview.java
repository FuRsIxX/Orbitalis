package com.orbitalis.client.dev;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.core.Direction;
import com.orbitalis.core.registry.ModBlocks;
import com.orbitalis.phase1.spaceport.PadPlan;
import com.orbitalis.Orbitalis;
import com.orbitalis.core.creative.CreativeRockets;
import com.orbitalis.core.registry.ModFluids;
import com.orbitalis.core.registry.ModEntities;
import com.orbitalis.phase2.flight.CapsuleEntity;
import com.orbitalis.phase2.station.StationManager;
import com.orbitalis.phase2.survival.SpaceRules;
import com.orbitalis.phase1.satellite.SatelliteType;
import com.orbitalis.phase1.satellite.SatelliteManager;
import com.orbitalis.phase1.rocket.RocketStats;
import com.orbitalis.core.research.ResearchManager;
import com.orbitalis.phase1.spaceport.LaunchConsoleBlock;
import com.orbitalis.phase1.spaceport.LaunchTableBlockEntity;
import com.orbitalis.phase1.spaceport.MissionDisplayBlockEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Outil de développement : construit une scène de démonstration dans le monde, place la caméra à plusieurs
 * points de vue, capture une image à chacun, puis ferme le jeu.
 * <p>
 * Inactif en jeu normal. Il ne se déclenche que si le jeu est lancé avec
 * {@code gradlew runClient -Porbitalis.worldPreview=true}, qui ouvre le monde {@code run/saves/orbitalis_render} ;
 * les images sont écrites dans {@code run/world_preview/screenshots}. Il sert à vérifier le rendu des blocs,
 * des structures et des écrans sans avoir à jouer. La scène est bâtie en hauteur, sur une plateforme,
 * pour ne rien abîmer du monde.
 */
@EventBusSubscriber(modid = Orbitalis.MODID, value = Dist.CLIENT)
public final class WorldPreview {
    private static final boolean ENABLED = Boolean.getBoolean("orbitalis.worldPreview");
    /** Altitude de la plateforme de démonstration. */
    private static final int Y = 200;
    private static final int ORIGIN_X = 4000;
    private static final int ORIGIN_Z = 4000;

    /**
     * Une prise de vue.
     *
     * @param setup  préparation de la scène, exécutée côté serveur avant la capture
     * @param settle ticks laissés au jeu pour afficher la scène avant la capture
     */
    private record Shot(String name, Consumer<ServerLevel> setup, double x, double y, double z, float yaw, float pitch, int settle) {
    }

    /** L'atlas ouvert par les captures, pour en changer la vue d'une capture à l'autre. */
    private static com.orbitalis.client.screen.AtlasScreen atlasPreview;

    /** La capsule des vues de manœuvre. */
    private static CapsuleEntity nodeCapsule;
    private static final List<Shot> SHOTS = new ArrayList<>();
    private static BlockPos origin;
    /** Le centre spatial trouvé par la dernière recherche : les vues « site_ » se placent par rapport à lui. */
    private static BlockPos site;
    /** Le centre trouvé, et la place et le regard d'une vue d'intérieur (« inside_ »). */
    private static net.minecraft.world.level.levelgen.structure.StructureStart siteStart;
    private static net.minecraft.world.phys.Vec3 inside;
    private static float insideYaw;
    private static String siteKind;
    private static int index = -1;
    private static int waited;
    private static boolean finished;
    /** Ticks passés sans être entré dans le monde. */
    private static int stalled;

    private WorldPreview() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!ENABLED || finished) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        // Les captures ne doivent dépendre ni de la souris ni du focus de la fenêtre : pas de pause quand
        // elle passe à l'arrière-plan, et la souris reste libre pour le reste de l'ordinateur.
        minecraft.options.pauseOnLostFocus = false;
        minecraft.mouseHandler.releaseMouse();
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (minecraft.level == null || minecraft.player == null || server == null) {
            // Si le monde ne s'ouvre pas (déjà ouvert ailleurs, par exemple), on ne laisse pas le jeu ouvert pour rien.
            if (++stalled > 900) {
                finished = true;
                // L'écran où le jeu s'est arrêté dit pourquoi : une confirmation, une erreur de chargement…
                Orbitalis.LOGGER.error("Aperçu du monde : le monde ne s'est pas ouvert (écran : {}), arrêt.",
                        minecraft.gui.screen() == null ? "aucun" : minecraft.gui.screen().getClass().getName() + " « "
                                + minecraft.gui.screen().getTitle().getString() + " »");
                minecraft.stop();
            }
            return;
        }

        if (index < 0) {
            // On attend que le monde soit chargé, puis on prépare la liste des prises de vue.
            if (++waited > 60) {
                // Origine fixe : la scène est rebâtie au même endroit d'un lancement à l'autre.
                origin = new BlockPos(ORIGIN_X, Y, ORIGIN_Z);
                buildShots();
                index = 0;
                start(server);
            }
            return;
        }

        Shot shot = SHOTS.get(index);
        waited++;
        if (shot.name().startsWith("rover_")) {
            // Le second rover est figé en plein virage à droite : roues braquées, caisse qui penche.
            for (net.minecraft.world.entity.Entity entity : minecraft.level.entitiesForRendering()) {
                if (entity instanceof com.orbitalis.phase3.exploration.RoverEntity rover) {
                    boolean turning = Math.abs(net.minecraft.util.Mth.wrapDegrees(rover.getYRot() - 210.0F)) < 1.0F;
                    rover.previewPose(turning ? 2.2F : 0.6F, turning ? 22.0F : 0.0F, turning ? 1.5F : 0.0F, turning ? 3.0F : 0.0F);
                }
            }
        }
        if (waited == shot.settle()) {
            File directory = new File(minecraft.gameDirectory, "world_preview");
            new File(directory, "screenshots").mkdirs();
            String name = "%02d_%s.png".formatted(index, shot.name());
            Screenshot.grab(directory, name, minecraft.gameRenderer.mainRenderTarget(), 1,
                    message -> Orbitalis.LOGGER.info("Capture du monde : {}", name));
        } else if (waited > shot.settle() + 3) {
            index++;
            if (index >= SHOTS.size()) {
                finished = true;
                minecraft.stop();
                return;
            }
            start(server);
        }
    }

    /** Une vue dont la caméra (ses yeux) regarde un point : lacet et tangage calculés, positions relatives à l'origine. */
    private static Shot look(String name, Consumer<ServerLevel> setup, double x, double y, double z, double tx, double ty, double tz, int settle) {
        double eye = y + 1.62;
        double dx = tx - x;
        double dz = tz - z;
        float yaw = (float) Math.toDegrees(-Math.atan2(dx, dz));
        float pitch = (float) Math.toDegrees(-Math.atan2(ty - eye, Math.hypot(dx, dz)));
        return new Shot(name, setup, x, y, z, yaw, pitch, settle);
    }

    /** Lance la prise de vue courante : préparation de la scène, puis placement de la caméra. */
    private static void start(IntegratedServer server) {
        waited = 0;
        Shot shot = SHOTS.get(index);
        server.execute(() -> {
            ServerLevel level = server.overworld();
            if (shot.setup() != null) {
                shot.setup().accept(level);
            }
            // Les vues dont le nom commence par « orbit_ » sont prises dans la dimension d'orbite, « moon_ » sur la Lune.
            String prefix = shot.name().startsWith("orbit_") ? "execute in orbitalis:orbit run "
                    : shot.name().startsWith("moon_") ? "execute in orbitalis:moon run "
                    : shot.name().startsWith("mars_") ? "execute in orbitalis:mars run "
                    : shot.name().startsWith("titan_") ? "execute in orbitalis:titan run "
                    : shot.name().startsWith("europa_") ? "execute in orbitalis:europa run " : "execute in minecraft:overworld run ";
            // Sur la Lune, sur Mars et sur Titan, les hauteurs sont comptées à partir du sol, là où il se trouve.
            double baseY = shot.name().startsWith("moon_") ? moonGround(level) : shot.name().startsWith("mars_") ? marsGround(level)
                    : shot.name().startsWith("titan_") ? titanGround(level)
                    : shot.name().startsWith("europa_") ? europaGround(level) : origin.getY();
            BlockPos base = origin;
            if (shot.name().startsWith("titan_lake") && titanLake != null) {
                base = titanLake;
                baseY = titanLake.getY();
            }
            if (shot.name().startsWith("site_") && site != null) {
                base = site;
                baseY = site.getY();
            }
            // Les vues de la carte de test : en coordonnées du monde, sans rien y bâtir.
            // Une vue sans place fixe (NaN) : sa préparation y place elle-même la caméra.
            if (Double.isNaN(shot.x())) {
                return;
            }
            if (shot.name().startsWith("tm_")) {
                String where = shot.name().startsWith("tm_proxima_orbit") ? "execute in orbitalis:proxima_orbit run "
                        : shot.name().startsWith("tm_proxima") ? "execute in orbitalis:proxima_b run "
                        : shot.name().equals("tm_station") || shot.name().startsWith("tm_orbit")
                        ? "execute in orbitalis:orbit run " : "execute in minecraft:overworld run ";
                run(level, (where + "tp @a %.2f %.2f %.2f %.1f %.1f").formatted(shot.x(), shot.y(), shot.z(), shot.yaw(), shot.pitch())
                        .replace(',', '.'));
                return;
            }
            if (shot.name().startsWith("inside_") && inside != null) {
                run(level, "tp @a %.2f %.2f %.2f %.1f %.1f".formatted(inside.x, inside.y, inside.z, insideYaw + shot.yaw(), shot.pitch()).replace(',', '.'));
                return;
            }
            run(level, (prefix + "tp @a %.2f %.2f %.2f %.1f %.1f").formatted(base.getX() + shot.x(), baseY + shot.y(),
                    base.getZ() + shot.z(), shot.yaw(), shot.pitch()).replace(',', '.'));
        });
    }

    /** Hauteur du sol lunaire à l'origine de la scène. */
    private static double moonGround(ServerLevel level) {
        ServerLevel moon = level.getServer().getLevel(SpaceRules.MOON);
        if (moon == null) {
            return origin.getY();
        }
        moon.getChunk(origin.getX() >> 4, origin.getZ() >> 4);
        return moon.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, origin.getX(), origin.getZ());
    }

    /** Hauteur du sol martien à l'origine de la scène. */
    private static double marsGround(ServerLevel level) {
        ServerLevel mars = level.getServer().getLevel(SpaceRules.MARS);
        if (mars == null) {
            return origin.getY();
        }
        mars.getChunk(origin.getX() >> 4, origin.getZ() >> 4);
        return mars.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, origin.getX(), origin.getZ());
    }

    /** Rive d'un lac de méthane, trouvée par la vue des lacs. */
    private static BlockPos titanLake;

    /** Cherche, en cercles autour de l'origine, une colonne de Titan dont la surface est du méthane liquide. */
    private static BlockPos findTitanLake(ServerLevel titan) {
        for (int ring = 0; ring <= 12; ring++) {
            for (int k = 0; k < Math.max(1, ring * 8); k++) {
                double angle = 2.0 * Math.PI * k / Math.max(1, ring * 8);
                int x = origin.getX() + (int) Math.round(Math.cos(angle) * ring * 64);
                int z = origin.getZ() + (int) Math.round(Math.sin(angle) * ring * 64);
                titan.getChunk(x >> 4, z >> 4);
                int top = titan.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z);
                if (titan.getFluidState(new BlockPos(x, top - 1, z)).getType().isSame(com.orbitalis.core.registry.ModFluids.LIQUID_METHANE.get())) {
                    return new BlockPos(x, top, z);
                }
            }
        }
        return null;
    }

    /** Hauteur de la banquise d'Europe à l'origine de la scène. */
    private static double europaGround(ServerLevel level) {
        ServerLevel europa = level.getServer().getLevel(SpaceRules.EUROPA);
        if (europa == null) {
            return origin.getY();
        }
        europa.getChunk(origin.getX() >> 4, origin.getZ() >> 4);
        return europa.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, origin.getX(), origin.getZ());
    }

    /** Le drone posé par la vue du drone, que la suivante envoie en vol. */
    private static com.orbitalis.phase3.exploration.RotorDroneEntity previewDrone;

    /** Hauteur du sol de Titan (ou de la surface d'un lac) à l'origine de la scène. */
    private static double titanGround(ServerLevel level) {
        ServerLevel titan = level.getServer().getLevel(SpaceRules.TITAN);
        if (titan == null) {
            return origin.getY();
        }
        titan.getChunk(origin.getX() >> 4, origin.getZ() >> 4);
        return titan.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, origin.getX(), origin.getZ());
    }

    private static void run(ServerLevel level, String command) {
        level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack().withSuppressedOutput(), command);
    }

    /** Pose un bloc de la scène, à une position relative à son origine. */
    private static void set(ServerLevel level, int x, int y, int z, String block) {
        run(level, "setblock %d %d %d %s".formatted(origin.getX() + x, origin.getY() + y, origin.getZ() + z, block));
    }

    private static void fill(ServerLevel level, int x0, int y0, int z0, int x1, int y1, int z1, String block) {
        run(level, "fill %d %d %d %d %d %d %s".formatted(origin.getX() + x0, origin.getY() + y0, origin.getZ() + z0,
                origin.getX() + x1, origin.getY() + y1, origin.getZ() + z1, block));
    }

    /** Journalise le refus d'une commande de la séquence, s'il y en a un. */
    private static void say(net.minecraft.network.chat.Component refusal) {
        if (refusal != null) {
            com.orbitalis.Orbitalis.LOGGER.info("APERCU commande refusée : {}", refusal.getString());
        }
    }

    /** Verse un fluide dans le réservoir modulable de la scène situé là. */
    private static void pour(ServerLevel level, int x, int y, int z, net.minecraft.world.level.material.Fluid fluid, int amount) {
        if (level.getBlockEntity(at(x, y, z)) instanceof com.orbitalis.phase1.fuel.FluidTankBlockEntity tank && tank.handler() != null) {
            try (var transaction = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
                tank.handler().insert(net.neoforged.neoforge.transfer.fluid.FluidResource.of(fluid), amount, transaction);
                transaction.commit();
            }
        }
    }

    private static BlockPos at(int x, int y, int z) {
        return origin.offset(x, y, z);
    }

    // ------------------------------------------------------------------
    // Scène de démonstration
    // ------------------------------------------------------------------

    private static void buildShots() {
        SHOTS.clear();
        SHOTS.add(new Shot("arrival", level -> {
            run(level, "gamemode spectator @a");
            run(level, "time set noon");
            run(level, "weather clear");
        }, 8.0, 12.0, -8.0, 0.0F, 30.0F, 100));

        // Les machines, en rang, face avant vers le nord (vers la caméra), en deux vues rapprochées.
        SHOTS.add(new Shot("machines_a", level -> {
            run(level, "gamemode spectator @a");
            run(level, "time set noon");
            run(level, "weather clear");
            fill(level, -24, -1, -24, 40, -1, 40, "minecraft:smooth_stone");
            // La commande de remplissage est limitée en volume : on vide la scène par tranches.
            for (int y = 0; y <= 90; y += 6) {
                fill(level, -24, y, -24, 40, y + 5, 40, "minecraft:air");
            }
            run(level, "kill @e[type=item]");
            String[] machines = {"combustion_generator", "arc_foundry", "rolling_mill", "electrolyzer", "cryogenic_liquefier",
                    "refinery", "cryogenic_tank", "launch_console", "mission_display"};
            for (int i = 0; i < machines.length; i++) {
                set(level, -10 + 2 * i, 0, 0, "orbitalis:" + machines[i]);
            }
        }, -5.5, 1.9, -3.4, 0.0F, 28.0F, 80));
        SHOTS.add(new Shot("machines_b", null, 5.5, 1.9, -3.4, 0.0F, 28.0F, 20));

        // Grand pas de tir 5×5 bâti selon le plan complet, avec le lanceur orbital.
        SHOTS.add(new Shot("large_pad", level -> {
            buildPad(level, 22, 1, 22, 2);
            placeRocket(level, 22, 1, 22, 2);
            if (level.getBlockEntity(at(22, 1, 22)) instanceof LaunchTableBlockEntity table) {
                table.refresh(level);
            }
        }, 37.0, 14.0, 7.0, 45.0F, 4.0F, 90));
        SHOTS.add(new Shot("large_pad_close", null, 14.0, 12.0, 14.0, -45.0F, 6.0F, 20));
        // Sous le tablier, entre ses appuis.
        SHOTS.add(new Shot("pad_under", null, 26.5, -0.9, 26.5, 135.0F, -25.0F, 12));
        // Le bras ombilical de près : ses conduites et son chemin de câbles.
        SHOTS.add(new Shot("arm_close", null, 20.9, 4.8, 23.6, 165.0F, 25.0F, 12));
        // Le bras et la passerelle de profil, de près, sur toute la hauteur de la fusée.
        SHOTS.add(new Shot("arm_side", null, 20.5, 6.0, 26.0, 180.0F, -10.0F, 12));
        SHOTS.add(new Shot("arm_side_high", null, 20.5, 14.0, 26.0, 180.0F, -10.0F, 12));
        SHOTS.add(new Shot("arm_top", null, 21.0, 6.5, 22.5, 90.0F, 80.0F, 12));
        // Une tour à escalier, vue par sa trémie puis de l'intérieur, au sommet.
        SHOTS.add(new Shot("stair_tower", level -> {
            buildPad(level, -14, 1, 36, 1);
            if (level.getBlockEntity(at(-14, 1, 36)) instanceof LaunchTableBlockEntity table) {
                table.commission(true);
                table.refresh(level);
            }
        }, -17.5, 14.0, 39.5, 180.0F, 62.0F, 40));
        SHOTS.add(new Shot("stair_tower_side", null, -8.5, 2.5, 30.5, 45.0F, 5.0F, 12));
        SHOTS.add(new Shot("pad_above", null, 17.5, 8.0, 27.5, -128.0F, 50.0F, 20));
        SHOTS.add(new Shot("mast_foot", null, 26.2, 0.6, 26.8, 135.0F, 5.0F, 12));
        SHOTS.add(new Shot("mast_top", null, 26.5, 5.5, 27.5, 140.0F, -8.0F, 12));
        SHOTS.add(new Shot("tower_foot", null, 17.0, 2.4, 24.5, -150.0F, 15.0F, 20));
        // Vue de profil, depuis le sud : les bras tendus entre la tour et la fusée.
        SHOTS.add(new Shot("arms_static", null, 20.5, 9.0, 31.0, 180.0F, 0.0F, 20));
        // Détails du pas de tir : la bouche du carneau, la table sous la fusée, l'escalier et les paliers de la tour.
        SHOTS.add(new Shot("pad_trench", null, 27.5, 2.2, 24.0, 116.0F, 20.0F, 12));
        SHOTS.add(new Shot("pad_table", null, 23.6, 2.0, 23.8, 140.0F, 30.0F, 12));
        // La tour : son entrée et l'ascenseur, vus de l'arrière ; le portique et sa passerelle ; le sommet.
        SHOTS.add(new Shot("tower_back", null, 9.5, 3.2, 26.5, -55.0F, 4.0F, 12));
        // La cabine de l'ascenseur, vue par la porte arrière de la tour, puis de l'intérieur.
        SHOTS.add(new Shot("elevator_door", null, 12.0, 0.1, 22.5, -90.0F, 0.0F, 12));
        SHOTS.add(new Shot("elevator_inside", null, 16.4, 0.1, 22.5, -90.0F, 5.0F, 12));
        SHOTS.add(new Shot("elevator_wall", null, 17.5, 0.1, 23.4, 180.0F, 0.0F, 12));
        SHOTS.add(new Shot("tower_gate", null, 23.5, 13.6, 27.5, 140.0F, 14.0F, 12));
        SHOTS.add(new Shot("tower_roof", null, 12.0, 30.5, 17.0, -50.0F, 28.0F, 12));
        // Un pas de tir en cours de montage : le plan montre les blocs qui manquent encore, en maquettes.
        SHOTS.add(new Shot("pad_plan", level -> {
            BlockPos pos = at(-10, 1, -14);
            level.setBlockAndUpdate(pos, ModBlocks.LAUNCH_TABLE.get().defaultBlockState());
            if (level.getBlockEntity(pos) instanceof LaunchTableBlockEntity table) {
                PadPlan plan = new PadPlan(1, false, Direction.EAST);
                table.setPlan(plan);
                // Le tiers du plan est déjà posé.
                int count = 0;
                for (PadPlan.Piece piece : plan.pieces()) {
                    if (count++ % 3 == 0 && piece.offset().getY() <= 0) {
                        level.setBlock(pos.offset(piece.offset()), piece.display(), 3);
                    }
                }
            }
        }, -6.5, 4.5, -10.5, 118.0F, 30.0F, 40));
        SHOTS.add(new Shot("pad_plan_far", null, 0.0, 9.0, -3.0, 128.0F, 8.0F, 12));
        // L'écran du kit de pas de tir.
        SHOTS.add(new Shot("kit_screen", level -> net.minecraft.client.Minecraft.getInstance().execute(
                () -> net.minecraft.client.Minecraft.getInstance().gui.setScreen(
                        new com.orbitalis.client.screen.PadKitScreen(null, Direction.EAST, 2, false))),
                -4.5, 5.0, -8.5, 135.0F, 28.0F, 30));
        SHOTS.add(new Shot("kit_closed", level -> net.minecraft.client.Minecraft.getInstance().execute(
                () -> net.minecraft.client.Minecraft.getInstance().gui.setScreen(null)), -4.5, 5.0, -8.5, 135.0F, 28.0F, 10));
        // Fusées : les blocs de construction en rang, et l'ordinateur de conception derrière eux.
        SHOTS.add(new Shot("rocket_parts", level -> {
            String[] parts = {"rocket_engine_solid", "rocket_engine_pressure", "rocket_engine_kerolox", "rocket_engine_hydrolox",
                    "rocket_tank_steel", "rocket_tank_aluminum", "rocket_tank_alloy", "rocket_tank_composite", "rocket_tank_cryo",
                    "rocket_motor_casing", "rocket_interstage", "rocket_payload_bay", "rocket_fairing", "rocket_nose", "crew_cabin",
                    "cabin_roof", "escape_tower", "rocket_fuselage", "rocket_raceway", "flight_computer", "rocket_engine_booster",
                    "rocket_engine_hypergolic", "rocket_engine_vacuum", "rocket_ion_thruster", "rocket_solar_sail", "rocket_tank_titanium",
                    "rocket_tank_stainless", "rocket_engine_nuclear"};
            for (int i = 0; i < parts.length; i++) {
                set(level, -19 + 2 * i, 0, -6, "orbitalis:" + parts[i]);
            }
            set(level, 0, 0, -12, "orbitalis:design_computer[facing=south]");
        }, -9.5, 2.4, -1.5, 180.0F, 24.0F, 30));
        SHOTS.add(new Shot("rocket_parts_b", null, 10.5, 2.4, -1.5, 180.0F, 24.0F, 12));
        SHOTS.add(new Shot("rocket_parts_c", null, 29.5, 2.4, -1.5, 180.0F, 24.0F, 12));
        SHOTS.add(new Shot("design_computer", null, 0.5, 2.2, -7.5, 180.0F, 12.0F, 12));
        // L'écran de conception, sur le lanceur orbital.
        SHOTS.add(new Shot("design_screen", level -> {
            // En créatif, l'écran propose toutes les pièces, étudiées ou non.
            run(level, "gamemode creative @a");
            net.minecraft.client.Minecraft.getInstance().execute(() -> {
            com.orbitalis.client.screen.DesignScreen screen = new com.orbitalis.client.screen.DesignScreen();
            screen.select(3);
            net.minecraft.client.Minecraft.getInstance().gui.setScreen(screen);
            });
        }, 0.5, 2.2, -7.5, 180.0F, 12.0F, 30));
        // L'éditeur sur une fusée sur mesure : un premier étage kérosène, un étage hypergolique, un propulseur ionique.
        SHOTS.add(new Shot("design_custom", level -> {
            // En créatif, l'écran propose toutes les pièces, étudiées ou non.
            run(level, "gamemode creative @a");
            net.minecraft.client.Minecraft.getInstance().execute(() -> {
            com.orbitalis.client.screen.DesignScreen screen = new com.orbitalis.client.screen.DesignScreen();
            screen.select(new com.orbitalis.phase1.rocket.RocketDraft(com.orbitalis.core.registry.ModItems.MARS_PROBE.get(), java.util.List.of(
                    new com.orbitalis.phase1.rocket.RocketDraft.Stage(com.orbitalis.core.registry.ModItems.KEROLOX_ENGINE.get(), 2,
                            com.orbitalis.core.registry.ModItems.ALUMINUM_LITHIUM_TANK.get(), 20),
                    new com.orbitalis.phase1.rocket.RocketDraft.Stage(com.orbitalis.core.registry.ModItems.HYPERGOLIC_ENGINE.get(), 2,
                            net.minecraft.world.item.Items.AIR, 0),
                    new com.orbitalis.phase1.rocket.RocketDraft.Stage(com.orbitalis.core.registry.ModItems.ION_THRUSTER.get(), 1,
                            net.minecraft.world.item.Items.AIR, 0))));
            net.minecraft.client.Minecraft.getInstance().gui.setScreen(screen);
            });
        }, 0.5, 2.2, -7.5, 180.0F, 12.0F, 30));
        // Une fusée bloquée : un seul moteur sous une fusée trop lourde, des réservoirs d'hydrogène sous un moteur cryogénique.
        SHOTS.add(new Shot("design_blocked", level -> {
            run(level, "gamemode creative @a");
            net.minecraft.client.Minecraft.getInstance().execute(() -> {
                com.orbitalis.client.screen.DesignScreen screen = new com.orbitalis.client.screen.DesignScreen();
                screen.select(new com.orbitalis.phase1.rocket.RocketDraft(com.orbitalis.core.registry.ModItems.TITAN_LANDER.get(), java.util.List.of(
                        new com.orbitalis.phase1.rocket.RocketDraft.Stage(com.orbitalis.core.registry.ModItems.KEROLOX_ENGINE.get(), 1,
                                com.orbitalis.core.registry.ModItems.ALUMINUM_TANK.get(), 40),
                        new com.orbitalis.phase1.rocket.RocketDraft.Stage(com.orbitalis.core.registry.ModItems.HYDROLOX_ENGINE.get(), 1,
                                com.orbitalis.core.registry.ModItems.HYDROGEN_TANK.get(), 8)), 0));
                net.minecraft.client.Minecraft.getInstance().gui.setScreen(screen);
            });
        }, 0.5, 2.2, -7.5, 180.0F, 12.0F, 30));
        for (boolean models : new boolean[] {true, false}) {
            SHOTS.add(new Shot(models ? "design_models" : "design_payloads", level -> {
                run(level, "gamemode creative @a");
                net.minecraft.client.Minecraft.getInstance().execute(() -> {
                    com.orbitalis.client.screen.DesignScreen screen = new com.orbitalis.client.screen.DesignScreen();
                    screen.select(3);
                    screen.showPopup(models);
                    net.minecraft.client.Minecraft.getInstance().gui.setScreen(screen);
                });
            }, 0.5, 2.2, -7.5, 180.0F, 12.0F, 30));
        }
        SHOTS.add(new Shot("design_closed", level -> {
            run(level, "gamemode spectator @a");
            net.minecraft.client.Minecraft.getInstance().execute(() -> net.minecraft.client.Minecraft.getInstance().gui.setScreen(null));
        }, 0.5, 2.2, -7.5, 180.0F, 12.0F, 10));
        // Une fusée en cours de construction sur le grand pas de tir : trois couches posées, le plan montre la suite.
        SHOTS.add(new Shot("rocket_plan", level -> {
            if (level.getBlockEntity(at(22, 1, 22)) instanceof LaunchTableBlockEntity table) {
                table.removeRocket();
                var template = com.orbitalis.phase1.rocket.RocketCatalog.all().get(3);
                table.applyBlueprint(com.orbitalis.phase1.rocket.RocketBlueprintItem.create(
                        com.orbitalis.phase1.rocket.RocketCatalog.build(template, 0, template.presets()),
                        net.minecraft.network.chat.Component.literal("Orbital")));
                for (var piece : table.rocketPieces()) {
                    if (piece.offset().getY() <= 3) {
                        level.setBlock(at(22, 1, 22).offset(piece.offset()), piece.block().defaultBlockState(), 3);
                    }
                }
            }
        }, 28.5, 9.5, 28.5, 135.0F, 30.0F, 50));
        // L'écran de chantier de cette fusée.
        SHOTS.add(new Shot("construction_screen", level -> {
            if (level.getBlockEntity(at(22, 1, 22)) instanceof LaunchTableBlockEntity table) {
                com.orbitalis.core.network.ConstructionPayload payload = table.construction(true);
                net.minecraft.client.Minecraft.getInstance().execute(() -> net.minecraft.client.Minecraft.getInstance().gui.setScreen(
                        new com.orbitalis.client.screen.ConstructionScreen(payload)));
            }
        }, 18.5, 3.0, 14.5, 135.0F, 20.0F, 30));
        // Une fusée à boosters latéraux dressée sur le grand pas de tir, vue de deux côtés, puis l'éditeur réglé ainsi.
        SHOTS.add(look("boosters_pad", level -> {
            if (level.getBlockEntity(at(22, 1, 22)) instanceof LaunchTableBlockEntity table) {
                table.removeRocket();
                table.takeBlueprint();
                fill(level, 20, 2, 20, 24, 48, 24, "minecraft:air");
                var template = com.orbitalis.phase1.rocket.RocketCatalog.all().get(3);
                // Un lanceur orbital raccourci : la tour de ce pas de tir n'est pas assez haute pour le modèle d'origine.
                net.minecraft.core.NonNullList<net.minecraft.world.item.ItemStack> slots = com.orbitalis.phase1.rocket.RocketCatalog.build(template, 0,
                        new int[] {16, 2});
                slots.set(com.orbitalis.phase1.rocket.RocketDesign.SLOT_BOOSTERS,
                        new net.minecraft.world.item.ItemStack(com.orbitalis.core.registry.ModItems.SOLID_BOOSTER.get(), 2));
                var boosted = com.orbitalis.phase1.rocket.RocketItem.create(slots);
                table.refresh(level);
                Orbitalis.LOGGER.info("APERCU boosters : place {} hauteur {}/{} posée {}", table.hasRoomFor(boosted),
                        com.orbitalis.phase1.spaceport.RocketShape.of(com.orbitalis.phase1.rocket.RocketItem.design(boosted)).height(),
                        table.getComplex().maxRocketHeight(), table.setRocket(boosted));
            }
        }, 33.5, 3.0, 22.5, 22.5, 6.0, 22.5, 40));
        SHOTS.add(look("boosters_pad_b", null, 22.5, 3.0, 33.5, 22.5, 6.0, 22.5, 10));
        SHOTS.add(new Shot("design_boosters", level -> {
            run(level, "gamemode creative @a");
            net.minecraft.client.Minecraft.getInstance().execute(() -> {
                com.orbitalis.client.screen.DesignScreen screen = new com.orbitalis.client.screen.DesignScreen();
                var template = com.orbitalis.phase1.rocket.RocketCatalog.all().get(3);
                var draft = com.orbitalis.phase1.rocket.RocketDraft.of(com.orbitalis.phase1.rocket.RocketCatalog.build(template, 0, template.presets()));
                screen.select(new com.orbitalis.phase1.rocket.RocketDraft(draft.payload(), draft.stages(), 2));
                net.minecraft.client.Minecraft.getInstance().gui.setScreen(screen);
            });
        }, 26.5, 1.0, 29.5, 135.0F, 12.0F, 30));
        SHOTS.add(new Shot("design_boosters_closed", level -> {
            run(level, "gamemode spectator @a");
            net.minecraft.client.Minecraft.getInstance().execute(() -> net.minecraft.client.Minecraft.getInstance().gui.setScreen(null));
        }, 26.5, 1.0, 29.5, 135.0F, 12.0F, 10));
        // L'écran du pas de tir, une fusée dressée : son bouton de copie du plan.
        SHOTS.add(new Shot("pad_screen", level -> net.minecraft.client.Minecraft.getInstance().execute(() -> net.minecraft.client.Minecraft.getInstance()
                .gui.setScreen(new com.orbitalis.client.screen.LaunchPadScreen(new com.orbitalis.core.network.LaunchPadInfoPayload(
                        true, 2, true, true, true, 12, 16, 12, 1, true, com.orbitalis.core.creative.CreativeRockets.all().get(2), 14, 4200, 9000, 0, 0)))),
                18.5, 3.0, 14.5, 135.0F, 20.0F, 30));
        SHOTS.add(new Shot("construction_closed", level -> net.minecraft.client.Minecraft.getInstance().execute(
                () -> net.minecraft.client.Minecraft.getInstance().gui.setScreen(null)), 18.5, 3.0, 14.5, 135.0F, 20.0F, 10));
        // Le méthalox : le réacteur de Sabatier, éteint et allumé, le moteur méthalox, le raccord de méthane et ses voisins.
        SHOTS.add(new Shot("methalox_blocks", level -> {
            run(level, "time set 6000");
            run(level, "weather clear");
            fill(level, 1, 0, 11, 11, 3, 13, "minecraft:air");
            set(level, 2, 0, 12, "orbitalis:sabatier_reactor[facing=south,lit=false]");
            set(level, 4, 0, 12, "orbitalis:sabatier_reactor[facing=south,lit=true]");
            set(level, 6, 0, 12, "orbitalis:rocket_engine_methalox[facing=south]");
            set(level, 8, 0, 12, "orbitalis:fuel_port[kind=ch4]");
            set(level, 9, 0, 12, "orbitalis:fuel_port[kind=he3]");
            set(level, 4, 0, 10, "orbitalis:rocket_engine_fusion[facing=south]");
            set(level, 10, 0, 12, "orbitalis:helium3_extractor[facing=south,lit=true]");
        }, 8.6, 1.4, 14.6, 180.0F, 22.0F, 20));
        // Le générateur d'air, à l'arrêt puis en marche (le second est pris avant que son bloc ne vérifie la pièce).
        SHOTS.add(new Shot("air_generator", level -> {
            fill(level, 1, 0, 28, 7, 3, 32, "minecraft:air");
            set(level, 2, 0, 30, "orbitalis:life_support[active=false]");
            set(level, 5, 0, 30, "orbitalis:life_support[active=true]");
        }, 3.9, -0.1, 33.6, 180.0F, 22.0F, 6));
        // Toutes les machines en rang, de face puis de dos (leur dos plein, où se branchent les câbles).
        SHOTS.add(new Shot("machines_front", level -> {
            fill(level, -14, -1, 38, 16, -1, 60, "minecraft:smooth_stone");
            fill(level, -14, 0, 38, 16, 5, 60, "minecraft:air");
            fill(level, -14, 6, 38, 16, 11, 60, "minecraft:air");
            String[] machines = {"combustion_generator", "arc_foundry", "rolling_mill", "electrolyzer", "cryogenic_liquefier", "refinery",
                    "sabatier_reactor", "helium3_extractor", "casimir_chamber", "fusion_reactor", "cryogenic_tank"};
            for (int i = 0; i < machines.length; i++) {
                set(level, -10 + 2 * i, 0, 50, "orbitalis:" + machines[i]);
            }
        }, 0.5, 2.6, 45.5, 0.0F, 24.0F, 60));
        SHOTS.add(new Shot("machines_back", null, 0.5, 2.6, 54.5, 180.0F, 24.0F, 30));
        SHOTS.add(new Shot("machines_close_a", null, -5.0, 0.6, 47.2, -25.0F, 22.0F, 20));
        SHOTS.add(new Shot("machines_close_b", null, 3.0, 0.6, 47.2, -25.0F, 22.0F, 20));
        SHOTS.add(new Shot("machines_close_back", null, -2.5, 0.6, 52.8, 155.0F, 22.0F, 20));
        // Le terminal de station, de près : son écran et son clavier en relief.
        SHOTS.add(new Shot("terminal_close", level -> {
            fill(level, 1, 0, 23, 5, 3, 27, "minecraft:air");
            set(level, 3, 0, 25, "orbitalis:station_terminal[facing=south]");
        }, 3.5, -0.4, 26.45, 180.0F, 50.0F, 20));
        // Le réacteur à fusion et la chambre de Casimir, éteints puis en marche.
        SHOTS.add(new Shot("fusion_power", level -> {
            run(level, "time set 6000");
            run(level, "weather clear");
            fill(level, 1, 0, 15, 11, 3, 17, "minecraft:air");
            set(level, 2, 0, 16, "orbitalis:fusion_reactor[facing=south,lit=false]");
            set(level, 4, 0, 16, "orbitalis:fusion_reactor[facing=south,lit=true]");
            set(level, 7, 0, 16, "orbitalis:casimir_chamber[facing=south,lit=false]");
            set(level, 9, 0, 16, "orbitalis:casimir_chamber[facing=south,lit=true]");
            // Sans gaz ni perle, ils s'éteindraient au premier tick : on leur donne de quoi tourner pendant la prise.
            if (level.getBlockEntity(at(4, 0, 16)) instanceof com.orbitalis.phase3.propulsion.FusionReactorBlockEntity reactor) {
                reactor.getTanks().setFluid(0, new net.neoforged.neoforge.fluids.FluidStack(com.orbitalis.core.registry.ModFluids.HELIUM_3.get(), 1000));
                reactor.getTanks().setFluid(1, new net.neoforged.neoforge.fluids.FluidStack(com.orbitalis.core.registry.ModFluids.HYDROGEN.get(), 8000));
            }
            if (level.getBlockEntity(at(9, 0, 16)) instanceof com.orbitalis.phase1.machines.ProcessingMachineBlockEntity chamber) {
                chamber.getInventory().setStack(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ENDER_PEARL, 16));
                chamber.getEnergy().generate(20_000);
            }
        }, 6.0, 1.5, 19.0, 180.0F, 22.0F, 20));
        // Les blocs du pas de tir posés seuls, tournés dans plusieurs sens.
        SHOTS.add(new Shot("pad_blocks", level -> {
            set(level, 2, 0, 8, "orbitalis:flame_trench[facing=east]");
            set(level, 4, 0, 8, "orbitalis:flame_trench[facing=south]");
            set(level, 6, 0, 8, "orbitalis:umbilical_arm[facing=east]");
            set(level, 8, 0, 8, "orbitalis:umbilical_arm[facing=north]");
            set(level, 10, 0, 8, "orbitalis:service_tower");
            set(level, 10, 1, 8, "orbitalis:tower_frame");
            set(level, 18, 0, 8, "orbitalis:tower_stairs");
            set(level, 20, 0, 8, "orbitalis:tower_landing");
            set(level, 20, 1, 8, "orbitalis:tower_railing");
            set(level, 22, 0, 8, "orbitalis:pad_support");
            set(level, 24, 0, 8, "orbitalis:deluge_pipe");
            set(level, 26, 0, 8, "orbitalis:fuel_port[kind=rp1]");
            set(level, 28, 0, 8, "orbitalis:fuel_port[kind=lox]");
            set(level, 30, 0, 8, "orbitalis:fuel_port[kind=lh2]");
            ModBlocks.BOARDING_GATE.get().place(level, at(16, 0, 14), Direction.EAST);
            set(level, 12, 0, 8, "orbitalis:lightning_mast[facing=west]");
            set(level, 12, 1, 8, "orbitalis:lightning_mast[facing=west]");
            set(level, 14, 0, 8, "orbitalis:flame_deflector");
            set(level, 14, 1, 8, "orbitalis:launch_table");
            set(level, 0, 0, 8, "orbitalis:launch_pad");
            set(level, -2, 0, 8, "orbitalis:pad_floodlight");
            set(level, -4, 0, 8, "orbitalis:pad_floodlight[facing=north,diagonal=true,lit=true]");
        }, 16.0, 3.6, 2.0, 0.0F, 22.0F, 30));
        SHOTS.add(new Shot("pad_blocks_b", null, 3.0, 3.0, 2.5, 0.0F, 22.0F, 12));
        // De près : la charpente, la colonne technique et le bras ; le bloc de ravitaillement vu de dessus ; le portique.
        SHOTS.add(new Shot("pad_blocks_tower", null, 8.5, 0.6, 5.2, 0.0F, 12.0F, 12));
        SHOTS.add(new Shot("pad_blocks_top", null, 9.0, 2.6, 6.0, -20.0F, 50.0F, 12));
        SHOTS.add(new Shot("pad_blocks_port", null, 28.5, 1.4, 6.0, 0.0F, 40.0F, 12));
        SHOTS.add(new Shot("pad_blocks_gate", null, 12.5, 0.4, 14.5, -90.0F, -5.0F, 12));
        SHOTS.add(new Shot("pad_blocks_gate_side", null, 13.0, 1.5, 9.5, -40.0F, 5.0F, 12));
        // Les trois grands postes de travail, de face puis de trois quarts arrière.
        SHOTS.add(new Shot("workstations", level -> {
            set(level, -14, 0, -20, "orbitalis:design_computer[facing=south]");
            set(level, -9, 0, -20, "orbitalis:launch_console[facing=south]");
            set(level, -4, 0, -20, "orbitalis:tracking_station[facing=south]");
        }, -8.5, 2.6, -13.5, 180.0F, 14.0F, 30));
        SHOTS.add(new Shot("workstations_close", null, -8.5, 2.0, -16.5, 180.0F, 10.0F, 12));
        SHOTS.add(new Shot("workstations_side", null, -16.5, 2.4, -17.0, -125.0F, 18.0F, 12));
        SHOTS.add(new Shot("workstations_back", null, -3.0, 3.2, -24.0, 60.0F, 16.0F, 12));
        // L'établi d'ingénieur, et à côté de lui les deux blocs moteurs à turbopompes : combustion étagée, puis kérosène.
        SHOTS.add(new Shot("workbench", level -> {
            set(level, -24, 0, -20, "orbitalis:engineer_workbench[facing=south]");
            set(level, -28, 0, -20, "orbitalis:rocket_engine_staged");
            set(level, -30, 0, -20, "orbitalis:rocket_engine_kerolox");
        }, -25.5, 1.8, -17.6, 180.0F, 12.0F, 20));
        SHOTS.add(new Shot("workbench_side", null, -22.2, 2.0, -18.0, 120.0F, 14.0F, 12));
        // Les miniatures, comme dans la barre d'objets.
        SHOTS.add(new Shot("item_icons", level -> net.minecraft.client.Minecraft.getInstance().execute(() -> {
            java.util.List<net.minecraft.world.item.ItemStack> stacks = new java.util.ArrayList<>();
            for (net.minecraft.world.level.block.Block block : java.util.List.of(ModBlocks.DESIGN_COMPUTER.get(), ModBlocks.LAUNCH_CONSOLE.get(),
                    ModBlocks.TRACKING_STATION.get(), ModBlocks.TOWER_STAIRS.get(), ModBlocks.TOWER_RAILING.get(),
                    ModBlocks.LIGHTNING_MAST.get(), ModBlocks.PAD_FLOODLIGHT.get(), ModBlocks.TOWER_ELEVATOR.get(),
                    ModBlocks.TOWER_FRAME.get(), ModBlocks.TOWER_DECK.get(), ModBlocks.SUIT_WORKBENCH.get(), ModBlocks.ENGINEER_WORKBENCH.get(),
                    ModBlocks.DRAFTING_TABLE.get())) {
                stacks.add(new net.minecraft.world.item.ItemStack(block));
            }
            // Les composants de fabrication.
            for (var item : com.orbitalis.core.registry.ModItems.ITEMS.getEntries()) {
                if (item.get() instanceof com.orbitalis.core.item.ComponentItem) {
                    stacks.add(new net.minecraft.world.item.ItemStack(item.get()));
                }
            }
            net.minecraft.client.Minecraft.getInstance().gui.setScreen(new ItemSheetScreen(stacks));
        }), -8.5, 2.6, -25.5, 180.0F, 14.0F, 20));
        // Les objets de la propulsion avancée et du vaisseau interstellaire, en case d'inventaire.
        SHOTS.add(new Shot("ship_icons", level -> net.minecraft.client.Minecraft.getInstance().execute(() -> {
            java.util.List<net.minecraft.world.item.ItemStack> stacks = new java.util.ArrayList<>();
            for (net.minecraft.world.level.ItemLike item : java.util.List.<net.minecraft.world.level.ItemLike>of(ModBlocks.WARP_CORE.get(),
                    ModBlocks.HIBERNATION_POD.get(), ModBlocks.RING_DRIVE.get(), ModBlocks.SHIP_HELM.get(), ModBlocks.SHIPYARD_CONSOLE.get(),
                    com.orbitalis.core.registry.ModItems.SHIPYARD_MODULE.get(), com.orbitalis.core.registry.ModItems.EXOTIC_MATTER.get(),
                    ModBlocks.FUSION_REACTOR.get(), ModBlocks.CASIMIR_CHAMBER.get())) {
                stacks.add(new net.minecraft.world.item.ItemStack(item));
            }
            ModBlocks.CREATIVE_TANKS.forEach(tank -> stacks.add(new net.minecraft.world.item.ItemStack(tank.get())));
            net.minecraft.client.Minecraft.getInstance().gui.setScreen(new ItemSheetScreen(stacks));
        }), -8.5, 2.6, -25.5, 180.0F, 14.0F, 20));
        // Les machines, en case d'inventaire : on doit les distinguer d'un coup d'œil.
        SHOTS.add(new Shot("machine_icons", level -> net.minecraft.client.Minecraft.getInstance().execute(() -> {
            java.util.List<net.minecraft.world.item.ItemStack> stacks = new java.util.ArrayList<>();
            for (String name : new String[] {"combustion_generator", "arc_foundry", "rolling_mill", "electrolyzer", "cryogenic_liquefier",
                    "refinery", "sabatier_reactor", "helium3_extractor", "casimir_chamber", "fusion_reactor", "cryogenic_tank", "flight_computer"}) {
                stacks.add(new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(
                        com.orbitalis.Orbitalis.id(name))));
            }
            net.minecraft.client.Minecraft.getInstance().gui.setScreen(new ItemSheetScreen(stacks));
        }), -8.5, 2.6, -25.5, 180.0F, 14.0F, 20));
        SHOTS.add(new Shot("item_icons_closed", level -> net.minecraft.client.Minecraft.getInstance().execute(
                () -> net.minecraft.client.Minecraft.getInstance().gui.setScreen(null)), -8.5, 2.6, -25.5, 180.0F, 14.0F, 6));
        // L'écran de l'établi de combinaison : la combinaison blanche posée, une carte en place, deux modules dans le sac.
        SHOTS.add(new Shot("bench_screen", level -> net.minecraft.client.Minecraft.getInstance().execute(() -> {
            var minecraft = net.minecraft.client.Minecraft.getInstance();
            var inventory = minecraft.player.getInventory();
            var menu = new com.orbitalis.phase2.survival.SuitWorkbenchMenu(0, inventory);
            var helmet = new net.minecraft.world.item.ItemStack(com.orbitalis.core.registry.ModItems.ADVANCED_SPACE_HELMET.get());
            com.orbitalis.phase2.survival.SpaceSuits.install(helmet, com.orbitalis.phase2.survival.SuitModule.MAP);
            menu.getSlot(0).set(helmet);
            menu.getSlot(1).set(new net.minecraft.world.item.ItemStack(com.orbitalis.core.registry.ModItems.ADVANCED_SPACESUIT.get()));
            menu.getSlot(2).set(new net.minecraft.world.item.ItemStack(com.orbitalis.core.registry.ModItems.ADVANCED_SPACE_LEGGINGS.get()));
            inventory.setItem(9, new net.minecraft.world.item.ItemStack(com.orbitalis.core.registry.ModItems.SUIT_MODULE_SENSOR.get()));
            inventory.setItem(10, new net.minecraft.world.item.ItemStack(com.orbitalis.core.registry.ModItems.SUIT_MODULE_THRUSTERS.get()));
            minecraft.gui.setScreen(new com.orbitalis.client.screen.SuitWorkbenchScreen(menu, inventory,
                    net.minecraft.network.chat.Component.translatable("container.orbitalis.suit_workbench")));
        }), -8.5, 2.6, -25.5, 180.0F, 14.0F, 20));
        SHOTS.add(new Shot("bench_screen_closed", level -> net.minecraft.client.Minecraft.getInstance().execute(
                () -> net.minecraft.client.Minecraft.getInstance().gui.setScreen(null)), -8.5, 2.6, -25.5, 180.0F, 14.0F, 6));
        // De nuit : les projecteurs des mâts éclairent le pied de la fusée, les balises clignotent.
        SHOTS.add(new Shot("pad_night", level -> run(level, "time set midnight"), 30.5, 9.0, 31.5, 135.0F, 22.0F, 110));
        SHOTS.add(new Shot("pad_night_far", null, 37.0, 14.0, 7.0, 45.0F, 4.0F, 12));
        // Les LED de près, à trois instants : la balise s'allume, brille, puis retombe ; les projecteurs de face et de dos.
        SHOTS.add(new Shot("mast_night", null, 26.5, 5.5, 27.5, 140.0F, -8.0F, 7));
        SHOTS.add(new Shot("mast_night_b", null, 26.5, 5.5, 27.5, 140.0F, -8.0F, 9));
        SHOTS.add(new Shot("mast_night_c", null, 26.5, 5.5, 27.5, 140.0F, -8.0F, 11));
        SHOTS.add(new Shot("lamps_front", null, 24.5, 1.2, 21.5, 0.0F, -40.0F, 8));
        SHOTS.add(new Shot("pad_day", level -> run(level, "time set noon"), 30.5, 9.0, 31.5, 135.0F, 22.0F, 90));
        // Les deux tailles extrêmes : le plus grand pas de tir (11×11) avec le lanceur lunaire, et le plus petit
        // (3×3), surélevé d'un bloc pour montrer ses piles.
        SHOTS.add(new Shot("huge_pad", level -> {
            fill(level, 46, -1, 8, 74, -1, 36, "minecraft:smooth_stone");
            for (int y = 0; y <= 66; y += 6) {
                fill(level, 46, y, 8, 74, y + 5, 36, "minecraft:air");
            }
            buildPad(level, 60, 1, 22, 5);
            placeRocket(level, 60, 1, 22, 7);
            buildPad(level, 10, 1, 14, 1);
            placeRocket(level, 10, 1, 14, 0);
        }, 86.0, 30.0, -4.0, 45.0F, 0.0F, 90));
        SHOTS.add(new Shot("huge_deck", null, 69.0, 9.0, 31.0, 135.0F, 35.0F, 12));
        SHOTS.add(new Shot("huge_foot", null, 51.5, 3.0, 26.5, -143.0F, 5.0F, 12));
        SHOTS.add(new Shot("small_pad", null, 13.6, 2.4, 17.6, 135.0F, 8.0F, 12));

        // Une dalle cassée : la structure se défait et les blocs d'origine réapparaissent.
        SHOTS.add(new Shot("pad_broken", level -> set(level, 24, 0, 24, "minecraft:air"), 17.5, 8.0, 27.5, -128.0F, 50.0F, 70));
        // La dalle reposée : le pas de tir se reforme.
        SHOTS.add(new Shot("pad_restored", level -> set(level, 24, 0, 24, "orbitalis:launch_pad"), 17.5, 8.0, 27.5, -128.0F, 50.0F, 70));

        // Murs d'écrans de quatre tailles, tournés vers le nord, reliés au grand pas de tir ; un cinquième non relié.
        SHOTS.add(new Shot("walls_idle", level -> {
            int[][] walls = {{-20, 3, 2}, {-15, 4, 2}, {-9, 5, 3}, {-2, 7, 4}};
            for (int[] wall : walls) {
                fill(level, wall[0], 1, 30, wall[0] + wall[1] - 1, wall[2], 30, "orbitalis:mission_display[facing=north]");
                for (int dx = 0; dx < wall[1]; dx++) {
                    for (int dy = 0; dy < wall[2]; dy++) {
                        if (level.getBlockEntity(at(wall[0] + dx, 1 + dy, 30)) instanceof MissionDisplayBlockEntity display) {
                            display.setLink(at(22, 1, 22));
                        }
                    }
                }
            }
            fill(level, 7, 1, 30, 10, 2, 30, "orbitalis:mission_display[facing=north]");
        }, -5.5, 3.0, 15.0, 0.0F, 2.0F, 60));
        SHOTS.add(new Shot("walls_small", null, -15.0, 2.2, 25.0, 0.0F, 0.0F, 20));
        SHOTS.add(new Shot("walls_large", null, 0.5, 3.0, 24.0, 0.0F, 0.0F, 20));

        // Séquence de lancement, vue de près : bras qui pivotent, tour qui bascule, décollage.
        SHOTS.add(new Shot("countdown_arms", level -> {
            if (level.getBlockEntity(at(22, 1, 22)) instanceof LaunchTableBlockEntity table) {
                table.setReliable(true);
                table.command(com.orbitalis.phase1.spaceport.LaunchStep.VALVES, "preview");
                table.fill(ModFluids.RP1.get(), 1_000_000, false);
                table.fill(ModFluids.LIQUID_OXYGEN.get(), 1_000_000, false);
                table.startCountdown("preview");
            }
        }, 20.5, 9.0, 31.0, 180.0F, 0.0F, 410));
        SHOTS.add(new Shot("countdown_tilt", null, 14.0, 12.0, 14.0, -45.0F, 6.0F, 110));
        // Déluge, allumage et décollage vus du côté où débouche le carneau, puis de loin.
        SHOTS.add(new Shot("deluge", null, 30.0, 4.0, 27.0, 125.0F, 15.0F, 16));
        SHOTS.add(new Shot("ignition", null, 30.0, 4.0, 27.0, 125.0F, 15.0F, 50));
        SHOTS.add(new Shot("liftoff", null, 37.0, 14.0, 7.0, 45.0F, -8.0F, 44));
        SHOTS.add(new Shot("ascent", null, 37.0, 14.0, 7.0, 45.0F, -35.0F, 60));
        // Plus haut : la fusée s'est couchée, sa poussée l'emmène de côté, et son sillage dessine sa courbe.
        SHOTS.add(new Shot("ascent_far", null, 60.0, 14.0, -20.0, 45.0F, -50.0F, 100));
        SHOTS.add(new Shot("aftermath", null, 30.0, 4.0, 27.0, 125.0F, 15.0F, 200));
        SHOTS.add(new Shot("walls_flight_small", null, -15.0, 2.2, 25.0, 0.0F, 0.0F, 30));
        SHOTS.add(new Shot("walls_flight_large", null, 0.5, 3.0, 24.0, 0.0F, 0.0F, 20));

        // Après le vol : le pas de tir vide, sa tour redressée et son bras au repos.
        SHOTS.add(new Shot("pad_idle", null, 14.0, 12.0, 14.0, -45.0F, 6.0F, 400));
        SHOTS.add(new Shot("pad_idle_side", null, 20.5, 6.0, 31.0, 180.0F, 0.0F, 20));

        // Écran du centre de contrôle, ouvert depuis un pupitre relié au grand pas de tir.
        SHOTS.add(new Shot("console_screen", level -> {
            set(level, 22, 0, 6, "orbitalis:launch_console");
            // L'écran est ouvert directement : le pupitre vient d'être posé, le client ne le connaît peut-être pas encore.
            com.orbitalis.core.network.LaunchStatusPayload status = LaunchConsoleBlock.status(level, at(22, 0, 6), true);
            net.minecraft.client.Minecraft.getInstance().execute(
                    () -> net.minecraft.client.Minecraft.getInstance().gui.setScreen(new com.orbitalis.client.screen.LaunchConsoleScreen(status)));
        }, 37.0, 14.0, 7.0, 45.0F, 4.0F, 60));
        // La fenêtre « Go / No-Go » du centre de contrôle, puis de nouveau l'écran, sur la maquette du pas de tir.
        SHOTS.add(new Shot("console_gonogo", level -> net.minecraft.client.Minecraft.getInstance().execute(() -> {
            if (net.minecraft.client.Minecraft.getInstance().gui.screen() instanceof com.orbitalis.client.screen.LaunchConsoleScreen console) {
                console.showGoWindow(true);
            }
        }), 37.0, 14.0, 7.0, 45.0F, 4.0F, 20));
        SHOTS.add(new Shot("console_pad", level -> net.minecraft.client.Minecraft.getInstance().execute(() -> {
            if (net.minecraft.client.Minecraft.getInstance().gui.screen() instanceof com.orbitalis.client.screen.LaunchConsoleScreen console) {
                console.showGoWindow(false);
            }
        }), 37.0, 14.0, 7.0, 45.0F, 4.0F, 30));

        // Le centre de contrôle suivi depuis le pupitre : le plein, puis l'allumage, puis le décollage, sur la maquette.
        SHOTS.add(new Shot("console_fill", level -> {
            set(level, 22, 0, 6, "orbitalis:launch_console");
            // Le pupitre ne répond qu'à qui a la technologie du pas de tir.
            for (ServerPlayer player : level.players()) {
                ResearchManager.of(player).forceUnlock(com.orbitalis.phase1.spaceport.LaunchTableBlock.RESEARCH_PAD);
            }
            if (level.getBlockEntity(at(22, 1, 22)) instanceof LaunchTableBlockEntity table) {
                table.setReliable(true);
                table.fill(net.minecraft.world.level.material.Fluids.WATER, 100_000, false);
                table.command(com.orbitalis.phase1.spaceport.LaunchStep.VALVES, "preview");
                table.fill(ModFluids.RP1.get(), 1_000_000, false);
                table.fill(ModFluids.LIQUID_OXYGEN.get(), 1_000_000, false);
            }
            com.orbitalis.core.network.LaunchStatusPayload status = LaunchConsoleBlock.status(level, at(22, 0, 6), true);
            net.minecraft.client.Minecraft.getInstance().execute(
                    () -> net.minecraft.client.Minecraft.getInstance().gui.setScreen(new com.orbitalis.client.screen.LaunchConsoleScreen(status)));
        }, 23.5, 1.0, 8.5, 180.0F, 30.0F, 70));
        SHOTS.add(new Shot("console_ignite", level -> {
            if (level.getBlockEntity(at(22, 1, 22)) instanceof LaunchTableBlockEntity table) {
                table.command(com.orbitalis.phase1.spaceport.LaunchStep.RETRACT, "preview");
                table.command(com.orbitalis.phase1.spaceport.LaunchStep.PRESSURIZE, "preview");
                table.command(com.orbitalis.phase1.spaceport.LaunchStep.DELUGE, "preview");
                table.command(com.orbitalis.phase1.spaceport.LaunchStep.IGNITE, "preview");
            }
        }, 23.5, 1.0, 8.5, 180.0F, 30.0F, 66));
        SHOTS.add(new Shot("console_liftoff", level -> {
            if (level.getBlockEntity(at(22, 1, 22)) instanceof LaunchTableBlockEntity table) {
                table.command(com.orbitalis.phase1.spaceport.LaunchStep.RELEASE, "preview");
            }
        }, 23.5, 1.0, 8.5, 180.0F, 30.0F, 50));
        SHOTS.add(new Shot("console_flight", level -> net.minecraft.client.Minecraft.getInstance().execute(() -> {
            if (net.minecraft.client.Minecraft.getInstance().gui.screen() instanceof com.orbitalis.client.screen.LaunchConsoleScreen console) {
                console.showView(0);
            }
        }), 23.5, 1.0, 8.5, 180.0F, 30.0F, 40));

        // Un vol mené à la main sur le grand pas de tir : la séquence au sol, puis l'écran des étapes en vol.
        SHOTS.add(new Shot("flight_ignite", level -> {
            if (level.getBlockEntity(at(22, 1, 22)) instanceof LaunchTableBlockEntity table) {
                table.fill(net.minecraft.world.level.material.Fluids.WATER, 100_000, false);
                table.command(com.orbitalis.phase1.spaceport.LaunchStep.VALVES, "preview");
                table.fill(ModFluids.RP1.get(), 1_000_000, false);
                table.fill(ModFluids.LIQUID_OXYGEN.get(), 1_000_000, false);
                table.command(com.orbitalis.phase1.spaceport.LaunchStep.RETRACT, "preview");
                table.command(com.orbitalis.phase1.spaceport.LaunchStep.PRESSURIZE, "preview");
                table.command(com.orbitalis.phase1.spaceport.LaunchStep.DELUGE, "preview");
                table.command(com.orbitalis.phase1.spaceport.LaunchStep.IGNITE, "preview");
            }
        }, 37.0, 14.0, 7.0, 45.0F, 4.0F, 66));
        SHOTS.add(new Shot("flight_release", level -> {
            if (level.getBlockEntity(at(22, 1, 22)) instanceof LaunchTableBlockEntity table) {
                table.command(com.orbitalis.phase1.spaceport.LaunchStep.RELEASE, "preview");
            }
        }, 37.0, 14.0, 7.0, 45.0F, -20.0F, 72));
        SHOTS.add(new Shot("flight_screen", level -> {
            var status = LaunchConsoleBlock.status(level, at(22, 1, 22), true);
            net.minecraft.client.Minecraft.getInstance().execute(() -> net.minecraft.client.Minecraft.getInstance().gui.setScreen(
                    new com.orbitalis.client.screen.SequenceScreen(status, null)));
        }, 37.0, 14.0, 7.0, 45.0F, -20.0F, 14));
        SHOTS.add(new Shot("flight_closed", level -> net.minecraft.client.Minecraft.getInstance().execute(
                () -> net.minecraft.client.Minecraft.getInstance().gui.setScreen(null)), 37.0, 14.0, 7.0, 45.0F, -40.0F, 110));

        // Un accident sur le grand pas de tir : attaches lâchées avant la pleine poussée. Quatre instants de la dislocation.
        SHOTS.add(new Shot("breakup_a", level -> {
            if (level.getBlockEntity(at(22, 1, 22)) instanceof LaunchTableBlockEntity table) {
                table.command(com.orbitalis.phase1.spaceport.LaunchStep.PRESSURIZE, "preview");
                table.command(com.orbitalis.phase1.spaceport.LaunchStep.IGNITE, "preview");
                table.command(com.orbitalis.phase1.spaceport.LaunchStep.RELEASE, "preview");
            }
        }, 40.0, 10.0, 4.0, 45.0F, -8.0F, 5));
        SHOTS.add(new Shot("breakup_b", null, 40.0, 10.0, 4.0, 45.0F, -8.0F, 8));
        SHOTS.add(new Shot("breakup_c", null, 40.0, 10.0, 4.0, 45.0F, -8.0F, 14));
        SHOTS.add(new Shot("breakup_d", null, 40.0, 10.0, 4.0, 45.0F, -8.0F, 30));
        SHOTS.add(new Shot("breakup_e", null, 30.0, 6.0, 30.0, 135.0F, 15.0F, 60));

        // Trois fusées perdues en vol, de plus en plus haut : chacune fait entendre le son de sa distance, puis son épave.
        SHOTS.add(new Shot("boom_near", level -> {
            if (level.getBlockEntity(at(22, 1, 22)) instanceof LaunchTableBlockEntity table) {
                table.removeRocket();
                boolean flying = table.launchForTest(level, com.orbitalis.core.creative.CreativeRockets.all().get(2), "preview");
                table.flameoutForTest(30);
                com.orbitalis.Orbitalis.LOGGER.info("APERCU essai d'explosion near : en vol {}, panne au tick 30", flying);
            }
        }, 40.0, 10.0, 4.0, 45.0F, -30.0F, 300));
        SHOTS.add(new Shot("boom_mid", level -> {
            if (level.getBlockEntity(at(22, 1, 22)) instanceof LaunchTableBlockEntity table) {
                table.removeRocket();
                boolean flying = table.launchForTest(level, com.orbitalis.core.creative.CreativeRockets.all().get(2), "preview");
                table.flameoutForTest(200);
                com.orbitalis.Orbitalis.LOGGER.info("APERCU essai d'explosion mid : en vol {}, panne au tick 200", flying);
            }
        }, 40.0, 10.0, 4.0, 45.0F, -30.0F, 460));
        SHOTS.add(new Shot("boom_far", level -> {
            if (level.getBlockEntity(at(22, 1, 22)) instanceof LaunchTableBlockEntity table) {
                table.removeRocket();
                boolean flying = table.launchForTest(level, com.orbitalis.core.creative.CreativeRockets.all().get(2), "preview");
                table.flameoutForTest(700);
                com.orbitalis.Orbitalis.LOGGER.info("APERCU essai d'explosion far : en vol {}, panne au tick 700", flying);
            }
        }, 40.0, 10.0, 4.0, 45.0F, -30.0F, 140));
        // Pour monter aussi haut, il faut avoir réduit les gaz au passage de la pression maximale, puis séparé l'étage.
        SHOTS.add(new Shot("boom_far_gas", level -> {
            if (level.getBlockEntity(at(22, 1, 22)) instanceof LaunchTableBlockEntity table) {
                say(table.command(com.orbitalis.phase1.spaceport.LaunchStep.THROTTLE, "preview"));
            }
        }, 40.0, 10.0, 4.0, 45.0F, -30.0F, 900));

        // Surpression : la montée en pression doit s'entendre, et finir à l'instant où les réservoirs cèdent. Puis la
        // même, vannes refermées à temps : elle doit se taire, et rien n'explose.
        SHOTS.add(new Shot("pressure_burst", level -> {
            net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
            minecraft.execute(() -> minecraft.getSoundManager().addListener((sound, events, range) -> {
                if (sound.getIdentifier().getNamespace().equals(com.orbitalis.Orbitalis.MODID) && minecraft.level != null) {
                    com.orbitalis.Orbitalis.LOGGER.info("APERCU son : {} au tick {}", sound.getIdentifier().getPath(), minecraft.level.getGameTime());
                }
            }));
            if (level.getBlockEntity(at(22, 1, 22)) instanceof LaunchTableBlockEntity table) {
                table.removeRocket();
                placeRocket(level, 22, 1, 22, 2);
                table.overpressureForTest(140);
                com.orbitalis.Orbitalis.LOGGER.info("APERCU surpression : rupture attendue au tick {}", level.getGameTime() + 140);
            }
        }, 40.0, 10.0, 4.0, 45.0F, -8.0F, 260));
        SHOTS.add(new Shot("pressure_saved", level -> {
            if (level.getBlockEntity(at(22, 1, 22)) instanceof LaunchTableBlockEntity table) {
                // Le pas de tir vient d'être soufflé : on le rebâtit avant d'y reposer une fusée.
                table.removeRocket();
                buildPad(level, 22, 1, 22, 2);
                placeRocket(level, 22, 1, 22, 2);
                table.refresh(level);
                table.overpressureForTest(120);
                com.orbitalis.Orbitalis.LOGGER.info("APERCU surpression : rupture attendue au tick {} si rien n'est fait", level.getGameTime() + 120);
            }
        }, 40.0, 10.0, 4.0, 45.0F, -8.0F, 70));
        SHOTS.add(new Shot("pressure_saved_close", level -> {
            if (level.getBlockEntity(at(22, 1, 22)) instanceof LaunchTableBlockEntity table) {
                say(table.command(com.orbitalis.phase1.spaceport.LaunchStep.VALVES, "preview"));
                com.orbitalis.Orbitalis.LOGGER.info("APERCU surpression : vannes refermées au tick {}, pression {} %", level.getGameTime(), table.overpressurePercent());
            }
        }, 40.0, 10.0, 4.0, 45.0F, -8.0F, 120));
        SHOTS.add(new Shot("pressure_saved_after", level -> {
            if (level.getBlockEntity(at(22, 1, 22)) instanceof LaunchTableBlockEntity table) {
                com.orbitalis.Orbitalis.LOGGER.info("APERCU surpression : après, fusée toujours là {}, pression {} %", table.hasRocket(), table.overpressurePercent());
            }
        }, 40.0, 10.0, 4.0, 45.0F, -8.0F, 10));

        // L'atlas, ouvert depuis le pupitre du grand pas de tir : le site, puis de plus en plus loin.
        SHOTS.add(new Shot("atlas_open", level -> {
            set(level, 22, 0, 6, "orbitalis:launch_console");
            net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
            minecraft.execute(() -> {
                atlasPreview = new com.orbitalis.client.screen.AtlasScreen(null, at(22, 0, 6), at(22, 1, 22));
                minecraft.gui.setScreen(atlasPreview);
            });
        }, 24.0, 2.0, 8.0, 0.0F, 0.0F, 160));
        SHOTS.add(new Shot("atlas_site", level -> net.minecraft.client.Minecraft.getInstance().execute(() -> {
            if (atlasPreview != null) {
                atlasPreview.setView(at(22, 1, 22).getX() + 0.5, at(22, 1, 22).getZ() + 0.5, 2.0F);
            }
        }), 24.0, 2.0, 8.0, 0.0F, 0.0F, 120));
        SHOTS.add(new Shot("atlas_region", level -> net.minecraft.client.Minecraft.getInstance().execute(() -> {
            if (atlasPreview != null) {
                atlasPreview.setView(at(22, 1, 22).getX() + 0.5, at(22, 1, 22).getZ() + 0.5, 0.07F);
            }
        }), 24.0, 2.0, 8.0, 0.0F, 0.0F, 200));
        SHOTS.add(new Shot("atlas_far", level -> net.minecraft.client.Minecraft.getInstance().execute(() -> {
            if (atlasPreview != null) {
                atlasPreview.setView(at(22, 1, 22).getX() + 0.5, at(22, 1, 22).getZ() + 0.5, 0.02F);
            }
        }), 24.0, 2.0, 8.0, 0.0F, 0.0F, 260));
        // Le pas de tir de près, une fois son relevé rafraîchi : ses dalles, leur pourtour rouge, le carneau.
        SHOTS.add(new Shot("atlas_pad", level -> net.minecraft.client.Minecraft.getInstance().execute(() -> {
            if (atlasPreview != null) {
                atlasPreview.setView(at(22, 1, 22).getX() + 0.5, at(22, 1, 22).getZ() + 0.5, 1.4F);
            }
        }), 24.0, 2.0, 8.0, 0.0F, 0.0F, 760));
        SHOTS.add(new Shot("atlas_turned", level -> net.minecraft.client.Minecraft.getInstance().execute(() -> {
            if (atlasPreview != null) {
                atlasPreview.setView(at(22, 1, 22).getX() + 0.5, at(22, 1, 22).getZ() + 0.5, 0.5F);
                atlasPreview.setTurn(2);
            }
        }), 24.0, 2.0, 8.0, 0.0F, 0.0F, 140));
        // Les calques : le pas de tir et sa fusée, sa zone d'exclusion, sa fiche, et le panneau des calques ouvert.
        SHOTS.add(new Shot("atlas_layers", level -> net.minecraft.client.Minecraft.getInstance().execute(() -> {
            if (atlasPreview != null) {
                atlasPreview.setTurn(0);
                atlasPreview.setView(at(22, 1, 22).getX() + 0.5, at(22, 1, 22).getZ() + 0.5, 0.35F);
                atlasPreview.select(at(22, 1, 22));
                atlasPreview.showLayers(true);
            }
        }), 24.0, 2.0, 8.0, 0.0F, 0.0F, 100));
        // Un vol en cours : la route suivie, sa suite jusqu'au bord du relevé, le couloir de retombée.
        SHOTS.add(new Shot("atlas_flight", level -> {
            if (level.getBlockEntity(at(22, 1, 22)) instanceof LaunchTableBlockEntity table) {
                table.setReliable(true);
                table.command(com.orbitalis.phase1.spaceport.LaunchStep.VALVES, "preview");
                table.fill(ModFluids.RP1.get(), 1_000_000, false);
                table.fill(ModFluids.LIQUID_OXYGEN.get(), 1_000_000, false);
                table.startCountdown("preview");
            }
            net.minecraft.client.Minecraft.getInstance().execute(() -> {
                if (atlasPreview != null) {
                    atlasPreview.showLayers(false);
                    atlasPreview.setView(at(22, 1, 22).getX() + 40.5, at(22, 1, 22).getZ() + 0.5, 0.11F);
                    atlasPreview.select(at(22, 1, 22));
                }
            });
        }, 24.0, 2.0, 8.0, 0.0F, 0.0F, 980));
        // Le même vol, sans fiche, la courbe entière à l'écran.
        SHOTS.add(new Shot("atlas_flight_arc", level -> net.minecraft.client.Minecraft.getInstance().execute(() -> {
            if (atlasPreview != null) {
                atlasPreview.clearSelection();
                atlasPreview.setView(at(22, 1, 22).getX() + 120.5, at(22, 1, 22).getZ() + 0.5, 0.16F);
            }
        }), 24.0, 2.0, 8.0, 0.0F, 0.0F, 30));
        // Le ciel de l'agence : un satellite de chaque type et quatre de navigation, par beau temps ; puis sous la pluie.
        SHOTS.add(new Shot("atlas_sky", level -> {
            level.getServer().setWeatherParameters(6000, 0, false, false);
            for (ServerPlayer player : level.players()) {
                String agency = ResearchManager.agencyKey(player);
                SatelliteManager.of(level.getServer(), agency).clear();
                for (SatelliteType type : SatelliteType.values()) {
                    SatelliteManager.onFlight(level.getServer(), agency, type, RocketStats.Reach.GEOSTATIONARY);
                }
                for (int k = 0; k < 3; k++) {
                    SatelliteManager.onFlight(level.getServer(), agency, SatelliteType.NAVIGATION, RocketStats.Reach.GEOSTATIONARY);
                }
            }
            set(level, 0, 0, -6, "orbitalis:tracking_station[facing=south]");
            net.minecraft.client.Minecraft.getInstance().execute(() -> {
                if (atlasPreview != null) {
                    atlasPreview.setTurn(0);
                    atlasPreview.showLayers(true);
                    atlasPreview.setView(at(22, 1, 22).getX() + 0.5, at(22, 1, 22).getZ() + 0.5, 0.05F);
                }
            });
        }, 24.0, 2.0, 8.0, 0.0F, 0.0F, 200));
        SHOTS.add(new Shot("atlas_sky_near", level -> net.minecraft.client.Minecraft.getInstance().execute(() -> {
            if (atlasPreview != null) {
                atlasPreview.showLayers(false);
                atlasPreview.setView(at(22, 1, 22).getX() + 0.5, at(22, 1, 22).getZ() + 0.5, 0.25F);
            }
        }), 24.0, 2.0, 8.0, 0.0F, 0.0F, 80));
        SHOTS.add(new Shot("atlas_sky_rain", level -> {
            level.getServer().setWeatherParameters(0, 6000, true, false);
        }, 24.0, 2.0, 8.0, 0.0F, 0.0F, 120));
        // Les repères de l'agence : deux posés autour du pas de tir, dont un nommé ; puis la fiche du premier.
        SHOTS.add(new Shot("atlas_pins", level -> {
            for (ServerPlayer player : level.players()) {
                String agency = ResearchManager.agencyKey(player);
                String dimension = level.dimension().identifier().toString();
                BlockPos pad = at(22, 1, 22);
                com.orbitalis.core.atlas.AtlasMarkers.add(level.getServer(), agency, dimension, pad.getX() + 60, 64, pad.getZ() - 40, player.getName().getString());
                com.orbitalis.core.atlas.AtlasMarkers.add(level.getServer(), agency, dimension, pad.getX() - 70, 64, pad.getZ() + 30, player.getName().getString());
                var placed = com.orbitalis.core.atlas.AtlasMarkers.of(level.getServer(), agency, dimension);
                com.orbitalis.core.atlas.AtlasMarkers.rename(level.getServer(), agency, placed.get(0).id(), "Zone de récupération");
            }
            level.getServer().setWeatherParameters(6000, 0, false, false);
            net.minecraft.client.Minecraft.getInstance().execute(() -> {
                if (atlasPreview != null) {
                    atlasPreview.showLayers(false);
                    atlasPreview.setView(at(22, 1, 22).getX() + 0.5, at(22, 1, 22).getZ() + 0.5, 0.45F);
                }
            });
        }, 24.0, 2.0, 8.0, 0.0F, 0.0F, 80));
        SHOTS.add(new Shot("atlas_pin_card", level -> net.minecraft.client.Minecraft.getInstance().execute(() -> {
            if (atlasPreview != null) {
                atlasPreview.selectFirstPin();
            }
        }), 24.0, 2.0, 8.0, 0.0F, 0.0F, 30));
        // La tablette de mission, en main, ouverte loin du centre de contrôle : l'atlas relayé par satellite.
        SHOTS.add(new Shot("tablet_atlas", level -> {
            for (ServerPlayer player : level.players()) {
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(com.orbitalis.core.registry.ModItems.MISSION_TABLET.get()));
                ResearchManager.of(player).forceUnlock(com.orbitalis.phase1.satellite.MissionTabletItem.RESEARCH);
                var refusal = com.orbitalis.core.atlas.AtlasAccess.tabletRefusal(player);
                com.orbitalis.Orbitalis.LOGGER.info("APERCU tablette : {}", refusal == null ? "ouverte" : refusal.getString());
                com.orbitalis.core.registry.ModItems.MISSION_TABLET.get().use(level, player, net.minecraft.world.InteractionHand.MAIN_HAND);
            }
        }, 24.0, 2.0, 8.0, 0.0F, 0.0F, 140));
        SHOTS.add(new Shot("atlas_closed", level -> net.minecraft.client.Minecraft.getInstance().execute(() -> {
            atlasPreview = null;
            net.minecraft.client.Minecraft.getInstance().gui.setScreen(null);
        }), 24.0, 2.0, 8.0, 0.0F, 0.0F, 10));

        // Sous la pluie, le lancement est retenu : la météo dit non, et l'allumage est refusé.
        SHOTS.add(new Shot("weather_hold", level -> {
            if (level.getBlockEntity(at(22, 1, 22)) instanceof LaunchTableBlockEntity table) {
                table.removeRocket();
                placeRocket(level, 22, 1, 22, 2);
                com.orbitalis.Orbitalis.LOGGER.info("APERCU météo : au sec, météo favorable {}", table.isWeatherGo());
                level.getServer().setWeatherParameters(0, 6000, true, false);
            }
        }, 40.0, 10.0, 4.0, 45.0F, -8.0F, 80));
        SHOTS.add(new Shot("weather_hold_check", level -> {
            if (level.getBlockEntity(at(22, 1, 22)) instanceof LaunchTableBlockEntity table) {
                var refused = table.command(com.orbitalis.phase1.spaceport.LaunchStep.IGNITE, "preview");
                com.orbitalis.Orbitalis.LOGGER.info("APERCU météo : sous la pluie, météo favorable {}, allumage {}", table.isWeatherGo(),
                        refused == null ? "accepté" : "refusé (" + refused.getString() + ")");
                level.getServer().setWeatherParameters(6000, 0, false, false);
            }
        }, 40.0, 10.0, 4.0, 45.0F, -8.0F, 10));

        // L'affichage tête haute du pilote : en vol, avec un écart d'assiette et une étape à faire ; puis au sol.
        SHOTS.add(new Shot("pilot_hud", level -> net.minecraft.client.Minecraft.getInstance().execute(() -> {
            com.orbitalis.client.CockpitHud.forceShow = true;
            com.orbitalis.client.CockpitHud.preview(RocketStats.of(com.orbitalis.phase1.rocket.RocketItem.design(CreativeRockets.satelliteLauncher())),
                    0.4);
            com.orbitalis.client.CockpitHud.onFlight(new com.orbitalis.core.network.FlightHudPayload(14.0F, 620.0F, 0.3F,
                    15 | 1 << 8 | 1 << 20, 4, 0, 6 * 20, 0));
        }), 37.0, 14.0, 7.0, -90.0F, -62.0F, 8));
        // Un QTE de stabilisation : tenir A, à moitié fait, deux secondes et demie pour finir.
        SHOTS.add(new Shot("pilot_hud_qte", level -> net.minecraft.client.Minecraft.getInstance().execute(() ->
                com.orbitalis.client.CockpitHud.onFlight(new com.orbitalis.core.network.FlightHudPayload(34.0F, 1620.0F, 0.0F,
                        15 | 1 << 4 | 1 << 20, 0, 0, 22 * 20, 1 | 2 << 2 | 1 << 4 | 55 << 5 | 48 << 13))), 37.0, 14.0, 7.0, -90.0F, -62.0F, 8));
        SHOTS.add(new Shot("pilot_hud_ground", level -> {
            var status = LaunchConsoleBlock.status(level, at(22, 1, 22), false);
            net.minecraft.client.Minecraft.getInstance().execute(() -> com.orbitalis.client.CockpitHud.onStatus(status));
        }, 37.0, 14.0, 7.0, 45.0F, 4.0F, 30));
        SHOTS.add(new Shot("pilot_hud_off", level -> net.minecraft.client.Minecraft.getInstance().execute(() -> {
            com.orbitalis.client.CockpitHud.forceShow = false;
            com.orbitalis.client.CockpitHud.preview(null, 0.0);
        }), 37.0, 14.0, 7.0, 45.0F, 4.0F, 4));

        // Le séquenceur de lancement, sur le grand pas de tir.
        SHOTS.add(new Shot("sequence_screen", level -> {
            var status = LaunchConsoleBlock.status(level, at(22, 1, 22), true);
            net.minecraft.client.Minecraft.getInstance().execute(() -> net.minecraft.client.Minecraft.getInstance().gui.setScreen(
                    new com.orbitalis.client.screen.SequenceScreen(status, null)));
        }, 37.0, 14.0, 7.0, 45.0F, 4.0F, 20));
        SHOTS.add(new Shot("sequence_closed", level -> net.minecraft.client.Minecraft.getInstance().execute(
                () -> net.minecraft.client.Minecraft.getInstance().gui.setScreen(null)), 37.0, 14.0, 7.0, 45.0F, 4.0F, 6));

        // Station de poursuite, vue de près, puis son écran avec un satellite de chaque type en orbite.
        SHOTS.add(new Shot("tracking_station", level -> {
            for (ServerPlayer player : level.players()) {
                player.closeContainer();
            }
            set(level, 0, 0, -6, "orbitalis:tracking_station[facing=south]");
            set(level, 2, 0, -6, "orbitalis:tracking_station[facing=east]");
        }, 1.5, 1.8, -3.0, 180.0F, 20.0F, 40));
        SHOTS.add(new Shot("tracking_screen", level -> {
            for (ServerPlayer player : level.players()) {
                String agency = ResearchManager.agencyKey(player);
                SatelliteManager.of(level.getServer(), agency).clear();
                for (SatelliteType type : SatelliteType.values()) {
                    SatelliteManager.onFlight(level.getServer(), agency, type, RocketStats.Reach.GEOSTATIONARY);
                }
                SatelliteManager.onFlight(level.getServer(), agency, SatelliteType.NAVIGATION, RocketStats.Reach.GEOSTATIONARY);
                PacketDistributor.sendToPlayer(player, SatelliteManager.status(player, at(0, 0, -6), true));
            }
        }, 1.5, 1.8, -3.0, 180.0F, 20.0F, 12));
        // Le même écran un peu plus tard, puis la planète tournée : les orbites tournent avec elle.
        SHOTS.add(new Shot("tracking_screen_globe", null, 1.5, 1.8, -3.0, 180.0F, 20.0F, 60));
        SHOTS.add(new Shot("tracking_screen_turned", level -> net.minecraft.client.Minecraft.getInstance().execute(
                () -> com.orbitalis.client.screen.TrackingStationScreen.setView(2.2F, -0.55F)), 1.5, 1.8, -3.0, 180.0F, 20.0F, 20));
        SHOTS.add(new Shot("tracking_screen_pole", level -> net.minecraft.client.Minecraft.getInstance().execute(
                () -> com.orbitalis.client.screen.TrackingStationScreen.setView(0.6F, 1.35F)), 1.5, 1.8, -3.0, 180.0F, 20.0F, 20));

        // L'observatoire : un télescope et un satellite d'observation ; la Lune et Mars au catalogue, Jupiter découverte,
        // les Pléiades et Orion au catalogue, la nébuleuse de l'Aigle en observation.
        SHOTS.add(new Shot("observatory", level -> {
            for (ServerPlayer player : level.players()) {
                String agency = ResearchManager.agencyKey(player);
                var sky = SatelliteManager.of(level.getServer(), agency);
                sky.clearObservatory();
                SatelliteManager.onFlight(level.getServer(), agency, SatelliteType.TELESCOPE, RocketStats.Reach.GEOSTATIONARY);
                for (var body : new com.orbitalis.phase1.satellite.CelestialBody[] {com.orbitalis.phase1.satellite.CelestialBody.MOON,
                        com.orbitalis.phase1.satellite.CelestialBody.MARS, com.orbitalis.phase1.satellite.CelestialBody.PLEIADES,
                        com.orbitalis.phase1.satellite.CelestialBody.ORION_NEBULA, com.orbitalis.phase1.satellite.CelestialBody.SATURN,
                        com.orbitalis.phase1.satellite.CelestialBody.JUPITER}) {
                    sky.discover(body);
                    if (body != com.orbitalis.phase1.satellite.CelestialBody.JUPITER) {
                        sky.image(body);
                    }
                }
                sky.observe(com.orbitalis.phase1.satellite.CelestialBody.EAGLE_NEBULA);
                sky.addProgress(com.orbitalis.phase1.satellite.CelestialBody.EAGLE_NEBULA, 3_400);
                SatelliteManager.changed(level.getServer());
                for (String research : new String[] {"spacesuits", "life_support"}) {
                    ResearchManager.of(player).forceUnlock(research);
                }
            }
            net.minecraft.client.Minecraft.getInstance().execute(() -> {
                if (net.minecraft.client.Minecraft.getInstance().gui.screen() instanceof com.orbitalis.client.screen.TrackingStationScreen station) {
                    net.minecraft.client.Minecraft.getInstance().gui.setScreen(new com.orbitalis.client.screen.ObservatoryScreen(station, at(0, 0, -6)));
                }
            });
        }, 1.5, 1.8, -3.0, 180.0F, 20.0F, 60));
        SHOTS.add(new Shot("observatory_eagle", level -> net.minecraft.client.Minecraft.getInstance().execute(() -> {
            if (net.minecraft.client.Minecraft.getInstance().gui.screen() instanceof com.orbitalis.client.screen.ObservatoryScreen screen) {
                screen.select(com.orbitalis.phase1.satellite.CelestialBody.EAGLE_NEBULA);
            }
        }), 1.5, 1.8, -3.0, 180.0F, 20.0F, 30));
        SHOTS.add(new Shot("catalogue_mars", level -> net.minecraft.client.Minecraft.getInstance().execute(() -> {
            if (net.minecraft.client.Minecraft.getInstance().gui.screen() instanceof com.orbitalis.client.screen.ObservatoryScreen screen) {
                screen.showCatalogue(com.orbitalis.phase1.satellite.CelestialBody.MARS);
            }
        }), 1.5, 1.8, -3.0, 180.0F, 20.0F, 30));
        SHOTS.add(new Shot("catalogue_saturn", level -> net.minecraft.client.Minecraft.getInstance().execute(() -> {
            if (net.minecraft.client.Minecraft.getInstance().gui.screen() instanceof com.orbitalis.client.screen.ObservatoryScreen screen) {
                screen.showCatalogue(com.orbitalis.phase1.satellite.CelestialBody.SATURN);
            }
        }), 1.5, 1.8, -3.0, 180.0F, 20.0F, 30));
        SHOTS.add(new Shot("catalogue_orion", level -> net.minecraft.client.Minecraft.getInstance().execute(() -> {
            if (net.minecraft.client.Minecraft.getInstance().gui.screen() instanceof com.orbitalis.client.screen.ObservatoryScreen screen) {
                screen.showCatalogue(com.orbitalis.phase1.satellite.CelestialBody.ORION_NEBULA);
            }
        }), 1.5, 1.8, -3.0, 180.0F, 20.0F, 30));

        // Fusée habitée sur un petit pas de tir : la capsule vitrée, sa tour de sauvetage et les balises des mâts.
        SHOTS.add(new Shot("crewed_rocket", level -> {
            for (ServerPlayer player : level.players()) {
                player.closeContainer();
            }
            buildPad(level, -12, 1, 14, 1);
            if (level.getBlockEntity(at(-12, 1, 14)) instanceof LaunchTableBlockEntity table) {
                table.commission(true);
                table.setRocket(CreativeRockets.crewedSuborbital());
                placeGate(level, table);
                // La capsule existe dès qu'on monte à bord : on la crée pour voir son écran de bord en service.
                var seat = table.capsule(true);
                com.orbitalis.Orbitalis.LOGGER.info("APERCU capsule en {}", seat == null ? null : seat.position().subtract(net.minecraft.world.phys.Vec3.atLowerCornerOf(at(0, 0, 0))));
            }
        }, -8.5, 4.5, 9.5, 38.0F, 2.0F, 60));
        // Vues depuis le siège du pilote, assis face au nord : devant (le pare-brise et l'écran de bord), sur le côté
        // (la baie), vers le bas (le pupitre), puis depuis le siège d'un équipier, derrière lui.
        SHOTS.add(new Shot("crewed_cockpit", null, -11.5, 4.505, 14.594, 180.0F, 14.0F, 20));
        SHOTS.add(new Shot("crewed_cockpit_side", null, -11.5, 4.505, 14.594, 90.0F, 5.0F, 12));
        SHOTS.add(new Shot("crewed_cockpit_down", null, -11.5, 4.505, 14.594, 180.0F, 45.0F, 12));
        SHOTS.add(new Shot("crewed_cockpit_crew", null, -11.094, 4.505, 14.938, 175.0F, 12.0F, 12));
        // Bras d'accès de l'équipage : au repos contre la cabine, puis en train de pivoter sur sa charnière.
        SHOTS.add(new Shot("crewed_arm", null, -13.0, 3.2, 17.0, 180.0F, 5.0F, 12));
        SHOTS.add(new Shot("crewed_arm_above", null, -14.5, 5.0, 18.5, -160.0F, 28.0F, 12));
        // L'écran de bord pendant le compte à rebours : le bandeau porte alors l'horloge et une pastille large.
        SHOTS.add(new Shot("crewed_cockpit_countdown", level -> {
            if (level.getBlockEntity(at(-12, 1, 14)) instanceof LaunchTableBlockEntity table) {
                table.setReliable(true);
                table.command(com.orbitalis.phase1.spaceport.LaunchStep.VALVES, "preview");
                table.fill(ModFluids.RP1.get(), 1_000_000, false);
                table.fill(ModFluids.LIQUID_OXYGEN.get(), 1_000_000, false);
                table.startCountdown("preview");
            }
        }, -11.5, 4.005, 14.7, 180.0F, -89.9F, 60));
        // La fusée habitée en vol, vue du sol : c'est sa capsule qui la dessine, avec ses flammes.
        // Sur le grand pas de tir, qui a son bras : la séquence complète, à la main, puis le vol.
        SHOTS.add(new Shot("crewed_flight_ignite", level -> {
            if (level.getBlockEntity(at(22, 1, 22)) instanceof LaunchTableBlockEntity table) {
                // Une cabine sur un moteur à poudre : aucun plein à faire.
                net.minecraft.core.NonNullList<net.minecraft.world.item.ItemStack> parts = net.minecraft.core.NonNullList.withSize(
                        com.orbitalis.phase1.rocket.RocketDesign.SLOT_COUNT, net.minecraft.world.item.ItemStack.EMPTY);
                parts.set(com.orbitalis.phase1.rocket.RocketDesign.SLOT_PAYLOAD, new net.minecraft.world.item.ItemStack(com.orbitalis.core.registry.ModItems.CREW_CAPSULE.get()));
                parts.set(com.orbitalis.phase1.rocket.RocketDesign.SLOT_NOSE, new net.minecraft.world.item.ItemStack(com.orbitalis.core.registry.ModItems.NOSE_CONE.get()));
                parts.set(com.orbitalis.phase1.rocket.RocketDesign.engineSlot(0), new net.minecraft.world.item.ItemStack(com.orbitalis.core.registry.ModItems.SOLID_MOTOR.get()));
                table.removeRocket();
                com.orbitalis.Orbitalis.LOGGER.info("APERCU fusée habitée posée : {}", table.setRocket(com.orbitalis.phase1.rocket.RocketItem.create(parts)));
                table.refresh(level);
                placeGate(level, table);
                table.commission(true);
                table.refresh(level);
                table.capsule(true);
                table.setReliable(true);
                com.orbitalis.Orbitalis.LOGGER.info("APERCU prêt : en service {}, fusée {}, masse {}, tour {}/{}, plein {}, manque {}", table.isOperational(),
                        table.rocketStats().isFlightReady(), table.isMassAllowed(), table.usableTowerHeight(), table.requiredTowerHeight(), table.isFuelled(),
                        table.getComplex().missing());
            }
        }, 37.0, 14.0, 7.0, 45.0F, 4.0F, 40));
        SHOTS.add(new Shot("crewed_flight_out", level -> {
            if (level.getBlockEntity(at(22, 1, 22)) instanceof LaunchTableBlockEntity table) {
                com.orbitalis.Orbitalis.LOGGER.info("APERCU prêt : en service {}, fusée {}, manque {}", table.isOperational(),
                        table.rocketStats().isFlightReady(), table.getComplex().missing());
                say(table.command(com.orbitalis.phase1.spaceport.LaunchStep.RETRACT, "preview"));
                say(table.command(com.orbitalis.phase1.spaceport.LaunchStep.IGNITE, "preview"));
            }
        }, 37.0, 14.0, 7.0, 45.0F, 4.0F, 70));
        SHOTS.add(new Shot("crewed_flight_up", level -> {
            if (level.getBlockEntity(at(22, 1, 22)) instanceof LaunchTableBlockEntity table) {
                say(table.command(com.orbitalis.phase1.spaceport.LaunchStep.RELEASE, "preview"));
                com.orbitalis.Orbitalis.LOGGER.info("APERCU vol habité : phase {}", table.getPhase());
            }
        }, 37.0, 14.0, 7.0, 45.0F, -20.0F, 110));
        SHOTS.add(new Shot("crewed_flight_far", null, 37.0, 14.0, 7.0, 45.0F, -55.0F, 70));
        // Le passage vers l'espace : le même horizon, vu de plus en plus haut.
        SHOTS.add(new Shot("sky_low", null, 0.5, 120.0, 0.5, 45.0F, 8.0F, 30));
        SHOTS.add(new Shot("sky_high", null, 0.5, 300.0, 0.5, 45.0F, 8.0F, 30));
        SHOTS.add(new Shot("sky_edge", null, 0.5, 480.0, 0.5, 45.0F, 8.0F, 30));
        SHOTS.add(new Shot("sky_space", null, 0.5, 760.0, 0.5, 45.0F, 8.0F, 30));
        SHOTS.add(new Shot("crewed_arm_swing", level -> {
            if (level.getBlockEntity(at(-12, 1, 14)) instanceof LaunchTableBlockEntity table) {
                table.setReliable(true);
                table.command(com.orbitalis.phase1.spaceport.LaunchStep.VALVES, "preview");
                table.fill(ModFluids.RP1.get(), 1_000_000, false);
                table.fill(ModFluids.LIQUID_OXYGEN.get(), 1_000_000, false);
                table.startCountdown("preview");
            }
        }, -13.0, 3.2, 17.0, 180.0F, 5.0F, 400));
        SHOTS.add(new Shot("crewed_arm_swing_above", null, -14.5, 5.0, 18.5, -160.0F, 28.0F, 30));
        SHOTS.add(new Shot("crewed_beacons", null, -9.0, 7.5, 11.0, 40.0F, -12.0F, 12));
        SHOTS.add(new Shot("crewed_beacons_later", null, -9.0, 7.5, 11.0, 40.0F, -12.0F, 9));

        // Conduits en service, chacun entre une source créative et ce qu'il alimente : un câble, un tuyau à
        // liquides (RP-1, puis oxygène liquide) et un tuyau à gaz (hydrogène, puis oxygène). À droite, les mêmes au repos.
        SHOTS.add(new Shot("conduits", level -> {
            set(level, 16, 0, -22, "orbitalis:creative_energy_source");
            fill(level, 17, 0, -22, 19, 0, -22, "orbitalis:energy_cable");
            set(level, 18, 1, -22, "orbitalis:energy_cable");
            set(level, 20, 0, -22, "orbitalis:arc_foundry");
            set(level, 16, 0, -20, "orbitalis:creative_fluid_source[fluid=0]");
            fill(level, 17, 0, -20, 19, 0, -20, "orbitalis:fluid_pipe");
            set(level, 18, 1, -20, "orbitalis:fluid_pipe");
            set(level, 20, 0, -20, "orbitalis:cryogenic_tank");
            set(level, 16, 0, -18, "orbitalis:creative_fluid_source[fluid=4]");
            fill(level, 17, 0, -18, 19, 0, -18, "orbitalis:gas_pipe");
            set(level, 18, 1, -18, "orbitalis:gas_pipe");
            set(level, 20, 0, -18, "orbitalis:cryogenic_tank");
            set(level, 16, 0, -16, "orbitalis:creative_fluid_source[fluid=1]");
            fill(level, 17, 0, -16, 19, 0, -16, "orbitalis:fluid_pipe");
            set(level, 20, 0, -16, "orbitalis:cryogenic_tank");
            set(level, 16, 0, -14, "orbitalis:creative_fluid_source[fluid=5]");
            fill(level, 17, 0, -14, 19, 0, -14, "orbitalis:gas_pipe");
            set(level, 20, 0, -14, "orbitalis:cryogenic_tank");
            fill(level, 22, 0, -22, 24, 0, -22, "orbitalis:energy_cable");
            fill(level, 22, 0, -20, 24, 0, -20, "orbitalis:fluid_pipe");
            fill(level, 22, 0, -18, 24, 0, -18, "orbitalis:gas_pipe");
        }, 20.5, 3.0, -11.0, 180.0F, 28.0F, 40));
        SHOTS.add(new Shot("conduits_close", null, 18.5, 0.6, -15.9, 180.0F, 18.0F, 12));
        SHOTS.add(new Shot("conduits_idle", null, 23.5, 0.6, -15.9, 180.0F, 18.0F, 12));

        // Dépôt d'ergols : quatre réservoirs modulables (un bloc d'eau, 2×2×3 de RP-1, 3×3×6 d'oxygène liquide, une
        // colonne d'hydrogène gazeux), les six réservoirs créatifs, et un réservoir que vide un tuyau en extraction.
        SHOTS.add(new Shot("depot_tanks", level -> {
            fill(level, 26, -1, -24, 58, -1, -2, "minecraft:smooth_stone");
            for (int y = 0; y <= 12; y += 4) {
                fill(level, 26, y, -24, 58, y + 3, -2, "minecraft:air");
            }
            set(level, 28, 0, -20, "orbitalis:fluid_tank");
            fill(level, 30, 0, -21, 31, 2, -20, "orbitalis:fluid_tank");
            fill(level, 33, 0, -22, 35, 5, -20, "orbitalis:fluid_tank");
            fill(level, 37, 0, -20, 37, 3, -20, "orbitalis:fluid_tank");
            pour(level, 28, 0, -20, net.minecraft.world.level.material.Fluids.WATER, 9_000);
            pour(level, 30, 0, -21, ModFluids.RP1.get(), 115_000);
            pour(level, 33, 0, -22, ModFluids.LIQUID_OXYGEN.get(), 390_000);
            pour(level, 37, 0, -20, ModFluids.HYDROGEN.get(), 32_000);
            String[] creative = {"rp1", "liquid_oxygen", "liquid_hydrogen", "water", "hydrogen", "oxygen", "methane", "liquid_methane", "helium_3"};
            for (int i = 0; i < creative.length; i++) {
                set(level, 39 + 2 * i, 0, -20, "orbitalis:creative_tank_" + creative[i]);
            }
            // Extraction : le réservoir de gauche se vide dans le réservoir cryogénique, par un tuyau au collier orange.
            set(level, 28, 0, -16, "orbitalis:fluid_tank");
            pour(level, 28, 0, -16, ModFluids.RP1.get(), 16_000);
            set(level, 32, 0, -16, "orbitalis:cryogenic_tank");
            set(level, 29, 0, -16, "orbitalis:fluid_pipe[pull=true]");
            fill(level, 30, 0, -16, 31, 0, -16, "orbitalis:fluid_pipe");
            // Un coude et un té de câble de données, pour voir un nœud à côté des tronçons droits.
            fill(level, 34, 0, -16, 38, 0, -16, "orbitalis:data_cable");
            fill(level, 36, 1, -16, 36, 2, -16, "orbitalis:data_cable");
            set(level, 39, 0, -16, "orbitalis:fluid_tank");
            // Écrans tournés vers le sud. Une dalle seule posée sur un réservoir ; deux petits écrans reliés au grand
            // pas de tir ; un écran de 4×2 qui lit trois réservoirs par un câble de données ; une dalle sans source.
            set(level, 28, 0, -10, "orbitalis:fluid_tank");
            pour(level, 28, 0, -10, ModFluids.LIQUID_OXYGEN.get(), 11_000);
            set(level, 28, 1, -10, "orbitalis:mission_display[facing=south]");
            fill(level, 30, 1, -10, 31, 1, -10, "orbitalis:mission_display[facing=south]");
            fill(level, 33, 1, -10, 33, 2, -10, "orbitalis:mission_display[facing=south]");
            for (int[] slab : new int[][] {{30, 1}, {31, 1}, {33, 1}, {33, 2}}) {
                if (level.getBlockEntity(at(slab[0], slab[1], -10)) instanceof MissionDisplayBlockEntity display) {
                    display.setLink(at(22, 1, 22));
                }
            }
            fill(level, 35, 1, -10, 38, 2, -10, "orbitalis:mission_display[facing=south]");
            // Le câble part du dos de l'écran, longe le mur et redescend sur trois réservoirs.
            fill(level, 38, 1, -11, 42, 1, -11, "orbitalis:data_cable");
            set(level, 40, 0, -11, "orbitalis:fluid_tank");
            set(level, 41, 0, -11, "orbitalis:cryogenic_tank");
            set(level, 42, 0, -11, "orbitalis:fluid_tank");
            pour(level, 40, 0, -11, ModFluids.RP1.get(), 12_500);
            pour(level, 42, 0, -11, net.minecraft.world.level.material.Fluids.WATER, 4_000);
            if (level.getBlockEntity(at(41, 0, -11)) instanceof com.orbitalis.phase1.fuel.CryogenicTankBlockEntity tank) {
                tank.getTank().setFluid(0, new net.neoforged.neoforge.fluids.FluidStack(ModFluids.LIQUID_HYDROGEN.get(), 21_000));
            }
            set(level, 45, 1, -10, "orbitalis:mission_display[facing=south]");
        }, 33.5, 5.0, -11.5, 180.0F, 14.0F, 70));
        SHOTS.add(new Shot("depot_tank_close", null, 31.5, 1.8, -17.6, 180.0F, 8.0F, 12));
        // Les réservoirs créatifs, de près : chacun doit montrer la couleur de son fluide.
        SHOTS.add(new Shot("depot_creative", null, 47.5, 1.4, -15.5, 180.0F, 12.0F, 20));
        SHOTS.add(new Shot("depot_creative", null, 44.5, 1.6, -17.0, 180.0F, 16.0F, 12));
        SHOTS.add(new Shot("depot_pipes", null, 30.5, 1.0, -13.6, 180.0F, 14.0F, 12));
        SHOTS.add(new Shot("depot_data", null, 36.5, 1.4, -13.2, 180.0F, 8.0F, 12));
        SHOTS.add(new Shot("depot_data_b", null, 36.5, 1.4, -13.2, 180.0F, 8.0F, 3));
        SHOTS.add(new Shot("depot_power", level -> {
            set(level, 28, 0, -6, "orbitalis:creative_energy_source");
            fill(level, 29, 0, -6, 32, 0, -6, "orbitalis:energy_cable");
            set(level, 33, 0, -6, "orbitalis:arc_foundry");
        }, 30.8, 1.3, -3.6, 180.0F, 18.0F, 40));
        SHOTS.add(new Shot("depot_power_b", null, 30.8, 1.3, -3.6, 180.0F, 18.0F, 3));
        SHOTS.add(new Shot("depot_displays", null, 35.5, 2.0, -3.6, 180.0F, 2.0F, 12));
        SHOTS.add(new Shot("depot_displays_small", null, 30.5, 1.6, -6.6, 180.0F, 0.0F, 12));
        SHOTS.add(new Shot("depot_displays_wall", null, 38.5, 1.9, -6.2, 180.0F, 0.0F, 12));
        SHOTS.add(new Shot("depot_displays_side", null, 41.5, 2.4, -7.6, 140.0F, 10.0F, 12));
        SHOTS.add(new Shot("depot_displays_back", null, 36.0, 2.0, -13.0, 0.0F, 0.0F, 12));

        // Panneaux solaires : un grand panneau de six sur trois, un panneau seul, et un sas d'amarrage.
        SHOTS.add(new Shot("solar_array", level -> {
            fill(level, -8, 0, -16, -3, 0, -14, "orbitalis:solar_panel[north=true,east=true,south=true,west=true]");
            fill(level, -8, 0, -16, -3, 0, -16, "orbitalis:solar_panel[north=false,east=true,south=true,west=true]");
            fill(level, -8, 0, -14, -3, 0, -14, "orbitalis:solar_panel[north=true,east=true,south=false,west=true]");
            fill(level, -8, 0, -15, -8, 0, -15, "orbitalis:solar_panel[north=true,east=true,south=true,west=false]");
            fill(level, -3, 0, -15, -3, 0, -15, "orbitalis:solar_panel[north=true,east=false,south=true,west=true]");
            set(level, -8, 0, -16, "orbitalis:solar_panel[north=false,east=true,south=true,west=false]");
            set(level, -3, 0, -16, "orbitalis:solar_panel[north=false,east=false,south=true,west=true]");
            set(level, -8, 0, -14, "orbitalis:solar_panel[north=true,east=true,south=false,west=false]");
            set(level, -3, 0, -14, "orbitalis:solar_panel[north=true,east=false,south=false,west=true]");
            set(level, -1, 0, -15, "orbitalis:solar_panel");
            set(level, 1, 0, -15, "orbitalis:docking_port[facing=south]");
            set(level, 3, 0, -15, "orbitalis:docking_port[facing=north]");
        }, -2.5, 2.2, -10.5, 155.0F, 28.0F, 40));
        SHOTS.add(new Shot("docking_port", null, 2.0, 0.2, -12.2, 180.0F, 5.0F, 12));

        // Capsule habitée : éjectée par sa tour de sauvetage, elle redescend sous parachutes, puis se pose.
        SHOTS.add(new Shot("capsule_chute", level -> {
            for (ServerPlayer player : level.players()) {
                player.closeContainer();
            }
            CapsuleEntity capsule = new CapsuleEntity(ModEntities.CAPSULE.get(), level);
            capsule.setPos(at(10, 0, -12).getX() + 0.5, at(10, 0, -12).getY(), at(10, 0, -12).getZ() + 0.5);
            capsule.setDrift(0.0, 0.0);
            level.addFreshEntity(capsule);
            capsule.abort();
        }, 17.5, 44.0, -11.5, 90.0F, 5.0F, 70));
        SHOTS.add(new Shot("capsule_landed", null, 14.5, 2.2, -11.5, 90.0F, 15.0F, 150));

        // En orbite : une capsule immobile dans le vide, sous le ciel noir.
        SHOTS.add(new Shot("orbit_capsule", level -> {
            ServerLevel orbit = level.getServer().getLevel(SpaceRules.ORBIT);
            if (orbit != null) {
                CapsuleEntity capsule = new CapsuleEntity(ModEntities.CAPSULE.get(), orbit);
                capsule.setPos(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5);
                capsule.parkInOrbit();
                orbit.addFreshEntity(capsule);
            }
        }, 4.5, 1.5, 3.5, 130.0F, 10.0F, 80));
        // Une manœuvre préparée, vue du siège du pilote : d'abord le compte à rebours, puis la poussée à 70 %.
        SHOTS.add(new Shot("orbit_node", level -> {
            ServerLevel orbit = level.getServer().getLevel(SpaceRules.ORBIT);
            if (orbit != null) {
                nodeCapsule = new CapsuleEntity(ModEntities.CAPSULE.get(), orbit);
                nodeCapsule.setPos(origin.getX() + 40.5, origin.getY() + 4, origin.getZ() + 0.5);
                nodeCapsule.parkInOrbit();
                orbit.addFreshEntity(nodeCapsule);
                nodeCapsule.previewNode(CapsuleEntity.NODE_MOON, 300, 0.0F);
                // En créatif : le vide ne fait rien au joueur le temps qu'il monte à bord.
                run(level, "gamemode creative @a");
                run(level, ("execute in orbitalis:orbit run tp @a %.2f %.2f %.2f 180 10").formatted(nodeCapsule.getX(), nodeCapsule.getY(), nodeCapsule.getZ())
                        .replace(',', '.'));
            }
        }, Double.NaN, 0.0, 0.0, 0.0F, 0.0F, 20));
        SHOTS.add(new Shot("orbit_node_seat", level -> {
            if (nodeCapsule != null) {
                for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
                    player.startRiding(nodeCapsule, true, false);
                }
            }
        }, Double.NaN, 0.0, 0.0, 0.0F, 0.0F, 30));
        SHOTS.add(new Shot("orbit_node_burn", level -> {
            if (nodeCapsule != null) {
                nodeCapsule.previewNode(CapsuleEntity.NODE_MOON, 420, 3150.0F * 0.7F);
            }
        }, Double.NaN, 0.0, 0.0, 0.0F, 0.0F, 10));
        // La vue carte, depuis le même siège : la manœuvre prévue, puis la poussée en cours.
        SHOTS.add(new Shot("orbit_map", level -> net.minecraft.client.Minecraft.getInstance().execute(() -> {
            com.orbitalis.client.OrbitMap.open = true;
            if (nodeCapsule != null) {
                nodeCapsule.previewNode(CapsuleEntity.NODE_MOON, 200, 0.0F);
            }
        }), Double.NaN, 0.0, 0.0, 0.0F, 0.0F, 10));
        SHOTS.add(new Shot("orbit_map_burn", level -> {
            if (nodeCapsule != null) {
                nodeCapsule.previewNode(CapsuleEntity.NODE_MOON, 420, 3150.0F * 0.7F);
            }
        }, Double.NaN, 0.0, 0.0, 0.0F, 0.0F, 10));
        SHOTS.add(new Shot("orbit_map_deorbit", level -> {
            if (nodeCapsule != null) {
                nodeCapsule.previewNode(CapsuleEntity.NODE_DEORBIT, 40, 0.0F);
            }
        }, Double.NaN, 0.0, 0.0, 0.0F, 0.0F, 10));
        SHOTS.add(new Shot("orbit_map_closed", level -> net.minecraft.client.Minecraft.getInstance().execute(
                () -> com.orbitalis.client.OrbitMap.open = false), Double.NaN, 0.0, 0.0, 0.0F, 0.0F, 4));
        SHOTS.add(new Shot("orbit_station", level -> {
            ServerLevel orbit = level.getServer().getLevel(SpaceRules.ORBIT);
            if (orbit != null) {
                StationManager.build(orbit, origin.offset(0, 0, -12));
            }
        }, 7.5, 2.5, -4.5, 135.0F, 12.0F, 100));
        // Le cockpit de la capsule, en orbite : vu du siège du pilote, de derrière l'équipage, le plafond, le manche.
        SHOTS.add(new Shot("orbit_cockpit_panel", level -> {
            ServerLevel orbit = level.getServer().getLevel(SpaceRules.ORBIT);
            if (orbit != null) {
                for (CapsuleEntity old : orbit.getEntitiesOfClass(CapsuleEntity.class, new net.minecraft.world.phys.AABB(at(30, 0, 0)).inflate(6.0))) {
                    old.discard();
                }
                CapsuleEntity capsule = new CapsuleEntity(ModEntities.CAPSULE.get(), orbit);
                capsule.setPos(at(30, 0, 0).getX() + 0.5, at(30, 0, 0).getY(), at(30, 0, 0).getZ() + 0.5);
                capsule.parkInOrbit();
                orbit.addFreshEntity(capsule);
            }
        }, 30.5, -0.495, 0.594, 180.0F, 28.0F, 60));
        // Les capsules Mk2 et Mk3, en orbite à côté de la Mk1 : leur cockpit, puis les trois de dehors.
        SHOTS.add(new Shot("orbit_mk2_panel", level -> {
            ServerLevel orbit = level.getServer().getLevel(SpaceRules.ORBIT);
            if (orbit != null) {
                for (int k = 1; k <= 2; k++) {
                    BlockPos at = at(30, 0, 0).offset(4 * k, 0, 0);
                    for (CapsuleEntity old : orbit.getEntitiesOfClass(CapsuleEntity.class, new net.minecraft.world.phys.AABB(at).inflate(1.5))) {
                        old.discard();
                    }
                    CapsuleEntity capsule = new CapsuleEntity(ModEntities.CAPSULE.get(), orbit);
                    capsule.setVariant(k);
                    capsule.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
                    capsule.parkInOrbit();
                    orbit.addFreshEntity(capsule);
                }
            }
        }, 34.5, -0.495, 0.594, 180.0F, 22.0F, 60));
        SHOTS.add(new Shot("orbit_mk2_crew", null, 34.5, -0.35, 1.25, 180.0F, 22.0F, 12));
        SHOTS.add(new Shot("orbit_mk2_back", null, 34.5, -0.495, 0.3, 0.0F, 18.0F, 12));
        SHOTS.add(new Shot("orbit_mk3_panel", null, 38.5, -0.495, 0.594, 180.0F, 22.0F, 12));
        SHOTS.add(new Shot("orbit_mk3_crew", null, 38.5, -0.35, 1.25, 180.0F, 22.0F, 12));
        SHOTS.add(new Shot("orbit_mk3_back", null, 38.5, -0.495, 0.3, 0.0F, 18.0F, 12));
        SHOTS.add(new Shot("orbit_capsules_line", null, 34.5, 1.4, 6.5, 180.0F, 8.0F, 12));
        SHOTS.add(new Shot("orbit_cockpit_eye", null, 30.5, -0.495, 0.594, 180.0F, 8.0F, 12));
        SHOTS.add(new Shot("orbit_cockpit_crew", null, 30.5, -0.35, 1.25, 180.0F, 24.0F, 12));
        SHOTS.add(new Shot("orbit_cockpit_up", null, 30.5, -0.495, 0.594, 180.0F, -55.0F, 12));
        SHOTS.add(new Shot("orbit_cockpit_stick", null, 30.5, -0.495, 0.594, -140.0F, 50.0F, 12));
        SHOTS.add(new Shot("orbit_cockpit_back", null, 30.5, -0.495, 0.3, 0.0F, 20.0F, 12));
        SHOTS.add(new Shot("orbit_cockpit_outside", null, 33.5, 1.2, 3.0, 135.0F, 12.0F, 12));
        SHOTS.add(new Shot("orbit_approach", level -> {
            ServerLevel orbit = level.getServer().getLevel(SpaceRules.ORBIT);
            if (orbit != null) {
                CapsuleEntity capsule = new CapsuleEntity(ModEntities.CAPSULE.get(), orbit);
                capsule.setPos(origin.getX() + 0.5, origin.getY(), origin.getZ() - 11.5);
                orbit.addFreshEntity(capsule);
                capsule.startApproach(origin.offset(0, 0, -12));
            }
        }, 9.5, 3.0, -4.0, 110.0F, 8.0F, 70));
        SHOTS.add(new Shot("orbit_docked", null, 7.5, 1.5, -6.5, 125.0F, 8.0F, 260));
        SHOTS.add(new Shot("orbit_undocking", level -> {
            ServerLevel orbit = level.getServer().getLevel(SpaceRules.ORBIT);
            if (orbit != null) {
                for (CapsuleEntity capsule : orbit.getEntitiesOfClass(CapsuleEntity.class,
                        new net.minecraft.world.phys.AABB(origin.offset(0, 0, -12)).inflate(12.0))) {
                    capsule.undock();
                }
            }
        }, 7.5, 1.5, -6.5, 125.0F, 8.0F, 75));
        SHOTS.add(new Shot("orbit_station_inside", null, 0.5, -1.4, -12.5, 150.0F, -5.0F, 20));
        SHOTS.add(new Shot("orbit_expanded", level -> {
            ServerLevel orbit = level.getServer().getLevel(SpaceRules.ORBIT);
            if (orbit != null) {
                StationManager.expand(orbit, origin.offset(2, 0, -12), net.minecraft.core.Direction.EAST);
            }
        }, 11.0, 5.0, -3.5, 125.0F, 22.0F, 30));
        SHOTS.add(new Shot("orbit_planet", null, 9.0, 6.0, -3.0, 120.0F, 40.0F, 30));
        SHOTS.add(new Shot("orbit_horizon", null, 9.0, 3.0, -3.0, 120.0F, 12.0F, 15));
        SHOTS.add(new Shot("orbit_sky", null, 4.5, 1.5, 3.5, -60.0F, -35.0F, 30));

        // La Lune : le vaisseau lunaire en descente, puis posé, et la planète dans le ciel.
        // Mars : le vaisseau en descente propulsée (l'ordinateur de bord le pose), puis posé ; le relief et les cratères.
        SHOTS.add(new Shot("mars_arrival", level -> {
            ServerLevel mars = level.getServer().getLevel(SpaceRules.MARS);
            com.orbitalis.Orbitalis.LOGGER.info("APERCU dimension de Mars présente : {}", mars != null);
            if (mars != null) {
                CapsuleEntity lander = new CapsuleEntity(ModEntities.CAPSULE.get(), mars);
                lander.setPos(origin.getX() + 0.5, marsGround(level) + 30.0, origin.getZ() + 0.5);
                mars.addFreshEntity(lander);
                lander.startMarsDescent();
            }
        }, 10.0, 12.0, 10.0, 135.0F, -12.0F, 40));
        SHOTS.add(new Shot("mars_landed", null, 6.5, 2.2, 6.5, 135.0F, 8.0F, 260));
        SHOTS.add(new Shot("mars_landscape", null, 0.5, 14.0, 0.5, 40.0F, 12.0F, 20));
        SHOTS.add(new Shot("mars_craters", null, 0.5, 50.0, 0.5, 220.0F, 45.0F, 60));
        // Titan : le vaisseau titanien sous ses parachutes, puis posé ; les dunes de tholines et les lacs de méthane.
        SHOTS.add(new Shot("titan_chutes", level -> {
            ServerLevel titan = level.getServer().getLevel(SpaceRules.TITAN);
            com.orbitalis.Orbitalis.LOGGER.info("APERCU dimension de Titan présente : {}", titan != null);
            if (titan != null) {
                CapsuleEntity lander = new CapsuleEntity(ModEntities.CAPSULE.get(), titan);
                lander.setPos(origin.getX() + 0.5, titanGround(level) + 60.0, origin.getZ() + 0.5);
                titan.addFreshEntity(lander);
                lander.startTitanEntry();
            }
        }, 9.0, 42.0, 9.0, 135.0F, 0.0F, 40));
        SHOTS.add(new Shot("titan_landed", null, 6.5, 2.2, 6.5, 135.0F, 8.0F, 220));
        // Europe : sa banquise et ses lineae, Jupiter dans le ciel, le cryobot au travail, le vaisseau posé.
        SHOTS.add(new Shot("europa_landscape", level -> {
            ServerLevel europa = level.getServer().getLevel(SpaceRules.EUROPA);
            com.orbitalis.Orbitalis.LOGGER.info("APERCU dimension d'Europe présente : {}", europa != null);
            if (europa != null) {
                int ground = (int) europaGround(level);
                BlockPos spot = new BlockPos(origin.getX() + 3, ground, origin.getZ() - 2);
                europa.setBlock(spot, com.orbitalis.core.registry.ModBlocks.CRYOBOT.get().defaultBlockState(), 3);
                CapsuleEntity lander = new CapsuleEntity(ModEntities.CAPSULE.get(), europa);
                lander.setPos(origin.getX() - 3.5, ground, origin.getZ() - 2.5);
                europa.addFreshEntity(lander);
                lander.setEuropan(true);
                lander.setVariant(2);
                lander.startEuropaDescent();
            }
        }, 0.5, 2.6, 4.5, 180.0F, 6.0F, 60));
        SHOTS.add(new Shot("europa_jupiter", null, 0.5, 2.0, 0.5, 42.0F, -22.0F, 20));
        SHOTS.add(new Shot("europa_plain", null, 0.5, 30.0, 0.5, 20.0F, 30.0F, 40));
        // Le drone volant, posé sur les dunes, puis en vol vers son premier site.
        SHOTS.add(new Shot("titan_drone", level -> {
            ServerLevel titan = level.getServer().getLevel(SpaceRules.TITAN);
            if (titan != null) {
                double ground = titanGround(level);
                for (var old : titan.getEntitiesOfClass(com.orbitalis.phase3.exploration.RotorDroneEntity.class,
                        new net.minecraft.world.phys.AABB(origin).inflate(64.0))) {
                    old.discard();
                }
                var drone = com.orbitalis.core.registry.ModEntities.ROTOR_DRONE.get().create(titan,
                        net.minecraft.world.entity.EntitySpawnReason.SPAWN_ITEM_USE);
                drone.snapTo(origin.getX() + 3.5, ground, origin.getZ() + 0.5, 30.0F, 0.0F);
                titan.addFreshEntity(drone);
                previewDrone = drone;
            }
        }, 0.5, 2.0, 4.0, 225.0F, 12.0F, 30));
        SHOTS.add(new Shot("titan_drone_flight", level -> {
            ServerLevel titan = level.getServer().getLevel(SpaceRules.TITAN);
            if (titan != null && previewDrone != null && !level.getServer().getPlayerList().getPlayers().isEmpty()) {
                com.orbitalis.Orbitalis.LOGGER.info("APERCU drone : {}", previewDrone.launch(titan,
                        level.getServer().getPlayerList().getPlayers().get(0)).getString());
            }
        }, 0.5, 2.0, 4.0, 225.0F, -25.0F, 45));
        SHOTS.add(new Shot("titan_landscape", null, 0.5, 14.0, 0.5, 40.0F, 12.0F, 20));
        SHOTS.add(new Shot("titan_lakes", level -> {
            ServerLevel titan = level.getServer().getLevel(SpaceRules.TITAN);
            titanLake = titan == null ? null : findTitanLake(titan);
            com.orbitalis.Orbitalis.LOGGER.info("APERCU lac de Titan : {}", titanLake);
        }, 0.5, 30.0, 0.5, 220.0F, 30.0F, 80));
        SHOTS.add(new Shot("titan_lake_shore", null, 0.5, 3.0, 0.5, 40.0F, 10.0F, 30));
        SHOTS.add(new Shot("titan_lake_under", null, 0.5, -2.5, 0.5, 40.0F, 0.0F, 20));
        SHOTS.add(new Shot("moon_arrival", level -> {
            ServerLevel moon = level.getServer().getLevel(SpaceRules.MOON);
            if (moon != null) {
                CapsuleEntity lander = new CapsuleEntity(ModEntities.CAPSULE.get(), moon);
                lander.setPos(origin.getX() + 0.5, moonGround(level) + 14.0, origin.getZ() + 0.5);
                moon.addFreshEntity(lander);
                lander.startLunarDescent();
            }
        }, 9.0, 5.0, 9.0, 135.0F, 0.0F, 30));
        SHOTS.add(new Shot("moon_landed", null, 6.5, 2.2, 6.5, 135.0F, 8.0F, 140));
        SHOTS.add(new Shot("moon_landscape", null, 0.5, 12.0, 0.5, 40.0F, 12.0F, 20));
        SHOTS.add(new Shot("moon_craters", null, 0.5, 45.0, 0.5, 40.0F, 50.0F, 60));
        SHOTS.add(new Shot("moon_craters_b", null, 60.5, 40.0, 60.5, 220.0F, 45.0F, 60));
        SHOTS.add(new Shot("moon_earth", null, 6.5, 2.2, 6.5, -55.0F, -45.0F, 15));

        // Affichages des murs d'écrans : un par mur, du plus petit au plus grand, dont celui des satellites.
        SHOTS.add(new Shot("walls_pages_small", level -> {
            for (ServerPlayer player : level.players()) {
                player.closeContainer();
                String agency = ResearchManager.agencyKey(player);
                setWallPage(level, -20, 3, 2, MissionDisplayBlockEntity.Page.TELEMETRY, null);
                setWallPage(level, -15, 4, 2, MissionDisplayBlockEntity.Page.SATELLITES, agency);
                setWallPage(level, -9, 5, 3, MissionDisplayBlockEntity.Page.SATELLITES, agency);
                setWallPage(level, -2, 7, 4, MissionDisplayBlockEntity.Page.MAP, null);
            }
        }, -15.0, 2.2, 25.0, 0.0F, 0.0F, 60));
        SHOTS.add(new Shot("walls_pages_large", null, 0.5, 3.0, 24.0, 0.0F, 0.0F, 20));
        SHOTS.add(new Shot("walls_pages_more", level -> {
            setWallPage(level, -20, 3, 2, MissionDisplayBlockEntity.Page.TRAJECTORY, null);
            setWallPage(level, -15, 4, 2, MissionDisplayBlockEntity.Page.ROCKET, null);
        }, -15.0, 2.2, 25.0, 0.0F, 0.0F, 40));

        // Carte de navigation en relief : des collines, une forêt et un lac autour de la scène, puis le relevé du
        // terrain refait, et le grand mur d'écrans passé sur la carte.
        SHOTS.add(new Shot("iso_map", level -> {
            for (int x = -104; x < 148; x += 8) {
                for (int z = -104; z < 148; z += 8) {
                    if (x >= -24 && x < 40 && z >= -24 && z < 40) {
                        continue;
                    }
                    int height = (int) Math.round(9 + 8 * Math.sin(x * 0.045) * Math.cos(z * 0.05) + 5 * Math.sin((x + z) * 0.08));
                    if (height < 5) {
                        fill(level, x, -6, z, x + 7, 2, z + 7, "minecraft:water");
                        continue;
                    }
                    fill(level, x, -6, z, x + 7, height - 1, z + 7, height > 17 ? "minecraft:stone" : "minecraft:dirt");
                    fill(level, x, height, z, x + 7, height, z + 7, height > 17 ? "minecraft:snow_block" : "minecraft:grass_block");
                    // Des bosquets : une case sur trois, à mi-pente.
                    if (height > 7 && height < 16 && Math.floorMod(x * 7 + z * 13, 24) < 9) {
                        fill(level, x + 1, height + 1, z + 1, x + 6, height + 4, z + 6, "minecraft:oak_leaves[persistent=true]");
                    }
                }
            }
            setWallPage(level, -2, 7, 4, MissionDisplayBlockEntity.Page.MAP, null);
        }, 0.5, 3.0, 24.0, 0.0F, 0.0F, 80));
        SHOTS.add(new Shot("iso_map_close", null, 1.5, 3.2, 27.0, 0.0F, 0.0F, 12));
        // La carte tournée d'un quart de tour : le coin nord-est au fond. Puis de retour au nord-ouest.
        SHOTS.add(new Shot("iso_map_turned", level -> net.minecraft.client.Minecraft.getInstance().execute(
                () -> com.orbitalis.client.render.IsoMap.turn(at(22, 1, 22), 1)), 1.5, 3.2, 27.0, 0.0F, 0.0F, 60));
        SHOTS.add(new Shot("iso_map_turned_back", level -> net.minecraft.client.Minecraft.getInstance().execute(
                () -> com.orbitalis.client.render.IsoMap.turn(at(22, 1, 22), 0)), 1.5, 3.2, 27.0, 0.0F, 0.0F, 40));
        SHOTS.add(new Shot("iso_map_terrain", null, 22.0, 60.0, -60.0, 0.0F, 40.0F, 30));
        // La même carte pendant un vol : au décollage, dans la montée, au passage des nuages, puis très haut.
        SHOTS.add(new Shot("iso_map_liftoff", level -> {
            if (level.getBlockEntity(at(22, 1, 22)) instanceof LaunchTableBlockEntity table) {
                table.setReliable(true);
                table.command(com.orbitalis.phase1.spaceport.LaunchStep.VALVES, "preview");
                table.fill(ModFluids.RP1.get(), 1_000_000, false);
                table.fill(ModFluids.LIQUID_OXYGEN.get(), 1_000_000, false);
                table.startCountdown("preview");
            }
        }, 1.5, 3.2, 27.0, 0.0F, 0.0F, 650));
        SHOTS.add(new Shot("iso_map_climb", null, 1.5, 3.2, 27.0, 0.0F, 0.0F, 90));
        SHOTS.add(new Shot("iso_map_clouds", null, 1.5, 3.2, 27.0, 0.0F, 0.0F, 110));
        SHOTS.add(new Shot("iso_map_curve", null, 1.5, 3.2, 27.0, 0.0F, 0.0F, 45));
        SHOTS.add(new Shot("iso_map_high", null, 1.5, 3.2, 27.0, 0.0F, 0.0F, 115));
        SHOTS.add(new Shot("iso_map_space", null, 1.5, 3.2, 27.0, 0.0F, 0.0F, 200));
        SHOTS.add(new Shot("iso_map_orbit", null, 1.5, 3.2, 27.0, 0.0F, 0.0F, 45));
        // La même vue, tournée d'un demi-tour : le globe se repeint dans la nouvelle orientation.
        SHOTS.add(new Shot("iso_map_orbit_turned", level -> net.minecraft.client.Minecraft.getInstance().execute(
                () -> com.orbitalis.client.render.IsoMap.turn(at(22, 1, 22), 2)), 1.5, 3.2, 27.0, 0.0F, 0.0F, 25));
        // Juste avant la mise en orbite : toute la trajectoire, sur le globe.
        SHOTS.add(new Shot("iso_map_insertion", level -> net.minecraft.client.Minecraft.getInstance().execute(
                () -> com.orbitalis.client.render.IsoMap.turn(at(22, 1, 22), 0)), 1.5, 3.2, 27.0, 0.0F, 0.0F, 45));

        // Un vol suivi sur deux murs : la télémesure sur le grand, la carte de navigation sur le moyen.
        SHOTS.add(new Shot("flight_telemetry", level -> {
            setWallPage(level, -2, 7, 4, MissionDisplayBlockEntity.Page.TELEMETRY, null);
            setWallPage(level, -9, 5, 3, MissionDisplayBlockEntity.Page.MAP, null);
            if (level.getBlockEntity(at(22, 1, 22)) instanceof LaunchTableBlockEntity table) {
                table.setReliable(true);
                table.command(com.orbitalis.phase1.spaceport.LaunchStep.VALVES, "preview");
                table.fill(ModFluids.RP1.get(), 1_000_000, false);
                table.fill(ModFluids.LIQUID_OXYGEN.get(), 1_000_000, false);
                table.startCountdown("preview");
            }
        }, 1.5, 3.2, 27.0, 0.0F, 0.0F, 810));
        SHOTS.add(new Shot("flight_map", null, -6.5, 2.5, 27.2, 0.0F, 0.0F, 10));
        SHOTS.add(new Shot("flight_telemetry_late", null, 1.5, 3.2, 27.0, 0.0F, 0.0F, 300));
        SHOTS.add(new Shot("flight_map_late", null, -6.5, 2.5, 27.2, 0.0F, 0.0F, 10));
        SHOTS.add(new Shot("flight_map_space", null, -6.5, 2.5, 27.2, 0.0F, 0.0F, 190));

        // Les blocs d'aménagement des stations, en rang, face avant vers la caméra.
        SHOTS.add(new Shot("station_blocks_a", level -> {
            run(level, "gamemode spectator @a");
            run(level, "time set noon");
            fill(level, -14, -1, 8, 14, -1, 12, "minecraft:smooth_stone");
            fill(level, -14, 0, 8, 14, 3, 12, "minecraft:air");
            String[] blocks = {"station_panel[facing=north]", "station_light[facing=north]", "station_floor", "station_truss",
                    "station_hatch[half=lower,facing=south]", "cupola_window", "station_terminal[facing=north]", "crew_bunk[facing=north]",
                    "lab_rack[facing=north,lit=true]", "hydroponic_tray[crop=wheat,age=3]", "cargo_rack[facing=north]",
                    "station_battery[facing=north,charge=3]"};
            for (int i = 0; i < blocks.length; i++) {
                set(level, -11 + 2 * i, 0, 10, "orbitalis:" + blocks[i]);
            }
            set(level, -3, 1, 10, "orbitalis:station_hatch[half=upper,facing=south]");
            set(level, 3, 1, 10, "orbitalis:crew_bunk[facing=north,half=upper]");
            set(level, -11, 1, 10, "orbitalis:station_panel[facing=north]");
            set(level, -9, 1, 10, "orbitalis:station_panel[facing=north]");
            set(level, 7, 1, 10, "orbitalis:hydroponic_tray[crop=carrot,age=2]");
        }, -6.0, 2.2, 6.2, 0.0F, 24.0F, 60));
        SHOTS.add(new Shot("station_blocks_b", null, 6.0, 2.2, 6.2, 0.0F, 24.0F, 12));
        // De près, un bloc à la fois.
        String[] closeNames = {"truss", "hatch", "bunk", "lab", "tray", "cargo", "battery"};
        int[] closeX = {-5, -3, 3, 5, 7, 9, 11};
        for (int i = 0; i < closeNames.length; i++) {
            SHOTS.add(new Shot("close_" + closeNames[i], null, closeX[i] + 0.5, 1.2, 8.4, 0.0F, 18.0F, 6));
        }
        // Les portes (sas fermé et ouvert, porte vitrée fermée et ouverte entre deux pans de mur), puis la baie de
        // serveurs, les rambardes empilées, le générateur d'air, le béton coulé et le projecteur de mât.
        SHOTS.add(new Shot("doors_a", level -> {
            run(level, "gamemode spectator @a");
            run(level, "time set noon");
            fill(level, -14, -1, 8, 25, -1, 12, "minecraft:smooth_stone");
            fill(level, -14, 0, 8, 25, 4, 12, "minecraft:air");
            for (int k = 0; k < 2; k++) {
                int x = -11 + 3 * k;
                String phase = k == 0 ? "closed" : "open";
                set(level, x, 0, 10, "orbitalis:airlock_door[facing=south,half=lower,phase=" + phase + "]");
                set(level, x, 1, 10, "orbitalis:airlock_door[facing=south,half=upper,phase=" + phase + "]");
            }
            for (int k = 0; k < 2; k++) {
                // Face au nord, la colonne de droite est à l'ouest de celle de gauche.
                int x = -1 + 5 * k;
                String open = k == 0 ? "false" : "true";
                fill(level, x + 1, 0, 10, x + 1, 1, 10, "orbitalis:station_hull");
                fill(level, x - 2, 0, 10, x - 2, 1, 10, "orbitalis:station_hull");
                for (int dy = 0; dy < 2; dy++) {
                    String half = dy == 0 ? "lower" : "upper";
                    set(level, x, dy, 10, "orbitalis:glass_door[facing=north,half=" + half + ",right=false,open=" + open + "]");
                    set(level, x - 1, dy, 10, "orbitalis:glass_door[facing=north,half=" + half + ",right=true,open=" + open + "]");
                }
            }
            set(level, 6, 0, 10, "orbitalis:server_rack[facing=north]");
            set(level, 8, 0, 10, "orbitalis:tower_railing");
            set(level, 8, 1, 10, "orbitalis:tower_railing");
            set(level, 9, 0, 10, "orbitalis:tower_railing");
            set(level, 10, 0, 10, "orbitalis:tower_railing");
            set(level, 12, 0, 10, "orbitalis:life_support");
            set(level, 14, 0, 10, "orbitalis:facility_concrete");
            set(level, 15, 0, 10, "orbitalis:cracked_facility_concrete");
            set(level, 17, 0, 10, "orbitalis:pad_floodlight");
            set(level, 20, 0, 10, "orbitalis:suit_workbench[facing=north,right=false]");
            set(level, 19, 0, 10, "orbitalis:suit_workbench[facing=north,right=true]");
            set(level, 23, 0, 10, "orbitalis:robotic_lander");
            run(level, "kill @e[type=orbitalis:rover]");
            fill(level, 17, -1, 1, 25, -1, 7, "minecraft:smooth_stone");
            fill(level, 17, 0, 1, 25, 4, 7, "minecraft:air");
            run(level, "summon orbitalis:rover %s %s %s {Rotation:[150.0f,0.0f]}".formatted(origin.getX() + 21.5, origin.getY(), origin.getZ() + 4.5));
        }, -4.5, 2.4, 5.0, 0.0F, 22.0F, 60));
        SHOTS.add(new Shot("doors_b", null, 11.5, 2.2, 6.0, 0.0F, 22.0F, 12));
        SHOTS.add(new Shot("doors_c", null, -9.5, 2.0, 14.5, 180.0F, 18.0F, 12));
        SHOTS.add(new Shot("railing_under", null, 9.0, -0.6, 7.5, 0.0F, -35.0F, 12));
        String[] nearNames = {"airlock_closed", "airlock_open", "glass_closed", "glass_open", "rack", "railing", "life", "concrete", "flood"};
        double[] nearX = {-10.5, -7.5, -1.0, 4.0, 6.5, 9.5, 12.5, 15.0, 17.5};
        for (int i = 0; i < nearNames.length; i++) {
            SHOTS.add(new Shot("near_" + nearNames[i], null, nearX[i], 1.6, 8.0, 0.0F, 18.0F, 6));
        }
        SHOTS.add(new Shot("near_airlock_open_side", null, -5.5, 1.8, 8.5, 60.0F, 12.0F, 6));
        SHOTS.add(new Shot("near_bench", null, 20.0, 1.9, 8.2, 0.0F, 30.0F, 6));
        SHOTS.add(new Shot("near_bench_side", null, 22.5, 1.9, 8.0, 40.0F, 25.0F, 6));
        SHOTS.add(new Shot("near_lander", null, 24.6, 1.3, 8.2, 25.0F, 18.0F, 6));
        SHOTS.add(new Shot("near_rover", null, 19.0, 2.4, 1.5, -40.0F, 32.0F, 6));

        // Le rover sur le régolithe, au repos puis en plein virage (sa pose est figée par la capture), devant un mur de
        // revêtement de station et ses plafonniers.
        SHOTS.add(look("rover_a", level -> {
            run(level, "gamemode spectator @a");
            run(level, "time set noon");
            run(level, "kill @e[type=orbitalis:rover]");
            fill(level, 28, -1, 0, 46, -1, 16, "orbitalis:moon_regolith");
            fill(level, 28, 0, 0, 46, 5, 16, "minecraft:air");
            fill(level, 30, 0, 14, 40, 1, 14, "orbitalis:station_panel[facing=north]");
            fill(level, 30, 2, 14, 40, 2, 14, "orbitalis:station_light[facing=north]");
            set(level, 33, 1, 14, "orbitalis:station_panel[facing=north]");
            run(level, "summon orbitalis:rover %s %s %s {Rotation:[150.0f,0.0f]}".formatted(origin.getX() + 35.5, origin.getY(), origin.getZ() + 7.5));
            run(level, "summon orbitalis:rover %s %s %s {Rotation:[210.0f,0.0f]}".formatted(origin.getX() + 41.5, origin.getY(), origin.getZ() + 6.5));
        }, 32.5, -0.3, 4.5, 35.5, 0.5, 7.5, 60));
        SHOTS.add(look("rover_side", null, 37.6, -1.1, 6.3, 35.5, 0.4, 7.5, 8));
        SHOTS.add(look("rover_top", null, 36.6, 0.4, 9.4, 35.5, 0.3, 7.5, 8));
        SHOTS.add(look("rover_wheel", null, 33.0, -1.3, 7.0, 34.56, 0.27, 7.41, 8));
        SHOTS.add(look("rover_steer", null, 42.8, -0.9, 4.25, 41.5, 0.4, 6.5, 8));
        SHOTS.add(look("rover_steer_top", null, 42.4, 0.6, 4.94, 41.5, 0.3, 6.5, 8));
        SHOTS.add(look("station_wall", null, 35.0, -0.2, 9.5, 35.0, 1.5, 14.0, 8));
        SHOTS.add(look("station_wall_close", null, 32.6, -0.3, 12.1, 32.8, 1.6, 14.0, 8));

        // Une station complète, en orbite : le module central, un laboratoire, une habitation, une serre, une coupole,
        // un sas de sortie, un module logistique et une poutre d'énergie.
        SHOTS.add(new Shot("orbit_station_full", level -> {
            ServerLevel orbit = level.getServer().getLevel(SpaceRules.ORBIT);
            if (orbit == null) {
                return;
            }
            run(level, "gamemode spectator @a");
            BlockPos c = origin.offset(0, 0, -12);
            for (BlockPos pos : BlockPos.betweenClosed(c.offset(-14, -6, -18), c.offset(16, 6, 8))) {
                if (!orbit.getBlockState(pos).isAir()) {
                    orbit.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2 | 16);
                }
            }
            StationManager.build(orbit, c);
            if (!level.getServer().getPlayerList().getPlayers().isEmpty()) {
                StationManager.register(level.getServer(), ResearchManager.agencyKey(level.getServer().getPlayerList().getPlayers().get(0)), c);
            }
            com.orbitalis.phase2.station.StationLayout.clear(level.getServer(), c);
            com.orbitalis.phase2.station.StationLayout.modules(level.getServer(), c);
            say(com.orbitalis.phase2.station.StationModules.attach(orbit, c.offset(-2, 0, 1), Direction.WEST, com.orbitalis.phase2.station.ModuleType.LABORATORY));
            say(com.orbitalis.phase2.station.StationModules.attach(orbit, c.offset(1, 0, -2), Direction.NORTH, com.orbitalis.phase2.station.ModuleType.HABITATION));
            say(com.orbitalis.phase2.station.StationModules.attach(orbit, c.offset(2, 0, 1), Direction.EAST, com.orbitalis.phase2.station.ModuleType.GREENHOUSE));
            say(com.orbitalis.phase2.station.StationModules.attach(orbit, c.offset(-5, 0, -1), Direction.NORTH, com.orbitalis.phase2.station.ModuleType.CUPOLA));
            say(com.orbitalis.phase2.station.StationModules.attach(orbit, c.offset(2, 0, -8), Direction.NORTH, com.orbitalis.phase2.station.ModuleType.POWER));
            say(com.orbitalis.phase2.station.StationModules.attach(orbit, c.offset(8, 0, 1), Direction.EAST, com.orbitalis.phase2.station.ModuleType.AIRLOCK));
            say(com.orbitalis.phase2.station.StationModules.attach(orbit, c.offset(3, 0, -4), Direction.EAST, com.orbitalis.phase2.station.ModuleType.CARGO));
            // La serre, plantée : des cultures variées, à des stades différents.
            int k = 0;
            for (BlockPos pos : BlockPos.betweenClosed(c.offset(3, -1, -1), c.offset(7, -1, 3))) {
                net.minecraft.world.level.block.state.BlockState state = orbit.getBlockState(pos);
                if (state.getBlock() instanceof com.orbitalis.phase2.station.HydroponicTrayBlock) {
                    orbit.setBlock(pos, state.setValue(com.orbitalis.phase2.station.HydroponicTrayBlock.CROP,
                                    com.orbitalis.phase2.station.HydroponicTrayBlock.Crop.values()[1 + k % 4])
                            .setValue(com.orbitalis.phase2.station.HydroponicTrayBlock.AGE, 3 - (k / 2) % 3), 3);
                    k++;
                }
            }
        }, 16.0, 12.0, -4.0, 132.0F, 31.0F, 180));
        SHOTS.add(new Shot("orbit_clock_check", level -> {
            net.minecraft.client.multiplayer.ClientLevel client = net.minecraft.client.Minecraft.getInstance().level;
            ServerLevel orbit = level.getServer().getLevel(SpaceRules.ORBIT);
            com.orbitalis.Orbitalis.LOGGER.info("APERCU horloge : client {} heure {} horloge {} ; serveur orbite heure {} horloge {}",
                    client == null ? "?" : client.dimension(), client == null ? -1 : client.getDefaultClockTime(),
                    client == null ? "?" : client.dimensionType().defaultClock(), orbit == null ? -1 : orbit.getDefaultClockTime(),
                    orbit == null ? "?" : orbit.dimensionType().defaultClock());
        }, 16.0, 12.0, -4.0, 132.0F, 31.0F, 5));
        SHOTS.add(new Shot("orbit_station_west", null, -14.0, 8.0, -30.0, -50.0F, 22.0F, 30));
        SHOTS.add(new Shot("orbit_station_below", null, 6.0, -9.0, -2.0, 150.0F, -28.0F, 30));
        SHOTS.add(new Shot("orbit_core_inside", null, 0.5, -1.0, -10.8, 180.0F, 12.0F, 40));
        SHOTS.add(new Shot("orbit_lab_inside", null, -2.4, -1.0, -10.5, 90.0F, 6.0F, 30));
        SHOTS.add(new Shot("orbit_lab_rack", null, -2.6, -0.9, -10.8, 60.0F, 15.0F, 20));
        SHOTS.add(new Shot("orbit_hab_inside", null, 1.5, -1.0, -12.6, 180.0F, 6.0F, 30));
        SHOTS.add(new Shot("orbit_hab_bunk", null, 1.6, -1.0, -15.3, 110.0F, 12.0F, 20));
        SHOTS.add(new Shot("orbit_greenhouse_inside", null, 3.6, -0.6, -10.5, -90.0F, 22.0F, 30));
        SHOTS.add(new Shot("orbit_cupola_inside", null, -4.5, -0.8, -13.8, 180.0F, 25.0F, 30));
        SHOTS.add(new Shot("orbit_airlock_inside", null, 9.4, -1.0, -10.5, -90.0F, 6.0F, 30));
        SHOTS.add(new Shot("orbit_airlock_cycling", level -> {
            ServerLevel orbit = level.getServer().getLevel(SpaceRules.ORBIT);
            if (orbit != null) {
                say(com.orbitalis.phase2.station.AirlockDoorBlock.toggle(orbit, origin.offset(11, -1, -11)));
            }
        }, 9.4, -1.0, -10.5, -90.0F, 6.0F, 20));
        SHOTS.add(new Shot("orbit_airlock_sliding", null, 9.4, -1.0, -10.5, -90.0F, 6.0F, 25));
        SHOTS.add(new Shot("orbit_airlock_open", null, 9.4, -1.0, -10.5, -90.0F, 6.0F, 20));
        SHOTS.add(new Shot("orbit_airlock_outside", null, 14.5, -0.6, -10.5, 90.0F, 4.0F, 20));
        SHOTS.add(new Shot("orbit_airlock_interlock", level -> {
            ServerLevel orbit = level.getServer().getLevel(SpaceRules.ORBIT);
            if (orbit != null) {
                say(com.orbitalis.phase2.station.AirlockDoorBlock.toggle(orbit, origin.offset(8, -1, -11)));
            }
        }, 7.4, -1.0, -10.5, -90.0F, 6.0F, 10));
        // Un joueur en survie, dans la station : il flotte (ni chute, ni vol créatif), debout.
        SHOTS.add(new Shot("orbit_float", level -> {
            run(level, "gamemode survival @a");
            run(level, "effect give @a minecraft:resistance 60 4 true");
        }, 0.5, -0.5, -11.0, 180.0F, 10.0F, 40));
        SHOTS.add(new Shot("orbit_float_check", level -> {
            for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
                net.minecraft.client.player.LocalPlayer local = net.minecraft.client.Minecraft.getInstance().player;
                com.orbitalis.Orbitalis.LOGGER.info("APERCU apesanteur serveur : y={} vol={} droit={} pesanteur={} posture={} ; client : y={} vol={} posture={} vitesse={}",
                        String.format(java.util.Locale.ROOT, "%.3f", player.getY()), player.getAbilities().flying, player.mayFly(),
                        player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.GRAVITY), player.getPose(),
                        local == null ? "?" : String.format(java.util.Locale.ROOT, "%.3f", local.getY()), local != null && local.getAbilities().flying,
                        local == null ? "?" : local.getPose(), local == null ? "?" : local.getDeltaMovement());
            }
        }, 0.5, -0.5, -11.0, 180.0F, 10.0F, 60));
        SHOTS.add(new Shot("orbit_float_after", level -> {
            for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
                net.minecraft.client.player.LocalPlayer local = net.minecraft.client.Minecraft.getInstance().player;
                com.orbitalis.Orbitalis.LOGGER.info("APERCU apesanteur, 3 s plus tard : serveur y={} ; client y={} vol={} posture={}",
                        String.format(java.util.Locale.ROOT, "%.3f", player.getY()),
                        local == null ? "?" : String.format(java.util.Locale.ROOT, "%.3f", local.getY()), local != null && local.getAbilities().flying,
                        local == null ? "?" : local.getPose());
            }
            run(level, "gamemode spectator @a");
        }, 0.5, -0.5, -11.0, 180.0F, 10.0F, 10));
        SHOTS.add(new Shot("orbit_airlock_closing", level -> {
            ServerLevel orbit = level.getServer().getLevel(SpaceRules.ORBIT);
            if (orbit != null) {
                say(com.orbitalis.phase2.station.AirlockDoorBlock.toggle(orbit, origin.offset(11, -1, -11)));
            }
        }, 9.4, -1.0, -10.5, -90.0F, 6.0F, 6));
        SHOTS.add(new Shot("orbit_airlock_closed_again", null, 9.4, -1.0, -10.5, -90.0F, 6.0F, 30));
        // Le tableau de bord, sur le terminal du module central.
        SHOTS.add(new Shot("orbit_terminal_screen", level -> {
            ServerLevel orbit = level.getServer().getLevel(SpaceRules.ORBIT);
            if (orbit != null) {
                BlockPos c = origin.offset(0, 0, -12);
                var payload = com.orbitalis.phase2.station.StationStatus.collect(orbit, c.offset(0, -1, -2), c, true);
                net.minecraft.client.Minecraft.getInstance().execute(() -> com.orbitalis.client.screen.StationScreen.accept(payload));
            }
        }, 0.5, -1.0, -11.5, 180.0F, 12.0F, 60));
        SHOTS.add(new Shot("orbit_terminal_closed", level -> net.minecraft.client.Minecraft.getInstance().execute(
                () -> net.minecraft.client.Minecraft.getInstance().gui.setScreen(null)), 0.5, -1.0, -11.5, 180.0F, 12.0F, 10));

        // Les deux combinaisons, portées par deux mannequins : de face, de dos, de trois quarts.
        SHOTS.add(new Shot("suits_display", level -> {
            run(level, "gamemode spectator @a");
            run(level, "time set 6000");
            run(level, "weather clear");
            for (net.minecraft.server.level.ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
                for (net.minecraft.world.entity.EquipmentSlot slot : com.orbitalis.phase2.survival.SpaceSuits.PIECES) {
                    player.setItemSlot(slot, net.minecraft.world.item.ItemStack.EMPTY);
                }
            }
            fill(level, 64, 39, -5, 78, 39, 7, "minecraft:smooth_stone");
            fill(level, 64, 40, -5, 78, 46, 7, "minecraft:air");
            for (net.minecraft.world.entity.decoration.ArmorStand old : level.getEntitiesOfClass(net.minecraft.world.entity.decoration.ArmorStand.class,
                    net.minecraft.world.phys.AABB.encapsulatingFullBlocks(at(64, 40, -5), at(78, 46, 7)))) {
                old.discard();
            }
            for (int k = 0; k < 2; k++) {
                net.minecraft.world.entity.decoration.ArmorStand stand = new net.minecraft.world.entity.decoration.ArmorStand(level,
                        origin.getX() + 70.0 + k * 1.6, origin.getY() + 40, origin.getZ() + 0.5);
                stand.setShowArms(true);
                stand.setNoBasePlate(true);
                stand.setYRot(0.0F);
                stand.setYBodyRot(0.0F);
                stand.setYHeadRot(0.0F);
                suit(stand, k == 1, false);
                level.addFreshEntity(stand);
            }
        }, 70.8, 41.5, 3.6, 180.0F, 8.0F, 40));
        SHOTS.add(new Shot("suits_back", null, 70.8, 41.5, -2.6, 0.0F, 8.0F, 12));
        SHOTS.add(new Shot("suits_three_quarter", null, 73.4, 41.9, 2.8, 140.0F, 14.0F, 12));
        SHOTS.add(new Shot("suits_close", null, 70.8, 41.7, 2.4, 180.0F, 14.0F, 12));
        // L'affichage du casque : la combinaison orange sur la planète.
        SHOTS.add(new Shot("suit_hud_orange", level -> {
            run(level, "gamemode survival @a");
            level.getServer().getPlayerList().getPlayers().forEach(player -> suit(player, false, false));
        }, 70.8, 40.0, 4.5, 180.0F, 5.0F, 40));
        SHOTS.add(new Shot("suit_hud_white", level -> {
            run(level, "gamemode survival @a");
            level.getServer().getPlayerList().getPlayers().forEach(player -> suit(player, true, true));
        }, 70.8, 40.0, 4.5, 180.0F, 5.0F, 40));
        // La combinaison blanche et tous ses modules, dans la station puis dehors, dans le vide.
        SHOTS.add(new Shot("orbit_suit_hud_inside", level -> {
            run(level, "gamemode survival @a");
            level.getServer().getPlayerList().getPlayers().forEach(player -> suit(player, true, true));
        }, 0.5, -1.0, -10.8, 180.0F, 12.0F, 60));
        SHOTS.add(new Shot("orbit_suit_hud_eva", null, 14.5, -0.6, -10.5, 90.0F, 4.0F, 50));
        SHOTS.add(new Shot("orbit_suit_hud_turn", null, 14.5, -0.6, -10.5, 120.0F, 4.0F, 3));

        // Les centres spatiaux : le plus proche de chaque sorte, vu d'avion puis de plus près.
        for (String kind : new String[] {"weather_station", "research_center", "observation_center", "control_center"}) {
            double far = kind.equals("control_center") ? 62.0 : 34.0;
            for (int round = 0; round < 2; round++) {
                int offset = round * 2400;
                String name = "site_" + kind + (round == 0 ? "" : "_b");
                SHOTS.add(new Shot(name, level -> findSite(level, kind, offset), far, far * 0.7, far, 135.0F, 27.0F, 260));
                SHOTS.add(new Shot(name + "_low", null, -far * 0.55, 9.0, far * 0.55, -135.0F, 10.0F, 60));
                SHOTS.add(new Shot(name + "_west", null, -far, far * 0.5, -far * 0.2, -80.0F, 22.0F, 60));
            }
        }

        // Dans les bâtiments du dernier centre trouvé : la place est donnée dans le repère du bâtiment, le regard par
        // rapport à sa façade (0 : vers la façade, 180 : vers le fond).
        SHOTS.add(new Shot("inside_firing_room", level -> lookInside(level, "control_center", com.orbitalis.core.world.FacilityBuilding.LAUNCH_CONTROL, 13, 7, 11), 0, 0, 0,
                0.0F, 18.0F, 300));
        SHOTS.add(new Shot("inside_firing_room_back", level -> lookInside(level, "control_center", com.orbitalis.core.world.FacilityBuilding.LAUNCH_CONTROL, 13, 6, 4), 0, 0, 0,
                180.0F, 0.0F, 40));
        SHOTS.add(new Shot("inside_lcc_lobby", level -> lookInside(level, "control_center", com.orbitalis.core.world.FacilityBuilding.LAUNCH_CONTROL, 13, 1, 4), 0, 0, 0,
                180.0F, 5.0F, 40));
        SHOTS.add(new Shot("inside_assembly", level -> lookInside(level, "control_center", com.orbitalis.core.world.FacilityBuilding.ASSEMBLY_BUILDING, 7, 1, 3), 0, 0, 0,
                180.0F, -45.0F, 40));
        SHOTS.add(new Shot("inside_pad", level -> lookInside(level, "control_center", com.orbitalis.core.world.FacilityBuilding.LAUNCH_PAD, 12, 4, 6), 0, 0, 0,
                180.0F, -20.0F, 40));
        SHOTS.add(new Shot("inside_lab", level -> lookInside(level, "research_center", com.orbitalis.core.world.FacilityBuilding.RESEARCH_LAB, 8, 1, 4), 0, 0, 0,
                180.0F, 5.0F, 240));
        SHOTS.add(new Shot("inside_control_hut", level -> lookInside(level, "observation_center", com.orbitalis.core.world.FacilityBuilding.CONTROL_HUT,
                4, 1, 3), 0, 0, 0, 180.0F, 10.0F, 240));
        SHOTS.add(new Shot("inside_control_hut_door", level -> lookInside(level, "observation_center", com.orbitalis.core.world.FacilityBuilding.CONTROL_HUT,
                4, 1, 4), 0, 0, 0, 0.0F, 15.0F, 40));
        SHOTS.add(new Shot("inside_lab_upper", level -> lookInside(level, "research_center", com.orbitalis.core.world.FacilityBuilding.RESEARCH_LAB,
                7, 5, 7), 0, 0, 0, 180.0F, 25.0F, 160));
        SHOTS.add(new Shot("inside_lab_door", level -> lookInside(level, "research_center", com.orbitalis.core.world.FacilityBuilding.RESEARCH_LAB,
                8, 1, 0), 0, 0, 0, 180.0F, 15.0F, 40));
        SHOTS.add(new Shot("inside_observatory", level -> lookInside(level, "observation_center", com.orbitalis.core.world.FacilityBuilding.OBSERVATORY, 5, 1, 3), 0, 0, 0,
                180.0F, -30.0F, 240));
        SHOTS.add(new Shot("inside_weather_hut", level -> lookInside(level, "weather_station", com.orbitalis.core.world.FacilityBuilding.WEATHER_HUT, 4, 1, 3), 0, 0, 0,
                180.0F, 10.0F, 240));

        // La carte de test (gradlew deployTestMap) : l'accueil, la galerie, les mannequins, les centres, la station.
        SHOTS.add(new Shot("tm_welcome", level -> run(level, "gamemode spectator @a"), 0.5, -57.0, 9.0, 180.0F, 25.0F, 120));
        SHOTS.add(new Shot("tm_gallery", null, 50.0, -50.0, -26.0, 180.0F, 35.0F, 80));
        SHOTS.add(new Shot("tm_suits", null, -11.5, -58.5, 0.5, 180.0F, 10.0F, 40));
        SHOTS.add(new Shot("tm_storage", null, -3.5, -58.5, -10.5, 180.0F, 30.0F, 30));
        SHOTS.add(new Shot("tm_sites_weather", null, 230.0, -30.0, 220.0, 135.0F, 30.0F, 120));
        SHOTS.add(new Shot("tm_sites_control", null, 1010.0, -5.0, 245.0, 135.0F, 30.0F, 160));
        SHOTS.add(new Shot("tm_station", null, 18.0, 112.0, 10.0, 132.0F, 31.0F, 160));
        // L'atelier : les machines en marche, posées en -24 -60 10.
        SHOTS.add(new Shot("tm_machines", null, -22.0, -58.2, 14.8, 180.0F, 24.0F, 80));
        // Les fusées du catalogue : en blocs au premier rang, assemblées au second.
        SHOTS.add(new Shot("tm_rockets", null, -14.0, -38.0, 22.0, -50.0F, 22.0F, 120));
        SHOTS.add(new Shot("tm_rockets_close", null, 18.0, -52.0, 28.0, -15.0F, 8.0F, 60));
        // Le chantier naval, au bout du module logistique : le pupitre est en 12 99 -4, le vaisseau part vers l'est. Pour
        // la vue, la salle des machines et l'anneau sont posés ; le reste du vaisseau est en fantômes.
        SHOTS.add(new Shot("tm_orbit_shipyard", level -> {
            ServerLevel orbit = level.getServer().getLevel(SpaceRules.ORBIT);
            BlockPos consolePos = new BlockPos(12, 99, -4);
            if (orbit != null) {
                orbit.getChunkAt(consolePos);
            }
            if (orbit != null && orbit.getBlockEntity(consolePos) instanceof com.orbitalis.phase3.interstellar.ShipyardConsoleBlockEntity console) {
                for (com.orbitalis.phase3.interstellar.ShipPlan.Piece piece : console.pieces()) {
                    if (piece.section().ordinal() <= com.orbitalis.phase3.interstellar.ShipPlan.Section.RING.ordinal()) {
                        orbit.setBlockAndUpdate(console.shipOrigin().offset(piece.offset()), piece.state());
                    }
                }
            }
        }, 25.0, 108.0, 12.0, 200.0F, 22.0F, 120));
        SHOTS.add(new Shot("tm_orbit_yard_room", null, 10.6, 99.0, -3.5, -90.0F, 8.0F, 60));
        // Le générateur d'air du module central, au plafond, en marche dans la station pressurisée.
        SHOTS.add(new Shot("tm_orbit_air", null, 0.5, 99.0, 1.4, 180.0F, -48.0F, 60));
        SHOTS.add(new Shot("tm_orbit_air_on", level -> {
            ServerLevel orbit = level.getServer().getLevel(SpaceRules.ORBIT);
            if (orbit != null) {
                orbit.setBlockAndUpdate(new BlockPos(0, 102, 0), ModBlocks.LIFE_SUPPORT.get().defaultBlockState()
                        .setValue(com.orbitalis.phase2.station.LifeSupportBlock.ACTIVE, true));
            }
        }, 0.5, 99.0, 1.4, 180.0F, -48.0F, 6));
        // Le vaisseau achevé et aménagé, son cœur chargé : la poupe est en 15 100 -4, la proue regarde l'est.
        SHOTS.add(new Shot("tm_orbit_ship", level -> {
            ServerLevel orbit = level.getServer().getLevel(SpaceRules.ORBIT);
            BlockPos consolePos = new BlockPos(12, 99, -4);
            if (orbit != null) {
                orbit.getChunkAt(consolePos);
            }
            if (orbit != null && orbit.getBlockEntity(consolePos) instanceof com.orbitalis.phase3.interstellar.ShipyardConsoleBlockEntity console) {
                for (com.orbitalis.phase3.interstellar.ShipPlan.Piece piece : console.pieces()) {
                    BlockPos pos = console.shipOrigin().offset(piece.offset());
                    if (com.orbitalis.phase3.interstellar.ShipPlan.free(piece)) {
                        continue;
                    }
                    if (piece.state().hasProperty(com.orbitalis.phase3.interstellar.TwoHighBlock.HALF)) {
                        com.orbitalis.phase3.interstellar.TwoHighBlock.place(orbit, pos, piece.state());
                    } else {
                        orbit.setBlockAndUpdate(pos, piece.state());
                    }
                }
                BlockPos core = console.shipOrigin().offset(com.orbitalis.phase3.interstellar.ShipPlan.local(net.minecraft.core.Direction.EAST, 2, 0, -1));
                com.orbitalis.phase3.interstellar.WarpCoreBlock.setCharge(orbit, core, orbit.getBlockState(core),
                        com.orbitalis.phase3.interstellar.WarpCoreBlock.MAX_CHARGE);
            }
        }, 29.0, 110.0, 14.0, 200.0F, 25.0F, 120));
        SHOTS.add(new Shot("tm_orbit_ship_core", null, 21.5, 99.0, -3.5, 90.0F, 12.0F, 60));
        // Proxima b : vers l'est, la naine rouge basse sur l'horizon et la bande du crépuscule ; puis de près, la flore ;
        // enfin l'orbite de Proxima, la planète sous le joueur.
        SHOTS.add(new Shot("tm_proxima_east", null, 0.5, 140.0, 0.5, -90.0F, 8.0F, 200));
        SHOTS.add(new Shot("tm_proxima_ground", null, 0.5, 112.0, 0.5, -60.0F, 45.0F, 120));
        // La flore, de près : la caméra se pose au ras du sol, là où il se trouve, près du méridien zéro.
        SHOTS.add(new Shot("tm_proxima_flora", level -> {
            ServerLevel proxima = level.getServer().getLevel(com.orbitalis.phase2.survival.SpaceRules.PROXIMA_B);
            if (proxima == null) {
                return;
            }
            // Le premier sol moussu du méridien zéro, vers le nord : la flore n'y pousse que là.
            for (int z = 0; z > -400; z -= 4) {
                proxima.getChunkAt(new BlockPos(0, 0, z));
                int ground = proxima.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, 0, z);
                if (proxima.getBlockState(new BlockPos(0, ground - 1, z)).is(ModBlocks.PROXIMA_MOSS.get())
                        || proxima.getBlockState(new BlockPos(0, ground - 2, z)).is(ModBlocks.PROXIMA_MOSS.get())) {
                    run(level, ("execute in orbitalis:proxima_b run tp @a 0.5 %.1f %.1f 180 30").formatted(ground + 2.5, z + 0.5).replace(',', '.'));
                    return;
                }
            }
        }, Double.NaN, 0.0, 0.0, 0.0F, 0.0F, 140));
        SHOTS.add(new Shot("tm_proxima_orbit_view", null, 0.5, 100.0, 0.5, -90.0F, 55.0F, 80));
        SHOTS.add(new Shot("tm_orbit_ship_pods", null, 27.5, 99.0, -3.5, -90.0F, 8.0F, 60));
        SHOTS.add(new Shot("tm_orbit_ship_greenhouse", null, 33.5, 99.0, -3.5, -90.0F, 14.0F, 60));
        SHOTS.add(new Shot("tm_orbit_ship_bridge", null, 39.5, 99.0, -3.5, -90.0F, 6.0F, 60));
        SHOTS.add(new Shot("tm_orbit_yard_screen", level -> net.minecraft.client.Minecraft.getInstance().execute(() ->
                net.minecraft.client.Minecraft.getInstance().gui.setScreen(new com.orbitalis.client.screen.ShipyardScreen(new BlockPos(12, 99, -4)))),
                10.6, 99.0, -3.5, -90.0F, 8.0F, 40));

        // Seules les vues demandées sont prises, s'il y en a (-Porbitalis.previewOnly=vue1,vue2).
        String only = System.getProperty("orbitalis.previewOnly", "");
        if (!only.isEmpty()) {
            java.util.Set<String> names = java.util.Set.of(only.split(","));
            SHOTS.removeIf(shot -> !names.contains(shot.name()));
        }
    }

    /** Cherche le centre spatial de cette sorte le plus proche de la scène, comme la commande « locate ». */
    private static void findSite(ServerLevel level, String kind, int offset) {
        run(level, "gamemode spectator @a");
        for (net.minecraft.server.level.ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            for (net.minecraft.world.entity.EquipmentSlot slot : com.orbitalis.phase2.survival.SpaceSuits.PIECES) {
                player.setItemSlot(slot, net.minecraft.world.item.ItemStack.EMPTY);
            }
        }
        run(level, "time set 6000");
        run(level, "weather clear");
        var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.STRUCTURE, Orbitalis.id(kind));
        var holder = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE).get(key);
        site = null;
        if (holder.isEmpty()) {
            Orbitalis.LOGGER.info("APERCU centre {} : structure inconnue", kind);
            return;
        }
        var found = level.getChunkSource().getGenerator().findNearestMapStructure(level, net.minecraft.core.HolderSet.direct(holder.get()), origin.offset(offset, 0, 0),
                kind.equals("control_center") ? 160 : 80, false);
        if (found == null) {
            Orbitalis.LOGGER.info("APERCU centre {} : aucun trouvé", kind);
            return;
        }
        BlockPos pos = found.getFirst();
        siteStart = level.getChunk(pos.getX() >> 4, pos.getZ() >> 4).getStartForStructure(holder.get().value());
        siteKind = kind;
        int x = (pos.getX() & ~15) + 8;
        int z = (pos.getZ() & ~15) + 8;
        int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        site = new BlockPos(x, y, z);
        Orbitalis.LOGGER.info("APERCU centre {} : trouvé en {} (à {} blocs)", kind, site, (int) Math.sqrt(site.distSqr(origin)));
    }

    /** Place la vue d'intérieur dans ce bâtiment du dernier centre trouvé. */
    private static void lookInside(ServerLevel level, String kind, com.orbitalis.core.world.FacilityBuilding building, int x, int y, int z) {
        if (!kind.equals(siteKind)) {
            // Loin des centres déjà bâtis : ceux-là gardent la version de leur génération.
            findSite(level, kind, kind.equals("control_center") ? -40000 : -20000);
        }
        inside = null;
        if (siteStart == null) {
            return;
        }
        for (var piece : siteStart.getPieces()) {
            if (piece instanceof com.orbitalis.core.world.FacilityPiece facility && facility.building() == building) {
                BlockPos at = facility.worldPos(x, y, z);
                inside = new net.minecraft.world.phys.Vec3(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
                insideYaw = facility.front().toYRot();
                Orbitalis.LOGGER.info("APERCU intérieur {} : {} (façade {})", building, at, facility.front());
                return;
            }
        }
        Orbitalis.LOGGER.info("APERCU intérieur {} : absent de ce centre", building);
    }

    /** Habille de pied en cap d'une combinaison, orange ou blanche ; la blanche avec tous ses modules, si demandé. */
    private static void suit(net.minecraft.world.entity.LivingEntity entity, boolean advanced, boolean modules) {
        net.minecraft.world.item.ItemStack head = new net.minecraft.world.item.ItemStack(advanced ? com.orbitalis.core.registry.ModItems.ADVANCED_SPACE_HELMET.get() : com.orbitalis.core.registry.ModItems.SPACE_HELMET.get());
        net.minecraft.world.item.ItemStack chest = new net.minecraft.world.item.ItemStack(advanced ? com.orbitalis.core.registry.ModItems.ADVANCED_SPACESUIT.get() : com.orbitalis.core.registry.ModItems.SPACESUIT.get());
        net.minecraft.world.item.ItemStack legs = new net.minecraft.world.item.ItemStack(advanced ? com.orbitalis.core.registry.ModItems.ADVANCED_SPACE_LEGGINGS.get() : com.orbitalis.core.registry.ModItems.SPACE_LEGGINGS.get());
        if (modules) {
            for (com.orbitalis.phase2.survival.SuitModule module : com.orbitalis.phase2.survival.SuitModule.values()) {
                com.orbitalis.phase2.survival.SpaceSuits.install(switch (module.slot()) {
                    case HEAD -> head;
                    case CHEST -> chest;
                    default -> legs;
                }, module);
            }
            // Un peu entamée, pour que la jauge ne soit pas pleine.
            com.orbitalis.phase2.survival.SpaceRules.addOxygen(chest, -380);
        }
        entity.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, head);
        entity.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, chest);
        entity.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, legs);
        entity.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, new net.minecraft.world.item.ItemStack(advanced
                ? com.orbitalis.core.registry.ModItems.ADVANCED_SPACE_BOOTS.get() : com.orbitalis.core.registry.ModItems.SPACE_BOOTS.get()));
    }

    /** Choisit l'affichage d'un mur d'écrans de la scène, et le relie aux satellites d'une agence si elle est donnée. */
    private static void setWallPage(ServerLevel level, int x, int width, int height, MissionDisplayBlockEntity.Page page, String agency) {
        for (int dx = 0; dx < width; dx++) {
            for (int dy = 0; dy < height; dy++) {
                if (level.getBlockEntity(at(x + dx, 1 + dy, 30)) instanceof MissionDisplayBlockEntity display) {
                    if (agency != null) {
                        display.setSatelliteLink(agency);
                        display.refreshSatellites(level.getServer());
                    }
                    display.setPage(page);
                }
            }
        }
    }

    /**
     * Bâtit un pas de tir complet d'après son plan : la table un bloc au-dessus du sol, le carneau vers l'est, la tour
     * (à ascenseur dès le 5×5, à escalier pour le 3×3) à l'ouest, un bras tourné vers la fusée et un bloc de
     * ravitaillement par ergol au pied de la tour.
     */
    private static void buildPad(ServerLevel level, int x, int y, int z, int radius) {
        BlockPos pos = at(x, y, z);
        level.setBlockAndUpdate(pos, ModBlocks.LAUNCH_TABLE.get().defaultBlockState());
        if (!(level.getBlockEntity(pos) instanceof LaunchTableBlockEntity table)) {
            return;
        }
        PadPlan plan = new PadPlan(radius, radius >= 2, Direction.EAST);
        table.setPlan(plan);
        for (PadPlan.Piece piece : plan.pieces()) {
            level.setBlock(pos.offset(piece.offset()), piece.display(), 3);
        }
        set(level, x - radius, y + 3, z, "orbitalis:umbilical_arm");
        String[] kinds = {"rp1", "lox", "lh2"};
        for (int i = 0; i < kinds.length; i++) {
            set(level, x - radius - 2 - i, y - 1, z + 2, "orbitalis:fuel_port[kind=" + kinds[i] + "]");
        }
    }

    /** Met le complexe en service et pose la fusée de test demandée. */
    private static void placeRocket(ServerLevel level, int x, int y, int z, int rocket) {
        if (level.getBlockEntity(at(x, y, z)) instanceof LaunchTableBlockEntity table) {
            table.commission(true);
            table.setRocket(CreativeRockets.all().get(rocket));
            placeGate(level, table);
        }
    }

    /** Fait relever sa structure à la table : le portique d'embarquement se place à la hauteur de la cabine. */
    private static void placeGate(ServerLevel level, LaunchTableBlockEntity table) {
        BlockPos base = table.getComplex().towerBase();
        if (base == null) {
            return;
        }
        // Le portique fait partie du plan, et la table le place d'elle-même à la hauteur de la cabine.
        table.refresh(level);
    }
}
