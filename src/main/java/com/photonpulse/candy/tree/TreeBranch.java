package com.photonpulse.candy.tree;

import java.util.HashMap;
import java.util.Map;

/**
 * TreeBranch — Candy beyninin 10 LOBU (v5.0; v4'te 6, v3'te 3 kök dal).
 *
 * Bunların altındaki alt dallar tamamen dinamik olarak büyür.
 * Kökler silinmez, hibernasyona giremez.
 *
 * GERİYE UYUM (kritik): mevcut 3 lobun {@code id} string'leri ve enum sabit adları
 * DEĞİŞMEDİ ({@code izleme}/{@code etkilesim}/{@code tepki}). Böylece hem eski dünya
 * JSON'ları hem de 4 tüketici (CloneManager, SignSystem, MobSpawnBooster, TickAccelerator)
 * hiç dokunulmadan çalışmaya devam eder. Yalnızca insan-okunur isim ve anlam genişledi.
 *
 * Loblar ve görevleri:
 *  ARDIL      (oksipital) → duyusal ön-işleme; ham sinyali özelliğe çevirir
 *  IZLEME     (parietal)  → uzam/rota: tabela/klon nerede yerleştirilecek
 *  ETKILESIM  (temporal)  → dil/hafıza/örüntü: tabela yazı tarzı, mesaj tonu
 *  ADACIK     (insula)    → interosepsiyon: oyuncunun İÇ durumu (korku, çözülme, tilt)
 *  KENAR      (limbik)    → ödül/ceza/travma: duygu ve motivasyon
 *  TEPKI      (frontal)   → karar/planlama: klon agresifliği, cooldown, saldırı
 */
public enum TreeBranch {

    ARDIL("ardil", "Ardıl lob"),
    IZLEME("izleme", "Yanlama lobu"),
    ETKILESIM("etkilesim", "Şakak lobu"),
    ADACIK("adacik", "Adacık lobu"),
    KENAR("kenar", "Kenar lobu"),
    TEPKI("tepki", "Öncül lob"),
    YANKI("yanki", "Yankı lobu"),
    TARTI("tarti", "Tartı lobu"),
    GOLGE("golge", "Gölge lobu"),
    BEYINCIK("beyincik", "Beyincik");

    public final String id;
    public final String isim;

    TreeBranch(String id, String isim) {
        this.id = id;
        this.isim = isim;
    }

    /**
     * Kökün başlangıç trait'i.
     * Bu değerler oyunla birlikte HER KÖK için ayrı ayrı kayabilir.
     * (Kök trait'leri sabit değil — Candy zamanla dinlediklerini değiştirebilir.)
     */
    public Map<String, Double> defaultTrait() {
        Map<String, Double> t = new HashMap<>();
        switch (this) {
            // Oksipital: ham duyu. Ne oluyor, nerede, tehdit var mi?
            case ARDIL -> {
                t.put("niyet_kesfet", 0.5);
                t.put("niyet_kaziyor", 0.4);
                t.put("tetik_klon", 0.6);
                t.put("tetik_karanlik", 0.5);
            }
            // Parietal: uzam ve rota
            case IZLEME -> {
                t.put("kesfetOrani", 0.6);
                t.put("donOrani", 0.3);
                t.put("kacOrani", 0.4);
                t.put("konforSkoru", 0.4);
                t.put("ongorulebilirlik", 0.5);
            }
            // Temporal: dil, hafiza, oruntu
            case ETKILESIM -> {
                t.put("aciklik", 0.7);
                t.put("inkarEgilimi", -0.4);
                t.put("kesfetmeIstegi", 0.5);
                t.put("kehanetGuveni", 0.5);
                t.put("hipotez_merakli", 0.4);
            }
            // Insula: oyuncunun IC durumu
            case ADACIK -> {
                t.put("korkuSkoru", 0.7);
                t.put("cozulme", 0.5);
                t.put("tiltSeviyesi", 0.4);
                t.put("paranoyaSkoru", 0.5);
                t.put("psikolojiKotulugu", 0.4);
            }
            // Limbik: odul, ceza, motivasyon
            case KENAR -> {
                t.put("duyguKorku", 0.6);
                t.put("duyguHayalKirikligi", 0.4);
                t.put("duyguMerak", 0.4);
                t.put("duyguSikinti", -0.5);
                t.put("memnuniyetsizlik", 0.5);
            }
            // Frontal: karar ve inhibisyon
            case TEPKI -> {
                t.put("saldirOrani", 0.5);
                t.put("panikEsigi", 0.4);
                t.put("kacOrani", -0.3);
                t.put("candyOzguven", 0.5);
                t.put("sasirmaSeviyesi", 0.4);
            }
            // Retrospeksiyon: gecmis kararlari sorgulama
            case YANKI -> {
                t.put("ogrenmeHizi", 0.6);
                t.put("sasirmaSeviyesi", 0.5);
                t.put("kehanetGuveni", 0.4);
                t.put("candyOzguven", -0.3);
                t.put("ongorulebilirlik", 0.4);
            }
            // Meta-degerlendirme: aglarin ise yararliligi
            case TARTI -> {
                t.put("metaFarkindalik", 0.6);
                t.put("candyOzFarkindalik", 0.5);
                t.put("ongorulebilirlik", 0.5);
                t.put("yetenekSkoru", 0.4);
                t.put("konforSkoru", -0.3);
            }
            // Bastirilmis oruntuler
            case GOLGE -> {
                t.put("bastirilmisBaski", 0.7);
                t.put("memnuniyetsizlik", 0.6);
                t.put("inkarEgilimi", 0.5);
                t.put("duyguSikinti", 0.4);
                t.put("cozulme", 0.3);
            }
            // Zamanlama: "ne zaman?"
            case BEYINCIK -> {
                t.put("konforSkoru", 0.6);
                t.put("ongorulebilirlik", 0.5);
                t.put("tiltSeviyesi", -0.4);
                t.put("korkuSkoru", -0.3);
                t.put("niyet_bekle", 0.4);
            }
        }
        return t;
    }
}
