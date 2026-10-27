package com.orbitalis.core.guide;

import java.util.function.Supplier;

import net.minecraft.world.level.ItemLike;

/**
 * Chapitre du manuel d'Orbitalis.
 * <p>
 * Le titre et le texte viennent des fichiers de traduction :
 * {@code guide.orbitalis.<id>.title} et {@code guide.orbitalis.<id>.text}.
 * Le texte est découpé en pages automatiquement selon la place disponible à l'écran.
 * Un chapitre peut aussi avoir une démonstration animée (voir {@code GuideScenes}, côté client).
 *
 * @param id      identifiant du chapitre, utilisé dans les clés de traduction
 * @param section rubrique du sommaire ({@code guide.orbitalis.section.<section>})
 * @param icon    objet affiché à côté du titre
 */
public record GuideChapter(String id, String section, Supplier<? extends ItemLike> icon) {
    public String titleKey() {
        return "guide.orbitalis." + id + ".title";
    }

    public String textKey() {
        return "guide.orbitalis." + id + ".text";
    }

    public String sectionKey() {
        return "guide.orbitalis.section." + section;
    }
}
