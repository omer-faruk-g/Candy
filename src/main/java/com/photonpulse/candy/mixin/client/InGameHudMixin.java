package com.photonpulse.candy.mixin.client;

import com.photonpulse.candy.client.CamscreenState;
import com.photonpulse.candy.client.CandyMixinConfig;
import com.photonpulse.candy.client.HastalikState;
import com.photonpulse.candy.client.IntensityState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Random;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * InGameHudMixin — HUD üzerine rastgele "Candy" fısıltısı çizer (v1.5.0-mixin).
 *
 * Intensity > 0.6 iken her tick %0.5 ihtimalle ekranın rastgele köşesinde
 * 15 frame boyunca soluk gri "candy" veya "?" yazısı gösterir.
 * Player'ın gözünün ucundan bir şey geçer hissi.
 *
 * Hedef sınıf ve metot sürüme bağlı (javap ile ölçüldü):
 *   1.21.x  Gui.render(GuiGraphics, DeltaTracker)
 *   26.x    Hud.extractRenderState(GuiGraphicsExtractor, DeltaTracker)
 * 26.x'te Gui bir kabuğa dönüştü (ekran/overlay yönetimi) ve HUD çizimi ayrı bir
 * Hud sınıfına taşındı; Gui'de render diye bir metot kalmadı. Parametre sayısı ve
 * sırası aynı olduğu için 190 satırlık gövde TEK kopya kalıyor — yalnızca iki
 * anotasyon satırı ayrılıyor.
 *
 * `Gui` -> `Hud` bir replacements kuralı OLAMAZ: 'Gui' alt dizgisi 'GuiGraphics'in
 * içinde de geçiyor, kural onu da bozardı.
 */
//? if >=26 {
/*@Mixin(net.minecraft.client.gui.Hud.class)
*/
//?}
//? if <26 {
@Mixin(Gui.class)
//?}
public class InGameHudMixin {

    private static final Random CANDY_RNG = new Random();
    private static final String[] FLASH_METIN = {
        "candy", "?", "burada", "yine", "seni", "gördüm", "aç gözünü"
    };

    private static int candy$flashKalanFrame = 0;
    private static int candy$flashX = 0;
    private static int candy$flashY = 0;
    private static String candy$flashYazi = "";

