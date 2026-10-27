package com.orbitalis.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.joml.Matrix3x2fStack;

import com.orbitalis.core.network.DesignPrintPayload;
import com.orbitalis.core.research.EngineeringStudies;
import com.orbitalis.core.research.ResearchNode;
import com.orbitalis.core.research.ResearchNodes;
import com.orbitalis.client.research.ClientResearch;
import com.orbitalis.phase1.rocket.DesignComputerBlock;
import com.orbitalis.phase1.rocket.Propellant;
import com.orbitalis.phase1.rocket.RocketCatalog;
import com.orbitalis.phase1.rocket.RocketDesign;
import com.orbitalis.phase1.rocket.RocketDraft;
import com.orbitalis.phase1.rocket.RocketFuel;
import com.orbitalis.phase1.rocket.RocketParts;
import com.orbitalis.phase1.rocket.RocketPlan;
import com.orbitalis.phase1.rocket.RocketStats;
import com.orbitalis.phase1.spaceport.LaunchComplex;
import com.orbitalis.phase1.spaceport.PadPlan;
import com.orbitalis.phase1.spaceport.RocketShape;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Écran de l'ordinateur de conception, pensé comme un bureau d'études.
 * <ul>
 * <li>À gauche, le modèle de départ (une fenêtre liste les modèles éprouvés et la feuille blanche), puis la fusée en
 * plan : chaque étage à sa hauteur réelle, les boosters sur les flancs, la coiffe au sommet. Un clic sur un étage le
 * choisit ; la case « + étage », au-dessus, en ajoute un.</li>
 * <li>À droite, la charge utile (une fenêtre la choisit dans une grille), puis l'étage choisi : ses moteurs et ses
 * réservoirs en rangées d'icônes (les pièces pas encore étudiées sont grisées, cadenassées, et disent pourquoi), leur
 * nombre (clic, clic droit pour cinq, molette), ses boosters, et ses chiffres.</li>
 * <li>En bas, l'échelle des destinations, de l'orbite basse à Mercure : jusqu'où va la fusée, et ce qui lui manque
 * pour la suivante ; puis le diagnostic (ou le résumé), et l'impression du plan.</li>
 * </ul>
 * Le diagnostic dit ce qui bloque, et le montre : ce qui ne va pas clignote en rouge là où il se règle (l'étage du
 * plan, la rangée des moteurs ou des réservoirs, les boosters, la charge utile), ce qui est à surveiller en orange. Le
 * bandeau du bas donne chaque problème et son remède ; un clic mène au suivant.
 */
public class DesignScreen extends Screen {
    private static final int WIDTH = 420;
    private static final int HEIGHT = 248;
    /** Hauteur des deux panneaux du haut : la fusée, et l'étage choisi. */
    private static final int PANEL_H = HEIGHT - 98;
    private static final int LINE = 11;
    private static final int CELL = 17;
    private static final int COLOR_GRID = 0xFF131A22;
    private static final int COLOR_PLAN = 0xFF0B1826;
    private static final int COLOR_PLAN_DOT = 0xFF1C3248;

    /** Une zone cliquable : clic gauche, clic droit, et la molette (vers le haut, vers le bas). */
    private record Zone(int x, int y, int width, int height, Runnable action, Runnable alternate, Runnable up, Runnable down) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    /** Fenêtre ouverte par-dessus l'écran. */
    private enum Popup {
        NONE,
        MODELS,
        PAYLOADS
    }

    private final List<Zone> zones = new ArrayList<>();
    private List<FormattedCharSequence> tooltip;
    private Popup popup = Popup.NONE;
    // Le brouillon en cours : la charge utile, puis chaque étage.
    private Item payload = Items.AIR;
    private final Item[] engine = new Item[RocketDesign.MAX_STAGES];
    private final int[] engineCount = new int[RocketDesign.MAX_STAGES];
    private final Item[] tank = new Item[RocketDesign.MAX_STAGES];
    private final int[] tankCount = new int[RocketDesign.MAX_STAGES];
    private int stageCount;
    /** Boosters latéraux du premier étage : 0, ou une paire. */
    private int boosters;
    /** L'étage qu'on règle. */
    private int selected;
    /**
     * Modèle de départ : le rang du modèle du catalogue chargé tel quel, {@link #BLANK} pour la feuille blanche,
     * {@link #CUSTOM} dès qu'on a changé un réglage, {@link #UNSET} avant le premier affichage.
     */
    private int preset = UNSET;
    private static final int UNSET = -2;
    private static final int CUSTOM = -1;
    private static final int BLANK = -3;
    private int left;
    private int top;
    /** Les problèmes du brouillon, relevés à chaque image, et celui que montre le bandeau. */
    private List<Problem> problems = List.of();
    private int problemIndex;

    public DesignScreen() {
        super(Component.translatable("design.orbitalis.title"));
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        if (preset == UNSET) {
            List<Integer> known = knownTemplates();
            if (known.isEmpty()) {
                blank();
            } else {
                load(known.get(0));
            }
        }
    }

    // ------------------------------------------------------------------
    // Le brouillon
    // ------------------------------------------------------------------

    /** Charge un modèle du catalogue, tel qu'il est proposé d'origine. */
    private void load(int index) {
        RocketCatalog.Template template = RocketCatalog.all().get(index);
        select(RocketDraft.of(RocketCatalog.build(template, 0, template.presets())));
        if (!studied(payload)) {
            payload = Items.AIR;
        }
        preset = index;
    }

    /** Charge un modèle du catalogue (outil des captures d'écran). */
    public void select(int index) {
        load(index);
    }

    /** Charge un brouillon composé à l'avance (outil des captures d'écran). */
    public void select(RocketDraft draft) {
        payload = draft.payload();
        boosters = draft.boosters();
        stageCount = draft.stages().size();
        for (int i = 0; i < stageCount; i++) {
            RocketDraft.Stage stage = draft.stages().get(i);
            engine[i] = stage.engine();
            engineCount[i] = stage.engines();
            tank[i] = stage.tank() == Items.AIR ? first(tanks()) : stage.tank();
            tankCount[i] = Math.max(1, stage.tanks());
        }
        selected = 0;
        preset = CUSTOM;
    }

    /** Ouvre la fenêtre des modèles (vrai) ou celle des charges utiles (faux) : outil des captures d'écran. */
    public void showPopup(boolean models) {
        popup = models ? Popup.MODELS : Popup.PAYLOADS;
    }

    /** Feuille blanche : un seul étage, le premier moteur et le premier réservoir étudiés. */
    private void blank() {
        payload = Items.AIR;
        boosters = 0;
        stageCount = 1;
        engine[0] = first(engines());
        engineCount[0] = 1;
        tank[0] = first(tanks());
        tankCount[0] = 4;
        selected = 0;
        preset = BLANK;
    }

    private static Item first(List<Item> items) {
        return items.isEmpty() ? Items.AIR : items.get(0);
    }

    /** Le brouillon tel qu'il est réglé. */
    private RocketDraft draft() {
        List<RocketDraft.Stage> stages = new ArrayList<>();
        for (int i = 0; i < stageCount; i++) {
            RocketDraft.Stage stage = new RocketDraft.Stage(engine[i], engineCount[i], Items.AIR, 0);
            stages.add(stage.preloaded() ? stage : new RocketDraft.Stage(engine[i], engineCount[i], tank[i], tankCount[i]));
        }
        return new RocketDraft(payload, List.copyOf(stages), boosters);
    }

    private void addStage() {
        if (stageCount >= RocketDesign.MAX_STAGES) {
            return;
        }
        int i = stageCount++;
        // Un étage supérieur part du même moteur et du même réservoir que celui du dessous, plus petit.
        engine[i] = engine[i - 1];
        engineCount[i] = 1;
        tank[i] = tank[i - 1];
        tankCount[i] = Math.max(1, tankCount[i - 1] / 4);
        selected = i;
        preset = CUSTOM;
    }

    private void removeStage() {
        if (stageCount > 1) {
            stageCount--;
            selected = Math.min(selected, stageCount - 1);
            preset = CUSTOM;
        }
    }

    // ------------------------------------------------------------------
    // Ce que l'agence peut employer
    // ------------------------------------------------------------------

