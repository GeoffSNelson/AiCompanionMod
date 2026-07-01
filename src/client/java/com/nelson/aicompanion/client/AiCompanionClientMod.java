package com.nelson.aicompanion.client;

import com.nelson.aicompanion.client.render.CompanionEntityRenderer;
import com.nelson.aicompanion.AiCompanionMod;
import com.nelson.aicompanion.registry.ModEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = AiCompanionMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class AiCompanionClientMod {
    private AiCompanionClientMod() {
    }
    
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.COMPANION_NPC.get(), CompanionEntityRenderer::new);
    }
}
