package com.photonpulse.candy.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.photonpulse.candy.boss.TrueRealCandyEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
//? if >=1.21.9 {
/*import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.CameraRenderState;
*/
//?}
//? if <1.21.9 {
import net.minecraft.client.renderer.MultiBufferSource;
//?}
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

/**
 * TrueRealCandyRenderer — boss'u oyuncunun kendi skiniyle, çok karanlık render eder.
 * "Karşındaki gerçek Candy — ve o sensin."
 */
@Environment(EnvType.CLIENT)
public class TrueRealCandyRenderer extends MobRenderer<
        TrueRealCandyEntity, PlayerRenderState, PlayerModel> {

    private final ItemModelResolver itemModelManager;

    public TrueRealCandyRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.7f);
        this.itemModelManager = ctx.getItemModelResolver();
    }

    @Override
    public PlayerRenderState createRenderState() {
        return new PlayerRenderState();
    }

    @Override
    public void extractRenderState(TrueRealCandyEntity entity, PlayerRenderState state, float tickDelta) {
        super.extractRenderState(entity, state, tickDelta);
        HumanoidMobRenderer.extractHumanoidRenderState(entity, state, tickDelta, this.itemModelManager);

        UUID owner = entity.getOwnerUuid().orElse(null);
        PlayerSkin skin = null;
        if (owner != null) {
            ClientPacketListener handler = Minecraft.getInstance().getConnection();
            if (handler != null) {
                PlayerInfo entry = handler.getPlayerInfo(owner);
                if (entry != null) skin = entry.getSkin();
            }
        }
        if (skin == null) {
            skin = owner != null ? DefaultPlayerSkin.get(owner) : DefaultPlayerSkin.getDefaultSkin();
        }
        state.skin = skin;
        state.showHat = true;
        state.showJacket = true;
        state.showLeftSleeve = true;
        state.showRightSleeve = true;
        state.showLeftPants = true;
        state.showRightPants = true;
        state.showCape = false;
        state.isBaby = false;
    }

    // 1.21.9: SkinTextures.texture() -> body().texturePath().
    @Override
    public ResourceLocation getTextureLocation(PlayerRenderState state) {
        if (state.skin == null) return DefaultPlayerSkin.getDefaultTexture();
        //? if >=1.21.9 {
        /*return state.skin.body().texturePath();
        */
        //?}
        //? if <1.21.9 {
        return state.skin.texture();
        //?}
    }

    // Çok karanlık render — sadece siluet.
    // 1.21.9'da light render imzasından çıkıp render state'e taşındı.
    //? if >=1.21.9 {
    /*@Override
    public void submit(PlayerRenderState state, PoseStack matrices,
                       SubmitNodeCollector queue, CameraRenderState camera) {
        state.lightCoords = 0x00200020;
        super.submit(state, matrices, queue, camera);
    }
    */
    //?}
    //? if <1.21.9 {
    @Override
    public void render(PlayerRenderState state, PoseStack matrices,
                       MultiBufferSource provider, int light) {
        super.render(state, matrices, provider, 0x00200020);
    }
    //?}
}
