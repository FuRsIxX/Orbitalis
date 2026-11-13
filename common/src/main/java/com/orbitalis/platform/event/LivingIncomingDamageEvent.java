package com.orbitalis.platform.event;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/** Une entité vivante va être blessée : on peut l'en protéger. */
public final class LivingIncomingDamageEvent extends Cancelable {
    private final LivingEntity entity;
    private final DamageSource source;
    private final float amount;

    public LivingIncomingDamageEvent(LivingEntity entity, DamageSource source, float amount) {
        this.entity = entity;
        this.source = source;
        this.amount = amount;
    }

    public LivingEntity getEntity() {
        return entity;
    }

    public DamageSource getSource() {
        return source;
    }

    public float getAmount() {
        return amount;
    }
}
