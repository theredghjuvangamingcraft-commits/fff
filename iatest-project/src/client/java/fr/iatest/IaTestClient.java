package fr.iatest;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.network.chat.Component;

import java.util.List;

public class IaTestClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(Modules::tick);

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            LiteralArgumentBuilder<FabricClientCommandSource> root = ClientCommandManager.literal("ia");
            for (String name : List.of("xray", "reach", "bridge", "aura", "totem", "mace", "crystal")) {
                root.then(ClientCommandManager.literal(name).executes(ctx -> {
                    boolean on = Modules.toggle(name);
                    ctx.getSource().sendFeedback(Component.literal("[IA] " + name + " : " + (on ? "ON" : "OFF")));
                    return 1;
                }));
            }
            root.then(ClientCommandManager.literal("off").executes(ctx -> {
                Modules.disableAll();
                ctx.getSource().sendFeedback(Component.literal("[IA] tout est OFF"));
                return 1;
            }));
            dispatcher.register(root);
        });
    }
}
