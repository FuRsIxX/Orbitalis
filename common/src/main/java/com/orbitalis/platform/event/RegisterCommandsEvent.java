package com.orbitalis.platform.event;

import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/** L'inscription des commandes. */
public record RegisterCommandsEvent(CommandDispatcher<CommandSourceStack> getDispatcher, CommandBuildContext getBuildContext,
        Commands.CommandSelection getCommandSelection) {
}
