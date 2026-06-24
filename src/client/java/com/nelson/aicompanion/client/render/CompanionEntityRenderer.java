package com.nelson.aicompanion.client.render;

import com.nelson.aicompanion.AiCompanionMod;
import com.nelson.aicompanion.entity.CompanionEntity;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.util.Identifier;

public class CompanionEntityRenderer extends BipedEntityRenderer<CompanionEntity, BipedEntityModel<CompanionEntity>> {
    
    private static final Identifier[] TEXTURES = {
            Identifier.of(AiCompanionMod.MOD_ID, "textures/entity/companion.png"),
            Identifier.of(AiCompanionMod.MOD_ID, "textures/entity/companion_miner.png"),
            Identifier.of(AiCompanionMod.MOD_ID, "textures/entity/companion_guardian.png"),
            Identifier.of(AiCompanionMod.MOD_ID, "textures/entity/companion_builder.png"),
            Identifier.of(AiCompanionMod.MOD_ID, "textures/entity/companion_scout.png")
    };

    public CompanionEntityRenderer(EntityRendererFactory.Context context) {
        super(context, new BipedEntityModel<>(context.getPart(EntityModelLayers.PLAYER)), 0.5f);
    }

    @Override
    public Identifier getTexture(CompanionEntity entity) {
        return TEXTURES[Math.floorMod(entity.getAppearanceVariant(), TEXTURES.length)];
    }
}
