package com.photonpulse.candy.client;

import com.photonpulse.candy.boss.TrueRealCandyEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * TrueRealCandyRenderer — boss'u oyuncunun kendi skiniyle, çok karanlık render eder.
 * "Karşındaki gerçek Candy — ve o sensin."
 */
@Environment(EnvType.CLIENT)
public class TrueRealCandyRenderer extends MobEntityRenderer<
        TrueRealCandyEntity, PlayerEntityRenderState, PlayerEntityModel> {

    private final ItemModelManager itemModelManager;

    public TrueRealCandyRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new PlayerEntityModel(ctx.getPart(EntityModelLayers.PLAYER), false), 0.7f);
        this.itemModelManager = ctx.getItemModelManager();
    }

    @Override
    public PlayerEntityRenderState createRenderState() {
        return new PlayerEntityRenderState();
    }

    @Override
    public void updateRenderState(TrueRealCandyEntity entity, PlayerEntityRenderState state, float tickDelta) {
        super.updateRenderState(entity, state, tickDelta);
        BipedEntityRenderer.updateBipedRenderState(entity, state, tickDelta, this.itemModelManager);

        UUID owner = entity.getOwnerUuid().orElse(null);
        SkinTextures skin = null;
        if (owner != null) {
            ClientPlayNetworkHandler handler = MinecraftClient.getInstance().getNetworkHandler();
            if (handler != null) {
                PlayerListEntry entry = handler.getPlayerListEntry(owner);
                if (entry != null) skin = entry.getSkinTextures();
            }
        }
        if (skin == null) {
            skin = owner != null ? DefaultSkinHelper.getSkinTextures(owner) : DefaultSkinHelper.getSteve();
        }
        state.skinTextures = skin;
        state.hatVisible = true;
        state.jacketVisible = true;
        state.leftSleeveVisible = true;
        state.rightSleeveVisible = true;
        state.leftPantsLegVisible = true;
        state.rightPantsLegVisible = true;
        state.capeVisible = false;
        state.baby = false;
    }

    @Override
    public Identifier getTexture(PlayerEntityRenderState state) {
        return state.skinTextures != null ? state.skinTextures.texture() : DefaultSkinHelper.getTexture();
    }

    @Override
    public void render(PlayerEntityRenderState state, MatrixStack matrices,
                       VertexConsumerProvider provider, int light) {
        // Çok karanlık render — sadece siluet
        super.render(state, matrices, provider, 0x00200020);
    }
}
