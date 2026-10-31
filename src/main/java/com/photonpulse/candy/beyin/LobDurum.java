package com.photonpulse.candy.beyin;

/**
 * LobDurum — bir lobun çalışma zamanı durumu (enerji, önem, atrofi).
 *
 * Aşama 3'te yalnızca {@code ortAktiflik} ve {@code cikis} kullanılır; enerji/atrofi/körelme
 * alanları Aşama 5'te (Enerji + ablasyon) devreye girer. Alanlar şimdiden tanımlı ki
 * kalıcılık şeması sonradan değişmesin.
 */
public class LobDurum {

    /** Lob adı (enum name) — JSON okunabilirliği için. */
    public String ad;

    /** Softmax ile dağıtılan enerji payı (0..1, tüm lobların toplamı 1). */
    public double enerjiPayi = 1.0 / Lob.LOBLAR.length;

    /** Anlık talep: ortalama aktiflik + plastisite etkinliği. */
    public double talep = 0.5;

    /** ABLASYON TESTİ ile ÖLÇÜLEN hayati önem (0..1) — tahmin edilmez. */
    public double hayatiOnem = 0.5;

    /** Enerjisiz geçen ardışık süpürme sayısı. */
    public int acKalanSupurme = 0;

    /** Körelme: nöronlar dondu, sinapsları nörogeneze iade edildi. */
    public boolean koreldi = false;

    /** Son süpürmedeki ortalama aktiflik (izleme/hata ayıklama). */
    public double ortAktiflik = 0.0;

    /** Lobun ağaca yazılan çıkış değeri (0..1). */
    public double cikis = 0.5;

    /**
     * Çıkış kazancı. Normalde 1.0; ABLASYON TESTİ sırasında geçici olarak 0.5'e düşürülür
     * ki "bu lobu kıssam ne kaybederim?" ölçülebilsin.
     */
    public transient float cikisKazanci = 1.0f;

    /** TARTI'nin bu lob icin verdigi son oy (-0,8..0,8) — yalnizca izleme/hata ayiklama. */
    public transient double tartiOyu = 0.0;

    // ── UYARLANIR CIKIS NORMALIZASYONU (v5) ──
    // Lob, ham cikisini KENDI gecmis dagilimina gore okur. Boylece cikisin dinamik araligi
    // OLCEKTEN BAGIMSIZ garanti altina alinir: ham cikis 0,498-0,502 arasina sikissa bile
    // agac koku tam 0..1 araligini gorur. Duyusal sistemlerin kazanc kontrolunun aynisi.
    public double cikisOrt = 0.5;
    public double cikisVar = 0.0225;    // std 0,15 baslangic
    public long cikisOrnek = 0L;

    public LobDurum() {}

    public LobDurum(Lob lob) {
        this.ad = lob.name();
    }

    /** Gson ile yüklenen eski/eksik kayıtları güvenli varsayılanlara çeker. */
    public void onar(Lob lob) {
        if (ad == null) ad = lob.name();
        if (!Double.isFinite(enerjiPayi)) enerjiPayi = 1.0 / Lob.LOBLAR.length;
        if (!Double.isFinite(talep)) talep = 0.5;
        if (!Double.isFinite(hayatiOnem)) hayatiOnem = 0.5;
        if (!Double.isFinite(ortAktiflik)) ortAktiflik = 0.0;
        if (!Double.isFinite(cikis)) cikis = 0.5;
        hayatiOnem = Math.max(0.0, Math.min(1.0, hayatiOnem));
        enerjiPayi = Math.max(0.0, Math.min(1.0, enerjiPayi));
        cikisKazanci = 1.0f;   // transient — her yuklemede notr baslar
        if (!Double.isFinite(cikisOrt)) cikisOrt = 0.5;
        if (!Double.isFinite(cikisVar) || cikisVar < 0) cikisVar = 0.0225;
        if (cikisOrnek < 0) cikisOrnek = 0;
    }
}
