package com.photonpulse.candy.tree;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * PersonalityTree — bir oyuncu için Candy'nin kişilik ağacı.
 *
 * Node'lar flat map'te tutulur (id → node), parent-child ilişkisi id ile.
 * Gson serialization için basit yapı.
 *
 * initEt() 10 sabit lobu oluşturur. Alt dallar dinamik oluşur (TreeGrowth).
 * initEt() idempotenttir: eski dünyalarda eksik loblar yüklemede otomatik eklenir.
 */
public class PersonalityTree {

    public UUID oyuncuId;
    public Map<String, TreeNode> nodes = new HashMap<>();

    /**
     * Node'ların dinleyebileceği duyu alanları (mutasyon yeni trait eklerken buradan seçer).
     *
     * v4.0: 8 → 48 alan. Mod ~60-70 özellik topluyordu (DerinAnaliz 14 boyut, FearDetection,
     * IcDusunce, Kehanet) ama ağaç bunların yalnızca 8'ini görüyordu — mimarinin en büyük
     * veri israfıydı. Tüm değerler {@link com.photonpulse.candy.tree.TreeGrowth#alanMapi}
     * tarafından 0..1 aralığına normalize edilir.
     *
     * <p>v8.0: 48 → 52. Yeni alanlar yalnızca SONA eklenir — {@link
     * com.photonpulse.candy.beyin.Lob#girdiIndeksleri} bu listeyi indeksle okur, araya ekleme
     * tüm lobların yanlış özelliği dinlemesine yol açar. Liste uzunluğu {@link
     * com.photonpulse.candy.beyin.Beyin#GIRDI_SAYISI} ile BİREBİR aynı olmalı; uyuşmazlıkta
     * {@code BeyinGirdi.semaGecerli()} false döner ve beyin tamamen devre dışı kalır.</p>
     */
    public static final List<String> ALANLAR = List.of(
        // ── Hareket simpleksi + kişilik çekirdeği (8) ──
        "kacOrani", "saldirOrani", "donOrani", "kesfetOrani",
        "aciklik", "inkarEgilimi", "kesfetmeIstegi", "panikEsigi",
        // ── Psikoloji (6) ──
        "korkuSkoru", "candyOzguven", "psikolojiKotulugu",
        "memnuniyetsizlik", "bastirilmisBaski", "riskToleransi",
        // ── Analiz 2.0 (9) ──
        "ongorulebilirlik", "yetenekSkoru", "cozulme", "riskIstahi", "ogrenmeHizi",
        "tiltSeviyesi", "konforSkoru", "paranoyaSkoru", "metaFarkindalik",
        // ── Bilişsel Motor v4 (8) ──
        "candyOzFarkindalik", "sasirmaSeviyesi", "kehanetGuveni", "kehanetOlasilik",
        "duyguKorku", "duyguHayalKirikligi", "duyguMerak", "duyguSikinti",
        // ── Candy'ye karşı tepki profili (4) ──
        "candyyeSaldiri", "candydenKacma", "candydeDonma", "candyGormezden",
        // ── Aktif niyet, one-hot (7) ──
        "niyet_kaziyor", "niyet_insa", "niyet_kesfet", "niyet_bekle",
        "niyet_don", "niyet_savas", "niyet_kac",
        // ── Bayesçi hipotezler (4) ──
        "hipotez_korkak", "hipotez_cesur", "hipotez_merakli", "hipotez_kayitsiz",
        // ── Baskın korku tetikleyicileri, normalize (2) ──
        "tetik_klon", "tetik_karanlik",
        // ── v8.0: tabela öfkesi (1) ──
        "candyOfke",
        // ── v8.0: Vagus — dış gözlemcinin iletisi (3) ──
        "vagusTahmin", "vagusKalibrasyon", "vagusDarbe",
        // ── v8.2: geri besleme eksenleri (2) ──
        // SIRA SON: yeni alan daima sona eklenir. Araya girmek Lob.girdiIndeksleri
        // tablolarının tamamını sessizce kaydırır — loblar yanlış duyuyu dinler.
        "arkayaBakma", "kacisArayisi",
        // ── v8.3: dünyaya uzanan elin iki ekseni (2) ──
        // 54 arkadasYasi — Arkadaş öldürüldükten sonra sönmeyen öfke.
        // 55 tuzakIlgisi — oyuncu bulmaca malzemesinin peşine düşüyor mu.
        // SIRA SON: yine sona eklendi.
        "arkadasYasi", "tuzakIlgisi"
    );

