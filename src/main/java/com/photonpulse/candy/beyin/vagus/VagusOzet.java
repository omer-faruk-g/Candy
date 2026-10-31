package com.photonpulse.candy.beyin.vagus;

import com.photonpulse.candy.beyin.Beyin;
import com.photonpulse.candy.beyin.Kurul;
import com.photonpulse.candy.core.TruePersonality;

import java.util.Locale;

/**
 * VagusOzet — oyuncu durumunu ~300 token'lık <b>agrege</b> metne çevirir.
 *
 * <h3>NEDEN AGREGE, NEDEN HAM OLAY LİSTESİ DEĞİL</h3>
 * Ölçüldü: agrege özet 292 token → <b>2,19 s</b>; ham 60 olay 1859 token → <b>14,5 s</b>
 * (tek başına prompt değerlendirme 6,0 s). 6,6× fark ve maliyetin tamamı prompt tarafında.
 * Tick'i bloklamıyor olsak bile 45 s aralıkta 14,5 s'lik çağrı, modeli sürekli meşgul
 * tutar ve hız limitini anlamsızlaştırır.
 *
 * <h3>GİZLİLİK</h3>
 * Yalnızca sayısal/kategorik oyun telemetrisi girer. Oyuncu adı, UUID, e-posta, dosya
 * yolu, makine bilgisi <b>girmez</b>; hedef zaten yalnız loopback.
 */
public final class VagusOzet {

    private VagusOzet() {}

    /** Eylem adları — {@code ZihinselProva.Eylem} sırasıyla birebir aynı (ordinal eşleşir). */
    public static final String[] EYLEM_ADLARI = {
        "klon-arka", "karanlik", "ses", "sessizlik", "soru", "yakin-klon"
    };

    /**
     * Ölçülmüş eylem geçmişi — Vagus'un kendi hafızası.
     *
     * {@code Kurul.sonucOlcum} her geri bildirimde buraya da yazar, böylece özet
     * "hangi eylem bu oyuncuda ne getirdi" sorusunu <b>ölçümle</b> yanıtlar; modelin
     * uydurmasına bırakılmaz. Ring buffer sabit boyutlu — tahsis yok.
     */
    public static final class Gecmis {
        private static final int SON_N = 10;
        private final int[] deneme = new int[EYLEM_ADLARI.length];
        private final double[] sonucToplam = new double[EYLEM_ADLARI.length];
        private final double[] son = new double[SON_N];
        private int imlec = 0;
        private int sonSayi = 0;

        public void kaydet(int eylem, double odulNorm) {
            if (!Double.isFinite(odulNorm)) return;
            double o = Math.max(0.0, Math.min(1.0, odulNorm));
            if (eylem >= 0 && eylem < deneme.length) {
                deneme[eylem]++;
                sonucToplam[eylem] += o;
            }
            son[imlec] = o;
            imlec = (imlec + 1) % SON_N;
            if (sonSayi < SON_N) sonSayi++;
        }

        double sonOrtalama() {
            if (sonSayi == 0) return 0.25;
            double t = 0.0;
            for (int i = 0; i < sonSayi; i++) t += son[i];
            return t / sonSayi;
        }

        int toplamDeneme() {
            int t = 0;
            for (int d : deneme) t += d;
            return t;
        }
    }

    /** Model için sistem talimatı — çıktı şeması ve ölçek burada tanımlanır. */
    public static final String SISTEM = """
        Bir oyuncuyu izleyen sessiz bir gozlemcisin. Gorevin tek: oyuncunun davranis
        telemetrisine bakip, onu korkutmak icin secilecek bir sonraki eylemin ne kadar
        ise yarayacagini TAHMIN etmek.
        tahmin olcegi (oyunun kendi olcumu, 0..1):
          0.00 = korku 5 puan veya daha fazla DUSER
          0.25 = korkuda degisim yok
          0.50 = korku ~5 puan artar
          0.75 = korku ~10 puan artar
          1.00 = korku 15 puan veya daha fazla artar
        Kurallar: sadece JSON don, Turkce yaz.
        hipotez = bu oyuncunun NEDEN oyle davrandigina dair tek cumlelik aciklama, en fazla
        200 karakter; olcek metnini, tahmin sayisini veya eylem adini tekrar etme.
        surukleyen = en belirleyici tek etkenin kisa etiketi (ornek: "sessizlik doygunlugu").
        Olculen eylem gecmisini CIDDIYE al: ortalamasi dusuk eylemler bu oyuncuda tutmuyor.
        Varsayim uretme, veride olmayan sey soyleme.""";

