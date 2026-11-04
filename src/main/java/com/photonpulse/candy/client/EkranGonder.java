package com.photonpulse.candy.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import com.photonpulse.candy.Candy;
import com.photonpulse.candy.net.EkranPayload;
//? if fabric {
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
//?}
import com.photonpulse.candy.platform.AgKayit;
import com.photonpulse.candy.platform.IstemciAg;
import com.photonpulse.candy.platform.IstemciTick;
import com.photonpulse.candy.yuz.HaritaPaleti;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import java.util.Locale;

/**
 * EkranGonder (v9.1.0, istemci) — <b>F12</b>: o anki kare bir haritaya basılır.
 *
 * <p>Oyuncu tuşa basar, oyun karesi 128×128'e indirilir, harita paletine çevrilir ve
 * sunucuya 16 KB bayt olarak gider. Sunucu bunu kilitli bir haritaya basıp envantere
 * koyar ({@link com.photonpulse.candy.yuz.EkranHaritasi}) — elinde o anın fotoğrafı olur.
 * {@link com.photonpulse.candy.yuz.YuzHaritasi} ile aynı aile: Candy bir şeyi görüntüye
 * çeviriyor; orada senin yüzün, burada senin bakışın.</p>
 *
 * <h3>DİSKE HİÇBİR ŞEY YAZILMAZ</h3>
 * Vanilla F2 kareyi {@code screenshots/} klasörüne PNG olarak yazar. Burada öyle bir şey
 * yok: {@link Screenshot#takeScreenshot} yalnızca kareyi RAM'e okur, bayta çevirip paketi
 * yolluyoruz, {@link NativeImage} hemen kapatılıyor. Yani mod oyuncunun diskine bir dosya
 * bırakmıyor — harita verisi dünya kaydının içine gider, vanilla haritalar gibi.
 *
 * <h3>SADECE OYUNUN KENDİ KARESİ</h3>
 * Okunan şey Minecraft'ın kendi kare arabelleği. Masaüstü, başka pencere, tarayıcı,
 * alt-tab'daki uygulama — hiçbiri bu arabellekte yok, dolayısıyla hiçbiri okunamaz.
 *
 * <h3>TUŞ NEDEN YOKLAMAYLA OKUNUYOR</h3>
 * Vanilla'nın {@code KeyMapping} kaydı iki loader'da ayrı API, üstüne 1.21.9'da
 * yapıcısının imzası değişti ({@code String} kategori → {@code KeyMapping.Category}).
 * Yoklama dört düğümde de aynı kodu yazıyor; sürüme bağlı tek satır pencere tutamağı.
 * Bedeli: tick başına bir {@code glfwGetKey} çağrısı.
 */
//? if fabric {
@Environment(EnvType.CLIENT)
//?}
public final class EkranGonder {

    private EkranGonder() {}

    /** İki çekim arası en az bu kadar — tek basışın çift tetiklenmesini de keser. */
    private static final long BEKLEME_MS = 3_000L;

    private static boolean basiliydi = false;
    private static long sonCekimMs = 0L;
    /** GPU okuma geri çağrısı render iş parçacığından gelir — görünürlük garanti olsun. */
    private static volatile boolean cekimDeHazir = true;

    /** Çözülmüş tuş kodu — config'den bir kez okunur. */
    private static int tusKodu = -1;

    public static void register() {
        try {
            AgKayit.playC2S().register(EkranPayload.ID, EkranPayload.CODEC);
        } catch (Throwable t) {
            Candy.LOG.debug("[Candy] Ekran C2S zaten kayıtlı (istemci): {}", t.getMessage());
        }

        HaritaPaleti.hazirla();
        tusKodu = tusCoz(CandyMixinConfig.ekranHaritasiTus);
        IstemciTick.END_CLIENT_TICK.register(EkranGonder::istemciTick);

        Candy.LOG.info("[Candy] EkranGonder aktif — tus={} ({})",
            CandyMixinConfig.ekranHaritasiTus,
            CandyMixinConfig.ekranHaritasi ? "açık" : "KAPALI");
    }

    /** Config'deki tuş adını ("f12", "k", "insert") GLFW koduna çevir. */
    private static int tusCoz(String ad) {
        try {
            if (ad != null && !ad.isBlank()) {
                String tam = ad.trim().toLowerCase(Locale.ROOT);
                if (!tam.startsWith("key.")) tam = "key.keyboard." + tam;
                InputConstants.Key tus = InputConstants.getKey(tam);
                if (tus != null && tus != InputConstants.UNKNOWN
                    && tus.getType() == InputConstants.Type.KEYSYM) {
                    return tus.getValue();
                }
                Candy.LOG.warn("[Candy] ekranHaritasiTus=\"{}\" tanınmadı — F12'ye düşüldü", ad);
            }
        } catch (Throwable ignored) {}
        return InputConstants.KEY_F12;
    }

