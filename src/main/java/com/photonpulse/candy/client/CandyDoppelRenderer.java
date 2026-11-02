package com.photonpulse.candy.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.photonpulse.candy.entity.CandyDoppelEntity;
//? if fabric {
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
//?}
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
//? if >=1.21.9 {
/*import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.state.CameraRenderState;
*/
//?}
//? if <1.21.9 {
import net.minecraft.client.renderer.MultiBufferSource;
//?}
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

/**
 * CandyDoppelRenderer — client-side renderer.
 *
 * Player skinini fetch eder (owner UUID'sinden) ve PlayerEntityModel ile render eder.
 * Böylece klon oyuncunun kendi skiniyle görünür — spooky doppelganger etkisi.
 *
 * NOT: PlayerEntityRenderState'i doğrudan kullanıyor (subclass değil) çünkü
 * PlayerEntityModel invariant olarak BipedEntityModel<PlayerEntityRenderState>'e bağlı.
 * Owner UUID artık state'te değil, updateRenderState anında entity'den okunuyor.
 */
//? if fabric {
@Environment(EnvType.CLIENT)
//?}
public class CandyDoppelRenderer extends MobRenderer<
        CandyDoppelEntity, PlayerRenderState, PlayerModel> {

    private final ItemModelResolver itemModelManager;

    public CandyDoppelRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
        this.itemModelManager = ctx.getItemModelResolver();
        // Armor renderer — doppel senin zırhını gösterir.
        // 1.21.9: iç/dış zırh ayrı EntityModelLayer olmaktan çıktı, tek bir
        // EquipmentModelData (PLAYER_EQUIPMENT) slot başına model taşıyor.
        //? if >=1.21.9 {
        /*this.addLayer(new HumanoidArmorLayer<>(this,
            ArmorModelSet.bake(ModelLayers.PLAYER_ARMOR,
                ctx.getModelSet(), HumanoidModel::new),
            ctx.getEquipmentRenderer()));
        */
        //?}
        //? if <1.21.9 {
        this.addLayer(new HumanoidArmorLayer<>(this,
            new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
            new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
            ctx.getEquipmentRenderer()));
        //?}
        // Held item renderer — main hand'daki silahı gösterir
        this.addLayer(new ItemInHandLayer<>(this));
    }

    @Override
    public PlayerRenderState createRenderState() {
        return new PlayerRenderState();
    }

    @Override
    public void extractRenderState(CandyDoppelEntity entity, PlayerRenderState state, float tickDelta) {
        super.extractRenderState(entity, state, tickDelta);

        // KRİTİK: biped state + armor slotları + hand item state hepsini birden doldur.
        // MobEntityRenderer.updateRenderState BipedEntityRenderState alanlarını doldurmuyor:
        // equippedHeadStack, equippedChestStack, equippedLegsStack, equippedFeetStack,
        // rightHandItemState, leftHandItemState — hepsi burada set edilir.
        // Bu olmadan armor + main hand item render edilmiyor.
        HumanoidMobRenderer.extractHumanoidRenderState(entity, state, tickDelta, this.itemModelManager);

        UUID owner = entity.getOwnerUuid().orElse(null);

        // Skin fetch — PlayerListEntry client'ın gördüğü oyuncuların texture'larını cache'liyor.
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

        // Player-specific fields — model'in çalışması için makul default'lar
        state.showHat = true;
        state.showJacket = true;
        state.showLeftSleeve = true;
        state.showRightSleeve = true;
        state.showLeftPants = true;
        state.showRightPants = true;
        state.showCape = false;
    }

    // 1.21.9: SkinTextures.texture() yerine body(), ve artık doğrudan Identifier
    // değil AssetInfo.TextureAsset dönüyor — yolu texturePath() veriyor.
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

    /**
     * Render override — model'i dim light ile çiz (koyu tonlarda görünür).
     * Format: (sky << 20) | (block << 4). pack(6, 6) = 0x00600060 — dim ama görünür.
     * 1.21.9'da light render imzasından çıkıp render state'e taşındı, o yüzden
     * super'a geçmeden state.light'a yazıyoruz.
     */
    //? if >=1.21.9 {
    /*@Override
    public void submit(PlayerRenderState state, PoseStack matrices,
                       SubmitNodeCollector queue, CameraRenderState camera) {
        state.lightCoords = 0x00600060;
        super.submit(state, matrices, queue, camera);
    }
    */
    //?}
    //? if <1.21.9 {
    @Override
    public void render(PlayerRenderState state, PoseStack matrices,
                       MultiBufferSource provider, int light) {
        super.render(state, matrices, provider, 0x00600060);
    }
    //?}
}
