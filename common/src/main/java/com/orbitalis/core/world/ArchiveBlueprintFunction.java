package com.orbitalis.core.world;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.orbitalis.phase1.rocket.RocketBlueprintItem;
import com.orbitalis.phase1.rocket.RocketCatalog;

import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/**
 * Fonction de butin : fait d'une feuille un plan de fusée d'archive, tiré au hasard parmi les premiers modèles du
 * catalogue (jusqu'à {@code max_template}), avec l'une de ses charges utiles et ses réglages d'origine. On le dépose
 * sur la table d'un pas de tir, comme un plan sorti de l'ordinateur de conception.
 */
public class ArchiveBlueprintFunction extends LootItemConditionalFunction {
    public static final MapCodec<ArchiveBlueprintFunction> CODEC = RecordCodecBuilder.mapCodec(instance -> commonFields(instance)
            .and(Codec.INT.optionalFieldOf("max_template", 2).forGetter(function -> function.maxTemplate))
            .apply(instance, ArchiveBlueprintFunction::new));

    private final int maxTemplate;

    private ArchiveBlueprintFunction(List<LootItemCondition> predicates, int maxTemplate) {
        super(predicates);
        this.maxTemplate = maxTemplate;
    }

    @Override
    public MapCodec<ArchiveBlueprintFunction> codec() {
        return CODEC;
    }

    @Override
    protected ItemStack run(ItemStack stack, LootContext context) {
        return archive(context.getRandom(), maxTemplate);
    }

    /** Un plan d'archive : un modèle du catalogue parmi les premiers, l'une de ses charges utiles. */
    public static ItemStack archive(RandomSource random, int maxTemplate) {
        List<RocketCatalog.Template> templates = RocketCatalog.all();
        RocketCatalog.Template template = templates.get(random.nextInt(Math.clamp(maxTemplate + 1, 1, templates.size())));
        int payload = random.nextInt(template.payloads().size());
        ItemStack blueprint = RocketBlueprintItem.create(RocketCatalog.build(template, payload, template.presets()),
                Component.translatable("item.orbitalis.rocket_blueprint.archive", Component.translatable(template.nameKey())));
        // Pas encore validé : il faudra le faire étudier à l'établi d'ingénieur.
        blueprint.set(com.orbitalis.core.registry.ModDataComponents.ARCHIVE_PLAN.get(), template.id());
        return blueprint;
    }
}
