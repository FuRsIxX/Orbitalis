package com.orbitalis.client.port.render;

/** Ce qu'il faut savoir d'une entité pour la dessiner, pris à l'image en cours (comme en Minecraft 26). */
public class EntityRenderState {
    public double x;
    public double y;
    public double z;
    public float ageInTicks;
    public int lightCoords;
    public float partialTick;
    public float yRot;
    public double distanceToCameraSq;
    public int id;
}
