package xyz.breadloaf.voicertc.example;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;


import static xyz.breadloaf.voicertc.example.RTCMain.rtcAPI;

public class Main implements ModInitializer {
    /**
     * Runs the mod initializer.
     */
    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register(((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("magic").executes(context -> {
                if (rtcAPI == null) {
                    return 1;
                }
                rtcAPI.getConnectionURL((url)->{
                    context.getSource().sendSuccess(
                            Component.literal("Oh my god the magic actually worked!")
                                    .withStyle(style -> style
                                            .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url))
                                            .withColor(ChatFormatting.GREEN)), false);
                },(err)->{},context.getSource().getPlayer());
                return 0;
            }));
        }));
    }
}
