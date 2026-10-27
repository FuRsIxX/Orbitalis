package com.orbitalis.client.dev;

import java.io.File;
import java.util.List;

import com.orbitalis.Orbitalis;
import com.orbitalis.client.guide.GuideScene;
import com.orbitalis.client.guide.GuideScenes;
import com.orbitalis.client.research.ClientResearch;
import com.orbitalis.client.screen.GuideScreen;
import net.minecraft.world.item.ItemStack;
import com.orbitalis.core.network.ResearchSyncPayload;
import com.orbitalis.core.guide.GuideChapters;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Outil de développement : capture automatiquement des images du manuel, puis ferme le jeu.
 * <p>
 * Inactif en jeu normal. Il ne se déclenche que si le jeu est lancé avec
 * {@code gradlew runClient -Porbitalis.guidePreview=true}, qui ouvre directement le monde
 * {@code run/saves/orbitalis_preview} ; les images sont écrites dans {@code run/guide_preview/screenshots}.
 * Il sert à vérifier la mise en page du manuel sans avoir à jouer.
 */
@EventBusSubscriber(modid = Orbitalis.MODID, value = Dist.CLIENT)
public final class GuidePreview {
    private static final boolean ENABLED = Boolean.getBoolean("orbitalis.guidePreview");
    /** Ticks laissés à l'écran pour s'afficher avant la capture. */
    private static final int SETTLE_TICKS = 15;
    /** Ticks d'attente après l'entrée dans le monde, le temps que tout soit chargé. */
    private static final int WORLD_TICKS = 40;

    /** Vues du terminal de recherche à capturer : numéro de l'arbre, puis la technologie sélectionnée de chaque capture. */
    private static final String[][] RESEARCH_VIEWS = {
            {"0", "kerolox_engines", "methalox_engines"},
            {"1", "steelmaking", "aluminum_lithium"},
            {"2", "gyroscopes", "rtg"}};

    /** Écrans de contrôle capturés après le manuel et la recherche : le radar, le séquenceur, le pupitre. */
    private static final int EXTRA_SCREENS = 9;

    private static int chapter = -1;
    private static int page;
    private static int waited;
    private static boolean finished;

