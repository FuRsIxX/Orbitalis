package com.orbitalis.platform.event;

import net.minecraft.world.entity.Entity;

/** Une entité monte sur une autre, ou en descend : on peut l'en empêcher. */
public final class EntityMountEvent extends Cancelable {
    private final Entity mounting;
    private final Entity mounted;
    private final boolean mount;

    public EntityMountEvent(Entity mounting, Entity mounted, boolean mount) {
        this.mounting = mounting;
        this.mounted = mounted;
        this.mount = mount;
    }

    public Entity getEntityMounting() {
        return mounting;
    }

    public Entity getEntityBeingMounted() {
        return mounted;
    }

    public boolean isMounting() {
        return mount;
    }

    public boolean isDismounting() {
        return !mount;
    }
}
