package com.nelson.aicompanion.client;

import com.nelson.aicompanion.client.render.CompanionEntityRenderer;
import com.nelson.aicompanion.registry.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

@Environment(EnvType.CLIENT)
public class AiCompanionClientMod implements ClientModInitializer {
    
    @Override
    public void onInitializeClient() {
        // Bind our Companion NPC Entity to the Player/Biped model renderer!
        EntityRendererRegistry.register(ModEntities.COMPANION_NPC, CompanionEntityRenderer::new);
    }
}
