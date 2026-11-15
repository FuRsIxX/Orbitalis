package com.orbitalis.client.port.render;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Les modèles des états de bloc, lus à la façon de Minecraft 26 (un modèle fait de « parties », chacune avec ses faces), sur
 * les modèles de 1.21.1 : un modèle n'y a qu'une partie, lui-même.
 */
public final class BlockStateModelSet {
    public static final BlockStateModelSet INSTANCE = new BlockStateModelSet();

    private BlockStateModelSet() {
    }

    /** La texture des éclats d'un bloc. */
    public record Material(TextureAtlasSprite sprite) {
    }

    public Model get(BlockState state) {
        return new Model(state, Minecraft.getInstance().getBlockRenderer().getBlockModel(state));
    }

    public Material getParticleMaterial(BlockState state) {
        return new Material(Minecraft.getInstance().getBlockRenderer().getBlockModel(state).getParticleIcon());
    }

    /** Le modèle d'un état de bloc. */
    public record Model(BlockState state, BakedModel model) {
        public void collectParts(RandomSource random, List<BlockStateModelPart> parts) {
            parts.add(new BlockStateModelPart(state, model, random.nextLong()));
        }
    }

    /** Une partie d'un modèle : ses faces, côté par côté (et celles qui ne tiennent à aucun côté). */
    public record BlockStateModelPart(BlockState state, BakedModel model, long seed) {
        public List<BakedQuad> getQuads(@Nullable Direction side) {
            List<BakedQuad> quads = new ArrayList<>();
            for (net.minecraft.client.renderer.block.model.BakedQuad quad : model.getQuads(state, side, RandomSource.create(seed))) {
                quads.add(new BakedQuad(quad));
            }
            return quads;
        }
    }
}
