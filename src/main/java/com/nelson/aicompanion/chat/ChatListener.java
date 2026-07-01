package com.nelson.aicompanion.chat;

import com.nelson.aicompanion.AiCompanionMod;
import com.nelson.aicompanion.entity.CompanionEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.ServerChatEvent;

public class ChatListener {

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ChatListener::onChat);
    }

    private static void onChat(ServerChatEvent event) {
            ServerPlayer sender = event.getPlayer();
            String playerName = sender.getName().getString();
            String messageText = event.getMessage().getString();

            AiCompanionMod.LOGGER.info("Intercepted chat from " + playerName + ": " + messageText);

            ForgivenessResult forgiveness = forgiveCompanionsIfApology(sender, messageText);
            if (forgiveness.count() > 0) {
                String speaker = forgiveness.count() == 1 ? forgiveness.speakerName() : "AI Companions";
                String reply = forgiveness.count() == 1
                        ? "We're good. I'll stand down."
                        : "Apology accepted. Weapons down.";
                sender.getCommandSenderWorld().getServer().getPlayerList()
                        .broadcastSystemMessage(Component.literal("§e<" + speaker + "> §f" + reply), false);
                return;
            }

            AiCompanionMod.AI_CLIENT.sendChatMessage(playerName, messageText).thenAccept(response -> {
                if (response == null || !response.has("reply")) return;

                String aiReply = response.get("reply").getAsString();
                if (aiReply == null || aiReply.isBlank() || aiReply.equals("IGNORE")) return;

                String speaker = response.has("speaker") ? response.get("speaker").getAsString() : "Bot";

                AiCompanionMod.LOGGER.info("AI replied: " + aiReply);

                if (sender.level() == null || sender.level().isClientSide()) return;

                sender.level().getServer().execute(() -> {
                    // Broadcast the reply
                    sender.level().getServer().getPlayerList()
                            .broadcastSystemMessage(Component.literal("§e<" + speaker + "> §f" + aiReply), false);

                    // Find the named companion and make it look at the player who spoke
                    String finalSpeaker = speaker;
                    for (ServerLevel world : sender.level().getServer().getAllLevels()) {
                        for (Entity entity : world.getAllEntities()) {
                            if (entity instanceof CompanionEntity companion
                                    && companion.getName().getString().equals(finalSpeaker)) {
                                companion.speakerLookTarget = sender;
                                companion.speakerLookTicks = 80; // Look for 4 seconds
                                companion.rememberSpokenPlayer(playerName);
                                break;
                            }
                        }
                    }
                });
            });
    }

    private static ForgivenessResult forgiveCompanionsIfApology(ServerPlayer sender, String messageText) {
        if (!isApology(messageText) || sender.getCommandSenderWorld() == null || sender.getCommandSenderWorld().isClientSide()) {
            return new ForgivenessResult(0, "AI Companions");
        }

        int forgiven = 0;
        String speakerName = "AI Companions";
        for (ServerLevel world : sender.getCommandSenderWorld().getServer().getAllLevels()) {
            for (Entity entity : world.getAllEntities()) {
                if (!(entity instanceof CompanionEntity companion)) continue;
                if (!companion.isAngryAtPlayer(sender)) continue;

                companion.forgivePlayer(sender);
                companion.speakerLookTarget = sender;
                companion.speakerLookTicks = 80;
                forgiven++;
                speakerName = companion.getName().getString();
            }
        }

        return new ForgivenessResult(forgiven, speakerName);
    }

    private static boolean isApology(String messageText) {
        String text = messageText == null ? "" : messageText.toLowerCase();
        text = text.replaceAll("[^a-z0-9]+", " ").trim();
        return text.contains("sorry")
                || text.contains("my bad")
                || text.contains("apologize")
                || text.contains("apology")
                || text.contains("forgive me")
                || text.contains("i didnt mean")
                || text.contains("i did not mean")
                || text.contains("accident");
    }

    private record ForgivenessResult(int count, String speakerName) {
    }
}

