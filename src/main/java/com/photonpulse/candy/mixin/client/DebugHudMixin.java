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
 * Hedef metot sürüme bağlı (javap ile ölçüldü):
 *   1.21.7            List&lt;String&gt; getGameInformation()
 *   1.21.9 – 1.21.11  List&lt;String&gt; getLeftText()   (replacements kuralıyla yeniden adlandırılır)
 *   26.x              void extractLines(GuiGraphicsExtractor, List&lt;String&gt;, boolean solTaraf, int)
 *
 * 26.x'te F3 satırlarını üreten tek bir metot yok: satırlar DebugScreenEntry'lerden
 * gelip extractRenderState içinde iki ArrayList'e doluyor, extractLines ise onları
 * SADECE ÇİZİYOR. Bu yüzden 26.x dalı extractLines'in HEAD'ine giriyor ve listeyi
 * çizimden önce YERİNDE düzeltiyor (TAIL işe yaramazdı, çizim bitmiş olurdu).
 * Spoof davranışı üç sürümde de aynı; 26.x'te sağ kolon da aynı süzgeçten
 * geçiyor, ama orada ne boyut adı ne XYZ satırı bulunduğu için bir etkisi olmuyor.
 */
@Mixin(DebugScreenOverlay.class)
public class DebugHudMixin {

    //? if >=26 {
    /*@Inject(method = "extractLines", at = @At("HEAD"))
    private void candy$f3Spoof(GuiGraphics cikarici, List<String> satirlar,
                               boolean solTaraf, int genislik, CallbackInfo ci) {
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
    //? if <26 {
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
