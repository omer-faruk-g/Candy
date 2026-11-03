package com.photonpulse.candy.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.DebugScreenOverlay;

/**
 * DebugHudMixin (v3.0 Faz 1.1) — F3 spoof.
 *
 * Çıktıyı tara: "candy:mirror" veya "candy:anti_mirror" içeren satırlar
 * "minecraft:overworld" ile değiştirilir. Ayrıca Y koordinatı Mirror'da -250000 gösterilir.
 *
 * Hedef metot sürüme bağlı (her biri o sürümün jar'ında javap ile ölçüldü):
 *   1.21.7   List&lt;String&gt; getGameInformation()
 *   1.21.11  void renderLines(GuiGraphics, List&lt;String&gt;, boolean solTaraf)
 *   26.x     void extractLines(GuiGraphicsExtractor, List&lt;String&gt;, boolean solTaraf, int)
 *
 * DÜZELTME: 1.21.11 dalı daha önce `getLeftText` hedefliyordu — bir `replacements`
 * kuralı `getGameInformation`'ı o ada çeviriyordu. 1.21.11 jar'ında <b>öyle bir metot
 * yok</b>; `getGameInformation` da 1.21.9'da kalkmış. `defaultRequire = 0` olduğu için
 * enjeksiyon <b>sessizce</b> ölüyordu: jar derleniyor, log tertemiz, F3 spoof çalışmıyor.
 * Kalan tek şekil `renderLines` ve 1.21.11 dalı artık onu hedefliyor.
 *
 * 1.21.9'dan itibaren F3 satırlarını üreten tek bir metot yok: satırlar başka yerde
 * doluyor, 1.21.11'de `renderLines` ve 26.x'te `extractLines` onları SADECE çiziyor.
 * Bu yüzden iki dal da HEAD'e giriyor ve listeyi çizimden önce YERİNDE düzeltiyor
 * (TAIL işe yaramazdı, çizim bitmiş olurdu). Liste değiştirilemez olsa bile sorun yok:
 * düzeltme {@code try/catch(Throwable)} içinde ve değişiklik <b>fikir-güçsüz</b>
 * (idempotent), aynı satıra iki kez uygulanması aynı sonucu veriyor.
 *
 * Spoof davranışı üç sürümde de aynı; 1.21.9+ sağ kolondan da geçiyor, ama orada ne
 * boyut adı ne XYZ satırı bulunduğu için bir etkisi olmuyor.
 */
@Mixin(DebugScreenOverlay.class)
public class DebugHudMixin {

    // Hedefin parametreleri surumle degisiyor, geri cagri imzasi da aynen degismek
    // zorunda (Mixin aksi halde uygulama aninda reddediyor):
    //   26.2  extractLines(GuiGraphicsExtractor, List<String>, boolean)
    //   26.3  extractLines(GuiGraphicsExtractor, List<String>, boolean, int)
    //? if >=26.3 {
    /*@Inject(method = "extractLines", at = @At("HEAD"))
    private void candy$f3Spoof(GuiGraphics cikarici, List<String> satirlar,
                               boolean solTaraf, int genislik, CallbackInfo ci) {
        candy$f3Duzelt(satirlar);
    }
    */
    //?}
    //? if >=26 && <26.3 {
    /*@Inject(method = "extractLines", at = @At("HEAD"))
    private void candy$f3Spoof(GuiGraphics cikarici, List<String> satirlar,
                               boolean solTaraf, CallbackInfo ci) {
        candy$f3Duzelt(satirlar);
    }
    */
    //?}
    //? if >=1.21.9 && <26 {
    /*@Inject(method = "renderLines", at = @At("HEAD"))
    private void candy$f3Spoof(GuiGraphics grafik, List<String> satirlar,
                               boolean solTaraf, CallbackInfo ci) {
        candy$f3Duzelt(satirlar);
    }
    */
    //?}
    // Spoof'un kendisi -- 1.21.11 ve iki 26.x kabugu da buraya giriyor, liste
    // YERINDE duzeltiliyor.
    // (Yorum directive blogunun DISINDA: Stonecutter dali aktif ederken blok icindeki
    //  satirlarin `//` onekini de siliyor, icerde kalsa gecersiz Java uretirdi.)
    //? if >=1.21.9 {
    /*private void candy$f3Duzelt(List<String> satirlar) {
        try {
            if (satirlar == null || satirlar.isEmpty()) return;

            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.level == null) return;

            String dimId = mc.level.dimension().location().toString();
            boolean mirror = dimId.equals("candy:mirror");
            boolean antiMirror = dimId.equals("candy:anti_mirror");
            boolean ayna = dimId.equals("candy:ayna");
            // Ayna KUSURSUZ kilik: F3 overworld der, Y hilesi YOK (Mirror'dan farki bu)
            if (!mirror && !antiMirror && !ayna) return;

            for (int i = 0; i < satirlar.size(); i++) {
                String satir = satirlar.get(i);
                if (satir == null) continue;

                String s = satir;
                s = s.replace("candy:mirror", "minecraft:overworld");
                s = s.replace("candy:anti_mirror", "minecraft:overworld");
                s = s.replace("candy:ayna", "minecraft:overworld");

                // Y koordinatini -250000 goster (XYZ: satiri)
                if (mirror && s.startsWith("XYZ:")) {
                    try {
                        // Format: "XYZ: %.3f / %.5f / %.3f" -- Y'yi bul ve degistir
                        String[] parts = s.split(" / ");
                        if (parts.length == 3) {
                            s = parts[0] + " / -250000.00000 / " + parts[2];
                        }
                    } catch (Throwable ignored) {}
                }

                if (!s.equals(satir)) satirlar.set(i, s);
            }
        } catch (Throwable ignored) {}
    }
    */
    //?}
    //? if <1.21.9 {
    @Inject(method = "getGameInformation", at = @At("RETURN"), cancellable = true)
    private void candy$f3Spoof(CallbackInfoReturnable<List<String>> cir) {
        try {
            List<String> original = cir.getReturnValue();
            if (original == null || original.isEmpty()) return;

            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.level == null) return;

            String dimId = mc.level.dimension().location().toString();
            boolean mirror = dimId.equals("candy:mirror");
            boolean antiMirror = dimId.equals("candy:anti_mirror");
            boolean ayna = dimId.equals("candy:ayna");
            // Ayna KUSURSUZ kılık: F3 overworld der, Y hilesi YOK (Mirror'dan farkı bu)
            if (!mirror && !antiMirror && !ayna) return;

            List<String> yeni = new ArrayList<>(original.size());
            for (String satir : original) {
                if (satir == null) { yeni.add(satir); continue; }

                String s = satir;
                s = s.replace("candy:mirror", "minecraft:overworld");
                s = s.replace("candy:anti_mirror", "minecraft:overworld");
                s = s.replace("candy:ayna", "minecraft:overworld");

                // Y koordinatını -250000 göster (XYZ: satırı)
                if (mirror && s.startsWith("XYZ:")) {
                    try {
                        // Format: "XYZ: %.3f / %.5f / %.3f" — Y'yi bul ve değiştir
                        String[] parts = s.split(" / ");
                        if (parts.length == 3) {
                            s = parts[0] + " / -250000.00000 / " + parts[2];
                        }
                    } catch (Throwable ignored) {}
                }

                yeni.add(s);
            }
            cir.setReturnValue(yeni);
        } catch (Throwable ignored) {}
    }
    //?}
}