    private GuidePreview() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!ENABLED || finished) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();

        // On attend d'être entré dans un monde : les objets ne peuvent pas être dessinés avant.
        if (chapter < 0) {
            if (minecraft.level != null && minecraft.player != null && minecraft.gui.screen() == null) {
                waited++;
                if (waited > WORLD_TICKS) {
                    // -Porbitalis.guideResearchOnly=true : seulement l'arbre des compétences et les écrans qui suivent.
                    chapter = Boolean.getBoolean("orbitalis.guideResearchOnly") ? GuideChapters.ALL.size() : 0;
                    // La page -1 du premier chapitre : le sommaire.
                    page = chapter == 0 ? -1 : 0;
                    show(minecraft);
                }
            }
            return;
        }

        waited++;
        if (waited == SETTLE_TICKS) {
            String name = chapter < GuideChapters.ALL.size()
                    ? "guide_%02d_%s_page%d.png".formatted(chapter, GuideChapters.ALL.get(chapter).id(), page + 1)
                    : chapter < GuideChapters.ALL.size() + RESEARCH_VIEWS.length
                            ? "research_%d_%d.png".formatted(chapter - GuideChapters.ALL.size(), page + 1)
                            : "screen_%d_%d.png".formatted(chapter - GuideChapters.ALL.size() - RESEARCH_VIEWS.length, page + 1);
            File directory = new File(minecraft.gameDirectory, "guide_preview");
            new File(directory, "screenshots").mkdirs();
            Screenshot.grab(directory, name, minecraft.gameRenderer.mainRenderTarget(), 1,
                    message -> Orbitalis.LOGGER.info("Capture du manuel : {}", name));
        } else if (waited > SETTLE_TICKS + 3) {
            // Deux captures par chapitre : la première page (démonstration ou texte), puis la suivante.
            if (page < 0) {
                page = 0;
            } else if (page == 0) {
                page = 1;
            } else {
                page = 0;
                chapter++;
            }
            if (chapter >= GuideChapters.ALL.size() + RESEARCH_VIEWS.length + EXTRA_SCREENS) {
                finished = true;
                minecraft.stop();
                return;
            }
            show(minecraft);
        }
    }

    /** Ouvre le manuel au chapitre voulu ; une démonstration est montrée à sa dernière étape, où tout est en place. */
    private static void show(Minecraft minecraft) {
        waited = 0;
        if (chapter >= GuideChapters.ALL.size() + RESEARCH_VIEWS.length) {
            showControl(minecraft, chapter - GuideChapters.ALL.size() - RESEARCH_VIEWS.length);
            return;
        }
        if (chapter >= GuideChapters.ALL.size()) {
            // Vues du terminal de recherche, avec un état de recherche fictif pour montrer les trois états des cases.
            String[] view = RESEARCH_VIEWS[chapter - GuideChapters.ALL.size()];
            ClientResearch.receive(new ResearchSyncPayload(page == 0 ? 60 : 8,
                    List.of("solid_motors", "pressure_fed_engines", "aluminum_structures", "basic_electronics")));
            // L'arbre de compétences sur une technologie ; la dernière vue montre l'onglet des objectifs.
            minecraft.gui.setScreen(com.orbitalis.client.screen.SkillTreeScreen.at(
                    chapter - GuideChapters.ALL.size() == RESEARCH_VIEWS.length - 1 && page == 1 ? "" : view[1 + page]));
            return;
        }
        if (page < 0) {
            minecraft.gui.setScreen(GuideScreen.index());
            return;
        }
        GuideScene scene = GuideScenes.get(GuideChapters.ALL.get(chapter).id());
        int lastStep = scene == null ? 0 : scene.stepCount() - 1;
        minecraft.gui.setScreen(GuideScreen.at(chapter, page, lastStep));
    }

    /** Les écrans de contrôle, avec un état fictif : deux moments de chacun. */
    private static void showControl(Minecraft minecraft, int which) {
        net.minecraft.core.BlockPos here = minecraft.player.blockPosition();
        if (which == 5) {
            // L'ordinateur de conception.
            minecraft.gui.setScreen(new com.orbitalis.client.screen.DesignScreen());
            return;
        }
        if (which == 6) {
            // Le kit de pas de tir : un 5×5 à escalier, puis un 9×9 à ascenseur.
            minecraft.gui.setScreen(new com.orbitalis.client.screen.PadKitScreen(null, net.minecraft.core.Direction.EAST, page == 0 ? 2 : 4, page == 1));
            return;
        }
        if (which == 7) {
            // Le panneau de l'ascenseur : la cabine au sol, puis au deuxième portique.
            minecraft.gui.setScreen(new com.orbitalis.client.screen.ElevatorScreen(
                    new com.orbitalis.core.network.ElevatorPayload(0, java.util.List.of(64, 72, 81, 90), page == 0 ? 0 : 2, 0)));
            return;
        }
        if (which == 8) {
            // Le pas de tir : sans fusée, puis avec un lanceur à moitié plein.
            ItemStack craft = page == 0 ? ItemStack.EMPTY : com.orbitalis.core.creative.CreativeRockets.all().get(2);
            minecraft.gui.setScreen(new com.orbitalis.client.screen.LaunchPadScreen(new com.orbitalis.core.network.LaunchPadInfoPayload(
                    true, 2, true, true, page == 1, 12, 16, 12, 1, true, craft, 14, 4200, 9000, 0, 0)));
            return;
        }
        if (which == 4) {
            // L'établi d'ingénieur : une pièce, puis un modèle de lanceur.
            minecraft.gui.setScreen(com.orbitalis.client.screen.EngineerScreen.at(page == 0 ? 0 : 3, page == 0 ? "kerolox_engine" : "rocket_heavy"));
            return;
        }
        if (which == 3) {
            // La prospection : un relevé fictif, avec des filons et un coin de terrain hors de vue.
            com.orbitalis.client.screen.TrackingStationScreen screen = new com.orbitalis.client.screen.TrackingStationScreen(
                    new com.orbitalis.core.network.SatelliteStatusPayload(here, true, true, false, 0, 0, 4800, java.util.List.of(
                            new com.orbitalis.core.network.SatelliteStatusPayload.Entry(1, 3, 24_000L, 1_200_000L, 8))));
            int size = page == 1 ? 9 : 5;
            int kinds = com.orbitalis.phase1.satellite.ProspectOre.values().length;
            int[] counts = new int[size * size * kinds];
            for (int cell = 0; cell < size * size; cell++) {
                boolean unseen = cell % size == size - 1 && cell / size < 2;
                for (int kind = 0; kind < kinds; kind++) {
                    counts[cell * kinds + kind] = unseen ? 0 : 1 + Math.floorMod(cell * 31 + kind * 17, 11) * (Math.floorMod(cell + kind * 3, 4) == 0 ? 6 : 0);
                }
            }
            minecraft.gui.setScreen(screen);
            screen.showSurvey(new com.orbitalis.core.network.ProspectPayload(here, size, 10, -4, kinds, counts));
            return;
        }
        if (which == 0) {
            java.util.List<com.orbitalis.core.network.SatelliteStatusPayload.Entry> fleet = new java.util.ArrayList<>();
            if (page == 1) {
                long day = 24_000L;
                fleet.add(new com.orbitalis.core.network.SatelliteStatusPayload.Entry(1, 0, 20 * day, 0, 0));
                fleet.add(new com.orbitalis.core.network.SatelliteStatusPayload.Entry(2, 1, 12 * day, 48 * day, 14));
                fleet.add(new com.orbitalis.core.network.SatelliteStatusPayload.Entry(3, 3, 9 * day, 51 * day, 36));
                fleet.add(new com.orbitalis.core.network.SatelliteStatusPayload.Entry(4, 4, 6 * day, 94 * day, 0));
                fleet.add(new com.orbitalis.core.network.SatelliteStatusPayload.Entry(5, 2, 3 * day, 117 * day, 0));
                fleet.add(new com.orbitalis.core.network.SatelliteStatusPayload.Entry(6, 5, 1 * day, 149 * day, 12));
            }
            minecraft.gui.setScreen(new com.orbitalis.client.screen.TrackingStationScreen(new com.orbitalis.core.network.SatelliteStatusPayload(
                    here, true, true, page == 1, page == 1 ? 1 : 0, page == 1 ? 1 : -1, 4800, fleet)));
            return;
        }
        // Au sol, pendant le tour de table (page 1) ou juste avant (page 0) ; fusée à ergols liquides, pas de tir 5×5.
        com.orbitalis.core.network.LaunchStatusPayload status = new com.orbitalis.core.network.LaunchStatusPayload(here, true, true, 42, true,
                2, 0, true, 12, 1, true, 8, true, 48_000.0F, 3, true, 100, true, 0, 0, 0.0F, 0.0F, 0, 0, true, false,
                page == 1 ? 96 : 0, page == 1, true, 0, 100, 0, 0, 0, 0, 1, 2, page == 1 ? com.orbitalis.phase1.spaceport.LaunchStep.POLL.ordinal() : -1,
                page == 1 ? 70 : 0, page == 1 ? 70 : -1, page == 1 ? 0 : -1, 1500, page == 1 ? 88 : 0, page, 0);
        com.orbitalis.client.screen.LaunchConsoleScreen console = new com.orbitalis.client.screen.LaunchConsoleScreen(status);
        minecraft.gui.setScreen(which == 1 ? new com.orbitalis.client.screen.SequenceScreen(status, console) : console);
    }
}
