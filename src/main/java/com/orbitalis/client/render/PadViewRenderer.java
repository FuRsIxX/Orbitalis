package com.orbitalis.client.render;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joml.Quaternionf;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.orbitalis.phase1.spaceport.LaunchComplex;
import com.orbitalis.phase1.spaceport.LaunchTableBlockEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * La maquette d'un pas de tir, dans une interface, en vue simplifiée, comme la carte : chaque bloc du complexe et du
 * terrain alentour est un cube, avec la vraie texture de chacune de ses faces (l'herbe dessus, la terre sur les côtés,
 * aux couleurs du biome) et l'ombrage de la carte : dessus clair, flancs plus sombres. Le pas de tir est posé sur un
 * îlot de terrain découpé, vu de trois quarts, dans l'orientation de la carte de navigation.
 * <p>
 * Ce que la table de lancement dessine elle-même et qui s'anime (le tablier, les bras et les passerelles qui
 * s'écartent, la lueur des moteurs, la fusée qui s'élève) l'est par son propre rendu, comme dans le monde.
 */
public final class PadViewRenderer extends PictureInPictureRenderer<PadViewRenderer.State> {
    /** Terrain montré autour du complexe, en blocs. */
    private static final int MARGIN = 4;
    /** Profondeur de l'îlot sous le tablier, en blocs. */
    private static final int DEPTH = 3;
    /** Ombrage des faces, comme sur la carte : dessus, dessous, flancs nord et sud, flancs est et ouest. */
    private static final float SHADE_UP = 1.0F;
    private static final float SHADE_DOWN = 0.5F;
    private static final float SHADE_NORTH_SOUTH = 0.8F;
    private static final float SHADE_EAST_WEST = 0.64F;
    private static final int WATER_TINT = 0x3F76E4;

