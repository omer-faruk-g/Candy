package com.photonpulse.candy.lore;

import java.util.List;
import java.util.Random;

/**
 * JournalTexts — geçmiş oyuncu günlükleri (translation key referansları).
 *
 * v0.9+: metinler `assets/candy/lang/*.json` dosyalarında. Journal record'ları
 * key referanslarını tutar; client kendi diline göre render eder.
 */
public class JournalTexts {

    public static class Journal {
        public final String baslikKey;
        public final String yazarKey;
        public final List<String> sayfaKeys;

        public Journal(String baslikKey, String yazarKey, List<String> sayfaKeys) {
            this.baslikKey = baslikKey;
            this.yazarKey = yazarKey;
            this.sayfaKeys = sayfaKeys;
        }
    }

    public static final List<Journal> HEPSI = List.of(
        new Journal("candy.journal.kerem.title", "candy.journal.kerem.author", List.of(
            "candy.journal.kerem.p1", "candy.journal.kerem.p2", "candy.journal.kerem.p3",
            "candy.journal.kerem.p4", "candy.journal.kerem.p5"
        )),
        new Journal("candy.journal.elif.title", "candy.journal.elif.author", List.of(
            "candy.journal.elif.p1", "candy.journal.elif.p2",
            "candy.journal.elif.p3", "candy.journal.elif.p4"
        )),
        new Journal("candy.journal.ozan.title", "candy.journal.ozan.author", List.of(
            "candy.journal.ozan.p1", "candy.journal.ozan.p2",
            "candy.journal.ozan.p3", "candy.journal.ozan.p4"
        )),
        new Journal("candy.journal.zeynep.title", "candy.journal.zeynep.author", List.of(
            "candy.journal.zeynep.p1", "candy.journal.zeynep.p2", "candy.journal.zeynep.p3",
            "candy.journal.zeynep.p4", "candy.journal.zeynep.p5"
        )),
        new Journal("candy.journal.emre.title", "candy.journal.emre.author", List.of(
            "candy.journal.emre.p1", "candy.journal.emre.p2", "candy.journal.emre.p3",
            "candy.journal.emre.p4", "candy.journal.emre.p5"
        )),
        new Journal("candy.journal.deniz.title", "candy.journal.deniz.author", List.of(
            "candy.journal.deniz.p1", "candy.journal.deniz.p2", "candy.journal.deniz.p3",
            "candy.journal.deniz.p4", "candy.journal.deniz.p5", "candy.journal.deniz.p6"
        )),
        new Journal("candy.journal.mert.title", "candy.journal.mert.author", List.of(
            "candy.journal.mert.p1", "candy.journal.mert.p2", "candy.journal.mert.p3",
            "candy.journal.mert.p4", "candy.journal.mert.p5"
        )),
        new Journal("candy.journal.aylin.title", "candy.journal.aylin.author", List.of(
            "candy.journal.aylin.p1", "candy.journal.aylin.p2", "candy.journal.aylin.p3",
            "candy.journal.aylin.p4", "candy.journal.aylin.p5"
        )),
        new Journal("candy.journal.sinan.title", "candy.journal.sinan.author", List.of(
            "candy.journal.sinan.p1", "candy.journal.sinan.p2",
            "candy.journal.sinan.p3", "candy.journal.sinan.p4"
        )),
        new Journal("candy.journal.yusuf.title", "candy.journal.yusuf.author", List.of(
            "candy.journal.yusuf.p1", "candy.journal.yusuf.p2", "candy.journal.yusuf.p3",
            "candy.journal.yusuf.p4", "candy.journal.yusuf.p5"
        )),
        new Journal("candy.journal.nazan.title", "candy.journal.nazan.author", List.of(
            "candy.journal.nazan.p1", "candy.journal.nazan.p2",
            "candy.journal.nazan.p3", "candy.journal.nazan.p4"
        )),
        new Journal("candy.journal.cansu.title", "candy.journal.cansu.author", List.of(
            "candy.journal.cansu.p1", "candy.journal.cansu.p2", "candy.journal.cansu.p3",
            "candy.journal.cansu.p4", "candy.journal.cansu.p5"
        )),
        new Journal("candy.journal.baba.title", "candy.journal.baba.author", List.of(
            "candy.journal.baba.p1", "candy.journal.baba.p2",
            "candy.journal.baba.p3", "candy.journal.baba.p4"
        )),
        new Journal("candy.journal.imam.title", "candy.journal.imam.author", List.of(
            "candy.journal.imam.p1", "candy.journal.imam.p2",
            "candy.journal.imam.p3", "candy.journal.imam.p4"
        )),
        new Journal("candy.journal.ressam.title", "candy.journal.ressam.author", List.of(
            "candy.journal.ressam.p1", "candy.journal.ressam.p2",
            "candy.journal.ressam.p3", "candy.journal.ressam.p4"
        )),
        new Journal("candy.journal.avci.title", "candy.journal.avci.author", List.of(
            "candy.journal.avci.p1", "candy.journal.avci.p2", "candy.journal.avci.p3",
            "candy.journal.avci.p4", "candy.journal.avci.p5"
        )),
        new Journal("candy.journal.yolcu.title", "candy.journal.yolcu.author", List.of(
            "candy.journal.yolcu.p1", "candy.journal.yolcu.p2",
            "candy.journal.yolcu.p3", "candy.journal.yolcu.p4"
        ))
    );

    private static final Random RNG = new Random();

    public static Journal rastgele() {
        return HEPSI.get(RNG.nextInt(HEPSI.size()));
    }
}
