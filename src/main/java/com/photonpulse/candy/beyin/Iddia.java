package com.photonpulse.candy.beyin;

/**
 * Iddia — Candy'nin oyuncu hakkında tuttuğu TEK bir bilgi parçası ve ona ne kadar güvendiği.
 *
 * "Bu oyuncu karanlıktan korkar" bir iddiadır: bir anahtarı, bir değeri, bir güvenilirliği
 * ve hangi dünyada öğrenildiği bilgisi vardır. Öz-sorgulama tam olarak bunları test eder;
 * dünyalar arası doğrulama (Aşama 8) ise aynı anahtarın farklı dünyalarda farklı değer
 * almasını ÇELİŞKİ olarak yakalar.
 */
public class Iddia {

    /** Ne hakkında: "korku_kaynagi", "baskin_niyet", "tepki_bicimi", "ongorulebilirlik" ... */
    public String anahtar;
    /** Ne olduğu: "karanlik", "kaziyor", "kacma" ... */
    public String deger;
    /** 0..1 — kaç kez doğrulandı / yanlışlandı. */
    public double guvenilirlik = 0.5;
    /** Hangi dünyada öğrenildi (16 hex dünya kimliği). */
    public String kaynakDunya;
    /** Kaç kez denetlendi. */
    public int denetimSayisi = 0;
    /** Çelişkili gözlem sayısı (aynı anahtar, farklı değer). */
    public int celiskiSayisi = 0;
    public long sonDenetimSupurme = 0L;

    public Iddia() {}

    public Iddia(String anahtar, String deger, String kaynakDunya) {
        this.anahtar = anahtar;
        this.deger = deger;
        this.kaynakDunya = kaynakDunya;
    }

    /** Denetim sonucu: doğrulandı mı yanlışlandı mı? */
    public void denetle(boolean dogrulandi) {
        denetimSayisi++;
        double hedef = dogrulandi ? 1.0 : 0.0;
        guvenilirlik = guvenilirlik + (hedef - guvenilirlik) * 0.25;
        if (!Double.isFinite(guvenilirlik)) guvenilirlik = 0.5;
        guvenilirlik = Math.max(0.0, Math.min(1.0, guvenilirlik));
        if (!dogrulandi) celiskiSayisi++;
    }

    /** Şüpheli iddia: çok denetlendi ama hâlâ güvenilmiyor. */
    public boolean supheliMi() {
        return denetimSayisi >= 3 && guvenilirlik < 0.4;
    }

    public void onar() {
        if (anahtar == null) anahtar = "?";
        if (deger == null) deger = "?";
        if (!Double.isFinite(guvenilirlik)) guvenilirlik = 0.5;
        guvenilirlik = Math.max(0.0, Math.min(1.0, guvenilirlik));
    }
}
