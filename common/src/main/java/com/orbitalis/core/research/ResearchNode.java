package com.orbitalis.core.research;

import java.util.List;
import java.util.function.Supplier;

import com.orbitalis.core.config.ServerConfig;

import net.minecraft.world.level.ItemLike;

/**
 * Technologie d'un arbre de recherche.
 *
 * @param id            identifiant unique, utilisé dans les sauvegardes et les traductions
 * @param tree          arbre auquel la technologie appartient
 * @param icon          objet qui la représente dans le terminal
 * @param baseCost      coût en points de recherche, avant le multiplicateur de la configuration ;
 *                      0 = technologie connue dès le départ
 * @param prerequisites technologies à débloquer d'abord (elles peuvent venir d'un autre arbre)
 * @param column        colonne dans l'arbre affiché (de gauche à droite, dans l'ordre de la progression)
 * @param row           ligne dans l'arbre affiché
 */
public record ResearchNode(String id, ResearchTree tree, Supplier<? extends ItemLike> icon, int baseCost,
        List<String> prerequisites, int column, int row) {

    /** Coût réel, selon le multiplicateur des coûts choisi par le serveur. */
    public int cost() {
        if (baseCost == 0) {
            return 0;
        }
        return Math.max(1, (int) Math.round(baseCost * ServerConfig.COST_MULTIPLIER.get()));
    }

    /** Vrai si la technologie est acquise sans recherche. */
    public boolean isStartingKnowledge() {
        return baseCost == 0;
    }

    public String nameKey() {
        return "research.orbitalis.node." + id;
    }

    public String descriptionKey() {
        return "research.orbitalis.node." + id + ".desc";
    }
}
