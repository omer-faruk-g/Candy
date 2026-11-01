package com.photonpulse.candy.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.photonpulse.candy.Candy;
import com.photonpulse.candy.mixin.client.SpriteContentsAccessor;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;

/**
 * MeYuz — "ME" dünyasının kimliği: <b>her blok senin yüzün olur</b>.
 *
 * <h3>NASIL</h3>
 * ME dünyası tamamen {@code candy:me} bloğundan örülür. Bu blok, gemide bir "yüz" dokusuyla
 * gelir (solgun ten + koyu gözler + ağız) — yani istemci hiçbir şey yapmasa bile ME zaten
 * <i>yüzlerle dolu</i> bir dünyadır. Bu sınıf bunu bir adım öteye taşır: oyuncu ME'ye
 * girdiğinde, KENDİ skin'inin yüz bölgesini bloğun atlas sprite'ının üstüne yazar. Böylece
 * her yöne baktığında kendi yüzünü görür.
 *
 * <h3>SAĞLAMLIK</h3>
 * Skin çoğu zaman GPU'ya yüklenmiş bir doku olduğu için piksellerini CPU'dan okumak her
 * zaman mümkün değildir. Skin okunamazsa hiçbir şey yapılmaz — gemideki solgun yüz dokusu
 * kalır. Yani en kötü durum "genel bir yüz", en iyi durum "senin yüzün". Her şey try/catch
 * içinde: bir hata olsa bile oyun akmaz, ME yine yüzlerle dolu görünür.
 */
@Environment(EnvType.CLIENT)
public final class MeYuz {

    /** v8: Identifier olarak tutulur — toString() her istemci tick'inde String tahsisiydi. */
    private static final ResourceLocation ME_DUNYA = ResourceLocation.fromNamespaceAndPath("candy", "me");
    private static final ResourceLocation SPRITE = ResourceLocation.fromNamespaceAndPath(Candy.MOD_ID, "block/me");

    private static boolean icerideydi = false;

    private MeYuz() {}

    /** CandyClient her istemci tick'inde çağırır. */
    public static void istemciTick(Minecraft client) {
        if (client == null || client.level == null || client.player == null) {
            icerideydi = false;
            return;
        }
        boolean meDe;
        try {
            meDe = ME_DUNYA.equals(client.level.dimension().location());
        } catch (Throwable t) { return; }

        if (meDe && !icerideydi) {
            // ME'ye YENİ girildi → yüzü uygula (skin bu ana kadar yüklenmiş olabilir)
            try { uygula(client); }
            catch (Throwable t) { Candy.LOG.debug("[Candy] MeYuz uygulanamadı: {}", t.getMessage()); }
        }
        icerideydi = meDe;
    }

    private static void uygula(Minecraft client) {
        // 1.21.9'da atlas GPU yolu yeniden yazıldı: SpriteContents.upload artık sprite'ı
        // hedef dokunun (0,0) köşesine yazıyor, atlas içindeki x/y ofsetini uygulamıyor
        // (Sprite.upload de ofsetsiz delege ediyor — bytecode'dan doğrulandı). Bu numara
        // tam o varsayıma dayanıyordu. Doğrulanmamış bir blit paylaşılan blok atlasını
        // bozacağı için özellik bu sürümlerde KAPALI; aşağıdaki gövde derlenir, çalışmaz.
        //? if >=1.21.9 {
        /*Candy.LOG.info("[Candy] ME yuz boyama 1.21.9+ surumlerinde devre disi (atlas yolu degisti)");
        if (true) return;
        */
        //?}
        //? if >=1.21.9 {
        /*TextureAtlas atlas = client.getAtlasManager()
            .getAtlasOrThrow(TextureAtlas.LOCATION_BLOCKS);
        */
        //?}
        //? if <1.21.9 {
        TextureAtlas atlas = client.getModelManager()
            .getAtlas(TextureAtlas.LOCATION_BLOCKS);
        //?}
        if (atlas == null) return;
        TextureAtlasSprite sprite = atlas.getSprite(SPRITE);
        if (sprite == null) return;
        SpriteContents contents = sprite.contents();
        if (contents == null) return;

        NativeImage[] mips = ((SpriteContentsAccessor) (Object) contents).candy$mipmaps();
        if (mips == null || mips.length == 0 || mips[0] == null) return;

        NativeImage skin = skinImaji(client);
        if (skin == null || skin.getWidth() < 64 || skin.getHeight() < 64) {
            return;   // skin okunamadı → gemideki solgun yüz kalsın
        }

        // Skin'i bir kez ARGB dizisine oku (her mip için tekrar okumayalım)
        final int sw = skin.getWidth(), sh = skin.getHeight();
        int[] skinArgb = new int[sw * sh];
        for (int y = 0; y < sh; y++)
            for (int x = 0; x < sw; x++)
                skinArgb[y * sw + x] = skin.getPixel(x, y);
        // Her mip seviyesine yüzü ölçekleyerek yaz (uzaktan da yüz görünsün)
        for (NativeImage mip : mips) {
            if (mip != null) yuzYaz(mip, skinArgb, sw);
        }
        //? if >=1.21.9 {
        /*sprite.uploadFirstFrame(atlas.getTexture(), 0);   // erişilmez — yukarıda return var
        */
        //?}
        //? if <1.21.9 {
        contents.uploadFirstFrame(sprite.getX(), sprite.getY(), atlas.getTexture());
        //?}
        Candy.LOG.info("[Candy] ME: bloklar oyuncunun yüzüne boyandı");
    }

    /** Skin GPU dokusundan CPU'da okunabilen NativeImage (yoksa null). */
    private static NativeImage skinImaji(Minecraft client) {
        try {
            //? if >=1.21.9 {
            /*ResourceLocation skinId = client.player.getSkin().body().texturePath();
            */
            //?}
            //? if <1.21.9 {
            ResourceLocation skinId = client.player.getSkin().texture();
            //?}
            if (skinId == null) return null;
            AbstractTexture tex = client.getTextureManager().getTexture(skinId);
            if (tex instanceof DynamicTexture nb) return nb.getPixels();
        } catch (Throwable ignored) {}
        return null;
    }

    /** Skin'in yüz bölgesini hedef imaja ölçekleyerek yazar (matematik {@link MeYuzHesap}'ta). */
    private static void yuzYaz(NativeImage hedef, int[] skinArgb, int skinW) {
        final int w = hedef.getWidth(), h = hedef.getHeight();
        if (w <= 0 || h <= 0) return;
        int[] yuz = MeYuzHesap.yuzOlustur(skinArgb, skinW, w, h);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                hedef.setPixel(x, y, yuz[y * w + x]);
    }
}