    /**
     * Ce qu'il faut pour dessiner la maquette.
     *
     * @param table       la table de lancement du pas de tir
     * @param yaw         rotation de la caméra autour du pas de tir, en radians
     * @param pitch       inclinaison de la caméra au-dessus de l'horizon, en radians
     * @param partialTick fraction de tick de l'image
     * @param scale       pixels d'interface par bloc
     */
    public record State(BlockPos table, float yaw, float pitch, float partialTick, int x0, int y0, int x1, int y1, float scale,
            ScreenRectangle scissorArea, ScreenRectangle bounds) implements PictureInPictureRenderState {
        public State(BlockPos table, float yaw, float pitch, float partialTick, int x0, int y0, int x1, int y1, float scale,
                ScreenRectangle scissorArea) {
            this(table, yaw, pitch, partialTick, x0, y0, x1, y1, scale, scissorArea, PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissorArea));
        }
    }

    /** Le volume montré, autour de la table : bornes en blocs, la table à l'origine. */
    public record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public float centerX() {
            return (minX + maxX + 1) / 2.0F;
        }

        public float centerY() {
            return (minY + maxY + 1) / 2.0F;
        }

        public float centerZ() {
            return (minZ + maxZ + 1) / 2.0F;
        }

        /** La plus grande diagonale du volume, en blocs : ce qu'il faut faire tenir dans le cadre. */
        public float span() {
            float dx = maxX - minX + 1;
            float dy = maxY - minY + 1;
            float dz = maxZ - minZ + 1;
            return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
    }

    /** L'aspect d'une face d'un bloc : sa texture, et vrai si le biome la teinte (l'herbe, le feuillage). */
    private record Face(TextureAtlasSprite sprite, int tintIndex) {
    }

    /** Une face à dessiner : ses quatre coins, sa texture, et sa couleur (teinte et ombrage). */
    private record Quad(float[] corners, TextureAtlasSprite sprite, int color) {
    }

    private final LaunchTableRenderer tableRenderer = new LaunchTableRenderer(null);
    /** L'aspect des six faces de chaque état de bloc, relevé une fois. */
    private final Map<BlockState, Face[]> faces = new HashMap<>();
    private final RandomSource random = RandomSource.create(42L);

    /** Le volume de cette table : le complexe (tablier, tour, hauteur de la fusée) et le terrain alentour. */
    public static Box box(LaunchTableBlockEntity table) {
        LaunchComplex complex = table.getComplex();
        int radius = Math.max(1, complex.radius());
        int minX = -radius - 1;
        int maxX = radius + 1;
        int minZ = -radius - 1;
        int maxZ = radius + 1;
        int top = 6;
        if (complex.towerBase() != null && complex.towerHeight() > 0) {
            BlockPos tower = complex.towerBase().subtract(table.getBlockPos());
            minX = Math.min(minX, tower.getX() - 1);
            maxX = Math.max(maxX, tower.getX() + 1);
            minZ = Math.min(minZ, tower.getZ() - 1);
            maxZ = Math.max(maxZ, tower.getZ() + 1);
            top = Math.max(top, tower.getY() + complex.towerHeight() + 3);
        }
        if (table.hasRocket()) {
            top = Math.max(top, table.rocketHeight() + 2);
        }
        return new Box(minX - MARGIN, -DEPTH, minZ - MARGIN, maxX + MARGIN, top, maxZ + MARGIN);
    }

    @Override
    public Class<State> getRenderStateClass() {
        return State.class;
    }

    /** Vrai si ce bloc est dessiné en cube : il cache alors la face de son voisin qui le touche. */
    private static boolean cube(BlockState block) {
        return !block.isAir() && block.getRenderShape() == RenderShape.MODEL && block.getFluidState().isEmpty();
    }

    /** L'aspect des six faces d'un état de bloc : la texture de la face du modèle tournée de ce côté, à défaut une autre. */
    private Face[] faces(BlockState block) {
        Face[] known = faces.get(block);
        if (known != null) {
            return known;
        }
        var models = Minecraft.getInstance().getModelManager().getBlockStateModelSet();
        List<BlockStateModelPart> parts = new ArrayList<>();
        random.setSeed(42L);
        models.get(block).collectParts(random, parts);
        TextureAtlasSprite particle = models.getParticleMaterial(block).sprite();
        Face[] found = new Face[6];
        for (Direction direction : Direction.values()) {
            BakedQuad quad = null;
            for (BlockStateModelPart part : parts) {
                if (quad == null && !part.getQuads(direction).isEmpty()) {
                    quad = part.getQuads(direction).get(0);
                }
            }
            // Un modèle qui n'est pas un cube plein (une poutrelle, une fusée) : sa face la mieux tournée de ce côté.
            for (BlockStateModelPart part : parts) {
                for (BakedQuad candidate : part.getQuads(null)) {
                    if (quad == null && candidate.direction() == direction) {
                        quad = candidate;
                    }
                }
            }
            found[direction.ordinal()] = quad == null ? new Face(particle, -1) : new Face(quad.materialInfo().sprite(), quad.materialInfo().tintIndex());
        }
        faces.put(block, found);
        return found;
    }

    /** Les quatre coins de la face d'un cube unité, tournés vers l'extérieur. */
    private static float[] corners(Direction direction) {
        return switch (direction) {
            case UP -> new float[] {0, 1, 0, 0, 1, 1, 1, 1, 1, 1, 1, 0};
            case DOWN -> new float[] {0, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1};
            case NORTH -> new float[] {1, 0, 0, 0, 0, 0, 0, 1, 0, 1, 1, 0};
            case SOUTH -> new float[] {0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 1, 1};
            case WEST -> new float[] {0, 0, 0, 0, 0, 1, 0, 1, 1, 0, 1, 0};
            case EAST -> new float[] {1, 0, 1, 1, 0, 0, 1, 1, 0, 1, 1, 1};
        };
    }

    private static float shade(Direction direction) {
        return switch (direction) {
            case UP -> SHADE_UP;
            case DOWN -> SHADE_DOWN;
            case NORTH, SOUTH -> SHADE_NORTH_SOUTH;
            case EAST, WEST -> SHADE_EAST_WEST;
        };
    }

    private static int tinted(int rgb, float shade) {
        int r = Math.round((rgb >> 16 & 0xFF) * shade);
        int g = Math.round((rgb >> 8 & 0xFF) * shade);
        int b = Math.round((rgb & 0xFF) * shade);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    @Override
    protected void renderToTexture(State state, PoseStack poseStack, SubmitNodeCollector collector) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || !(level.getBlockEntity(state.table()) instanceof LaunchTableBlockEntity table)) {
            return;
        }
        if (faces.size() > 4096) {
            faces.clear();
        }
        minecraft.gameRenderer.lighting().setupFor(Lighting.Entry.ITEMS_3D);
        Box box = box(table);
        // La vue : le monde à l'endroit, de trois quarts, d'au-dessus, centré sur le volume.
        Quaternionf view = new Quaternionf().rotateZ((float) Math.PI).rotateX(-state.pitch()).rotateY(state.yaw());
        poseStack.mulPose(view);
        poseStack.translate(-box.centerX(), -box.centerY(), -box.centerZ());

        // Les cubes : une face n'est dessinée que si rien ne la cache.
        BlockPos origin = state.table();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos next = new BlockPos.MutableBlockPos();
        List<Quad> quads = new ArrayList<>();
        var colors = minecraft.getBlockColors();
        for (int y = box.minY(); y <= box.maxY(); y++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                for (int x = box.minX(); x <= box.maxX(); x++) {
                    pos.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                    BlockState block = level.getBlockState(pos);
                    boolean water = !block.getFluidState().isEmpty();
                    if (!cube(block) && !water) {
                        continue;
                    }
                    if (water) {
                        // L'eau : sa seule surface, à fleur, quand rien ne la couvre.
                        next.setWithOffset(pos, Direction.UP);
                        if (level.getBlockState(next).getFluidState().isEmpty() && !cube(level.getBlockState(next))) {
                            TextureAtlasSprite sprite = minecraft.getModelManager().getBlockStateModelSet()
                                    .getParticleMaterial(Blocks.WATER.defaultBlockState()).sprite();
                            float[] corners = corners(Direction.UP);
                            for (int k = 0; k < 4; k++) {
                                corners[k * 3] += x;
                                corners[k * 3 + 1] += y - 0.1F;
                                corners[k * 3 + 2] += z;
                            }
                            quads.add(new Quad(corners, sprite, tinted(WATER_TINT, SHADE_UP)));
                        }
                        continue;
                    }
                    Face[] look = faces(block);
                    for (Direction direction : Direction.values()) {
                        next.setWithOffset(pos, direction);
                        int nx = x + direction.getStepX();
                        int ny = y + direction.getStepY();
                        int nz = z + direction.getStepZ();
                        boolean inside = nx >= box.minX() && nx <= box.maxX() && ny >= box.minY() && ny <= box.maxY() && nz >= box.minZ() && nz <= box.maxZ();
                        // Au bord de l'îlot, la face reste : le terrain est découpé, comme sur une maquette.
                        if (inside && cube(level.getBlockState(next))) {
                            continue;
                        }
                        // Le dessous ne se voit jamais d'en haut.
                        if (direction == Direction.DOWN) {
                            continue;
                        }
                        Face face = look[direction.ordinal()];
                        int rgb = 0xFFFFFF;
                        if (face.tintIndex() >= 0) {
                            BlockTintSource source = colors.getTintSource(block, face.tintIndex());
                            if (source != null) {
                                rgb = source.colorInWorld(block, level, pos) & 0xFFFFFF;
                            }
                        }
                        float[] corners = corners(direction);
                        for (int k = 0; k < 4; k++) {
                            corners[k * 3] += x;
                            corners[k * 3 + 1] += y;
                            corners[k * 3 + 2] += z;
                        }
                        quads.add(new Quad(corners, face.sprite(), tinted(rgb, shade(direction))));
                    }
                }
            }
        }
        collector.submitCustomGeometry(poseStack, RenderTypes.text(TextureAtlas.LOCATION_BLOCKS), (pose, buffer) -> {
            for (Quad quad : quads) {
                float[] c = quad.corners();
                TextureAtlasSprite sprite = quad.sprite();
                // La texture couvre la face entière : en haut du flanc, le haut de l'image.
                float[][] uv = {{sprite.getU0(), sprite.getV1()}, {sprite.getU1(), sprite.getV1()}, {sprite.getU1(), sprite.getV0()},
                        {sprite.getU0(), sprite.getV0()}};
                for (int k = 0; k < 4; k++) {
                    buffer.addVertex(pose, c[k * 3], c[k * 3 + 1], c[k * 3 + 2]).setColor(quad.color()).setUv(uv[k][0], uv[k][1])
                            .setLight(LightCoordsUtil.FULL_BRIGHT);
                }
            }
        });

        // Ce que la table dessine elle-même, et qui s'anime : le tablier, les bras, la lueur des moteurs, la fusée en vol.
        LaunchTableRenderer.State tableState = tableRenderer.createRenderState();
        tableRenderer.extractRenderState(table, tableState, state.partialTick(), Vec3.atCenterOf(origin), null);
        tableState.lightCoords = LightCoordsUtil.FULL_BRIGHT;
        CameraRenderState camera = new CameraRenderState();
        camera.orientation = new Quaternionf().rotateY(-state.yaw()).rotateX(state.pitch());
        tableRenderer.submit(tableState, poseStack, collector, camera);
    }

    @Override
    protected float getTranslateY(int height, int guiScale) {
        return height / 2.0F;
    }

    @Override
    protected String getTextureLabel() {
        return "orbitalis pad view";
    }
}
