package com.hollowdread;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

/** Comandos de teste (nível 2): /dread fear <0-1000>, /dread event, /dread spawn */
public final class DreadCommand {
    private DreadCommand() {}

    public static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, access, env) -> dispatcher.register(
                CommandManager.literal("dread")
                        .requires(src -> src.hasPermissionLevel(2))
                        .then(CommandManager.literal("fear")
                                .then(CommandManager.argument("valor", IntegerArgumentType.integer(0, 1000))
                                        .executes(ctx -> {
                                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                                            int v = IntegerArgumentType.getInteger(ctx, "valor");
                                            FearManager.setFear(p, v);
                                            ctx.getSource().sendFeedback(() -> Text.literal("Medo definido para " + v), false);
                                            return 1;
                                        })))
                        .then(CommandManager.literal("event").executes(ctx -> {
                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                            FearManager.trigger(p, 499);
                            return 1;
                        }))
                        .then(CommandManager.literal("spawn").executes(ctx -> {
                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                            boolean ok = FearManager.spawnStalker(p, (ServerWorld) p.getWorld());
                            ctx.getSource().sendFeedback(() -> Text.literal(ok
                                    ? "Algo acordou..." : "Não achei um lugar para ele."), false);
                            return ok ? 1 : 0;
                        }))
        ));
    }
}
