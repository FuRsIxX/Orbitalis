package com.orbitalis.client.render;

import java.util.IdentityHashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Modèles de blocs prêts à dessiner, gardés en mémoire.
 * <p>
 * Le pas de tir, la fusée en vol et la capsule sont dessinés pièce par pièce, à partir de modèles de blocs :
 * une tour de vingt-quatre segments, ses bras, quatre mâts, une fusée de vingt tronçons. Préparer chacun de ces
 * modèles à chaque image coûtait cher pour rien, puisqu'un même état de bloc donne toujours le même modèle.
 * On ne le prépare donc qu'une fois, et on le réutilise.
 * <p>
 * Les modèles gardés sont renouvelés toutes les deux secondes : s'ils changent (rechargement des ressources du
 * jeu), le nouveau modèle est pris en compte sans rien avoir à surveiller.
 */
public final class ModelCache {
    private static final BlockDisplayContext DISPLAY_CONTEXT = BlockDisplayContext.create();
    /** Durée de validité d'un modèle gardé en mémoire, en nanosecondes. */
    private static final long LIFETIME = 2_000_000_000L;

    private record Entry(BlockModelRenderState model, long builtAt) {
    }

    private static final Map<BlockState, Entry> MODELS = new IdentityHashMap<>();

    private ModelCache() {
    }

    /** Modèle prêt à dessiner de cet état de bloc. Il ne doit pas être modifié : il est partagé. */
    public static BlockModelRenderState get(BlockState state) {
        long now = System.nanoTime();
        Entry entry = MODELS.get(state);
        if (entry == null || now - entry.builtAt() > LIFETIME) {
            // Un nouvel objet à chaque renouvellement : l'ancien peut encore être en cours de dessin.
            BlockModelRenderState model = new BlockModelRenderState();
            Minecraft.getInstance().getBlockModelResolver().update(model, state, DISPLAY_CONTEXT);
            entry = new Entry(model, now);
            MODELS.put(state, entry);
        }
        return entry.model();
    }
}
