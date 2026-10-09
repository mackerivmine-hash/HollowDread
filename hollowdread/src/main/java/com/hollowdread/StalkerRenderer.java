package com.hollowdread;

import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

public class StalkerRenderer extends BipedEntityRenderer<StalkerEntity, BipedEntityModel<StalkerEntity>> {
    private static final Identifier TEXTURE = Identifier.of(HollowDread.MOD_ID, "textures/entity/stalker.png");

    public StalkerRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new BipedEntityModel<>(ctx.getPart(EntityModelLayers.ZOMBIE)), 0.5f);
    }

    @Override
    public Identifier getTexture(StalkerEntity entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(StalkerEntity entity, MatrixStack matrices, float amount) {
        matrices.scale(1.0f, 1.45f, 1.0f); // alto e esguio
    }
}
