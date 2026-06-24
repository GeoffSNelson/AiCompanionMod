package com.nelson.aicompanion.chat;

import com.nelson.aicompanion.AiCompanionMod;
import com.nelson.aicompanion.entity.CompanionEntity;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

public class ChatListener {

    public static void register() {
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            String playerName = sender.getName().getString();
            String messageText = message.getContent().getString();

            AiCompanionMod.LOGGER.info("Intercepted chat from " + playerName + ": " + messageText);

            ForgivenessResult forgiveness = forgiveCompanionsIfApology(sender, messageText);
            if (forgiveness.count() > 0) {
                String speaker = forgiveness.count() == 1 ? forgiveness.speakerName() : "AI Companions";
                String reply = forgiveness.count() == 1
                        ? "We're good. I'll stand down."
                        : "Apology accepted. Weapons down.";
                sender.getEntityWorld().getServer().getPlayerManager()
                        .broadcast(Text.literal("§e<" + speaker + "> §f" + reply), false);
                return;
            }

            AiCompanionMod.AI_CLIENT.sendChatMessage(playerName, messageText).thenAccept(response -> {
                if (response == null || !response.has("reply")) return;

                String aiReply = response.get("reply").getAsString();
                if (aiReply == null || aiReply.isBlank() || aiReply.equals("IGNORE")) return;

                String speaker = response.has("speaker") ? response.get("speaker").getAsString() : "Bot";

                AiCompanionMod.LOGGER.info("AI replied: " + aiReply);

                if (sender.getEntityWorld() == null || sender.getEntityWorld().isClient()) return;

                sender.getEntityWorld().getServer().execute(() -> {
                    // Broadcast the reply
                    sender.getEntityWorld().getServer().getPlayerManager()
                            .broadcast(Text.literal("§e<" + speaker + "> §f" + aiReply), false);

                    // Find the named companion and make it look at the player who spoke
                    String finalSpeaker = speaker;
                    for (ServerWorld world : sender.getEntityWorld().getServer().getWorlds()) {
                        for (Entity entity : world.iterateEntities()) {
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
        });
    }

    private static ForgivenessResult forgiveCompanionsIfApology(ServerPlayerEntity sender, String messageText) {
        if (!isApology(messageText) || sender.getEntityWorld() == null || sender.getEntityWorld().isClient()) {
            return new ForgivenessResult(0, "AI Companions");
        }

        int forgiven = 0;
        String speakerName = "AI Companions";
        for (ServerWorld world : sender.getEntityWorld().getServer().getWorlds()) {
            for (Entity entity : world.iterateEntities()) {
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

