package com.orbitalis.fabric.mixin.client;

/** La lumière propre d'un élément de modèle (« light_emission »), de 0 à 15, lue par le mod sous Fabric. */
public interface EmissiveElement {
    int orbitalis$light();

    void orbitalis$setLight(int light);
}
