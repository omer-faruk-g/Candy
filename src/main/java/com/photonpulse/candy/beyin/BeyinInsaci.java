package com.photonpulse.candy.beyin;

import com.photonpulse.candy.Candy;

import java.util.SplittableRandom;
import java.util.UUID;

/**
 * BeyinInsaci — beyin topolojisini sıfırdan üretir.
 *
 * DETERMİNİSTİK: tohum oyuncunun UUID'sinden türetilir. Aynı oyuncu için aynı iskelet
 * doğar; öğrenmeyle farklılaşan şey ağırlıklardır, yapı değil.
 *
 * <h3>BAĞLANTI ŞEMASI (v5)</h3>
 * <pre>
 *   Enjekte blok    ← sinaps YOK; aktiflik Beyin.icDuyu'dan yazılır (katman = -1)
 *   Katman 0        ← lobun kendi duyu özellikleri + kendi enjekte bloğu
 *   Katman k &gt; 0    ← aynı lobun katman k-1'inden, YEREL PENCERE (±96) içinden
 *   Çıkış sütunu    ← son geniş katmandan CIKIS_FANIN genişliğinde düzgün örnekleme
 *   Demetler        ← kaynak lobun son katmanı → hedef lobun her nöronuna dalBasi kadar
 * </pre>
 *
 * <h3>NEDEN YEREL PENCERE</h3>
 * Küresel rastgele bağlantıda lob büyüdükçe komşu nöronların ortak girdisi sıfıra gider;
 * çıkış sütununun ortalaması merkeze yakınsar ve <b>lob sessizce susar</b> — ölçek arttıkça
 * kötüleşen bir arıza. Yerel pencerede bitişik nöronların pencereleri neredeyse tamamen
 * örtüşür, ilinti korunur. Bonus: gather L1 önbellekte kalır, ileri besleme hızlanır.
 *
 * <h3>TAHSİSSİZ İKİ GEÇİŞ</h3>
 * Eskiden nöron başına bir {@code ArrayList}, sinaps başına bir {@code int[2]} ayrılıyordu:
 * 100k ölçeğinde ~55-70 MB genç nesil çöp ve ana thread'de 200-400 ms. Fan-in analitik
 * olarak belirli olduğu için 1. geçiş sayar, 2. geçiş yerinde yazar — ara nesne yok.
 *
 * <h3>GEOMETRİ TEK YERDE</h3>
 * Katman sınırları {@link Lob}'un kendi yardımcılarından okunur. Aynı formül burada üç
 * kez (katman kurulumu, çıkış sütunu, demet kaynağı) kopyalanmıştı; enjekte bloğu
 * eklenirken üçünden birinin unutulması sessiz bir topoloji bozulması olurdu.
 */
public class BeyinInsaci {

    /** Katman k>0 için nöron başına lob içi fan-in. */
    private static final int LOB_ICI_FANIN = 12;
    /** Katman 0'ın duyu özelliklerinden aldığı en fazla bağlantı. */
    private static final int DUYU_FANIN = 12;
    /**
     * Katman 0'ın KENDİ enjekte bloğundan aldığı bağlantı.
     *
     * İç duyu bilgisinin loba girdiği tek kapı budur; 0 olsaydı enjekte nöronlar
     * hesaplanır ama hiçbir şeye bağlanmaz, lob dış duyuyla aynı kalırdı.
     */
    private static final int ENJEKTE_FANIN = 6;

    private BeyinInsaci() {}

    /** Geriye uyum: uye belirtilmezse govde (uye 0) insa edilir. */
    public static Beyin insaEt(UUID oyuncuId) {
        return insaEt(oyuncuId, 0);
    }

