package com.orbitalis.phase2.station;

import com.orbitalis.core.research.ResearchManager;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;

/**
 * Module de station, replié dans sa coiffe. Le module central, lancé seul par une fusée, se déploie en orbite et
 * devient le cœur d'une station ; une fois la station en place, il sert aussi de pièce d'extension nue.
 * <p>
 * Les kits de modules (nœud, habitation, laboratoire, serre, fret, coupole, sas, chantier) s'apportent à bord, dans
 * l'inventaire de l'équipage ou comme fret d'un lanceur. Visé, un port de jonction montre le module en fantôme de
 * l'autre côté ; un clic droit l'y déploie, tout aménagé, et ouvre le passage. La poutre d'énergie, elle, se plante
 * d'un clic droit sur un mur, depuis l'intérieur.
 */
public class StationModuleItem extends Item {
    private final ModuleType type;

    public StationModuleItem(Properties properties, ModuleType type) {
        super(properties);
        this.type = type;
    }

    public ModuleType type() {
        return type;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level) || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }
        if (!player.getAbilities().instabuild && !ResearchManager.isUnlocked(player, type.research())) {
            player.sendSystemMessage(Component.translatable("station.orbitalis.expand.research",
                    Component.translatable("research.orbitalis.node." + type.research())).withStyle(ChatFormatting.GOLD));
            return InteractionResult.SUCCESS;
        }
        // Un module s'accroche sur un port de jonction ; la poutre d'énergie, qui reste dehors, sur un mur, vu de l'intérieur.
        Component problem = type.pressurized() ? StationModules.attach(level, context.getClickedPos(), type)
                : StationModules.attachTruss(level, context.getClickedPos(), context.getClickedFace().getOpposite());
        if (problem != null) {
            player.sendSystemMessage(problem.copy().withStyle(ChatFormatting.GOLD));
            return InteractionResult.SUCCESS;
        }
        level.playSound(null, context.getClickedPos(), SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1.0F, 0.7F);
        level.playSound(null, context.getClickedPos(), SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.6F, 0.6F);
        player.sendSystemMessage(Component.translatable("station.orbitalis.expand.done", Component.translatable(type.nameKey()))
                .withStyle(ChatFormatting.GREEN));
        if (!player.getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
            TooltipFlag flag) {
        builder.accept(Component.translatable("station.orbitalis.module." + type.id() + ".desc").withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable(type.pressurized() ? "station.orbitalis.module.use" : "station.orbitalis.module.use_truss")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