    /**
     * Özeti kurar. <b>Ana thread'de</b> çağrılmalı — oyun durumunu okur.
     *
     * @param onceki Vagus'un bir önceki sezgisi (süreklilik için), yoksa null
     */
    public static String uret(TruePersonality tp, Kurul kurul, Gecmis g,
                              VagusSezgi onceki, long simdi) {
        StringBuilder s = new StringBuilder(900);
        s.append("GOZLEM (tek oyuncu, anonim)\n");

        long oturumDk = Math.max(0, (simdi - tp.oturumBaslangicTick) / 1200);
        s.append(String.format(Locale.ROOT, "oturum_dk=%d toplam_gozlem_dk=%d%n",
            oturumDk, Math.max(0, tp.toplamGozlemTick / 1200)));

        double tepkiToplam = tp.kacOrani + tp.saldirOrani + tp.donOrani + tp.kesfetOrani;
        if (tepkiToplam <= 0.0001) tepkiToplam = 1.0;
        s.append(String.format(Locale.ROOT,
            "tepki: kac=%.0f%% saldir=%.0f%% don=%.0f%% kesfet=%.0f%%%n",
            100 * tp.kacOrani / tepkiToplam, 100 * tp.saldirOrani / tepkiToplam,
            100 * tp.donOrani / tepkiToplam, 100 * tp.kesfetOrani / tepkiToplam));

        s.append(String.format(Locale.ROOT,
            "candy_tepkisi: saldiri=%.2f kacma=%.2f donma=%.2f gormezden=%.2f%n",
            tp.candyyeSaldiri, tp.candydenKacma, tp.candydeDonma, tp.candyGormezden));

        s.append(String.format(Locale.ROOT,
            "korku=%.1f ofke=%.2f ihmal=%d cozulme=%.1f tilt=%.2f%n",
            tp.korkuSkoru, tp.candyOfke, tp.ihmalSayisi, tp.cozulme, tp.tiltSeviyesi));

        s.append(String.format(Locale.ROOT,
            "ongorulebilirlik=%.0f yetenek=%.0f risk_istahi=%.0f ogrenme=%.0f konfor=%.2f%n",
            tp.ongorulebilirlik, tp.yetenekSkoru, tp.riskIstahi, tp.ogrenmeHizi,
            tp.konforSkoru));

        s.append(String.format(Locale.ROOT,
            "egilim: panik=%.2f risk_tol=%.2f kesfetme=%.2f inkar=%.2f aciklik=%.2f%n",
            tp.panikEsigi, tp.riskToleransi, tp.kesfetmeIstegi, tp.inkarEgilimi,
            tp.aciklik));

        s.append(String.format(Locale.ROOT,
            "olum=%d klon_oldurdu=%d mesale_kirdi=%d hastalik=%s%n",
            tp.toplamOlum, tp.oldurulenKlonSayisi, tp.kirilanMesaleSayisi,
            (tp.aktifHastalik == null || tp.aktifHastalik.isBlank()) ? "yok" : tp.aktifHastalik));

        if (kurul != null) {
            double uyeOrt = 0.0;
            int sayi = 0;
            for (Beyin b : kurul.uyeler()) {
                if (b == null) continue;
                uyeOrt += b.sicil.kalibrasyon();
                sayi++;
            }
            if (sayi > 0) uyeOrt /= sayi;
            s.append(String.format(Locale.ROOT,
                "kurul: uye=%d/%d hakem_kalibrasyon=%.0f%% uye_ort_kalibrasyon=%.0f%%%n",
                sayi, Kurul.UYE_SAYISI, 100 * kurul.hakem.sicil.kalibrasyon(), 100 * uyeOrt));
        }

        s.append(String.format(Locale.ROOT,
            "senin_kalibrasyonun=%.0f%% en_iyin=%.0f%% olculen_tahmin=%d%n",
            100 * tp.vagusKalibrasyon, 100 * tp.vagusEnIyi, g.toplamDeneme()));

        s.append("eylem_gecmisi (deneme, ort_sonuc 0..1):\n");
        for (int i = 0; i < EYLEM_ADLARI.length; i++) {
            if (g.deneme[i] == 0) {
                s.append("  ").append(EYLEM_ADLARI[i]).append(" n=0\n");
            } else {
                s.append(String.format(Locale.ROOT, "  %s n=%d ort=%.2f%n",
                    EYLEM_ADLARI[i], g.deneme[i], g.sonucToplam[i] / g.deneme[i]));
            }
        }
        s.append(String.format(Locale.ROOT, "son_10_ort=%.2f%n", g.sonOrtalama()));

        if (onceki != null && !onceki.hipotez().isBlank()) {
            s.append("onceki_hipotezin: ").append(onceki.hipotez()).append('\n');
            s.append(String.format(Locale.ROOT, "onceki_tahminin=%.2f%n", onceki.tahmin()));
        }
        return s.toString();
    }
}
