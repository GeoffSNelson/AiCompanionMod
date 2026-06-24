package com.nelson.aicompanion.client.render;

import com.nelson.aicompanion.AiCompanionMod;
import com.nelson.aicompanion.entity.CompanionEntity;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.util.Identifier;

import net.minecraft.client.render.entity.state.BipedEntityRenderState;

public class CompanionEntityRenderer extends BipedEntityRenderer<CompanionEntity, CompanionEntityRenderer.CompanionRenderState, BipedEntityModel<CompanionEntityRenderer.CompanionRenderState>> {
    
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
    public CompanionRenderState createRenderState() {
        return new CompanionRenderState();
    }

    @Override
    public void updateRenderState(CompanionEntity entity, CompanionRenderState state, float tickDelta) {
        super.updateRenderState(entity, state, tickDelta);
        state.appearanceVariant = entity.getAppearanceVariant();
    }

    @Override
    public Identifier getTexture(CompanionRenderState state) {
        return TEXTURES[Math.floorMod(state.appearanceVariant, TEXTURES.length)];
    }

    public static class CompanionRenderState extends BipedEntityRenderState {
        public int appearanceVariant;
    }
}