    /** Vrai si la pièce est étudiée à l'établi d'ingénieur (toujours en mode créatif, ou pour « aucune »). */
    private static boolean studied(Item item) {
        Minecraft client = Minecraft.getInstance();
        return item == Items.AIR || client.player == null || client.player.getAbilities().instabuild
                || EngineeringStudies.firstUnstudied(client.player, List.of(new ItemStack(item))) == null;
    }

    /** Ce qui empêche d'employer cette pièce (technologie, étude), ou {@code null} si rien. */
    private static Component lockReason(Item item) {
        Minecraft client = Minecraft.getInstance();
        if (item == Items.AIR || client.player == null || client.player.getAbilities().instabuild) {
            return null;
        }
        RocketParts.Part part = RocketParts.get(new ItemStack(item));
        if (part != null && part.research() != null && !part.research().isEmpty()) {
            ResearchNode node = ResearchNodes.get(part.research());
            if (node != null && !node.isStartingKnowledge() && !ClientResearch.get().isUnlocked(node.id())) {
                return Component.translatable("design.orbitalis.locked", Component.translatable(node.nameKey()));
            }
        }
        return studied(item) ? null : Component.translatable("design.orbitalis.to_study");
    }

    private static List<Item> known(RocketParts.Kind kind) {
        return RocketParts.ofKind(kind).stream().filter(DesignScreen::studied).toList();
    }

    private static List<Item> engines() {
        return known(RocketParts.Kind.ENGINE);
    }

    private static List<Item> tanks() {
        return known(RocketParts.Kind.TANK);
    }

    /** Rangs, dans le catalogue, des lanceurs que l'agence a étudiés à l'établi d'ingénieur ; tous, en mode créatif. */
    private static List<Integer> knownTemplates() {
        List<Integer> known = new ArrayList<>();
        List<RocketCatalog.Template> catalog = RocketCatalog.all();
        for (int index = 0; index < catalog.size(); index++) {
            if (templateKnown(index)) {
                known.add(index);
            }
        }
        return known;
    }

    private static boolean templateKnown(int index) {
        Minecraft client = Minecraft.getInstance();
        var study = EngineeringStudies.ofTemplate(RocketCatalog.all().get(index));
        return client.player == null || client.player.getAbilities().instabuild || study == null
                || EngineeringStudies.isDone(client.player, study.id());
    }

    // ------------------------------------------------------------------
    // Dessin
    // ------------------------------------------------------------------

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        Ui.panel(graphics, left, top, WIDTH, HEIGHT);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        zones.clear();
        tooltip = null;
        // Sous une fenêtre ouverte, l'écran ne réagit plus à la souris.
        int mx = popup == Popup.NONE ? mouseX : -1000;
        int my = popup == Popup.NONE ? mouseY : -1000;
        graphics.text(font, title.copy().withStyle(ChatFormatting.BOLD), left + 12, top + 9, Ui.TEXT, false);
        Ui.hline(graphics, left + 1, top + 23, WIDTH - 2);

        // Sans moteur ni réservoir étudié, il n'y a rien à concevoir : l'écran dit quoi faire.
        if (engines().isEmpty() || tanks().isEmpty()) {
            int textY = top + 40;
            for (var line : font.split(Component.translatable("design.orbitalis.nothing_studied"), WIDTH - 40)) {
                graphics.text(font, line, left + 20, textY, Ui.MUTED, false);
                textY += LINE + 1;
            }
            return;
        }
        selected = Math.clamp(selected, 0, stageCount - 1);
        RocketDraft draft = draft();
        NonNullList<ItemStack> parts = draft.slots();
        RocketDesign design = RocketDesign.fromSlots(parts::get);
        RocketStats stats = RocketStats.of(design);
        RocketShape shape = RocketShape.of(design);
        problems = diagnose(stats, shape);

        // Le coup d'œil, en haut à droite : l'état de la fusée en un mot (prête, à surveiller, bloquée), puis la
        // destination qu'elle atteint.
        String reachName = Component.translatable(stats.reach().nameKey()).getString().toUpperCase(Locale.ROOT);
        Ui.pillRight(graphics, font, reachName, left + WIDTH - 12, top + 7, Ui.ACCENT);
        long blocking = problems.stream().filter(Problem::blocking).count();
        String state = (blocking > 0 ? Component.translatable("design.orbitalis.state.blocked", blocking)
                : !problems.isEmpty() ? Component.translatable("design.orbitalis.state.watch")
                : Component.translatable("design.orbitalis.state.ready")).getString().toUpperCase(Locale.ROOT);
        int stateColor = blocking > 0 ? Ui.alpha(Ui.BAD, 0.55F + 0.45F * pulse()) : !problems.isEmpty() ? Ui.WARN : Ui.OK;
        Ui.pill(graphics, font, state, left + WIDTH - 12 - Ui.pillWidth(font, reachName) - 4 - Ui.pillWidth(font, state), top + 7, stateColor);

        extractPlan(graphics, left + 8, top + 28, 108, PANEL_H, mx, my, stats, shape);
        extractStage(graphics, left + 122, top + 28, WIDTH - 130, mx, my, draft, stats);
        // Le bas respire : la phrase à six pixels des panneaux, l'échelle des destinations, puis le trait et l'impression.
        extractLadder(graphics, left + 8, top + 28 + PANEL_H + 6, WIDTH - 16, mx, my, stats);
        extractFooter(graphics, left + 8, top + HEIGHT - 24, WIDTH - 16, mx, my, draft, parts, stats, shape);

