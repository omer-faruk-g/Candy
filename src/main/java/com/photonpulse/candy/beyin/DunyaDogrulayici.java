package com.photonpulse.candy.beyin;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.core.TruePersonality;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * DunyaDogrulayici — beynin oyuncu modelini YENİ bir dünyada sınar.
 *
 * KÖR DOĞRULAMA MODU: yeni bir dünyaya girildiğinde beyin ilk 5 dakika boyunca
 * neredeyse hiç öğrenmez ({@code plastisiteCarpani = 0.20}) ama tahmin etmeye devam eder.
 * Önce ÖLÇ, sonra ÖĞREN — aksi halde model yeni dünyaya anında uyum sağlar ve "gerçekten
 * kişiyi mi öğrenmiştim?" sorusu asla cevaplanamaz.
 *
 * KARAR:
 * <pre>
 *   Brier ≤ temel + 0.05  → MODEL GENELLEDİ. Öğrendiğim DÜNYA değil, KİŞİ.
 *   Brier &gt; temel + 0.15  → DÜNYAYA BAĞIMLIYMIŞ. Yanıltan özellikler zayıflatılır.
 * </pre>
 * İkinci durumda yanıltan duyu özelliklerinin ARDIL ağırlıkları kalıcı olarak ×0.6
 * yapılır — bu, bedava bir özellik-seviyesi transfer öğrenmesidir.
 */
public class DunyaDogrulayici {

    /**
     * Kor mod suresi (supurme). Supurme v5'te ~10x uzadigi icin 250 -> 25;
     * duvar saati ~5 dakika olarak KORUNUR.
     */
    private static final int KOR_SURE = 25;
    private static final float KOR_PLASTISITE = 0.20f;
    private static final int MIN_TAHMIN = 30;
    private static final double GENELLEME_ESIGI = 0.05;
    private static final double BASARISIZ_ESIGI = 0.15;

    private DunyaDogrulayici() {}

    /** Dünya kimliği: ad + seed karması. Aynı dünya her açılışta aynı kimliği alır. */
    public static String dunyaKimligi(MinecraftServer server) {
        try {
            String ad = server.getSaveProperties().getLevelInfo().getLevelName();
            long seed = server.getOverworld().getSeed();
            long h = (ad == null ? 0 : ad.hashCode()) * 1125899906842597L + seed;
            return String.format("%016x", h);
        } catch (Throwable t) {
            return "0000000000000000";
        }
    }

    /**
     * Oyuncu bir dünyaya girdiğinde çağrılır. Dünya yeniyse kör doğrulama modunu başlatır.
     */
    public static void dunyayaGir(Beyin b, String dunyaId) {
        if (b == null || dunyaId == null) return;
        if (b.dunyalar == null) b.dunyalar = new java.util.ArrayList<>();

        for (Dogrulama d : b.dunyalar) {
            if (dunyaId.equals(d.dunyaId)) {
                b.aktifDunya = d;
                b.dogrulamaBitis = 0L;      // tanıdık dünya — kör mod yok
                b.plastisiteCarpani = 1.0f;
                return;
            }
        }

        // YENİ DÜNYA — kör doğrulama
        Dogrulama d = new Dogrulama(dunyaId, b.toplamSupurme);
        b.dunyalar.add(d);
        while (b.dunyalar.size() > 8) b.dunyalar.remove(0);   // son 8 dünya
        b.aktifDunya = d;
        b.dogrulamaBitis = b.toplamSupurme + KOR_SURE;
        b.plastisiteCarpani = KOR_PLASTISITE;
        Candy.LOG.info("[Candy] YENİ DÜNYA ({}) — kör doğrulama başladı, {} süpürme",
            dunyaId, KOR_SURE);
    }

    /**
     * Her süpürmede tahminleri puanlar; kör mod bitince kararı verir.
     */
    public static void supurme(Beyin b, ServerPlayerEntity player, TruePersonality tp) {
        if (b == null || b.aktifDunya == null || b.oyuncuHaritasi == null) return;
        Dogrulama d = b.aktifDunya;
        d.toplamSupurme++;

        // Tahmin puanla: modelin gerçek niyete verdiği olasılık
        int hedef = OyuncuHaritasi.niyetIndeksi(tp.aktifNiyet);
        if (hedef >= 0) {
            float[] p = OyuncuHaritasi.olasilikTamponu();   // v8: paylasilan tampon
            b.oyuncuHaritasi.tahminEt(b.girdi, p);
            int enIyi = 0;
            for (int c = 1; c < p.length; c++) if (p[c] > p[enIyi]) enIyi = c;
            d.puanla(p[hedef], enIyi == hedef);
        }

        // Kör mod bitti mi?
        if (b.dogrulamaBitis > 0 && b.toplamSupurme >= b.dogrulamaBitis) {
            b.dogrulamaBitis = 0L;
            b.plastisiteCarpani = 1.0f;
            karar(b, d, player, tp);
        }
    }