    //? if >=26 {
    /*@Inject(method = "extractRenderState", at = @At("TAIL"))
    */
    //?}
    //? if <26 {
    @Inject(method = "render", at = @At("TAIL"))
    //?}
    private void candy$flashOverlay(GuiGraphics ctx, DeltaTracker counter, CallbackInfo ci) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.player == null || mc.font == null) return;

            // v1.8.0: Depresyon gri overlay — atmosferi soluk yapar
            if (HastalikState.var() && "depresyon".equals(HastalikState.get())) {
                int w0 = ctx.guiWidth();
                int h0 = ctx.guiHeight();
                ctx.fill(0, 0, w0, h0, 0x50808080); // semi-transparent gray
            }

            // v3.1: Boss Faz 8 countdown — sağ üstte kırmızı geri sayım
            if (com.photonpulse.candy.client.BossCountdownState.aktif()) {
                int sn = com.photonpulse.candy.client.BossCountdownState.getSaniye();
                int w2 = ctx.guiWidth();
                String zaman = String.format("%d:%02d", sn / 60, sn % 60);
                Component ctd = Component.translatable("candy.boss.countdown", zaman);
                // Süre azaldıkça kızar
                ChatFormatting renk = sn <= 30 ? ChatFormatting.DARK_RED
                    : sn <= 60 ? ChatFormatting.RED
                    : sn <= 120 ? ChatFormatting.GOLD : ChatFormatting.YELLOW;
                Component metin = ctd.copy().withStyle(renk, ChatFormatting.BOLD);
                int tw = mc.font.width(metin);
                ctx.drawString(mc.font, metin, w2 - tw - 6, 6, 0xFFFFFFFF, true);
            }

            // v8.2: ALTYAZI KANALI — vanilla altyazı kutusunun yerinde, onun görünümüyle.
            // Oyuncu chat'e güvenmeyi bırakmış olabilir; altyazıya yalnızca oyunun kendi
            // sesleri düşer, oraya bakarken şüphe etmiyor.
            if (com.photonpulse.candy.client.AltyaziState.aktif()) {
                int w3 = ctx.guiWidth();
                int h3 = ctx.guiHeight();
                int idx3 = com.photonpulse.candy.client.AltyaziState.indeks();
                float a3 = com.photonpulse.candy.client.AltyaziState.alfa();
                int op = Math.max(0, Math.min(255, (int) (a3 * 255)));
                Component alt = Component.translatable("candy.altyazi." + idx3);
                int tw3 = mc.font.width(alt);
                // Vanilla altyazı kutusu: sağ kenardan 2px içeri, hotbar'ın üstünde.
                int bx = w3 - tw3 - 6;
                int by = h3 - 40;
                ctx.fill(bx - 2, by - 2, bx + tw3 + 2, by + 10,
                    ((op * 3 / 4) << 24));                       // yarı saydam siyah kutu
                ctx.drawString(mc.font, alt, bx, by, (op << 24) | 0xFFFFFF, false);
            }

            // v8.3: İKİLİK FISILTI — siyah üzerine yalnızca 0 ve 1. Terminal AÇILMADI
            // (kullanıcının kendi kuralı); aynı estetik pencere içinde veriliyor.
            if (com.photonpulse.candy.client.IkilikEkranState.aktif()) {
                int w4 = ctx.guiWidth();
                int h4 = ctx.guiHeight();
                float a4 = com.photonpulse.candy.client.IkilikEkranState.alfa();
                int op4 = Math.max(0, Math.min(255, (int) (a4 * 255)));
                String bit = com.photonpulse.candy.client.IkilikEkranState.metin();
                Component bitT = Component.literal(bit);

                if (com.photonpulse.candy.client.IkilikEkranState.kanal() == 0) {
                    // Tam ekran: siyah zemin + ortada tek satır. Dizi ekrana sığmazsa
                    // 8'li gruplardan bölünür — rastgele bir yerden kesmek "kod" izlenimini
                    // bozar, bayt sınırından kesmek korur.
                    ctx.fill(0, 0, w4, h4, (op4 << 24));
                    int renk4 = (op4 << 24) | 0xC0C0C0;
                    int enGenis = w4 - 16;
                    java.util.List<String> satirlar = new java.util.ArrayList<>();
                    StringBuilder sb = new StringBuilder();
                    for (String bayt : bit.split(" ")) {
                        String deneme = sb.length() == 0 ? bayt : sb + " " + bayt;
                        if (mc.font.width(deneme) > enGenis && sb.length() > 0) {
                            satirlar.add(sb.toString());
                            sb.setLength(0);
                            sb.append(bayt);
                        } else {
                            sb.setLength(0);
                            sb.append(deneme);
                        }
                    }
                    if (sb.length() > 0) satirlar.add(sb.toString());
                    int bas = h4 / 2 - (satirlar.size() * 10) / 2;
                    for (int i = 0; i < satirlar.size(); i++) {
                        Component st = Component.literal(satirlar.get(i));
                        int sw = mc.font.width(st);
                        ctx.drawString(mc.font, st, (w4 - sw) / 2, bas + i * 10,
                            renk4, false);
                    }
                } else {
                    // Köşe: altyazı kalıbı, kutulu.
                    int tw4 = mc.font.width(bitT);
                    int bx4 = w4 - tw4 - 6;
                    int by4 = h4 - 54;
                    ctx.fill(bx4 - 2, by4 - 2, bx4 + tw4 + 2, by4 + 10,
                        ((op4 * 3 / 4) << 24));
                    ctx.drawString(mc.font, bitT, bx4, by4,
                        (op4 << 24) | 0xC0C0C0, false);
                }
            }

            // v1.9.0: Hastalık HUD — sol-alt köşede küçük italik semptom (subtle)
            if (HastalikState.var()) {
                int h0 = ctx.guiHeight();
                Component hastalik = Component.translatable("candy.hastalik.hud." + HastalikState.get())
                    .withStyle(ChatFormatting.ITALIC, ChatFormatting.GRAY);
                ctx.drawString(mc.font, hastalik, 4, h0 - 12, 0xAA888888, false);
            }

            // v1.6.0/1: Camscreen overlay — HUD flash'tan önce, en üstte
            if (CandyMixinConfig.camscreen && CamscreenState.aktif()) {
                int w = ctx.guiWidth();
                int h = ctx.guiHeight();
                int idx = CamscreenState.getTextIndex();

                // v3.0: idx=7 = BlackoutGlitch — 0.1-0.3sn saf siyah, fade YOK (ani glitch,
                // yumuşak camscreen fade'inden bilinçli olarak farklı).
                if (idx == 7) {
                    ctx.fill(0, 0, w, h, 0xFF000000);
                    return;
                }

                float alfa = CamscreenState.getAlfa();
                int alphaByte = Math.max(0, Math.min(255, (int) (alfa * 255)));

                // Siyah arka plan
                int bgRenk = (alphaByte << 24);
                ctx.fill(0, 0, w, h, bgRenk);
                // v1.9.5: idx 0-3 normal text, 6 = ending text ("SEN ARTIK BENSİN")
                //          idx 4 = smiley PNG, idx 5 = neverdie PNG
                if (idx <= 3 || idx == 6) {
                    // TEXT variant
                    String key = "candy.camscreen." + idx;
                    Component metin = Component.translatable(key)
                        .withStyle(ChatFormatting.WHITE, ChatFormatting.ITALIC, ChatFormatting.BOLD);
                    int textW = mc.font.width(metin);
                    int textX = (w - textW) / 2;
                    int textY = h / 3;
                    int textAlpha = (alphaByte << 24) | 0xFFFFFF;
                    ctx.drawString(mc.font, metin, textX, textY, textAlpha, true);
                } else {
                    // IMAGE variant (4=smiley, 5=neverdie, 8=mask, 9=sssss) — full screen scale
                    String dosya = switch (idx) {
                        case 4 -> "camscreen_smiley.png";
                        case 8 -> "camscreen_mask.png";
                        case 9 -> "camscreen_sssss.png";
                        default -> "camscreen_neverdie.png"; // 5
                    };
                    net.minecraft.resources.ResourceLocation tex =
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("candy", "textures/gui/" + dosya);
                    try {
                        ctx.blit(
                            net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
                            tex, 0, 0, 0f, 0f, w, h, w, h);
                    } catch (Throwable t) {
                        // Texture bulunamadıysa fallback: sadece siyah + kısa text
                        String fb = switch (idx) {
                            case 4 -> ":)";
                            case 9 -> "sssss";
                            default -> "Candy is never die";
                        };
                        Component f = Component.literal(fb).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD);
                        int fw = mc.font.width(f);
                        ctx.drawString(mc.font, f, (w - fw) / 2, h / 3,
                            (alphaByte << 24) | 0xFFFFFF, true);
                    }
                }
                return; // camscreen sırasında flash overlay çakışmasın
            }

            if (!CandyMixinConfig.hudFlash) return;

            double intensity = IntensityState.get();
            if (intensity < 0.6) {
                candy$flashKalanFrame = 0;
                return;
            }

            if (candy$flashKalanFrame <= 0 && CANDY_RNG.nextDouble() < 0.005) {
                candy$flashKalanFrame = 15;
                int w = ctx.guiWidth();
                int h = ctx.guiHeight();
                candy$flashX = CANDY_RNG.nextInt(Math.max(1, w - 50));
                candy$flashY = CANDY_RNG.nextInt(Math.max(1, h - 20));
                candy$flashYazi = FLASH_METIN[CANDY_RNG.nextInt(FLASH_METIN.length)];
            }

            if (candy$flashKalanFrame > 0) {
                int alpha = Math.min(255, candy$flashKalanFrame * 15);
                int renk = (alpha << 24) | 0x707070;
                ctx.drawString(mc.font,
                    Component.literal(candy$flashYazi).withStyle(ChatFormatting.ITALIC),
                    candy$flashX, candy$flashY, renk, false);
                candy$flashKalanFrame--;
            }
        } catch (Throwable t) {
            candy$flashKalanFrame = 0;
        }
    }
}
