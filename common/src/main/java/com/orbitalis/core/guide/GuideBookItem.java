package com.orbitalis.core.guide;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * Manuel d'Orbitalis : un clic droit ouvre le livre de tutoriels.
 */
public class GuideBookItem extends Item {
    /**
     * Ouvre l'écran du manuel. Renseigné par le client au démarrage : le code commun ne peut pas
     * référencer directement les écrans, absents d'un serveur dédié.
     */
    public static Runnable screenOpener = () -> {
    };

    public GuideBookItem(Properties properties) {
        super(properties);
    }

    @Override
    public net.minecraft.world.InteractionResultHolder<net.minecraft.world.item.ItemStack> use(Level level, Player player, InteractionHand hand) {
        return com.orbitalis.platform.Compat.holder(use$(level, player, hand), player.getItemInHand(hand), level);
    }

    private InteractionResult use$(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            screenOpener.run();
        }
        return InteractionResult.SUCCESS;
    }
}
