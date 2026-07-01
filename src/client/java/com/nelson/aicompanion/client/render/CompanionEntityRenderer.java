package com.nelson.aicompanion.client.render;

import com.nelson.aicompanion.AiCompanionMod;
import com.nelson.aicompanion.entity.CompanionEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

public class CompanionEntityRenderer extends HumanoidMobRenderer<CompanionEntity, HumanoidModel<CompanionEntity>> {
    
    private static final ResourceLocation[] TEXTURES = {
            ResourceLocation.tryBuild(AiCompanionMod.MOD_ID, "textures/entity/companion.png"),
            ResourceLocation.tryBuild(AiCompanionMod.MOD_ID, "textures/entity/companion_miner.png"),
            ResourceLocation.tryBuild(AiCompanionMod.MOD_ID, "textures/entity/companion_guardian.png"),
            ResourceLocation.tryBuild(AiCompanionMod.MOD_ID, "textures/entity/companion_builder.png"),
            ResourceLocation.tryBuild(AiCompanionMod.MOD_ID, "textures/entity/companion_scout.png"),
            ResourceLocation.tryBuild(AiCompanionMod.MOD_ID, "textures/entity/companion.png"),
            ResourceLocation.tryBuild(AiCompanionMod.MOD_ID, "textures/entity/companion.png")
    };

    public CompanionEntityRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(CompanionEntity entity) {
        return TEXTURES[Math.floorMod(entity.getAppearanceVariant(), TEXTURES.length)];
    }
}
