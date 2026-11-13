package com.orbitalis.platform;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.component.DataComponentType;

/** Ce qu'on peut lire des composants d'un objet (un bloc reposé relit ainsi l'énergie ou les fluides gardés sur son objet). */
@FunctionalInterface
public interface ComponentGetter {
    <T> @Nullable T get(DataComponentType<T> type);
}
