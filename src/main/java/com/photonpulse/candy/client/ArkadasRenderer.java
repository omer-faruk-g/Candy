package com.photonpulse.candy.client;

import com.photonpulse.candy.arkadas.ArkadasEntity;
//? if fabric {
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
//?}
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;

/**
 * ArkadasRenderer (v8.3) — Arkadaş'ı oyuncu modeliyle, <b>tamamen siyah</b> çizer.
 *
 * <h3>NİYE AYRI DOKU, NİYE RENK FİLTRESİ DEĞİL</h3>
 * Modeli siyaha boyamanın iki yolu var: render sırasında renk çarpanı vermek, ya da
 * baştan siyah bir doku kullanmak. Çarpan yolu gölgelendirmeyle etkileşir — ışıkta
 * koyu gri, karanlıkta siyah olur ve "koyu tenli bir oyuncu" gibi görünür. Tam opak
 * siyah doku ise her ışıkta siyah kalıyor: silueti var, yüzü yok. Aranan şey bu.
 *
 * <p>{@code skinTextures} alanı yine dolduruluyor ama yalnızca model çalışsın diye;
 * çizimde kullanılan şey {@link #getTexture} ve o daima bizim dokumuzu dönüyor.</p>
 *
 * <h3>IŞIK</h3>
 * {@link CandyDoppelRenderer} doppel'i kısık ışıkla çiziyor. Arkadaş <b>normal ışıkla</b>
 * çizilir: doku zaten siyah, kısık ışık onu görünmez yapar ve ortada bir şeyin durduğu
 * hiç anlaşılmaz. Burada istenen "göremiyorum" değil, "orada duruyor ve yüzü yok".
 */
//? if fabric {
@Environment(EnvType.CLIENT)
//?}
public class ArkadasRenderer extends MobRenderer<
        ArkadasEntity, PlayerRenderState, PlayerModel> {

    private static final ResourceLocation DOKU =
        ResourceLocation.fromNamespaceAndPath("candy", "textures/entity/arkadas.png");

    private final ItemModelResolver itemModelManager;

    public ArkadasRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
        this.itemModelManager = ctx.getItemModelResolver();
        // Elindeki alet görünsün — kazarken kazması, dövüşürken kılıcı. Zırh katmanı
        // KASITLI eklenmedi: Arkadaş'ın envanteri yok, zırh hep boş çizilirdi.
        this.addLayer(new ItemInHandLayer<>(this));
    }

    @Override
    public PlayerRenderState createRenderState() {
        return new PlayerRenderState();
    }

    @Override
    public void extractRenderState(ArkadasEntity entity, PlayerRenderState state,
                                  float tickDelta) {
        super.extractRenderState(entity, state, tickDelta);
        // Biped alanları (eldeki eşya dâhil) MobEntityRenderer tarafından doldurulmuyor.
        HumanoidMobRenderer.extractHumanoidRenderState(entity, state, tickDelta,
            this.itemModelManager);

        state.skin = DefaultPlayerSkin.getDefaultSkin();
        state.showHat = true;
        state.showJacket = true;
        state.showLeftSleeve = true;
        state.showRightSleeve = true;
        state.showLeftPants = true;
        state.showRightPants = true;
        state.showCape = false;
    }

    @Override
    public ResourceLocation getTextureLocation(PlayerRenderState state) {
        return DOKU;
    }
}