    /**
     * ÖZEL alan — bir sensör değil, YAPISAL bağlantı: node'un parent'ının aktifliği.
     *
     * v4.0 DERİNLİK: eskiden her node aynı global girdi vektörünü okuyordu, yani 40 node
     * aslında 40 KOPYA TEK NÖRON'du — parent-child ilişkisi sadece isimlendirme içindi.
     * Bu anahtar sayesinde çocuk, parent'ının ÇIKTISINI de girdi olarak alır: bilgi akışı
     * doğar ve ağaç gerçek bir çok katmanlı ağa dönüşür.
     */
    public static final String PARENT_ALANI = "parent";

    public PersonalityTree() {}

    /**
     * Sabit lobları oluşturur (yoksa). Idempotent — yükleme sonrası da güvenle çağrılabilir.
     * v4.0: eski 3-köklü ağaçlara ARDIL/ADACIK/KENAR eklenir.
     * v5.0: 6-köklü ağaçlara YANKI/TARTI/GOLGE/BEYINCIK eklenir. Eski dünyalar bu
     * sayede güncellemede kendiliğinden 10 köke kavuşur; göç adımı gerekmez.
     */
    public void initEt() {
        for (TreeBranch dal : TreeBranch.values()) {
            if (!nodes.containsKey(dal.id)) {
                TreeNode kok = new TreeNode();
                kok.id = dal.id;
                kok.isim = dal.isim;
                kok.parentId = null;
                kok.trait = new HashMap<>(dal.defaultTrait());
                kok.aktiflik = 0.5;   // kökler her zaman biraz aktif
                nodes.put(dal.id, kok);
            }
        }
    }

    /**
     * Belirli bir kök dalın "en aktif alt dalını" bulur.
     * Alt dal yoksa kökün kendisini döner (kökler her zaman erişilebilir).
     * Hibernasyondaki node'lar dışlanır.
     */
    public TreeNode baskinAlt(TreeBranch dal) {
        TreeNode kok = nodes.get(dal.id);
        if (kok == null) return null;

        // v4.0 BUG FIX: eskiden yalnızca 3 seviye taranıyordu (kök + child + grandchild).
        // catallan() herhangi bir derinlikte çocuk üretebildiği için 4. seviye ve altındaki
        // node'lar GÖRÜNMEZ kalıyordu: büyüyor, diske yazılıyor, node bütçesini yiyor ama
        // hiçbir çıktıyı etkilemiyordu. Artık tüm alt ağaç özyinelemeli taranır.
        Arama a = new Arama();
        a.enaktif = kok;
        a.maxA = kok.aktiflik * 0.5;   // kökün eşiği düşük — çocuklar öne çıkabilsin
        tara(kok, a, new HashSet<>(), 0);
        return a.enaktif;
    }

    /** Güvenlik tavanı — bozuk/döngülü JSON'da sonsuz özyineleme olmasın. */
    private static final int MAX_DERINLIK = 16;

    private static final class Arama {
        TreeNode enaktif;
        double maxA;
    }

    /** Alt ağacı özyinelemeli tara; en aktif (hibernasyonda olmayan) node'u bul. */
    private void tara(TreeNode n, Arama a, Set<String> gorulen, int derinlik) {
        if (n == null || n.childIds == null || derinlik > MAX_DERINLIK) return;
        for (String cid : n.childIds) {
            if (cid == null || !gorulen.add(cid)) continue;   // döngü koruması
            TreeNode c = nodes.get(cid);
            if (c == null) continue;
            if (!c.hibernation && c.aktiflik > a.maxA) {
                a.maxA = c.aktiflik;
                a.enaktif = c;
            }
            // Hibernasyondaki bir node'un altındaki uyanık çocuklar hâlâ aday olabilir
            tara(c, a, gorulen, derinlik + 1);
        }
    }

    /**
     * Aktif (hibernasyonda olmayan) tüm node'ların listesi. Debug/logging için.
     */
    public List<TreeNode> aktifNodeList() {
        List<TreeNode> r = new ArrayList<>();
        for (TreeNode n : nodes.values()) if (!n.hibernation) r.add(n);
        return r;
    }
}
