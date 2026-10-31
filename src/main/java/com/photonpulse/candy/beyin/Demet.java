package com.photonpulse.candy.beyin;

/**
 * Demet — iki lob arasındaki beyaz madde bağlantısı (aksonal demet).
 *
 * Lob içi bağlantılar anonimdir; loblar ARASI bağlantılar ise demet olarak isimlendirilir,
 * çünkü kullanıcının istediği "hangi ağ ile iletişimin önemsiz olduğu" kararı bu düzeyde
 * verilir: her demetin kendi gücü, enerji payı ve ÖLÇÜLEN hayati önemi vardır (Aşama 5).
 *
 * v5: 19 demet, 10 lob arasinda.
 */
public class Demet {

    /** Kaynak lob adı (enum name). */
    public String kaynak;
    /** Hedef lob adı (enum name). */
    public String hedef;
    /** Bu demete ayrılan toplam sinaps kotası (dalBasi x hedefNoron olarak turetilir). */
    public int kota;
    /**
     * Hedef noron BASINA gelen sinaps sayisi — asil topoloji parametresi.
     *
     * BUG NOTU: once yalnizca {@code kota} vardi ve BeyinInsaci {@code kota / hedefNoron}
     * tam sayi bolmesi yapiyordu. Hedef lob 10.240 noron oldugunda 2048/10240 = 0 -> max(1,0)
     * = 1, yani TUM demetler dalBasi 1 olurdu; kota alani sessizce anlamsizlasirdi.
     */
    public int dalBasi = 1;

    /** Demet kazancı — plastiktir, enerji ile kapılanır (0..2). */
    public double guc = 1.0;
    /** Softmax enerji payı. */
    public double enerjiPayi = 0.1;
    /** ABLASYON ile ÖLÇÜLEN önem (0..1): "bu demeti kessem ne kaybederim?" */
    public double hayatiOnem = 0.5;

    public Demet() {}

    public Demet(Lob kaynak, Lob hedef, int dalBasi) {
        this.kaynak = kaynak.name();
        this.hedef = hedef.name();
        this.dalBasi = Math.max(1, dalBasi);
        this.kota = this.dalBasi * hedef.noronSayisi();
    }

    public void onar() {
        if (dalBasi < 1) dalBasi = 1;
        if (!Double.isFinite(guc)) guc = 1.0;
        if (!Double.isFinite(enerjiPayi)) enerjiPayi = 0.1;
        if (!Double.isFinite(hayatiOnem)) hayatiOnem = 0.5;
        guc = Math.max(0.0, Math.min(2.0, guc));
        hayatiOnem = Math.max(0.0, Math.min(1.0, hayatiOnem));
    }

    /**
     * Beyaz madde topolojisi — 19 demet.
     *
     * v5'te 10 -> 19: dort ust duzey lob agi yalnizca BUYUTMEZ, geri besleme DONGULERI
     * kurar. Onculun kararı YANKI'ya gider, YANKI'nin dersi Onculle geri doner; GOLGE
     * bastirdigini duyusal ve duygusal loblara sizdirir; BEYINCIK zamanlamayi karar ve
     * ic durum loblarina yayar. Ileri besleyen bir yigin degil, kendi ustune katlanan
     * bir ag.
     *
     * DIKKAT: hedef lobun her noronu icin dalBasi kadar sinaps acilir. 19 demet, hedef
     * noron basina toplam fan-in'i belirler; buyutmeden once BeyinInsaci'nin GECIS 1
     * sayimindaki demetFan toplamina bak.
     */
    public static Demet[] varsayilan() {
        // Parametre artik HEDEF NORON BASINA sinaps sayisi (dalBasi), toplam kota degil.
        return new Demet[]{
            // ── Temel omurga (v4) ──
            new Demet(Lob.ARDIL,   Lob.YANLAMA, 2),  // nerede olduğumu görüyorum
            new Demet(Lob.ARDIL,   Lob.SAKAK,   1),  // ne yaptığını görüyorum
            new Demet(Lob.ARDIL,   Lob.ADACIK,  1),  // nasıl göründüğünü hissediyorum
            new Demet(Lob.ADACIK,  Lob.KENAR,   2),  // hissettiğim şey beni ödüllendiriyor
            new Demet(Lob.KENAR,   Lob.ONCUL,   1),  // istediğim şey kararımı yönlendiriyor
            new Demet(Lob.KENAR,   Lob.SAKAK,   1),  // duygu hafızayı renklendiriyor
            new Demet(Lob.YANLAMA, Lob.ONCUL,   1),
            new Demet(Lob.SAKAK,   Lob.ONCUL,   2),  // en kalın demet
            new Demet(Lob.ONCUL,   Lob.ARDIL,   1),  // yukarıdan aşağı dikkat
            new Demet(Lob.ONCUL,   Lob.YANLAMA, 1),

            // ── Retrospeksiyon halkası: karar → iz → ders → karar ──
            new Demet(Lob.ONCUL,   Lob.YANKI,   2),  // ne karar verdiğimi izliyorum
            new Demet(Lob.SAKAK,   Lob.YANKI,   1),  // kararın hangi bağlamda verildiği
            new Demet(Lob.YANKI,   Lob.ONCUL,   2),  // geçmişim bir sonraki kararımı bükür

            // ── Meta-değerlendirme: ağın kendi işe yararlılığı ──
            new Demet(Lob.KENAR,   Lob.TARTI,   1),  // neye değer verdiğim neyi ölçtüğümü belirler
            new Demet(Lob.TARTI,   Lob.ONCUL,   1),  // "bu yol işe yaramıyor" karara sızar

            // ── Bastırılan geri döner ──
            new Demet(Lob.GOLGE,   Lob.KENAR,   1),  // bastırılan şey duyguya sızar
            new Demet(Lob.GOLGE,   Lob.SAKAK,   1),  // ve hatırlamayı renklendirir

            // ── Zamanlama yayılımı ──
            new Demet(Lob.BEYINCIK, Lob.ONCUL,  1),  // "şimdi mi?" kararın parçası olur
            new Demet(Lob.BEYINCIK, Lob.ADACIK, 1)   // ritim iç duruma da işler
        };
    }
}
