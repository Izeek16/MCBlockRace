package com.toasterz.blockrace;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;

public final class RaceCommands {

    private RaceCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandManager.literal("race")
                        .then(CommandManager.literal("start").executes(ctx -> {
                            RaceManager.INSTANCE.startHere(ctx.getSource().getServer());
                            return 1;
                        }))
                        .then(CommandManager.literal("next").executes(ctx -> {
                            RaceManager.INSTANCE.nextLocation(ctx.getSource().getServer());
                            return 1;
                        }))
                        .then(CommandManager.literal("skip").executes(ctx -> {
                            RaceManager.INSTANCE.skip(ctx.getSource().getServer());
                            return 1;
                        }))
                        .then(CommandManager.literal("stop").executes(ctx -> {
                            RaceManager.INSTANCE.stop(ctx.getSource().getServer());
                            return 1;
                        }))
                        .then(CommandManager.literal("scores").executes(ctx -> {
                            RaceManager.INSTANCE.showScores(ctx.getSource().getServer());
                            return 1;
                        }))
                        .then(CommandManager.literal("pool")
                                .then(CommandManager.literal("easy").executes(ctx -> {
                                    RaceManager.INSTANCE.setPool(ctx.getSource().getServer(), ItemPools.Pool.EASY);
                                    return 1;
                                }))
                                .then(CommandManager.literal("normal").executes(ctx -> {
                                    RaceManager.INSTANCE.setPool(ctx.getSource().getServer(), ItemPools.Pool.NORMAL);
                                    return 1;
                                }))
                                .then(CommandManager.literal("everything").executes(ctx -> {
                                    RaceManager.INSTANCE.setPool(ctx.getSource().getServer(), ItemPools.Pool.EVERYTHING);
                                    return 1;
                                })))
                ));
    }
}
