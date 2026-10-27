package com.orbitalis.client.render;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.joml.Vector3fc;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Blocs fantômes : les blocs d'un plan qui restent à poser, dessinés en vraie grandeur à leur place, à demi
 * transparents et teintés de bleu comme un hologramme. Le plan est montré en entier, du premier bloc au dernier.
 * <ul>
 * <li>Les deux couches les plus basses, celles où l'on bâtit, montrent le vrai modèle de chaque bloc : on voit
 * exactement quel bloc va où, et dans quel sens.</li>
 * <li>La couche la plus basse est en plus cernée d'un trait vif : c'est là qu'il faut poser. Des cases voisines sont
 * cernées d'un seul tenant.</li>
 * <li>Tout le reste, au-dessus, est montré en blocs pleins, plus pâles : la silhouette de ce qu'il reste à bâtir.
 * Un plan compte des milliers de blocs ; seules les faces tournées vers l'extérieur de cette silhouette sont
 * dessinées.</li>
 * </ul>
 * Les faces de chaque modèle de bloc sont relevées une fois, puis gardées deux secondes (assez pour suivre un
 * rechargement des ressources du jeu).
 */
public final class GhostBlocks {
    /** Couleur des fantômes détaillés : un blanc bleuté, opaque à un peu plus du tiers. */
    private static final int COLOR = 0x64BEE1FF;
    /** Couleur de la silhouette du reste du plan : le même bleu, plus soutenu car sa texture est elle-même transparente. */
    private static final int BULK = 0x96A8D8FF;
    /** Couleur du trait qui cerne les cases à remplir : un jaune vif, qui tranche sur le bleu des fantômes. */
    private static final int OUTLINE = 0xFFFFD84A;
    /** Nombre de fantômes détaillés au plus : au-delà, les blocs des couches basses passent eux aussi en silhouette. */
    private static final int DETAILED = 320;
    /** Les fantômes sont un rien plus petits que leur case : leurs faces ne se confondent pas avec celles des blocs voisins. */
    private static final float SHRINK = 0.004F;
    private static final long LIFETIME = 2_000_000_000L;

    private record Entry(List<BakedQuad> quads, long builtAt) {
    }