    /** Kör mod sonucu: model genelledi mi, yoksa dünyaya mı bağımlıymış? */
    private static void karar(Beyin b, Dogrulama d, ServerPlayerEntity player, TruePersonality tp) {
        if (d.tahmin < MIN_TAHMIN || d.karariVerildi) return;
        d.karariVerildi = true;

        double temel = temelBrier(b, d);
        double simdi = d.brier();
        double fark = simdi - temel;

        // KURUL YALITIMI: doğrulama her üye için ayrı ayrı çalışır (her üyenin kendi
        // haritası, kendi Brier'ı var), AMA oyuncuya mesaj ve tp'ye yazma yalnızca
        // gövdeden gelir. Aksi halde tek bir doğrulama 11 kez sızar ve — daha kötüsü —
        // her üyenin iç durumu tp üzerinden diğer 10 üyenin girdisine karışırdı:
        // komitenin bağımsızlığı, dolayısıyla tüm anlamı yok olurdu.
        final boolean govde = (b.uyeNo == 0);
        final ServerPlayerEntity sesli = govde ? player : null;

        if (fark <= GENELLEME_ESIGI) {
            // ── GENELLEDİ: öğrendiğim dünya değil, kişi ──
            d.genellemeSkoru = 1.0 - Math.max(0, fark) * 4.0;
            b.plastisiteCarpani = 1.0f;
            if (b.ozModel != null) {
                b.ozModel.tutarlilik = Math.min(1.0, b.ozModel.tutarlilik + 0.15);
            }
            if (govde) tp.candyOzFarkindalik = Math.min(100, tp.candyOzFarkindalik + 8);
            sizdir(sesli, tp, "candy.dogrulama.tuttu");
            Candy.LOG.info("[Candy] DOĞRULAMA: model GENELLEDİ (brier {} vs temel {})",
                String.format("%.3f", simdi), String.format("%.3f", temel));

        } else if (fark > BASARISIZ_ESIGI) {
            // ── BAŞARISIZ: dünyaya bağımlıymış ──
            d.genellemeSkoru = Math.max(0.0, 1.0 - fark * 3.0);
            b.plastisiteCarpani = 1.4f;      // hızla yeniden öğren
            yaniltanlariZayiflat(b);
            sizdir(sesli, tp, "candy.dogrulama.kayip");
            Candy.LOG.info("[Candy] DOĞRULAMA: model DÜNYAYA BAĞIMLIYMIŞ (brier {} vs temel {})",
                String.format("%.3f", simdi), String.format("%.3f", temel));
        } else {
            d.genellemeSkoru = 0.6;
        }

        // ÇELİŞKİ: aynı iddia farklı dünyalarda farklı çıktıysa
        if (b.ozModel != null && b.ozModel.celiskiToplam > 2) {
            sizdir(player, tp, "candy.celiski.0");
        }
    }

    /** Önceki dünyaların ağırlıklı ortalama Brier'ı (bu dünya hariç). */
    private static double temelBrier(Beyin b, Dogrulama haric) {
        double toplam = 0;
        int adet = 0;
        for (Dogrulama d : b.dunyalar) {
            if (d == haric || d.tahmin < MIN_TAHMIN) continue;
            toplam += d.brier();
            adet++;
        }
        return adet == 0 ? haric.brier() : toplam / adet;
    }

    /**
     * Yanıltan duyu özelliklerini zayıflat — özellik seviyesinde transfer öğrenme.
     * Sınıflayıcıda en büyük ağırlığa sahip özellikler bu dünyada işe yaramadıysa,
     * onlara olan güven kalıcı olarak düşürülür.
     */
    private static void yaniltanlariZayiflat(Beyin b) {
        if (b.oyuncuHaritasi == null) return;
        // En büyük mutlak ağırlıklı 5 özelliği bul
        double[] guc = new double[Beyin.GIRDI_SAYISI];
        for (int c = 0; c < OyuncuHaritasi.NIYET_SAYISI; c++) {
            for (int i = 0; i < Beyin.GIRDI_SAYISI; i++) guc[i] += Math.abs(b.oyuncuHaritasi.w[c][i]);
        }
        for (int k = 0; k < 5; k++) {
            int enIyi = -1;
            double max = 0;
            for (int i = 0; i < Beyin.GIRDI_SAYISI; i++) if (guc[i] > max) { max = guc[i]; enIyi = i; }
            if (enIyi < 0) break;
            guc[enIyi] = 0;
            // Sınıflayıcıda zayıflat
            for (int c = 0; c < OyuncuHaritasi.NIYET_SAYISI; c++) b.oyuncuHaritasi.w[c][enIyi] *= 0.6f;
            // Beyinde bu duyudan gelen sinapsları da zayıflat
            for (int j = 0; j < b.sinapsSayisi; j++) {
                if (b.kaynak[j] == enIyi) b.agirlik[j] *= 0.6f;
            }
        }
    }

    private static void sizdir(ServerPlayerEntity player, TruePersonality tp, String anahtar) {
        if (player == null || tp.bossOldurulmeTick > 0) return;
        player.sendMessage(Text.translatable(anahtar)
            .formatted(Formatting.DARK_GRAY, Formatting.ITALIC), false);
    }
}