    private static void istemciTick(Minecraft mc) {
        try {
            if (!CandyMixinConfig.ekranHaritasi) return;
            if (mc == null || mc.player == null || mc.level == null) {
                basiliydi = false;
                return;
            }

            boolean basili = tusBasili(mc, tusKodu);
            boolean yeniBasim = basili && !basiliydi;
            basiliydi = basili;
            if (!yeniBasim) return;

            long simdi = System.currentTimeMillis();
            // GPU okuma geri çağrısı hiç gelmezse kapı sonsuza kadar kapalı kalırdı;
            // 10 sn sonra açılıyor, en kötü durum bir kayıp çekim oluyor.
            if (!cekimDeHazir && simdi - sonCekimMs > 10_000L) cekimDeHazir = true;
            if (!cekimDeHazir || simdi - sonCekimMs < BEKLEME_MS) return;
            if (!IstemciAg.canSend(EkranPayload.ID)) return;

            sonCekimMs = simdi;
            cek(mc);
        } catch (Throwable t) {
            basiliydi = false;
            cekimDeHazir = true;
        }
    }

    /**
     * Tuş durumu. 1.21.9'da {@code Window.getWindow()} → {@code Window.handle()} oldu,
     * {@code isKeyDown} da tutamak yerine pencereyi alıyor. Sürüme bağlı tek yer burası.
     */
    private static boolean tusBasili(Minecraft mc, int tus) {
        //? if >=1.21.9 {
        /*return InputConstants.isKeyDown(mc.getWindow(), tus);
        */
        //?}
        //? if <1.21.9 {
        return InputConstants.isKeyDown(mc.getWindow().getWindow(), tus);
        //?}
    }

    /**
     * Kareyi al. {@code takeScreenshot} GPU'dan okuma yapıyor ve sonucu geri çağrı ile
     * veriyor (okuma bir sonraki kareye sarkabilir), o yüzden {@link #cekimDeHazir}
     * kapısı var: geri çağrı gelmeden ikinci okuma başlatılmıyor.
     */
    private static void cek(Minecraft mc) {
        cekimDeHazir = false;
        try {
            Screenshot.takeScreenshot(mc.getMainRenderTarget(), kare -> {
                byte[] renkler = null;
                try {
                    renkler = kucult(kare);
                } catch (Throwable t) {
                    Candy.LOG.debug("[Candy] Ekran karesi çevrilemedi: {}", t.getMessage());
                } finally {
                    try {
                        kare.close();
                    } catch (Throwable ignored) {}
                    cekimDeHazir = true;
                }
                if (renkler == null) return;
                final byte[] son = renkler;
                mc.execute(() -> gonder(son));
            });
        } catch (Throwable t) {
            cekimDeHazir = true;
            Candy.LOG.debug("[Candy] Ekran karesi alınamadı: {}", t.getMessage());
        }
    }

    private static void gonder(byte[] renkler) {
        try {
            if (!IstemciAg.canSend(EkranPayload.ID)) return;
            IstemciAg.send(new EkranPayload(renkler));
            Candy.LOG.info("[Candy] Ekran karesi gonderildi (128x128)");
        } catch (Throwable ignored) {}
    }

    /**
     * Kareyi 128×128 harita baytlarına indir.
     *
     * <p>Kare 16:9, harita kare. Kenardan kırpmak yerine <b>sığdırılıyor</b>: tüm kare
     * görünür, üstte ve altta koyu bant kalır. Gerekçe: "ekranında ne vardı" korkusunda
     * kenarı kesmek tam o anlamlı şeyi kesebilir.</p>
     *
     * <p>Çıktı pikseli başına en çok 3×3 kaynak örneği ortalanıyor (nokta örneklemesi
     * 1/15 küçültmede kumlu çıkıyor, tam ortalama ise 2 milyon piksel okumak demek).</p>
     */
    private static byte[] kucult(NativeImage kare) {
        byte[] cikti = new byte[EkranPayload.BOYUT];
        java.util.Arrays.fill(cikti, HaritaPaleti.koyu());

        int w = kare.getWidth(), h = kare.getHeight();
        if (w <= 0 || h <= 0) return cikti;

        // Sığdır: uzun kenar 128'e iner
        int uzun = Math.max(w, h);
        int hedefW = Math.max(1, Math.min(128, w * 128 / uzun));
        int hedefH = Math.max(1, Math.min(128, h * 128 / uzun));
        int bx = (128 - hedefW) / 2, by = (128 - hedefH) / 2;

        int ornek = Math.max(1, Math.min(3, Math.min(w / hedefW, h / hedefH)));

        for (int my = 0; my < hedefH; my++) {
            int y0 = (int) ((long) my * h / hedefH);
            int y1 = (int) ((long) (my + 1) * h / hedefH);
            if (y1 <= y0) y1 = y0 + 1;

            for (int mx = 0; mx < hedefW; mx++) {
                int x0 = (int) ((long) mx * w / hedefW);
                int x1 = (int) ((long) (mx + 1) * w / hedefW);
                if (x1 <= x0) x1 = x0 + 1;

                int r = 0, g = 0, b = 0, n = 0;
                for (int sy = 0; sy < ornek; sy++) {
                    int py = y0 + (y1 - y0) * sy / ornek;
                    if (py >= h) break;
                    for (int sx = 0; sx < ornek; sx++) {
                        int px = x0 + (x1 - x0) * sx / ornek;
                        if (px >= w) break;
                        int argb = kare.getPixel(px, py);   // 0xAARRGGBB
                        r += (argb >> 16) & 0xFF;
                        g += (argb >> 8) & 0xFF;
                        b += argb & 0xFF;
                        n++;
                    }
                }
                if (n == 0) continue;
                cikti[(by + my) * 128 + (bx + mx)] =
                    HaritaPaleti.bayt(((r / n) << 16) | ((g / n) << 8) | (b / n));
            }
        }
        return cikti;
    }
}