    public static Beyin insaEt(UUID oyuncuId, int uye) {
        final Lob[] loblar = Lob.LOBLAR;
        final int n = Lob.toplamNoron();

        Beyin b = new Beyin();
        b.oyuncuId = oyuncuId;
        b.noronSayisi = n;
        b.aktiflik = new float[n];
        b.esik = new float[n];
        b.lobId = new byte[n];
        b.katman = new byte[n];
        b.girdi = new float[Beyin.GIRDI_SAYISI];
        b.icDuyu = new float[Lob.TOPLAM_ENJEKTE];
        b.demetler = Demet.varsayilan();
        b.olcek = Lob.OLCEK;
        b.topolojiSurumu = Lob.TOPOLOJI_SURUMU;

        // ÜYE BAŞINA AYRI TOHUM: aynı oyuncu, aynı mimari — ama farklı kablolama.
        // Altın oran sabitiyle karıştırılır ki komşu üye numaraları benzer tohum
        // üretmesin; benzer tohum benzer ağ, benzer ağ İLİNTİLİ HATA demektir ve
        // komitenin tüm istatistiksel kazancını sessizce yok ederdi.
        long tohum = (oyuncuId == null) ? 239L
            : oyuncuId.getMostSignificantBits() ^ oyuncuId.getLeastSignificantBits();
        tohum ^= (long) uye * 0x9E3779B97F4A7C15L;
        b.topolojiTohumu = tohum;
        Kurul.mizacUygula(b, uye);
        // SplittableRandom: java.util.Random her cagrida AtomicLong uzerinde CAS yapar
        // (~25 ns); milyonlarca sinapsta tek basina ~80 ms. Bu ise CAS'siz, ~2 ns.
        SplittableRandom rng = new SplittableRandom(tohum);

        // Hedef lob başına gelen demet fan-in'i
        int[] demetFan = new int[loblar.length];
        for (Demet d : b.demetler) {
            try { demetFan[Lob.valueOf(d.hedef).ordinal()] += Math.max(1, d.dalBasi); }
            catch (Throwable ignored) {}
        }

        // ── GEÇİŞ 1: fan-in say, CSR offsetlerini kur (tahsis yok) ──
        int[] fanIn = new int[n];
        for (Lob lob : loblar) {
            final int bas = lob.baslangic();
            final int adet = lob.noronSayisi();
            final int enj = lob.enjekte;
            final int kat = Math.max(1, lob.katmanSayisi);
            final int katmanli = lob.katmanliAdet();
            final int katBoyu = lob.katmanBoyu();
            final int duyuFan = Math.min(lob.girdiIndeksleri.length, DUYU_FANIN);
            final int enjFan = (enj > 0) ? Math.min(ENJEKTE_FANIN, enj) : 0;

            for (int i = 0; i < adet; i++) {
                int gi = bas + i;
                b.lobId[gi] = (byte) lob.ordinal();
                if (i < enj) {
                    // ENJEKTE: sinaps yok. -1 isareti ileri beslemede ve plastisitede atlanir.
                    b.katman[gi] = -1;
                    fanIn[gi] = 0;
                    continue;
                }
                int j = i - enj;
                int fan;
                if (j >= katmanli) {
                    b.katman[gi] = (byte) kat;                       // çıkış sütunu
                    fan = Math.min(Lob.CIKIS_FANIN, katBoyu);
                } else {
                    int k = Math.min(kat - 1, j / katBoyu);
                    b.katman[gi] = (byte) k;
                    fan = (k == 0) ? duyuFan + enjFan : Math.min(LOB_ICI_FANIN, katBoyu);
                }
                fanIn[gi] = fan + demetFan[lob.ordinal()];
            }
        }

        b.girisBasi = new int[n + 1];
        int toplam = 0;
        for (int i = 0; i < n; i++) {
            b.girisBasi[i] = toplam;
            toplam += fanIn[i];
            if (toplam > Beyin.MAX_SINAPS) { toplam = Beyin.MAX_SINAPS; break; }
        }
        b.girisBasi[n] = toplam;
        b.sinapsSayisi = toplam;
        b.kaynak = new int[toplam];
        b.agirlik = new float[toplam];
        b.sinapsIz = new float[toplam];

        // ── GEÇİŞ 2: bağlantıları yerinde yaz ──
        for (Lob lob : loblar) {
            final int bas = lob.baslangic();
            final int adet = lob.noronSayisi();
            final int enj = lob.enjekte;
            final int kat = Math.max(1, lob.katmanSayisi);
            final int katmanli = lob.katmanliAdet();
            final int katBoyu = lob.katmanBoyu();
            final int govdeBas = lob.govdeBaslangic();
            final int duyuFan = Math.min(lob.girdiIndeksleri.length, DUYU_FANIN);
            final int enjFan = (enj > 0) ? Math.min(ENJEKTE_FANIN, enj) : 0;
            final int sonKatBas = lob.sonKatmanBaslangic();
            final int sonKatSon = lob.sonKatmanBitis();

            for (int i = 0; i < adet; i++) {
                int gi = bas + i;
                if (gi + 1 >= b.girisBasi.length) break;
                if (i < enj) {
                    // Enjekte nöron: ne eşik ne sinaps. Nötr başlar, icDuyuYaz() sürer.
                    b.esik[gi] = 0f;
                    b.aktiflik[gi] = IcDuyu.NOTR;
                    continue;
                }
                int yaz = b.girisBasi[gi];
                int sinir = b.girisBasi[gi + 1];
                b.esik[gi] = (float) ((rng.nextDouble() - 0.5) * 0.2);
                int j = i - enj;

                if (j >= katmanli) {
                    // ── ÇIKIŞ SÜTUNU: son geniş katmanı DÜZGÜN örnekle ──
                    int aralik = Math.max(1, sonKatSon - sonKatBas);
                    int fan = Math.max(1, Math.min(Lob.CIKIS_FANIN, aralik));
                    int adim = Math.max(1, aralik / fan);
                    for (int f = 0; f < fan && yaz < sinir; f++) {
                        int src = sonKatBas + (int) ((long) f * aralik / fan) + rng.nextInt(adim);
                        if (src >= sonKatSon) src = sonKatSon - 1;
                        b.kaynak[yaz] = Beyin.GIRDI_SAYISI + src;
                        b.agirlik[yaz] = agirlik(rng, fan);
                        yaz++;
                    }
                } else {
                    int k = Math.min(kat - 1, j / katBoyu);
                    if (k == 0) {
                        int[] oz = lob.girdiIndeksleri;
                        for (int f = 0; f < duyuFan && yaz < sinir; f++) {
                            b.kaynak[yaz] = oz[rng.nextInt(oz.length)];
                            b.agirlik[yaz] = agirlik(rng, duyuFan + enjFan);
                            yaz++;
                        }
                        // ── İÇ DUYU KAPISI: lobun kendi enjekte bloğundan ──
                        for (int f = 0; f < enjFan && yaz < sinir; f++) {
                            b.kaynak[yaz] = Beyin.GIRDI_SAYISI + bas + rng.nextInt(enj);
                            b.agirlik[yaz] = agirlik(rng, duyuFan + enjFan);
                            yaz++;
                        }
                    } else {
                        // ── DERİN KATMAN: önceki katmandan YEREL PENCERE ──
                        int oncekiBas = govdeBas + (k - 1) * katBoyu;
                        int oncekiSon = Math.min(govdeBas + k * katBoyu, govdeBas + katmanli);
                        int oncekiBoy = Math.max(1, oncekiSon - oncekiBas);
                        int yerelIdx = j - k * katBoyu;
                        int merkez = oncekiBas
                            + (int) ((long) yerelIdx * oncekiBoy / Math.max(1, katBoyu));
                        int pBas = Math.max(oncekiBas, merkez - Lob.PENCERE_YARI);
                        int pSon = Math.min(oncekiSon, merkez + Lob.PENCERE_YARI);
                        int pAralik = Math.max(1, pSon - pBas);
                        int fan = Math.max(1, Math.min(LOB_ICI_FANIN, pAralik));
                        for (int f = 0; f < fan && yaz < sinir; f++) {
                            b.kaynak[yaz] = Beyin.GIRDI_SAYISI + pBas + rng.nextInt(pAralik);
                            b.agirlik[yaz] = agirlik(rng, fan);
                            yaz++;
                        }
                    }
                }

                // ── DEMETLER: bu loba gelen beyaz madde ──
                for (Demet d : b.demetler) {
                    if (!lob.name().equals(d.hedef)) continue;
                    Lob kay;
                    try { kay = Lob.valueOf(d.kaynak); } catch (Throwable t) { continue; }
                    int kBas = kay.sonKatmanBaslangic();
                    int kAralik = Math.max(1, kay.sonKatmanBitis() - kBas);
                    for (int f = 0; f < Math.max(1, d.dalBasi) && yaz < sinir; f++) {
                        b.kaynak[yaz] = Beyin.GIRDI_SAYISI + kBas + rng.nextInt(kAralik);
                        b.agirlik[yaz] = agirlik(rng, Math.max(4, d.dalBasi * 4));
                        yaz++;
                    }
                }
                // Fazla slot kalırsa ölü bırakılır → nörogenez havuzu
                while (yaz < sinir) { b.kaynak[yaz] = 0; b.agirlik[yaz] = 0f; yaz++; }
            }
        }

        b.loblar = new LobDurum[loblar.length];
        for (Lob l : loblar) b.loblar[l.ordinal()] = new LobDurum(l);

        Candy.LOG.debug("[Candy] üye {} inşa edildi: {} nöron, {} sinaps, mizaç {}/{}",
            uye, b.noronSayisi, b.sinapsSayisi,
            String.format("%.2f", b.mizacPlastisite), String.format("%.2f", b.mizacAtilganlik));
        return b;
    }

    /** Fan-in'e göre ölçekli rastgele başlangıç ağırlığı (Xavier benzeri). */
    private static float agirlik(SplittableRandom rng, int fanIn) {
        double olcek = 1.5 / Math.sqrt(Math.max(1, fanIn));
        return (float) ((rng.nextDouble() * 2.0 - 1.0) * olcek);
    }
}
