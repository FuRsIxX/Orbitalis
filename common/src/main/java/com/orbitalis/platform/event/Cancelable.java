package com.orbitalis.platform.event;

/** Un événement qu'un abonné peut annuler. */
public abstract class Cancelable {
    private boolean canceled;

    public void setCanceled(boolean canceled) {
        this.canceled = canceled;
    }

    public boolean isCanceled() {
        return canceled;
    }
}
