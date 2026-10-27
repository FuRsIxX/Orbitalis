package com.orbitalis.core.research;

import java.util.Collection;
import java.util.function.Consumer;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.orbitalis.Orbitalis;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Commande d'administration {@code /orbitalis research} : donner ou fixer des points, débloquer une technologie,
 * tout remettre à zéro. Elle agit sur l'agence de chaque joueur visé.
 */
@EventBusSubscriber(modid = Orbitalis.MODID)
public final class ResearchCommand {
    private static final DynamicCommandExceptionType UNKNOWN_NODE = new DynamicCommandExceptionType(
            id -> Component.translatable("commands.orbitalis.research.unknown", id));

    private ResearchCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("orbitalis")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("research")
                        .then(Commands.literal("points")
                                .then(Commands.literal("add")
                                        .then(Commands.argument("targets", EntityArgument.players())
                                                .then(Commands.argument("amount", IntegerArgumentType.integer())
                                                        .executes(context -> addPoints(context, IntegerArgumentType.getInteger(context, "amount"))))))
                                .then(Commands.literal("set")
                                        .then(Commands.argument("targets", EntityArgument.players())
                                                .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                                        .executes(context -> setPoints(context, IntegerArgumentType.getInteger(context, "amount")))))))
                        .then(Commands.literal("unlock")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .then(Commands.literal("all").executes(ResearchCommand::unlockAll))
                                        .then(Commands.argument("node", StringArgumentType.word())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                        ResearchNodes.all().stream().map(ResearchNode::id), builder))
                                                .executes(context -> unlock(context, StringArgumentType.getString(context, "node"))))))
                        .then(Commands.literal("reset")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(ResearchCommand::reset)))));
    }

    /** Applique un changement à l'agence de chaque joueur visé, puis l'enregistre et la renvoie aux joueurs. */
    private static int forEachTarget(CommandContext<CommandSourceStack> context, Consumer<AgencyResearch> change) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
        for (ServerPlayer player : targets) {
            change.accept(ResearchManager.of(player));
            ResearchManager.changed(player);
        }
        return targets.size();
    }

    private static int addPoints(CommandContext<CommandSourceStack> context, int amount) throws CommandSyntaxException {
        int count = forEachTarget(context, research -> research.addPoints(amount));
        context.getSource().sendSuccess(() -> Component.translatable("commands.orbitalis.research.points.add", amount, count), true);
        return count;
    }

    private static int setPoints(CommandContext<CommandSourceStack> context, int amount) throws CommandSyntaxException {
        int count = forEachTarget(context, research -> research.setPoints(amount));
        context.getSource().sendSuccess(() -> Component.translatable("commands.orbitalis.research.points.set", amount, count), true);
        return count;
    }

    private static int unlock(CommandContext<CommandSourceStack> context, String nodeId) throws CommandSyntaxException {
        ResearchNode node = ResearchNodes.get(nodeId);
        if (node == null) {
            throw UNKNOWN_NODE.create(nodeId);
        }
        int count = forEachTarget(context, research -> research.forceUnlock(nodeId));
        context.getSource().sendSuccess(() -> Component.translatable("commands.orbitalis.research.unlock",
                Component.translatable(node.nameKey()), count), true);
        return count;
    }

    private static int unlockAll(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        int count = forEachTarget(context, research -> ResearchNodes.all().forEach(node -> research.forceUnlock(node.id())));
        context.getSource().sendSuccess(() -> Component.translatable("commands.orbitalis.research.unlock_all", count), true);
        return count;
    }

    private static int reset(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        int count = forEachTarget(context, AgencyResearch::reset);
        context.getSource().sendSuccess(() -> Component.translatable("commands.orbitalis.research.reset", count), true);
        return count;
    }
}
