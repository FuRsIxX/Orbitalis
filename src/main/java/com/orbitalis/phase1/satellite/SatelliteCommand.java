package com.orbitalis.phase1.satellite;

import java.util.Arrays;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.orbitalis.Orbitalis;
import com.orbitalis.core.research.ResearchManager;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Commande d'administration {@code /orbitalis satellites} : mettre un satellite en orbite sans lancement,
 * lister ceux de son agence, ou tous les retirer. Elle agit sur l'agence du joueur qui la tape.
 */
@EventBusSubscriber(modid = Orbitalis.MODID)
public final class SatelliteCommand {
    private static final DynamicCommandExceptionType UNKNOWN_TYPE = new DynamicCommandExceptionType(
            id -> Component.translatable("commands.orbitalis.satellites.unknown", id));

    private SatelliteCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("orbitalis")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("satellites")
                        .then(Commands.literal("deploy")
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                Arrays.stream(SatelliteType.values()).map(SatelliteType::id), builder))
                                        .executes(SatelliteCommand::deploy)))
                        .then(Commands.literal("list").executes(SatelliteCommand::list))
                        .then(Commands.literal("clear").executes(SatelliteCommand::clear))));
    }

    private static int deploy(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String id = StringArgumentType.getString(context, "type");
        SatelliteType type = Arrays.stream(SatelliteType.values()).filter(candidate -> candidate.id().equals(id)).findFirst()
                .orElseThrow(() -> UNKNOWN_TYPE.create(id));
        // Mise en orbite comme à la fin d'un vol réussi vers l'orbite de travail du satellite.
        SatelliteManager.Deployment deployment = SatelliteManager.onFlight(player.level().getServer(), ResearchManager.agencyKey(player),
                type, type.orbit().requiredReach());
        for (Component message : deployment.messages()) {
            context.getSource().sendSuccess(() -> message, false);
        }
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        MinecraftServer server = player.level().getServer();
        AgencySatellites satellites = SatelliteManager.of(server, ResearchManager.agencyKey(player));
        long now = SatelliteManager.now(server);
        boolean limited = SatelliteManager.limited();
        if (satellites.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.translatable("commands.orbitalis.satellites.none"), false);
            return 0;
        }
        for (Satellite satellite : satellites.all()) {
            Component line = Component.translatable("commands.orbitalis.satellites.line", Component.translatable(satellite.type().nameKey()),
                    satellite.number(), Component.translatable(satellite.type().orbit().nameKey()),
                    AgencySatellites.pending(satellite, now, limited));
            context.getSource().sendSuccess(() -> line, false);
        }
        return satellites.all().size();
    }

    private static int clear(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        MinecraftServer server = player.level().getServer();
        SatelliteManager.of(server, ResearchManager.agencyKey(player)).clear();
        SatelliteManager.changed(server);
        context.getSource().sendSuccess(() -> Component.translatable("commands.orbitalis.satellites.clear"), true);
        return 1;
    }
}
