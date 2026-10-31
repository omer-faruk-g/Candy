package com.photonpulse.candy.client;

import com.photonpulse.candy.entity.CandyDoppelEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
//? if >=1.21.9 {
/*import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.render.entity.model.EquipmentModelData;
*/
//?}
//? if <1.21.9 {
import net.minecraft.client.render.VertexConsumerProvider;
//?}
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

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
@Environment(EnvType.CLIENT)
public class CandyDoppelRenderer extends MobEntityRenderer<
        CandyDoppelEntity, PlayerEntityRenderState, PlayerEntityModel> {

    private final ItemModelManager itemModelManager;

    public CandyDoppelRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new PlayerEntityModel(ctx.getPart(EntityModelLayers.PLAYER), false), 0.5f);
        this.itemModelManager = ctx.getItemModelManager();
        // Armor renderer — doppel senin zırhını gösterir.
        // 1.21.9: iç/dış zırh ayrı EntityModelLayer olmaktan çıktı, tek bir
        // EquipmentModelData (PLAYER_EQUIPMENT) slot başına model taşıyor.
        //? if >=1.21.9 {
        /*this.addFeature(new ArmorFeatureRenderer<>(this,
            EquipmentModelData.mapToEntityModel(EntityModelLayers.PLAYER_EQUIPMENT,
                ctx.getEntityModels(), BipedEntityModel::new),
            ctx.getEquipmentRenderer()));
        */
        //?}
        //? if <1.21.9 {
        this.addFeature(new ArmorFeatureRenderer<>(this,
            new BipedEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER_INNER_ARMOR)),
            new BipedEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER_OUTER_ARMOR)),
            ctx.getEquipmentRenderer()));
        //?}
        // Held item renderer — main hand'daki silahı gösterir
        this.addFeature(new HeldItemFeatureRenderer<>(this));
    }

    @Override
    public PlayerEntityRenderState createRenderState() {
        return new PlayerEntityRenderState();
    }

    @Override
    public void updateRenderState(CandyDoppelEntity entity, PlayerEntityRenderState state, float tickDelta) {
        super.updateRenderState(entity, state, tickDelta);

        // KRİTİK: biped state + armor slotları + hand item state hepsini birden doldur.
        // MobEntityRenderer.updateRenderState BipedEntityRenderState alanlarını doldurmuyor:
        // equippedHeadStack, equippedChestStack, equippedLegsStack, equippedFeetStack,
        // rightHandItemState, leftHandItemState — hepsi burada set edilir.
        // Bu olmadan armor + main hand item render edilmiyor.
        BipedEntityRenderer.updateBipedRenderState(entity, state, tickDelta, this.itemModelManager);

        UUID owner = entity.getOwnerUuid().orElse(null);

        // Skin fetch — PlayerListEntry client'ın gördüğü oyuncuların texture'larını cache'liyor.
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

        // Player-specific fields — model'in çalışması için makul default'lar
        state.hatVisible = true;
        state.jacketVisible = true;
        state.leftSleeveVisible = true;
        state.rightSleeveVisible = true;
        state.leftPantsLegVisible = true;
        state.rightPantsLegVisible = true;
        state.capeVisible = false;
    }

    // 1.21.9: SkinTextures.texture() yerine body(), ve artık doğrudan Identifier
    // değil AssetInfo.TextureAsset dönüyor — yolu texturePath() veriyor.
    @Override
    public Identifier getTexture(PlayerEntityRenderState state) {
        if (state.skinTextures == null) return DefaultSkinHelper.getTexture();
        //? if >=1.21.9 {
        /*return state.skinTextures.body().texturePath();
        */
        //?}
        //? if <1.21.9 {
        return state.skinTextures.texture();
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
    public void render(PlayerEntityRenderState state, MatrixStack matrices,
                       OrderedRenderCommandQueue queue, CameraRenderState camera) {
        state.light = 0x00600060;
        super.render(state, matrices, queue, camera);
    }
    */
    //?}
    //? if <1.21.9 {
    @Override
    public void render(PlayerEntityRenderState state, MatrixStack matrices,
                       VertexConsumerProvider provider, int light) {
        super.render(state, matrices, provider, 0x00600060);
    }
    //?}
}