        if (popup != Popup.NONE) {
            graphics.nextStratum();
            if (popup == Popup.MODELS) {
                extractModels(graphics, mouseX, mouseY);
            } else {
                extractPayloads(graphics, mouseX, mouseY);
            }
        }
        if (tooltip != null) {
            graphics.setTooltipForNextFrame(font, tooltip, mouseX, mouseY);
        }
    }

    // ------------------------------------------------------------------
    // La fusée en plan
    // ------------------------------------------------------------------

    /** Teinte d'un étage, selon ce qu'il brûle. */
    private static int stageColor(Item engineItem) {
        RocketParts.Part part = RocketParts.get(new ItemStack(engineItem));
        Propellant propellant = part == null || part.engine() == null ? Propellant.KEROLOX : part.engine().propellant();
        return switch (propellant) {
            case SOLID -> 0xFFC9B48A;
            case HYDROLOX -> 0xFFE08A3C;
            case HYDROGEN -> 0xFF4A515A;
            case METHALOX -> 0xFFC4CCD4;
            case FUSION -> 0xFF8E7CC3;
            case XENON, SUNLIGHT -> 0xFFD9A93A;
            default -> 0xFFDDE3EA;
        };
    }

    /**
     * Colonne de gauche : le modèle de départ, puis la fusée en plan, sur fond de calque bleu. Chaque étage est à sa
     * hauteur réelle ; un clic le choisit ; la case pointillée du dessus ajoute un étage.
     */
    private void extractPlan(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int mouseX, int mouseY, RocketStats stats, RocketShape shape) {
        Component presetName = preset >= 0 ? Component.translatable(RocketCatalog.all().get(preset).nameKey())
                : Component.translatable(preset == BLANK ? "design.orbitalis.blank" : "design.orbitalis.custom");
        dropdown(graphics, x, y, w, presetName, mouseX, mouseY, () -> popup = Popup.MODELS);
        int planY = y + 18;
        int planH = h - 18;
        Ui.round(graphics, x, planY, w, planH, COLOR_PLAN);
        Ui.frame(graphics, x, planY, w, planH, Ui.LINE);
        // Un calque à points, plus discret qu'un quadrillage.
        for (int gx = x + 6; gx < x + w - 1; gx += 8) {
            for (int gy = planY + 6; gy < planY + planH - 1; gy += 8) {
                graphics.fill(gx, gy, gx + 1, gy + 1, COLOR_PLAN_DOT);
            }
        }
        if (flagged(-1, Target.ROCKET)) {
            alarm(graphics, x, planY, w, planH, flagColor(-1, Target.ROCKET));
        }

        // Hauteur de chaque étage en blocs, puis celle de la coiffe.
        int[] blocks = new int[RocketDesign.MAX_STAGES];
        int topBlocks = 0;
        for (RocketShape.Segment segment : shape.segments()) {
            if (segment.stage() >= 0) {
                blocks[segment.stage()]++;
            } else {
                topBlocks++;
            }
        }
        int total = Math.max(1, shape.height() + (stageCount < RocketDesign.MAX_STAGES ? 2 : 0));
        float pixels = Math.min(7.0F, (planH - 12) / (float) total);
        int bodyHalf = shape.isWide() ? 14 : 10;
        int centre = x + w / 2;
        float cursor = planY + planH - 5;
        for (int s = 0; s < stageCount; s++) {
            int stageH = Math.max(4, Math.round(blocks[s] * pixels));
            int y1 = Math.round(cursor);
            int y0 = y1 - stageH;
            int color = stageColor(engine[s]);
            boolean hovered = Ui.inside(mouseX, mouseY, centre - bodyHalf - 12, y0, 2 * bodyHalf + 24, stageH);
            graphics.fill(centre - bodyHalf, y0, centre + bodyHalf, y1, color);
            // Un reflet à gauche, une ombre à droite : le fût paraît rond.
            graphics.fill(centre - bodyHalf + 2, y0, centre - bodyHalf + 4, y1, 0x30FFFFFF);
            graphics.fill(centre + bodyHalf - 4, y0, centre + bodyHalf, y1, 0x26000000);
            // La baie de propulsion, plus sombre, et la tuyère qui dépasse dessous.
            graphics.fill(centre - bodyHalf, y1 - 2, centre + bodyHalf, y1, 0xFF3A4048);
            graphics.fill(centre - bodyHalf / 2, y1, centre + bodyHalf / 2, y1 + 2, 0xFF8C949E);
            if (s > 0) {
                graphics.fill(centre - bodyHalf, y1 - 1, centre + bodyHalf, y1, 0xFF15181C);
            }
            // Les boosters du premier étage, sur ses flancs.
            if (s == 0 && boosters > 0) {
                int boosterH = Math.max(4, Math.round(shape.boosterHeight() * pixels));
                for (int sign = -1; sign <= 1; sign += 2) {
                    int bx = sign < 0 ? centre - bodyHalf - 7 : centre + bodyHalf + 1;
                    graphics.fill(bx, y1 - boosterH + 2, bx + 6, y1, 0xFFC9B48A);
                    graphics.fill(bx + 1, y1 - boosterH, bx + 5, y1 - boosterH + 2, 0xFFE9EBED);
                    if (flagged(0, Target.BOOSTERS)) {
                        alarm(graphics, bx - 1, y1 - boosterH - 1, 8, boosterH + 2, flagColor(0, Target.BOOSTERS));
                    }
                }
            }
            String label = String.valueOf(s + 1);
            graphics.text(font, label, centre - font.width(label) / 2 + 1, (y0 + y1) / 2 - 4, 0xFF15181C, false);
            boolean faulty = flagged(s, Target.STAGE, Target.ENGINES, Target.TANKS);
            if (s == selected) {
                Ui.frame(graphics, centre - bodyHalf - 2, y0 - 1, 2 * bodyHalf + 4, stageH + 2, Ui.ACCENT);
                graphics.text(font, "▶", centre - bodyHalf - 12, (y0 + y1) / 2 - 4, Ui.ACCENT, false);
            } else if (hovered) {
                Ui.frame(graphics, centre - bodyHalf - 2, y0 - 1, 2 * bodyHalf + 4, stageH + 2, Ui.LINE_HI);
            }
            if (faulty) {
                // L'étage fautif clignote, et une pastille le signale à droite, même s'il n'est pas celui qu'on règle.
                int alert = flagColor(s, Target.STAGE, Target.ENGINES, Target.TANKS);
                alarm(graphics, centre - bodyHalf - 3, y0 - 2, 2 * bodyHalf + 6, stageH + 4, alert);
                int badgeX = centre + bodyHalf + (s == 0 && boosters > 0 ? 10 : 4);
                Ui.dot(graphics, badgeX, (y0 + y1) / 2 - 2, Ui.alpha(alert, 0.4F + 0.6F * pulse()));
            }
            int chosen = s;
            zones.add(new Zone(centre - bodyHalf - 12, y0, 2 * bodyHalf + 24, stageH, () -> selected = chosen, null, null, null));
            if (hovered) {
                List<FormattedCharSequence> lines = new ArrayList<>();
                lines.add(Component.translatable("design.orbitalis.stage", s + 1).getVisualOrderText());
                lines.add(Component.literal(stageFigures(stats, s)).withStyle(ChatFormatting.GRAY).getVisualOrderText());
                for (Problem problem : problems) {
                    if (problem.stage() == s) {
                        lines.add(Component.literal("⚠ ").append(problem.title())
                                .withStyle(problem.blocking() ? ChatFormatting.RED : ChatFormatting.GOLD).getVisualOrderText());
                    }
                }
                tooltip = lines;
            }
            cursor = y0;
        }
        // La coiffe, ou la cabine : un fût, puis une pointe en gradins.
        int coneH = Math.max(6, Math.round(topBlocks * pixels));
        int y1 = Math.round(cursor);
        for (int k = 0; k < coneH; k++) {
            int half = Math.max(1, Math.round(bodyHalf * (k < coneH / 2 ? 1.0F : (coneH - k) / (float) (coneH - coneH / 2))));
            graphics.fill(centre - half, y1 - k - 1, centre + half, y1 - k, 0xFFE9EBED);
        }
        cursor = y1 - coneH;
        // La case « + étage », pointillée, au-dessus.
        if (stageCount < RocketDesign.MAX_STAGES) {
            int slotY = Math.max(planY + 3, Math.round(cursor) - 16);
            boolean hovered = Ui.inside(mouseX, mouseY, centre - bodyHalf - 4, slotY, 2 * bodyHalf + 8, 12);
            for (int dx = centre - bodyHalf - 4; dx < centre + bodyHalf + 4; dx += 3) {
                graphics.fill(dx, slotY, dx + 2, slotY + 1, hovered ? Ui.ACCENT : Ui.MUTED);
                graphics.fill(dx, slotY + 11, dx + 2, slotY + 12, hovered ? Ui.ACCENT : Ui.MUTED);
            }
            String plus = Component.translatable("design.orbitalis.add_stage_short").getString();
            graphics.text(font, plus, centre - font.width(plus) / 2, slotY + 2, hovered ? Ui.ACCENT : Ui.MUTED, false);
            zones.add(new Zone(centre - bodyHalf - 4, slotY, 2 * bodyHalf + 8, 12, this::addStage, null, null, null));
        }
    }

    /** Les chiffres d'un étage : delta-v (ses phases cumulées), rapport poussée/poids à l'allumage. */
    private static String stageFigures(RocketStats stats, int stage) {
        RocketStats.StageStats first = stats.stages().stream().filter(entry -> entry.number() == stage + 1).findFirst().orElse(null);
        if (first == null) {
            return "—";
        }
        double deltaV = stats.stages().stream().filter(entry -> entry.number() == stage + 1).mapToDouble(RocketStats.StageStats::deltaV).sum();
        return Component.translatable("design.orbitalis.stage_stats", grouped(deltaV), String.format(Locale.ROOT, "%.2f", first.twr())).getString();
    }

    // ------------------------------------------------------------------
    // L'étage choisi
    // ------------------------------------------------------------------

    private void extractStage(GuiGraphicsExtractor graphics, int x, int y, int w, int mouseX, int mouseY, RocketDraft draft, RocketStats stats) {
        // La charge utile : son icône, son nom ; un clic ouvre la grille.
        Component cargoName = payload == Items.AIR ? Component.translatable("design.orbitalis.no_payload") : new ItemStack(payload).getHoverName();
        boolean cargoHovered = Ui.inside(mouseX, mouseY, x, y, w, 16);
        Ui.round(graphics, x, y, w, 16, cargoHovered ? Ui.SURFACE_HI : Ui.SURFACE);
        Ui.frame(graphics, x, y, w, 16, cargoHovered ? Ui.ACCENT : Ui.LINE);
        if (payload != Items.AIR) {
            graphics.item(new ItemStack(payload), x + 1, y);
        }
        String cargoText = Component.translatable("design.orbitalis.payload").getString().toUpperCase(Locale.ROOT);
        graphics.text(font, cargoText, x + 20, y + 4, Ui.MUTED, false);
        graphics.text(font, Ui.fit(font, cargoName.getString(), w - 34 - font.width(cargoText)), x + 26 + font.width(cargoText), y + 4, Ui.TEXT, false);
        graphics.text(font, "▾", x + w - 9, y + 4, cargoHovered ? Ui.ACCENT : Ui.MUTED, false);
        if (flagged(-1, Target.PAYLOAD)) {
            alarm(graphics, x, y, w, 16, flagColor(-1, Target.PAYLOAD));
        }
        zones.add(new Zone(x, y, w, 16, () -> popup = Popup.PAYLOADS, null, null, null));
        y += 21;

        // La carte de l'étage.
        int cardH = PANEL_H - 21;
        Ui.card(graphics, x, y, w, cardH, false);
        int cx = x + 6;
        int cw = w - 12;
        int cy = y + 5;
        int s = selected;
        String head = Component.translatable("design.orbitalis.stage", s + 1).getString().toUpperCase(Locale.ROOT);
        Ui.heading(graphics, font, Component.literal(head), cx, cy);
        String role = Component.translatable(s == 0 ? "design.orbitalis.role.first" : "design.orbitalis.role.upper").getString();
        graphics.text(font, Ui.fit(font, role, cw - font.width(head) - 30), cx + font.width(head) + 6, cy, Ui.DIM, false);
        if (s == stageCount - 1 && stageCount > 1) {
            iconButton(graphics, cx + cw - 11, cy - 2, "×", mouseX, mouseY, this::removeStage,
                    Component.translatable("design.orbitalis.remove_stage"));
        }
        cy += LINE + 2;

        // Les moteurs, en icônes, et leur nombre.
        graphics.text(font, Component.translatable("design.orbitalis.engine"), cx, cy, Ui.MUTED, false);
        cy += 10;
        partRow(graphics, cx, cy, cw - 52, RocketParts.ofKind(RocketParts.Kind.ENGINE), engine[s], mouseX, mouseY, item -> {
            engine[s] = item;
            preset = CUSTOM;
        });
        counter(graphics, cx + cw - 46, cy, engineCount[s], mouseX, mouseY, delta -> {
            engineCount[s] = Math.clamp(engineCount[s] + delta, 1, RocketDraft.MAX_ENGINES);
            preset = CUSTOM;
        });
        if (flagged(s, Target.ENGINES)) {
            alarm(graphics, cx - 2, cy - 2, cw + 4, CELL + 4, flagColor(s, Target.ENGINES));
        }
        cy += CELL + 4;

        // Les réservoirs ; un moteur qui embarque ses ergols n'en a pas.
        graphics.text(font, Component.translatable("design.orbitalis.tanks"), cx, cy, Ui.MUTED, false);
        cy += 10;
        if (draft.stages().get(s).preloaded()) {
            graphics.text(font, Ui.fit(font, Component.translatable("design.orbitalis.preloaded").getString(), cw), cx, cy + 4, Ui.DIM, false);
        } else {
            partRow(graphics, cx, cy, cw - 52, RocketParts.ofKind(RocketParts.Kind.TANK), tank[s], mouseX, mouseY, item -> {
                tank[s] = item;
                preset = CUSTOM;
            });
            counter(graphics, cx + cw - 46, cy, tankCount[s], mouseX, mouseY, delta -> {
                tankCount[s] = Math.clamp(tankCount[s] + delta, 1, RocketDraft.MAX_TANKS);
                preset = CUSTOM;
            });
        }
        if (flagged(s, Target.TANKS)) {
            alarm(graphics, cx - 2, cy - 2, cw + 4, CELL + 4, flagColor(s, Target.TANKS));
        }
        cy += CELL + 4;

        // Les boosters du premier étage : un interrupteur.
        if (s == 0) {
            Item booster = com.orbitalis.core.registry.ModItems.SOLID_BOOSTER.get();
            boolean available = boosters > 0 || studied(booster);
            boolean hovered = available && Ui.inside(mouseX, mouseY, cx, cy, cw, 14);
            Ui.round(graphics, cx, cy, cw, 14, boosters > 0 ? Ui.alpha(Ui.ACCENT, 0.18F) : hovered ? Ui.SURFACE_HI : Ui.SURFACE);
            Ui.frame(graphics, cx, cy, cw, 14, boosters > 0 ? Ui.ACCENT : Ui.LINE);
            Component text = !available ? Component.translatable("design.orbitalis.boosters.locked")
                    : boosters > 0 ? Component.translatable("design.orbitalis.boosters.pair", new ItemStack(booster).getHoverName())
                    : Component.translatable("design.orbitalis.boosters.add");
            graphics.text(font, Ui.fit(font, text.getString(), cw - 8), cx + 4, cy + 3, available ? Ui.TEXT : Ui.DIM, false);
            if (flagged(0, Target.BOOSTERS)) {
                alarm(graphics, cx, cy, cw, 14, flagColor(0, Target.BOOSTERS));
            }
            if (available) {
                zones.add(new Zone(cx, cy, cw, 14, () -> {
                    boosters = boosters > 0 ? 0 : RocketDesign.BOOSTER_PAIR;
                    preset = CUSTOM;
                }, null, null, null));
            }
        }
        // Les chiffres de l'étage, en quatre tuiles : ce qu'il donne de vitesse, s'il soulève ce qu'il porte, ce qu'il
        // emporte d'ergols, et combien de temps il brûle. Le survol d'une tuile dit ce que le chiffre veut dire.
        cy = y + cardH - 27;
        Ui.hline(graphics, cx, cy - 4, cw);
        double deltaV = 0;
        double propellant = 0;
        double burn = 0;
        double twr = -1;
        for (RocketStats.StageStats entry : stats.stages()) {
            if (entry.number() == s + 1) {
                deltaV += entry.deltaV();
                propellant += entry.propellantMass();
                burn += entry.burnTime();
                if (twr < 0) {
                    twr = entry.twr();
                }
            }
        }
        String burnText = burn >= 600 ? String.format(Locale.ROOT, "%.0f", burn / 60.0)
                : String.format(Locale.ROOT, "%d:%02d", (int) burn / 60, (int) burn % 60);
        int tileW = (cw - 6) / 4;
        tile(graphics, cx, cy, tileW, "delta_v", grouped(deltaV), "m/s", -1.0F, 0, mouseX, mouseY);
        // Au décollage, il faut plus de 1 ; en haut, la poussée ne lutte plus contre la pesanteur, la jauge n'est qu'indicative.
        int twrColor = s > 0 ? Ui.ACCENT : twr < 1.0 ? Ui.BAD : twr < 1.2 ? Ui.WARN : Ui.OK;
        tile(graphics, cx + tileW + 2, cy, tileW, "twr", twr < 0 ? "—" : String.format(Locale.ROOT, "%.2f", twr), "",
                twr < 0 ? -1.0F : (float) (twr / 3.0), twrColor, mouseX, mouseY);
        tile(graphics, cx + 2 * (tileW + 2), cy, tileW, "propellant", tonnes(propellant).replace(" t", ""), "t", -1.0F, 0, mouseX, mouseY);
        tile(graphics, cx + 3 * (tileW + 2), cy, tileW, "burn", burnText, burn >= 600 ? "min" : "min:s", -1.0F, 0, mouseX, mouseY);
    }

    /**
     * Une tuile de chiffre : son titre en petites capitales grises, sa valeur en blanc et son unité en gris, et dessous,
     * si {@code share} n'est pas négatif, une jauge (avec un trait au tiers : la poussée/poids de 1). Son survol
     * explique le chiffre.
     */
    private void tile(GuiGraphicsExtractor graphics, int x, int y, int w, String key, String value, String unit, float share, int color,
            int mouseX, int mouseY) {
        Ui.round(graphics, x, y, w, 23, COLOR_GRID);
        Matrix3x2fStack pose = graphics.pose();
        String title = Ui.fit(font, Component.translatable("design.orbitalis.tile." + key).getString().toUpperCase(Locale.ROOT), Math.round((w - 6) / 0.75F));
        pose.pushMatrix();
        pose.translate(x + 3, y + 3);
        pose.scale(0.75F, 0.75F);
        graphics.text(font, title, 0, 0, Ui.MUTED, false);
        pose.popMatrix();
        String shown = Ui.fit(font, value, w - 6 - (unit.isEmpty() ? 0 : font.width(unit) + 3));
        graphics.text(font, shown, x + 3, y + 11, Ui.TEXT, false);
        if (!unit.isEmpty()) {
            graphics.text(font, unit, x + 3 + font.width(shown) + 3, y + 11, Ui.MUTED, false);
        }
        if (share >= 0.0F) {
            Ui.bar(graphics, x + 3, y + 20, w - 6, 1, share, color);
            int one = x + 3 + Math.round((w - 6) / 3.0F);
            graphics.fill(one, y + 19, one + 1, y + 22, Ui.MUTED);
        }
        if (Ui.inside(mouseX, mouseY, x, y, w, 23)) {
            List<FormattedCharSequence> lines = new ArrayList<>();
            lines.add(Component.translatable("design.orbitalis.tile." + key).withStyle(ChatFormatting.BOLD).getVisualOrderText());
            lines.addAll(font.split(Component.translatable("design.orbitalis.tile." + key + ".hint").withStyle(ChatFormatting.GRAY), 220));
            tooltip = lines;
        }
    }

    /** Une rangée de pièces en icônes : celle qui est choisie est cerclée ; les autres se choisissent d'un clic. */
    private void partRow(GuiGraphicsExtractor graphics, int x, int y, int w, List<Item> items, Item current, int mouseX, int mouseY,
            java.util.function.Consumer<Item> choose) {
        int perRow = Math.max(1, w / CELL);
        for (int i = 0; i < items.size() && i < perRow; i++) {
            Item item = items.get(i);
            int ix = x + i * CELL;
            boolean chosen = item == current;
            Component lock = lockReason(item);
            boolean hovered = Ui.inside(mouseX, mouseY, ix, y, CELL - 1, CELL);
            Ui.round(graphics, ix, y, CELL - 1, CELL, chosen ? Ui.alpha(Ui.ACCENT, 0.18F) : hovered && lock == null ? Ui.SURFACE_HI : COLOR_GRID);
            if (chosen) {
                Ui.frame(graphics, ix, y, CELL - 1, CELL, Ui.alpha(Ui.ACCENT, 0.7F));
                graphics.fill(ix + 2, y + CELL - 2, ix + CELL - 3, y + CELL - 1, Ui.ACCENT);
            }
            graphics.item(new ItemStack(item), ix, y);
            if (lock != null) {
                // Pas encore employable : l'icône est voilée, et un cadenas le dit.
                graphics.nextStratum();
                graphics.fill(ix, y, ix + CELL - 1, y + CELL, 0xB00A0D11);
                lock(graphics, ix + 5, y + 4);
            }
            if (hovered) {
                List<FormattedCharSequence> lines = new ArrayList<>();
                for (Component line : Screen.getTooltipFromItem(Minecraft.getInstance(), new ItemStack(item))) {
                    lines.add(line.getVisualOrderText());
                }
                if (lock != null) {
                    lines.add(lock.copy().withStyle(ChatFormatting.RED).getVisualOrderText());
                }
                tooltip = lines;
            }
            if (lock == null && !chosen) {
                zones.add(new Zone(ix, y, CELL - 1, CELL, () -> choose.accept(item), null, null, null));
            }
        }
    }

    /** Un nombre, entre « moins » et « plus » : clic pour un, clic droit pour cinq, molette au-dessus du nombre. */
    private void counter(GuiGraphicsExtractor graphics, int x, int y, int value, int mouseX, int mouseY, java.util.function.IntConsumer change) {
        iconButton(graphics, x, y + 3, "−", mouseX, mouseY, () -> change.accept(-1), null);
        zones.set(zones.size() - 1, withAlternate(zones.get(zones.size() - 1), () -> change.accept(-5)));
        iconButton(graphics, x + 35, y + 3, "+", mouseX, mouseY, () -> change.accept(1), null);
        zones.set(zones.size() - 1, withAlternate(zones.get(zones.size() - 1), () -> change.accept(5)));
        String number = "× " + value;
        graphics.text(font, number, x + 23 - font.width(number) / 2, y + 5, Ui.ACCENT, false);
        zones.add(new Zone(x + 11, y, 24, CELL, () -> {
        }, null, () -> change.accept(1), () -> change.accept(-1)));
        if (Ui.inside(mouseX, mouseY, x, y, 46, CELL)) {
            tooltip = List.of(Component.translatable("design.orbitalis.count_hint").withStyle(ChatFormatting.GRAY).getVisualOrderText());
        }
    }

    /** Un petit cadenas : son anse, puis son corps. */
    private static void lock(GuiGraphicsExtractor graphics, int x, int y) {
        int color = Ui.MUTED;
        graphics.fill(x + 1, y, x + 5, y + 1, color);
        graphics.fill(x + 1, y, x + 2, y + 4, color);
        graphics.fill(x + 4, y, x + 5, y + 4, color);
        graphics.fill(x, y + 4, x + 6, y + 9, color);
        graphics.fill(x + 2, y + 5, x + 4, y + 7, 0xFF0A0D11);
    }

    private static Zone withAlternate(Zone zone, Runnable alternate) {
        return new Zone(zone.x(), zone.y(), zone.width(), zone.height(), zone.action(), alternate, zone.up(), zone.down());
    }

    private void iconButton(GuiGraphicsExtractor graphics, int x, int y, String label, int mouseX, int mouseY, Runnable action, Component hint) {
        boolean hovered = Ui.inside(mouseX, mouseY, x, y, 11, 11);
        Ui.round(graphics, x, y, 11, 11, hovered ? Ui.alpha(Ui.ACCENT, 0.25F) : Ui.SURFACE_HI);
        Ui.frame(graphics, x, y, 11, 11, hovered ? Ui.ACCENT : Ui.LINE_HI);
        graphics.text(font, label, x + 6 - font.width(label) / 2, y + 2, hovered ? Ui.ACCENT : Ui.TEXT, false);
        zones.add(new Zone(x, y, 11, 11, action, null, null, null));
        if (hovered && hint != null) {
            tooltip = List.of(hint.getVisualOrderText());
        }
    }

    private void dropdown(GuiGraphicsExtractor graphics, int x, int y, int w, Component label, int mouseX, int mouseY, Runnable open) {
        boolean hovered = Ui.inside(mouseX, mouseY, x, y, w, 14);
        Ui.round(graphics, x, y, w, 14, hovered ? Ui.SURFACE_HI : Ui.SURFACE);
        Ui.frame(graphics, x, y, w, 14, hovered ? Ui.ACCENT : Ui.LINE);
        graphics.text(font, Ui.fit(font, label.getString(), w - 14), x + 4, y + 3, Ui.TEXT, false);
        graphics.text(font, "▾", x + w - 9, y + 3, hovered ? Ui.ACCENT : Ui.MUTED, false);
        zones.add(new Zone(x, y, w, 14, open, null, null, null));
    }

    // ------------------------------------------------------------------
    // L'échelle des destinations
    // ------------------------------------------------------------------

    /**
     * Les destinations orbitales, de l'orbite basse à Mercure, à intervalles réguliers : celles que la fusée atteint
     * sont pleines, la jauge avance vers la suivante à proportion du delta-v, et la ligne du dessus dit ce qu'il manque.
     */
    private void extractLadder(GuiGraphicsExtractor graphics, int x, int y, int w, int mouseX, int mouseY, RocketStats stats) {
        List<RocketStats.Reach> steps = new ArrayList<>();
        for (RocketStats.Reach reach : RocketStats.Reach.values()) {
            if (reach.deltaV() > 0) {
                steps.add(reach);
            }
        }
        double deltaV = stats.totalDeltaV();
        RocketStats.Reach next = null;
        for (RocketStats.Reach reach : steps) {
            if (deltaV < reach.deltaV()) {
                next = reach;
                break;
            }
        }
        // La phrase : bloquée, elle le dit ; sinon, jusqu'où elle va, puis ce qu'il lui manque pour la suivante.
        String total = Component.translatable("design.orbitalis.delta_v_total", grouped(deltaV)).getString();
        graphics.text(font, total, x + w - font.width(total), y, Ui.MUTED, false);
        int room = w - font.width(total) - 12;
        long blocking = problems.stream().filter(Problem::blocking).count();
        if (blocking > 0) {
            // Bloquée : la phrase dit quoi faire pour le problème que montre le bandeau.
            Problem shown = problems.get(Math.floorMod(problemIndex, problems.size()));
            int mark = font.width("⚠") + 4;
            graphics.text(font, "⚠", x, y, Ui.alpha(Ui.BAD, 0.5F + 0.5F * pulse()), false);
            graphics.text(font, Ui.fit(font, shown.fix().getString(), room - mark), x + mark, y, Ui.TEXT, false);
        } else {
            String main = Component.translatable("design.orbitalis.sentence.reach", Component.translatable(stats.reach().nameKey())).getString();
            main = Ui.fit(font, main, room);
            graphics.text(font, main, x, y, Ui.TEXT, false);
            String more = next == null ? Component.translatable("design.orbitalis.all_reached").getString()
                    : Component.translatable("design.orbitalis.sentence.next", grouped(next.deltaV() - deltaV),
                            Component.translatable("design.orbitalis.reach_short." + next.name().toLowerCase(Locale.ROOT))).getString();
            int moreX = x + font.width(main) + 6;
            if (moreX < x + room - 20) {
                graphics.text(font, Ui.fit(font, "· " + more, x + room - moreX), moreX, y, next == null ? Ui.OK : Ui.MUTED, false);
            }
        }

        int lineY = y + 14;
        int gap = (w - 16) / (steps.size() - 1);
        int x0 = x + 8;
        graphics.fill(x0, lineY, x0 + gap * (steps.size() - 1), lineY + 2, Ui.LINE_HI);
        // La jauge : jusqu'à la dernière destination atteinte, puis la part du chemin vers la suivante.
        double previous = 0;
        int filled = x0;
        for (int i = 0; i < steps.size(); i++) {
            RocketStats.Reach reach = steps.get(i);
            int nx = x0 + i * gap;
            if (deltaV >= reach.deltaV()) {
                filled = nx;
            } else {
                float share = (float) Math.clamp((deltaV - previous) / (reach.deltaV() - previous), 0.0, 1.0);
                filled = i == 0 ? x0 : filled + Math.round((nx - filled) * share);
                break;
            }
            previous = reach.deltaV();
        }
        graphics.fill(x0, lineY, filled, lineY + 2, stats.isFlightReady() ? Ui.OK : Ui.WARN);
        Matrix3x2fStack pose = graphics.pose();
        for (int i = 0; i < steps.size(); i++) {
            RocketStats.Reach reach = steps.get(i);
            int nx = x0 + i * gap;
            boolean reached = deltaV >= reach.deltaV();
            graphics.fill(nx - 2, lineY - 2, nx + 3, lineY + 4, reached ? Ui.OK : reach == next ? Ui.ACCENT : Ui.LINE_HI);
            String label = Component.translatable("design.orbitalis.reach_short." + reach.name().toLowerCase(Locale.ROOT)).getString();
            pose.pushMatrix();
            pose.translate(nx, lineY + 7);
            pose.scale(0.75F, 0.75F);
            graphics.text(font, label, -font.width(label) / 2, 0, reached ? Ui.TEXT : Ui.DIM, false);
            pose.popMatrix();
            if (Ui.inside(mouseX, mouseY, nx - gap / 2, lineY - 4, gap, 18)) {
                tooltip = List.of(Component.translatable(reach.nameKey()).getVisualOrderText(),
                        Component.literal(grouped(reach.deltaV()) + " m/s").withStyle(ChatFormatting.GRAY).getVisualOrderText());
            }
        }
    }

    // ------------------------------------------------------------------
    // Le bas : défaut ou résumé, et l'impression
    // ------------------------------------------------------------------

    private void extractFooter(GuiGraphicsExtractor graphics, int x, int y, int w, int mouseX, int mouseY, RocketDraft draft,
            NonNullList<ItemStack> parts, RocketStats stats, RocketShape shape) {
        Ui.hline(graphics, x, y - 6, w);
        boolean ready = stats.isFlightReady();
        int buttonW = 118;
        String note;
        int noteColor;
        if (!problems.isEmpty()) {
            extractProblems(graphics, x, y, w - buttonW - 8, mouseX, mouseY);
            note = null;
            noteColor = 0;
        } else {
            int count = 0;
            for (Map.Entry<Item, Integer> entry : RocketPlan.materials(RocketDesign.fromSlots(parts::get)).entrySet()) {
                count += entry.getValue();
            }
            RocketFuel fuel = RocketFuel.required(stats);
            int ports = (fuel.rp1() > 0 ? 1 : 0) + (fuel.oxygen() > 0 ? 1 : 0) + (fuel.hydrogen() > 0 ? 1 : 0) + (fuel.methane() > 0 ? 1 : 0)
                    + (fuel.helium3() > 0 ? 1 : 0);
            int pad = requiredPad(stats, shape);
            Component padText = pad == 0 ? Component.translatable("design.orbitalis.pad.none")
                    : Component.literal((2 * pad + 1) + "×" + (2 * pad + 1));
            note = Component.translatable("design.orbitalis.summary_short", tonnes(stats.liftoffMass()), shape.height(), padText).getString();
            noteColor = pad == 0 ? Ui.BAD : Ui.MUTED;
            if (Ui.inside(mouseX, mouseY, x, y, w - buttonW - 8, 16)) {
                tooltip = List.of(Component.translatable("design.orbitalis.summary_detail").withStyle(ChatFormatting.BOLD).getVisualOrderText(),
                        Component.translatable("design.orbitalis.summary_parts", count).withStyle(ChatFormatting.GRAY).getVisualOrderText(),
                        Component.translatable("design.orbitalis.summary_ports", ports).withStyle(ChatFormatting.GRAY).getVisualOrderText());
            }
        }
        if (note != null) {
            // Rien ne bloque : une pastille verte, puis le résumé.
            Ui.dot(graphics, x, y + 5, Ui.OK);
            graphics.text(font, Ui.fit(font, note, w - buttonW - 16), x + 9, y + 4, noteColor, false);
        }

        // Impression : refusée tant que l'agence ne maîtrise pas, ou n'a pas étudié, toutes les pièces.
        Minecraft minecraft = Minecraft.getInstance();
        boolean creative = minecraft.player != null && minecraft.player.getAbilities().instabuild;
        String locked = creative || minecraft.player == null ? null : DesignComputerBlock.lockedResearch(minecraft.player, parts);
        EngineeringStudies.Study lacking = creative || minecraft.player == null || locked != null ? null
                : EngineeringStudies.firstUnstudied(minecraft.player, parts);
        boolean printable = ready && draft.isValid() && locked == null && lacking == null;
        long blocking = problems.stream().filter(Problem::blocking).count();
        Component label = blocking > 0 ? Component.translatable("design.orbitalis.blocked", blocking)
                : locked != null ? Component.translatable("design.orbitalis.locked", Component.translatable("research.orbitalis.node." + locked))
                : lacking != null ? Component.translatable("engineering.orbitalis.required", lacking.name())
                : Component.translatable("design.orbitalis.print");
        int bx = x + w - buttonW;
        boolean hovered = printable && Ui.inside(mouseX, mouseY, bx, y, buttonW, 16);
        Ui.button(graphics, font, Component.literal(Ui.fit(font, label.getString(), buttonW - 8)), bx, y, buttonW, 16, hovered, printable, printable,
                printable ? Ui.ACCENT : Ui.MUTED);
        if (!printable) {
            // Le bouton éteint dit en rouge qu'il est bloqué, et pourquoi au survol.
            Ui.frame(graphics, bx, y, buttonW, 16, Ui.alpha(Ui.BAD, 0.6F));
        }
        if (printable) {
            zones.add(new Zone(bx, y, buttonW, 16, this::print, null, null, null));
        } else if (Ui.inside(mouseX, mouseY, bx, y, buttonW, 16)) {
            tooltip = List.of(label.getVisualOrderText());
        }
    }

    /**
     * Le bandeau du diagnostic : une pastille qui clignote, le rang du problème montré, ce qui ne va pas et son remède.
     * Le survol les liste tous ; un clic mène au problème (il choisit son étage), puis au suivant.
     */
    private void extractProblems(GuiGraphicsExtractor graphics, int x, int y, int w, int mouseX, int mouseY) {
        problemIndex = Math.floorMod(problemIndex, problems.size());
        Problem shown = problems.get(problemIndex);
        int color = shown.blocking() ? Ui.BAD : Ui.WARN;
        boolean hovered = Ui.inside(mouseX, mouseY, x, y, w, 16);
        Ui.round(graphics, x, y, w, 16, Ui.alpha(color, hovered ? 0.22F : 0.12F));
        Ui.frame(graphics, x, y, w, 16, Ui.alpha(color, 0.35F + 0.4F * pulse()));
        Ui.dot(graphics, x + 5, y + 5, Ui.alpha(color, 0.35F + 0.65F * pulse()));
        int textX = x + 14;
        if (problems.size() > 1) {
            String rank = (problemIndex + 1) + "/" + problems.size();
            graphics.text(font, rank, textX, y + 4, Ui.alpha(color, 0.8F), false);
            textX += font.width(rank) + 6;
        }
        graphics.text(font, Ui.fit(font, shown.title().getString(), x + w - 6 - textX), textX, y + 4, color, false);
        // Un avertissement seul (rien ne bloque) : la ligne du dessus garde la destination, le remède suit ici.
        if (!shown.blocking()) {
            String title = Ui.fit(font, shown.title().getString(), x + w - 6 - textX);
            int fixX = textX + font.width(title) + 6;
            if (fixX < x + w - 30) {
                graphics.text(font, Ui.fit(font, "— " + shown.fix().getString(), x + w - 6 - fixX), fixX, y + 4, Ui.MUTED, false);
            }
        }
        zones.add(new Zone(x, y, w, 16, this::nextProblem, null, null, null));
        if (hovered) {
            List<FormattedCharSequence> lines = new ArrayList<>();
            lines.add(Component.translatable("design.orbitalis.problems.title").withStyle(ChatFormatting.BOLD).getVisualOrderText());
            for (Problem problem : problems) {
                lines.add(Component.literal("⚠ ").append(problem.title())
                        .withStyle(problem.blocking() ? ChatFormatting.RED : ChatFormatting.GOLD).getVisualOrderText());
                lines.addAll(font.split(Component.literal("   ").append(problem.fix()).withStyle(ChatFormatting.GRAY), 260));
            }
            lines.add(Component.translatable("design.orbitalis.problems.hint").withStyle(ChatFormatting.DARK_GRAY).getVisualOrderText());
            tooltip = lines;
        }
    }

    /** Clic sur le bandeau : on va d'abord à l'étage du problème montré, puis au problème suivant. */
    private void nextProblem() {
        if (problems.isEmpty()) {
            return;
        }
        Problem shown = problems.get(Math.floorMod(problemIndex, problems.size()));
        if (shown.stage() >= 0 && shown.stage() < stageCount && selected != shown.stage()) {
            selected = shown.stage();
            return;
        }
        problemIndex = (problemIndex + 1) % problems.size();
        Problem next = problems.get(problemIndex);
        if (next.stage() >= 0 && next.stage() < stageCount) {
            selected = next.stage();
        }
    }

    // ------------------------------------------------------------------
    // Le diagnostic : ce qui bloque, et où
    // ------------------------------------------------------------------

    /** L'endroit de l'écran qu'un problème désigne. */
    private enum Target {
        /** L'étage tout entier, dans le plan. */
        STAGE,
        ENGINES,
        TANKS,
        BOOSTERS,
        PAYLOAD,
        /** La fusée entière : sa masse ou sa hauteur. */
        ROCKET
    }

    /**
     * Un problème du brouillon : ce qui ne va pas, comment y remédier, l'étage en cause (-1 pour la fusée entière) et
     * l'endroit qui le règle. Un problème bloquant empêche d'imprimer ; les autres sont seulement à surveiller.
     */
    private record Problem(Component title, Component fix, int stage, Target target, boolean blocking) {
    }

    /** Tout ce qui ne va pas dans le brouillon, du premier étage au dernier, puis la fusée entière. */
    private List<Problem> diagnose(RocketStats stats, RocketShape shape) {
        List<Problem> list = new ArrayList<>();
        for (RocketStats.Issue issue : stats.issues()) {
            Target target = switch (issue.type()) {
                case EMPTY, NO_ENGINE, LOW_THRUST, NUCLEAR_LIFTOFF, CREWED_LOW_THRUST -> Target.ENGINES;
                case NO_TANK, USELESS_TANK, HYDROGEN_TANK -> Target.TANKS;
                case BAD_BOOSTERS -> Target.BOOSTERS;
                default -> Target.STAGE;
            };
            Component fix = issue.type() == RocketStats.IssueType.LOW_THRUST && !stats.stages().isEmpty()
                    ? Component.translatable("design.orbitalis.fix.low_thrust", String.format(Locale.ROOT, "%.2f", stats.stages().get(0).twr()))
                    : Component.translatable("design.orbitalis.fix." + issue.type().name().toLowerCase(Locale.ROOT));
            list.add(new Problem(Component.translatable(issue.type().messageKey(), issue.stage()), fix, Math.max(0, issue.stage() - 1), target, true));
        }
        // Les pièces que l'agence ne peut pas encore employer : un modèle de départ peut en porter.
        for (int i = 0; i < stageCount; i++) {
            partProblem(list, i, engine[i], Target.ENGINES);
            if (!new RocketDraft.Stage(engine[i], engineCount[i], Items.AIR, 0).preloaded()) {
                partProblem(list, i, tank[i], Target.TANKS);
            }
        }
        if (boosters > 0) {
            partProblem(list, 0, com.orbitalis.core.registry.ModItems.SOLID_BOOSTER.get(), Target.BOOSTERS);
        }
        Component cargo = lockReason(payload);
        if (cargo != null) {
            list.add(new Problem(Component.translatable("design.orbitalis.problem.payload", new ItemStack(payload).getHoverName()), cargo, -1, Target.PAYLOAD, true));
        }
        // Trop lourde ou trop haute pour le plus grand pas de tir : on peut imprimer le plan, mais pas la lancer.
        if (requiredPad(stats, shape) == 0) {
            int biggest = PadPlan.SIZES[PadPlan.SIZES.length - 1];
            list.add(new Problem(Component.translatable("design.orbitalis.problem.pad"),
                    Component.translatable("design.orbitalis.fix.pad", tonnes(stats.liftoffMass()), shape.height(),
                            tonnes(LaunchComplex.maxRocketMass(biggest)), LaunchComplex.maxRocketHeight(PadPlan.towerLevels(biggest))),
                    -1, Target.ROCKET, false));
        }
        return list;
    }

    private static void partProblem(List<Problem> list, int stage, Item item, Target target) {
        Component lock = lockReason(item);
        if (lock != null) {
            list.add(new Problem(Component.translatable("design.orbitalis.problem.part", stage + 1, new ItemStack(item).getHoverName()), lock, stage, target, true));
        }
    }

    /** Vrai si un problème désigne cet endroit de cet étage (-1 : la fusée entière, la charge utile). */
    private boolean flagged(int stage, Target... targets) {
        return problems.stream().anyMatch(problem -> problem.stage() == stage && List.of(targets).contains(problem.target()));
    }

    /** Rouge si l'un des problèmes de cet endroit bloque, orange s'ils ne sont qu'à surveiller. */
    private int flagColor(int stage, Target... targets) {
        return problems.stream().anyMatch(problem -> problem.blocking() && problem.stage() == stage && List.of(targets).contains(problem.target()))
                ? Ui.BAD : Ui.WARN;
    }

    /** Battement des alarmes, de 0 à 1, un peu plus d'une fois par seconde. */
    private static float pulse() {
        return 0.5F + 0.5F * (float) Math.sin(Util.getMillis() / 150.0);
    }

    /** Une alarme autour d'un endroit : un voile et un cadre de sa couleur, qui battent, et un halo plus pâle. */
    private static void alarm(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int color) {
        float beat = pulse();
        Ui.round(graphics, x, y, w, h, Ui.alpha(color, 0.05F + 0.10F * beat));
        Ui.frame(graphics, x, y, w, h, Ui.alpha(color, 0.45F + 0.55F * beat));
        Ui.frame(graphics, x - 1, y - 1, w + 2, h + 2, Ui.alpha(color, 0.25F * beat));
    }

    // ------------------------------------------------------------------
    // Les fenêtres
    // ------------------------------------------------------------------

    private int[] window(GuiGraphicsExtractor graphics, int w, int h, String titleKey, int mouseX, int mouseY) {
        graphics.fill(left, top, left + WIDTH, top + HEIGHT, 0xA0000000);
        int x = left + (WIDTH - w) / 2;
        int y = top + (HEIGHT - h) / 2;
        Ui.panel(graphics, x, y, w, h);
        Ui.frame(graphics, x, y, w, h, Ui.LINE_HI);
        graphics.text(font, Component.translatable(titleKey).withStyle(ChatFormatting.BOLD), x + 8, y + 7, Ui.TEXT, false);
        iconButton(graphics, x + w - 17, y + 5, "×", mouseX, mouseY, () -> popup = Popup.NONE, null);
        Ui.hline(graphics, x + 1, y + 20, w - 2);
        return new int[] {x, y};
    }

    /** La fenêtre des modèles de départ : la feuille blanche, puis chaque modèle, son nom et ce qu'il sait faire. */
    private void extractModels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        List<RocketCatalog.Template> catalog = RocketCatalog.all();
        int rows = catalog.size() + 1;
        int w = 320;
        int rowH = 20;
        int h = Math.min(HEIGHT - 12, 26 + rows * rowH);
        int[] at = window(graphics, w, h, "design.orbitalis.choose_model", mouseX, mouseY);
        int y = at[1] + 24;
        for (int index = -1; index < catalog.size() && y + rowH <= at[1] + h; index++) {
            boolean known = index < 0 || templateKnown(index);
            Component name = index < 0 ? Component.translatable("design.orbitalis.blank") : Component.translatable(catalog.get(index).nameKey());
            Component desc = index < 0 ? Component.translatable("design.orbitalis.blank.desc")
                    : known ? Component.translatable(catalog.get(index).descriptionKey()) : Component.translatable("design.orbitalis.to_study");
            boolean current = index < 0 ? preset == BLANK : preset == index;
            boolean hovered = known && Ui.inside(mouseX, mouseY, at[0] + 4, y, w - 8, rowH - 2);
            Ui.round(graphics, at[0] + 4, y, w - 8, rowH - 2, current ? Ui.alpha(Ui.ACCENT, 0.18F) : hovered ? Ui.SURFACE_HI : Ui.SURFACE);
            graphics.text(font, Ui.fit(font, name.getString(), 110), at[0] + 9, y + 5, known ? Ui.TEXT : Ui.DIM, false);
            graphics.text(font, Ui.fit(font, desc.getString(), w - 134), at[0] + 124, y + 5, known ? Ui.MUTED : Ui.DIM, false);
            if (known) {
                int chosen = index;
                zones.add(new Zone(at[0] + 4, y, w - 8, rowH - 2, () -> {
                    if (chosen < 0) {
                        blank();
                    } else {
                        load(chosen);
                    }
                    popup = Popup.NONE;
                }, null, null, null));
            }
            if (hovered) {
                tooltip = font.split(desc, 220);
            }
            y += rowH;
        }
    }

    /** La fenêtre des charges utiles : une grille d'icônes, « aucune » en tête ; les pièces pas encore étudiées sont voilées. */
    private void extractPayloads(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        List<Item> all = new ArrayList<>();
        all.add(Items.AIR);
        all.addAll(RocketParts.ofKind(RocketParts.Kind.PAYLOAD));
        int columns = 14;
        int cell = 19;
        int rows = (all.size() + columns - 1) / columns;
        int w = columns * cell + 16;
        int h = Math.min(HEIGHT - 12, 30 + rows * cell);
        int[] at = window(graphics, w, h, "design.orbitalis.choose_payload", mouseX, mouseY);
        for (int i = 0; i < all.size(); i++) {
            Item item = all.get(i);
            int ix = at[0] + 8 + (i % columns) * cell;
            int iy = at[1] + 25 + (i / columns) * cell;
            if (iy + cell > at[1] + h) {
                break;
            }
            Component lock = lockReason(item);
            boolean current = item == payload;
            boolean hovered = Ui.inside(mouseX, mouseY, ix, iy, cell - 1, cell - 1);
            Ui.round(graphics, ix, iy, cell - 1, cell - 1, current ? Ui.alpha(Ui.ACCENT, 0.22F) : hovered ? Ui.SURFACE_HI : Ui.SURFACE);
            if (current) {
                Ui.frame(graphics, ix, iy, cell - 1, cell - 1, Ui.ACCENT);
            }
            if (item == Items.AIR) {
                graphics.text(font, "∅", ix + 6, iy + 5, Ui.MUTED, false);
            } else {
                graphics.item(new ItemStack(item), ix + 1, iy + 1);
            }
            if (lock != null) {
                graphics.nextStratum();
                graphics.fill(ix, iy, ix + cell - 1, iy + cell - 1, 0xB00A0D11);
                lock(graphics, ix + 6, iy + 5);
            }
            if (hovered) {
                List<FormattedCharSequence> lines = new ArrayList<>();
                if (item == Items.AIR) {
                    lines.add(Component.translatable("design.orbitalis.no_payload").getVisualOrderText());
                } else {
                    for (Component line : Screen.getTooltipFromItem(Minecraft.getInstance(), new ItemStack(item))) {
                        lines.add(line.getVisualOrderText());
                    }
                }
                if (lock != null) {
                    lines.add(lock.copy().withStyle(ChatFormatting.RED).getVisualOrderText());
                }
                tooltip = lines;
            }
            if (lock == null) {
                zones.add(new Zone(ix, iy, cell - 1, cell - 1, () -> {
                    payload = item;
                    preset = CUSTOM;
                    popup = Popup.NONE;
                }, null, null, null));
            }
        }
    }

    // ------------------------------------------------------------------
    // Outils
    // ------------------------------------------------------------------

    /** Plus petit pas de tir qui accepte ce lanceur (masse et hauteur), de 1 à 5 ; 0 si aucun ne convient. */
    private static int requiredPad(RocketStats stats, RocketShape shape) {
        for (int radius : PadPlan.SIZES) {
            if (stats.liftoffMass() <= LaunchComplex.maxRocketMass(radius)
                    && shape.height() <= LaunchComplex.maxRocketHeight(PadPlan.towerLevels(radius))) {
                return radius;
            }
        }
        return 0;
    }

    private static String tonnes(double kilograms) {
        return kilograms >= 10_000 ? String.format(Locale.ROOT, "%.0f t", kilograms / 1000.0) : String.format(Locale.ROOT, "%.1f t", kilograms / 1000.0);
    }

    private static String grouped(double value) {
        return String.format(Locale.ROOT, "%,.0f", value).replace(',', ' ');
    }

    private void print() {
        ClientPacketDistributor.sendToServer(DesignPrintPayload.of(draft()));
        onClose();
    }

    /** Échap ferme d'abord la fenêtre ouverte, puis l'écran. */
    @Override
    public void onClose() {
        if (popup != Popup.NONE) {
            popup = Popup.NONE;
            return;
        }
        super.onClose();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        // Les fenêtres retiennent leurs propres zones, dessinées en dernier : on les essaie d'abord.
        List<Zone> ordered = new ArrayList<>(zones);
        java.util.Collections.reverse(ordered);
        for (Zone zone : ordered) {
            if (zone.contains(event.x(), event.y())) {
                Runnable action = event.button() == 1 && zone.alternate() != null ? zone.alternate() : zone.action();
                action.run();
                Ui.click();
                return true;
            }
        }
        // Un clic hors d'une fenêtre ouverte la referme.
        if (popup != Popup.NONE) {
            popup = Popup.NONE;
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        for (Zone zone : List.copyOf(zones)) {
            if (zone.up() != null && zone.contains(mouseX, mouseY) && scrollY != 0) {
                (scrollY > 0 ? zone.up() : zone.down()).run();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
