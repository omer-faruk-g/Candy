package com.photonpulse.candy.beyin;

import com.photonpulse.candy.core.TruePersonality;

import java.util.ArrayList;
import java.util.List;

/**
 * OzModel — Candy'nin KENDİSİ hakkındaki modeli.
 *
 * Kullanıcının isteği: "elde edilen bilgi dahil her şeyi kontrol edecek, sorgulayacak."
 * Burada Candy topladığı bilgiyi olduğu gibi kabul etmez; her iddiayı bir güvenilirlikle
 * tutar, periyodik olarak DENETLER (şu an gözlenen gerçek, iddiayı doğruluyor mu?) ve
 * çelişkileri sayar.
 *
 * TUTARLILIK: iddiaların güvenilirlik ortalaması. Düşükse Candy kendinden emin değildir —
 * bu da (Aşama 5) daha çok keşif, (Aşama 8) daha temkinli genelleme demektir.
 */
public class OzModel {

    private static final int MAX_IDDIA = 24;

    public List<Iddia> iddialar = new ArrayList<>();
    /** 0..1 — iddiaların ortalama güvenilirliği. */
    public double tutarlilik = 0.5;
    /** Toplam çelişki (aynı anahtar, farklı dünyada farklı değer). */
    public int celiskiToplam = 0;
    public long sonDenetimSupurme = 0L;
    /** Kendi doğasını sorgulama düzeyi (IcDusunce'deki candyOzFarkindalik ile beslenir). */
    public double ozSorgu = 0.0;

    public OzModel() {}

    /**
     * Gözlemden iddia üretir/günceller. Aynı anahtar farklı değerle gelirse ÇELİŞKİ sayılır.
     */
    public Iddia iddiaGuncelle(String anahtar, String deger, String dunyaId) {
        if (anahtar == null || deger == null) return null;
        for (Iddia i : iddialar) {
            if (anahtar.equals(i.anahtar)) {
                if (deger.equals(i.deger)) {
                    i.denetle(true);
                } else {
                    // Aynı konuda başka bir şey gözledik — iddia sarsılır
                    i.denetle(false);
                    celiskiToplam++;
                    if (i.guvenilirlik < 0.25) {   // artık savunulamaz, değeri güncelle
                        i.deger = deger;
                        i.kaynakDunya = dunyaId;
                        i.guvenilirlik = 0.45;
                    }
                }
                return i;
            }
        }
        Iddia yeni = new Iddia(anahtar, deger, dunyaId);
        iddialar.add(yeni);
        if (iddialar.size() > MAX_IDDIA) {
            // En zayıf iddiayı unut
            Iddia enZayif = iddialar.get(0);
            for (Iddia i : iddialar) if (i.guvenilirlik < enZayif.guvenilirlik) enZayif = i;
            iddialar.remove(enZayif);
        }
        return yeni;
    }

    /**
     * BİLGİ DENETİMİ: Candy topladığı bilgiyi gözden geçirir.
     *
     * Oyuncunun güncel durumundan "şimdi ne doğru?" çıkarır ve iddiaları buna karşı test
     * eder. Bu, pasif bir hafıza değil, aktif olarak kendini sorgulayan bir bilgi tabanıdır.
     */
    public void denetle(TruePersonality tp, String dunyaId, long supurme) {
        if (tp == null) return;
        sonDenetimSupurme = supurme;

        // Gözlenen gerçekler → iddia güncellemesi
        String korku = tp.enBuyukKorku();
        if (korku != null) iddiaGuncelle("korku_kaynagi", korku, dunyaId);

        String tepki = tp.baskinTepki();
        if (tepki != null) iddiaGuncelle("tepki_bicimi", tepki, dunyaId);

        String hipotez = tp.baskinHipotez();
        if (hipotez != null && !"bilinmiyor".equals(hipotez))
            iddiaGuncelle("kisilik", hipotez, dunyaId);

        String baglanma = tp.enCokBaglanilan();
        if (baglanma != null) iddiaGuncelle("baglanma", baglanma.length() > 12 ? "canli" : baglanma, dunyaId);

        iddiaGuncelle("ongorulebilir", tp.ongorulebilirlik > 60 ? "evet" : "hayir", dunyaId);

        // Tutarlılık = iddiaların güvenilirlik ortalaması
        if (!iddialar.isEmpty()) {
            double t = 0;
            for (Iddia i : iddialar) t += i.guvenilirlik;
            double yeni = t / iddialar.size();
            tutarlilik = Double.isFinite(yeni) ? yeni : tutarlilik;
        }
    }

    /** En şüpheli iddia — Candy'nin "bundan emin değilim" dediği şey. */
    public Iddia enSupheli() {
        Iddia en = null;
        for (Iddia i : iddialar) {
            if (!i.supheliMi()) continue;
            if (en == null || i.guvenilirlik < en.guvenilirlik) en = i;
        }
        return en;
    }

    /** En güvendiği iddia — Teşhis Raporu'nda kullanılır. */
    public Iddia enGuvenilir() {
        Iddia en = null;
        for (Iddia i : iddialar) {
            if (i.denetimSayisi < 2) continue;
            if (en == null || i.guvenilirlik > en.guvenilirlik) en = i;
        }
        return en;
    }

    public void onar() {
        if (iddialar == null) iddialar = new ArrayList<>();
        iddialar.removeIf(i -> i == null);
        for (Iddia i : iddialar) i.onar();
        if (!Double.isFinite(tutarlilik)) tutarlilik = 0.5;
        tutarlilik = Math.max(0.0, Math.min(1.0, tutarlilik));
        if (!Double.isFinite(ozSorgu)) ozSorgu = 0.0;
    }
}
