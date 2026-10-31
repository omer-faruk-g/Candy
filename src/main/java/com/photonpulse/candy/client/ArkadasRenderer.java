package com.photonpulse.candy.client;

import com.photonpulse.candy.arkadas.ArkadasEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.util.Identifier;

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
@Environment(EnvType.CLIENT)
public class ArkadasRenderer extends MobEntityRenderer<
        ArkadasEntity, PlayerEntityRenderState, PlayerEntityModel> {

    private static final Identifier DOKU =
        Identifier.of("candy", "textures/entity/arkadas.png");

    private final ItemModelManager itemModelManager;

    public ArkadasRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new PlayerEntityModel(ctx.getPart(EntityModelLayers.PLAYER), false), 0.5f);
        this.itemModelManager = ctx.getItemModelManager();
        // Elindeki alet görünsün — kazarken kazması, dövüşürken kılıcı. Zırh katmanı
        // KASITLI eklenmedi: Arkadaş'ın envanteri yok, zırh hep boş çizilirdi.
        this.addFeature(new HeldItemFeatureRenderer<>(this));
    }

    @Override
    public PlayerEntityRenderState createRenderState() {
        return new PlayerEntityRenderState();
    }

    @Override
    public void updateRenderState(ArkadasEntity entity, PlayerEntityRenderState state,
                                  float tickDelta) {
        super.updateRenderState(entity, state, tickDelta);
        // Biped alanları (eldeki eşya dâhil) MobEntityRenderer tarafından doldurulmuyor.
        BipedEntityRenderer.updateBipedRenderState(entity, state, tickDelta,
            this.itemModelManager);

        state.skinTextures = DefaultSkinHelper.getSteve();
        state.hatVisible = true;
        state.jacketVisible = true;
        state.leftSleeveVisible = true;
        state.rightSleeveVisible = true;
        state.leftPantsLegVisible = true;
        state.rightPantsLegVisible = true;
        state.capeVisible = false;
    }

    @Override
    public Identifier getTexture(PlayerEntityRenderState state) {
        return DOKU;
    }
}