    private static final Map<BlockState, Entry> QUADS = new IdentityHashMap<>();
    /** Contour des cases à remplir, gardé tant que ces cases ne changent pas : le réunir coûte un peu. */
    private static final Map<List<BlockPos>, VoxelShape> OUTLINES = new java.util.LinkedHashMap<>(8, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<List<BlockPos>, VoxelShape> eldest) {
            // Quelques plans à la fois, pas plus : celui d'une table, celui d'une fusée, l'esquisse du kit.
            return size() > 6;
        }
    };

    private GhostBlocks() {
    }

    /** Toutes les faces du modèle d'un état de bloc. */
    private static List<BakedQuad> quads(BlockState state) {
        long now = System.nanoTime();
        Entry entry = QUADS.get(state);
        if (entry == null || now - entry.builtAt() > LIFETIME) {
            List<BlockStateModelPart> parts = new ArrayList<>();
            Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state).collectParts(RandomSource.create(42L), parts);
            List<BakedQuad> quads = new ArrayList<>();
            for (BlockStateModelPart part : parts) {
                for (Direction direction : Direction.values()) {
                    quads.addAll(part.getQuads(direction));
                }
                quads.addAll(part.getQuads(null));
            }
            entry = new Entry(List.copyOf(quads), now);
            QUADS.put(state, entry);
        }
        return entry.quads();
    }

    /**
     * Dessine des blocs fantômes.
     *
     * @param offsets position de chaque fantôme, dans le repère en cours
     * @param states  bloc que chacun annonce
     */
    public static void submit(List<BlockPos> offsets, List<BlockState> states, PoseStack poseStack, SubmitNodeCollector collector) {
        submit(offsets, states, -1, poseStack, collector);
    }

    /**
     * Dessine des blocs fantômes dont les {@code focus} premiers sont ceux où l'on bâtit : détaillés et cernés, quelle que
     * soit leur hauteur. Le reste est montré en silhouette. Pour les plans qui se bâtissent par sections, comme un
     * vaisseau couché dans son berceau, plutôt que par couches.
     */
    public static void submitFocused(List<BlockPos> offsets, List<BlockState> states, int focus, PoseStack poseStack,
            SubmitNodeCollector collector) {
        submit(offsets, states, Math.max(0, focus), poseStack, collector);
    }

    /** @param focus nombre de fantômes de tête à détailler, ou -1 pour détailler les deux couches les plus basses */
    private static void submit(List<BlockPos> offsets, List<BlockState> states, int focus, PoseStack poseStack, SubmitNodeCollector collector) {
        if (offsets.isEmpty()) {
            return;
        }
        // Les listes peuvent changer pendant que l'image se prépare : on en dessine une copie.
        List<BlockPos> positions = List.copyOf(offsets);
        int lowest = Integer.MAX_VALUE;
        for (BlockPos position : positions) {
            lowest = Math.min(lowest, position.getY());
        }
        // Les couches où l'on bâtit, en détail ; le reste du plan, en silhouette.
        List<BlockPos> detailed = new ArrayList<>();
        List<List<BakedQuad>> models = new ArrayList<>();
        List<BlockPos> bulk = new ArrayList<>();
        List<BlockPos> first = new ArrayList<>();
        for (int i = 0; i < positions.size(); i++) {
            BlockPos position = positions.get(i);
            boolean focused = focus < 0 ? position.getY() <= lowest + 1 : i < focus;
            if (focus < 0 ? position.getY() == lowest : i < focus) {
                first.add(position);
            }
            if (focused && detailed.size() < DETAILED) {
                detailed.add(position);
                models.add(quads(states.get(i)));
            } else {
                bulk.add(position);
            }
        }
        submitOutline(first, poseStack, collector);
        Set<BlockPos> solid = new HashSet<>(bulk);
        List<BakedQuad> cube = quads(Blocks.STAINED_GLASS.lightBlue().defaultBlockState());
        collector.submitCustomGeometry(poseStack, Sheets.translucentBlockItemSheet(), (pose, buffer) -> {
            for (int i = 0; i < detailed.size(); i++) {
                BlockPos offset = detailed.get(i);
                for (BakedQuad quad : models.get(i)) {
                    put(pose, buffer, quad, offset.getX(), offset.getY(), offset.getZ(), COLOR);
                }
            }
            for (BlockPos offset : bulk) {
                for (BakedQuad quad : cube) {
                    // Une face collée à un autre bloc de la silhouette ne se verrait pas : on ne la dessine pas.
                    if (!solid.contains(offset.relative(quad.direction()))) {
                        put(pose, buffer, quad, offset.getX(), offset.getY(), offset.getZ(), BULK);
                    }
                }
            }
        });
    }

    /** Cerne d'un trait vif les cases de la couche la plus basse : celles qu'il faut remplir maintenant. */
    private static void submitOutline(List<BlockPos> cells, PoseStack poseStack, SubmitNodeCollector collector) {
        VoxelShape outline = OUTLINES.get(cells);
        if (outline == null) {
            VoxelShape shape = Shapes.empty();
            for (BlockPos cell : cells) {
                shape = Shapes.joinUnoptimized(shape, Shapes.create(cell.getX(), cell.getY(), cell.getZ(),
                        cell.getX() + 1.0, cell.getY() + 1.0, cell.getZ() + 1.0), BooleanOp.OR);
            }
            outline = shape.optimize();
            OUTLINES.put(List.copyOf(cells), outline);
        }
        if (!outline.isEmpty()) {
            float width = Minecraft.getInstance().gameRenderer.gameRenderState().windowRenderState.appropriateLineWidth;
            collector.submitShapeOutline(poseStack, outline, RenderTypes.lines(), OUTLINE, width * 1.5F, false);
        }
    }

    private static void put(PoseStack.Pose pose, VertexConsumer buffer, BakedQuad quad, float x, float y, float z, int color) {
        Vector3fc normal = quad.direction().getUnitVec3f();
        for (int vertex = 0; vertex < 4; vertex++) {
            Vector3fc position = quad.position(vertex);
            long uv = quad.packedUV(vertex);
            buffer.addVertex(pose, x + shrunk(position.x()), y + shrunk(position.y()), z + shrunk(position.z()))
                    .setColor(color).setUv(UVPair.unpackU(uv), UVPair.unpackV(uv)).setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, normal.x(), normal.y(), normal.z());
        }
    }

    /** Ramène une coordonnée vers le centre du bloc, d'un rien. */
    private static float shrunk(float coordinate) {
        return 0.5F + (coordinate - 0.5F) * (1.0F - 2.0F * SHRINK);
    }
}
