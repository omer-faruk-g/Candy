package com.photonpulse.candy.beyin;

import com.photonpulse.candy.core.TruePersonality;
import com.photonpulse.candy.tree.PersonalityTree;
import com.photonpulse.candy.tree.TreeNode;

/**
 * BeyinCikti — beynin lob çıkışlarını ağacın kök node'larına yazar.
 *
 * MİMARİNİN KİLİT NOKTASI: beyin gizlidir, ağaç "okuma yüzeyi" olarak kalır. Tüketiciler
 * (CloneManager, SignSystem, MobSpawnBooster, TickAccelerator) eskisi gibi
 * {@code TreeNode.aktiflik} okumaya devam eder; yalnızca o sayının arkasındaki hesap
 * 40 kopya nörondan 102.400'lük bir ağa dönüşmüştür.
 *
 * KARIŞIM (karisim): 0.0 = beyin hesaplanır ama çıktıya hiç karışmaz (A/B kontrolü),
 * 1.0 = kök aktiflikleri tamamen beyinden gelir. Aradaki değerler yumuşak geçiş sağlar;
 * beyin sezgiselden kötü çıkarsa tek parametreyle geri çekilebilir.
 */
public class BeyinCikti {

    private BeyinCikti() {}

    /**
     * Lob çıkışlarını ağaç köklerine uygular.
     *
     * @param karisim 0..1 arası karışım oranı
     */
    public static void uygula(Beyin beyin, PersonalityTree tree, double karisim) {
        if (beyin == null) return;
        // Cikis dagilim istatistigi — supurme basina TAM BIR KEZ, karisim dalindan ONCE.
        beyin.cikisIstatistikGuncelle();
        if (tree == null || tree.nodes == null) return;
        if (karisim <= 0.0) {
            // A/B kontrolü: yalnızca izleme değerlerini güncelle, ağaca DOKUNMA
            for (Lob lob : Lob.LOBLAR) {
                if (beyin.loblar != null && beyin.loblar[lob.ordinal()] != null) {
                    beyin.loblar[lob.ordinal()].cikis = beyin.lobCikisi(lob);
                    beyin.loblar[lob.ordinal()].ortAktiflik = beyin.lobOrtalamasi(lob);
                }
            }
            return;
        }

        double k = Math.max(0.0, Math.min(1.0, karisim));
        for (Lob lob : Lob.LOBLAR) {
            double cikis = beyin.lobCikisi(lob);
            if (beyin.loblar != null && beyin.loblar[lob.ordinal()] != null) {
                beyin.loblar[lob.ordinal()].cikis = cikis;
                beyin.loblar[lob.ordinal()].ortAktiflik = beyin.lobOrtalamasi(lob);
            }
            TreeNode kok = tree.nodes.get(lob.dal.id);
            if (kok == null) continue;
            // Yumuşak karışım — ani sıçrama yok (mod genelindeki kayma felsefesi)
            double yeni = kok.aktiflik * (1.0 - k) + cikis * k;
            if (Double.isFinite(yeni)) {
                kok.aktiflik = Math.max(0.0, Math.min(1.0, yeni));
                kok.beyinKatkisi = (float) cikis;
            }
        }
    }

    /**
     * Beynin öz-durumunu oyuncu profiline sızdırır (yalnızca sayısal; metin/ifşa YOK).
     * Aşama 6'da öz-model bunun üzerine kurulacak.
     */
    public static void durumYansit(Beyin beyin, TruePersonality tp) {
        if (beyin == null || tp == null || beyin.loblar == null) return;
        // Öncül (frontal) lobun etkinliği = Candy'nin "karar berraklığı"
        LobDurum oncul = beyin.loblar[Lob.ONCUL.ordinal()];
        if (oncul != null && Double.isFinite(oncul.ortAktiflik)) {
            // Çok küçük bir itki — mevcut dengeleri bozmadan beynin canlılığını yansıtır
            double hedef = Math.max(0.0, Math.min(100.0, oncul.ortAktiflik * 100.0));
            tp.candyOzFarkindalik = TruePersonality.kayma(tp.candyOzFarkindalik, hedef, 0.002);
        }
    }
}
