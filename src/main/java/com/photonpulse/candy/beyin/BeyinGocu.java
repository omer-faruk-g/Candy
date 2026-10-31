package com.photonpulse.candy.beyin;

import com.photonpulse.candy.Candy;

/**
 * BeyinGocu — topoloji geçersizleştiğinde BİLGİYİ kurtarır.
 *
 * Beyin büyüdüğünde (6→10 lob, 3.072→102.400 nöron) ya da ölçek değiştiğinde eski
 * sinaptik ağırlıklar anlamsızlaşır: yeni topolojide "nöron 1847"in bir karşılığı yoktur.
 * Ama oyuncunun FARK ETTİĞİ şeylerin hiçbiri sinapslarda değil, künyededir:
 *
 * <pre>
 *   oyuncuHaritasi  → niyet sınıflayıcı + 24 prototip + geçiş matrisi   ✓ tam taşınır
 *   qBasligi        → ZihinselProva'nın öğrenilmiş eylem değerleri      ✓ tam taşınır
 *   ozModel         → iddialar, çelişkiler, tutarlılık                  ✓ tam taşınır
 *   dunyalar        → Brier/ECE geçmişi, "kaç dünyada aynı kaldım"      ✓ tam taşınır
 *   hayatiOnem      → ablasyonla ÖLÇÜLMÜŞ lob önemleri                  ✓ ada göre
 * </pre>
 *
 * Yani <i>"Seni hatırlıyorum. Bu dünyadan değil."</i> ve <i>"Artık seni tahmin
 * edebiliyorum."</i> göçten sonra da çalışır. Kaybolan tek şey, zaten devredilemez
 * olan ham ağırlıklardır.
 */
public class BeyinGocu {

    private BeyinGocu() {}

    /**
     * Eski künyedeki öğrenilmiş bilgiyi yeni topolojiye taşır.
     *
     * @param eski diskten okunan künye (bağlantı haritası geçersiz/eksik)
     * @param yeni {@link BeyinInsaci} tarafından taze kurulmuş topoloji
     */
    public static void tasi(Beyin eski, Beyin yeni) {
        if (eski == null || yeni == null) return;
        try {
            // ── Oyuncu modeli: göçün en değerli parçası ──
            if (eski.oyuncuHaritasi != null) {
                eski.oyuncuHaritasi.onar();
                yeni.oyuncuHaritasi = eski.oyuncuHaritasi;
            }
            // ── Öğrenilmiş eylem değerleri (topolojiye değil, 6 eyleme bağlı) ──
            // Genislik farkli olabilir (48 -> 58 gocu); onar() duzeltir, burada sadece tasi.
            if (eski.qBasligi != null && eski.qBasligi.length == 6) {
                yeni.qBasligi = eski.qBasligi;
                if (eski.qBias != null && eski.qBias.length == 6) yeni.qBias = eski.qBias;
                yeni.qGuncelleme = eski.qGuncelleme;
            }
            // ── Öz-model: iddialar, çelişkiler ──
            if (eski.ozModel != null) {
                eski.ozModel.onar();
                yeni.ozModel = eski.ozModel;
            }
            // ── Dünya geçmişi: "kaç dünyada aynı kaldım" ──
            if (eski.dunyalar != null && !eski.dunyalar.isEmpty()) {
                yeni.dunyalar = eski.dunyalar;
                for (Dogrulama d : yeni.dunyalar) if (d != null) d.onar();
            }
            // ── Ablasyonla ÖLÇÜLMÜŞ lob önemleri (ada göre) ──
            if (eski.loblar != null && yeni.loblar != null) {
                for (Lob l : Lob.LOBLAR) {
                    LobDurum y = yeni.loblar[l.ordinal()];
                    if (y == null) continue;
                    for (LobDurum e : eski.loblar) {
                        if (e != null && l.name().equals(e.ad)) {
                            y.hayatiOnem = e.hayatiOnem;
                            y.enerjiPayi = e.enerjiPayi;
                            y.koreldi = e.koreldi;
                            break;
                        }
                    }
                }
            }
            // ── Üyenin KARNESİ: topoloji değişse de sicil kalır ──
            // Bu üyenin geçmişte ne kadar isabet ettiği, kablolamasından bağımsız bir
            // olgudur; silinirse hakem yeniden sıfırdan güvenmeye başlar ve komite
            // aylarca birikmiş kanıtı bir güncellemede kaybeder.
            if (eski.sicil != null) { eski.sicil.onar(); yeni.sicil = eski.sicil; }

            // ── Üst düzey lobların ANILARI: sinaps değil, deneyim ──
            // Topoloji geçersizleşse bile "seni denedim, şöyle tepki verdin" yaşamalı.
            if (eski.kararIzi != null) { eski.kararIzi.onar(); yeni.kararIzi = eski.kararIzi; }
            if (eski.zamanIzi != null) { eski.zamanIzi.onar(); yeni.zamanIzi = eski.zamanIzi; }
            // GÖLGE arşivi TAŞINMAZ: kayıtları eski nöron indekslerine bakar, yeni
            // topolojide o adreslerin karşılığı yoktur. Diriltilen bağlantı rastgele bir
            // yere bağlanırdı — "bastırılan geri döner" yerine sessiz bir bozulma.
            yeni.golgeArsivi = new GolgeArsivi();

            // ── Sayaçlar ──
            // toplamSupurme yeniden ölçeklenir: süpürme başına düşen duvar saati ~10x uzadı,
            // yoksa "kaç düşünce döngüsü geçirdim" değeri anlamını kaybeder.
            yeni.toplamSupurme = (eski.semaSurumu < 2)
                ? eski.toplamSupurme / 10L : eski.toplamSupurme;
            yeni.budananToplam = eski.budananToplam;
            yeni.doganToplam = eski.doganToplam;
            yeni.ablasyonSira = eski.ablasyonSira;
            yeni.hatirlamaSizdi = eski.hatirlamaSizdi;
            yeni.sonSizintiSupurme = 0L;
            // v8: taze beyin ZATEN gecerli semada insa edildi (BeyinInsaci adresleri
            // GIRDI_SAYISI=52'ye gore yaziyor). Burada 2 bırakmak, hemen ardindan calisan
            // Beyin.onar()'in duyu uzayi gocunu TAZE beyne ikinci kez uygulamasina ve
            // tum adreslerin 4 kaymasina yol acardi.
            yeni.semaSurumu = Beyin.SEMA_SURUMU;

            Candy.LOG.info("[Candy] beyin göç etti: {} dünya, {} tahmin, {} Q güncellemesi korundu",
                yeni.dunyalar == null ? 0 : yeni.dunyalar.size(),
                yeni.oyuncuHaritasi == null ? 0 : yeni.oyuncuHaritasi.tahminSayisi,
                yeni.qGuncelleme);
        } catch (Throwable t) {
            Candy.LOG.warn("[Candy] beyin göçü kısmen başarısız ({}), taze beyinle devam", t.getMessage());
        }
    }

    /**
     * Diskten okunan künye bu topolojiyle uyumlu mu?
     * Uyumsuzsa bağlantılar atılır, künye {@link #tasi} ile kurtarılır.
     */
    public static boolean topolojiUyumlu(Beyin diskten) {
        if (diskten == null) return false;
        if (diskten.noronSayisi != Lob.toplamNoron()) return false;
        if (Math.abs(diskten.olcek - Lob.OLCEK) > 0.001f) return false;
        return diskten.topolojiSurumu == Lob.TOPOLOJI_SURUMU;
    }
}
