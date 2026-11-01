package com.photonpulse.candy.core;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.entity.CandyDoppelEntity;
import com.photonpulse.candy.entity.CandyEntities;
import com.photonpulse.candy.sessizlik.SessizlikState;
import com.photonpulse.candy.simulation.SimulatedPersonality;
import com.photonpulse.candy.simulation.SimulationManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * ZihinselProva (v3.1) — Candy'nin zihinsel provası / karar motoru.
 *
 * NİCEL (TruePersonality) + NİTEL (SimulatedPersonality/gölge) + İÇ SES (IcDusunce niyet/kanaat)
 * üçünden çıkan sonucu ALIR, aday eylemleri HAYALİ TESTTEN geçirir (her birinin bu oyuncuda
 * ne kadar etki edeceğini profilinden TAHMİN eder), en yüksek tahminliyi UYGULAR.
 *
 * Sonra gerçek tepkiyi ölçer → tahminini gerçekle karşılaştırır → öğrenme (isabet) güncellenir.
 * Yani Candy denemeden önce kafasında dener; yanılırsa bir dahakine daha iyi tahmin eder.
 */
public class ZihinselProva {

    private static final boolean DEBUG = Boolean.getBoolean("candy.debug");
    private static final int ARALIK = 100;                         // 5 sn kontrol
    private static final long COOLDOWN = DEBUG ? 200L : 900L;      // debug 10sn / normal 45 sn
    private static final double MIN_KOTULUK = 20.0;

    private static final Random RNG = new Random();

    /**
     * Aday eylemler.
     *
     * <p>v8.2: SIRA SONU'na üç yeni sınıf eklendi. Araya girmek yasak — {@code ordinal()}
     * her üyenin Q başlığında eylem indeksi olarak kullanılıyor ve diskte duruyor; sırayı
     * bozmak aylarca öğrenilmiş eylem değerlerini birbirine karıştırırdı.</p>
     *
     * <p>Son üçü {@link com.photonpulse.candy.beyin.Perde} ile <b>kapılı</b>: Kurul
     * ölçülen kalibrasyonla tırmanma hakkını kazanana kadar yasaklı görünürler.</p>
     */
    private enum Eylem {
        KLON_ARKA, KARANLIK, SES, SESSIZLIK, SORU, YAKIN_KLON,
        // ── v8.2, perde kademesiyle açılır ──
        ALTYAZI,   // kademe 1 — oyuncunun şüphelenmediği kanal
        RUTIN,     // kademe 2 — oyuncunun kendi rutini geri oynatılır
        BASLIK,    // kademe 3 — oyun penceresinin dışına taşan tek etki
        // ── v8.3 ──
        // IKILIK en SONA eklendi: ordinal() Q-kafasının eylem indeksi ve diske
        // yazılıyor; araya sıkıştırmak eski kayıtlardaki tüm eylem kredilerini
        // kaydırır (öğrendiği her şey yanlış eyleme bağlanır).
        IKILIK     // kademe 2 — 0/1'lerden sızan metin
    }

    /** Eylemin açılması için gereken en küçük perde kademesi. */
    private static int gerekenKademe(Eylem e) {
        return switch (e) {
            case ALTYAZI -> 1;
            case RUTIN   -> 2;
            case BASLIK  -> 3;
            case IKILIK  -> 2;
            default      -> 0;
        };
    }

    // Feedback bookkeeping (persist değil — canlı akıl)
    // v4.0: izAnlik = eylem ANINDAKI duyu vektoru. Gecikmeli odul geldiginde kredi
    // dogru duruma gitsin diye saklanir (kredi atamasi).
    private record Bekleyen(Eylem eylem, double tahmin, double korkuOnce, long uygulamaTick,
                            float[] izAnlik) {}
    private static final Map<UUID, Bekleyen> bekleyen = new HashMap<>();
    private static final Map<UUID, Long> sonProva = new HashMap<>();

    private static int tick = 0;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(ZihinselProva::onTick);
        Candy.LOG.info("[Candy] ZihinselProva aktif — hayali test + karar motoru (v3.1)");
    }

    private static void onTick(MinecraftServer server) {
        tick++;
        if (tick % ARALIK != 0) return;
        long simdi = server.overworld().getGameTime();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            try {
                TruePersonality tp = PersonalityManager.get(player);
                if (!tp.kurulumTamam) continue;
                if (tp.bossOldurulmeTick > 0) continue; // WIN sessizliği
                UUID id = player.getUUID();

                // 1) Önceki eylemin gerçek sonucunu ölç → öğren (+ Silahlanma Yarışı)
                geribildirim(player, tp, id, simdi);

                // v5: beyin burada bir kez okunur — hem emeklilik parolu hem bekleme
                // kapisi hem de eylem secimi ayni anlik goruntuyu kullansin.
                var beyin = com.photonpulse.candy.beyin.BeyinMotoru.get(id);

                // Silahlanma: emeklilik kalıcı olmasın — Candy zamanla varyasyon icat eder,
                // rastgele bir emekli mekaniği geri devreye alır.
                // v5 GOLGE: geri donus orani artik sabit degil. Bastirilmis oruntuler lobu
                // ne kadar aktifse, emekli edilmis bir mekanik o kadar cabuk geri doner —
                // "bastirilan geri doner" ilkesinin davranis duzeyindeki karsiligi.
                double golge = (beyin == null) ? 0.5
                    : beyin.lobCikisi(com.photonpulse.candy.beyin.Lob.GOLGE);
                if (!tp.emekliMekanikler.isEmpty() && RNG.nextDouble() < (0.02 + 0.10 * golge)) {
                    var it = tp.emekliMekanikler.iterator();
                    String geri = it.next();
                    it.remove();
                    tp.mekanikAdaptasyon.put(geri, 0.0);
                }

                if (tp.psikolojiKotulugu < MIN_KOTULUK) continue;
                // v5 BEYINCIK: sabit bekleme suresi OGRENILEN bir kapiya donusur.
                // Rehavete girmis oyuncuyu erken vurur (x0,4'e kadar), tetikteki oyuncuyu
                // bekletir (x1,6'ya kadar). "Ne yapacagim" kadar "ne zaman" da ogrenilir.
                long etkinCooldown = COOLDOWN;
                if (beyin != null) {
                    double bey = beyin.lobCikisi(com.photonpulse.candy.beyin.Lob.BEYINCIK);
                    etkinCooldown = (long) Math.max(40, COOLDOWN * (1.6 - 1.2 * bey));
                }
                // v8 ÖFKE: NE YAPACAĞI kadar NE ZAMAN'ı da tahmin edilemez olsun.
                // Öfke yoksa jitter de yok (v7 davranışı birebir); tam öfkede bekleme
                // süresi ±%40 oynar, yani oyuncu Candy'nin ritmini de sayamaz.
                if (tp.candyOfke > 0.001) {
                    double j = 1.0 + (RNG.nextDouble() - 0.5) * 0.8 * Math.min(1.0, tp.candyOfke);
                    etkinCooldown = (long) Math.max(40, etkinCooldown * j);
                }
                Long son = sonProva.get(id);
                if (son != null && simdi - son < etkinCooldown) continue;

                // 2) 3 kaynağı topla
                SimulatedPersonality sp = SimulationManager.get(id);

                // 3) DENEY modu mu, KORKU modu mu? (idea 3 — Aktif Deney / Denek 239)
                // Bayesçi belirsizlik yüksekse Candy hangi hipotezin doğru olduğunu bilmiyor →
                // bazen korkuyu değil, hipotezleri en iyi AYIRAN eylemi seçer (bilgi kazancı).
                double belirsizlik = tp.hipotezBelirsizligi();
                boolean deneyModu = belirsizlik > 0.5 && RNG.nextDouble() < 0.5;

                // ── v6: KARARI KURUL VERİR ──
                // Sezgisel değerler TÜM üyeler için aynıdır (dünyanın bir özelliğidir).
                // Üyeleri ayıran şey öğrendikleri Q ve ona ne kadar güvendikleridir;
                // hakem bu on bir görüşü ÖLÇÜLEN sicillerine göre tartıp birleştirir.
                final Eylem[] eylemler = Eylem.values();
                double[] sezgiselDeger = new double[eylemler.length];
                boolean[] yasakli = new boolean[eylemler.length];
                for (int i = 0; i < eylemler.length; i++) {
                    yasakli[i] = tp.emekliMekanikler.contains(eylemler[i].name());
                    sezgiselDeger[i] = deneyModu
                        ? ayirtEdicilik(eylemler[i], tp) : tahminEt(eylemler[i], tp, sp);
                    // NOT: keşif gürültüsü ARTIK BURADA DEĞİL. Ortak diziye eklenirse on
                    // bir üye aynı sapmayı alır ve ayrışamaz; Kurul.gorusler her üyeye
                    // kendi sapmasını verir. Buradaki tek istisna kurulun hazır olmadığı
                    // yedek yoldur — orada zaten tek bir seçim yapılıyor.
                }

                var kurul = com.photonpulse.candy.beyin.BeyinMotoru.kurul(id);

                // ── v8.2 PERDE: tırmanma hakkı KAZANILIR ──
                // Kademe ölçülen kalibrasyondan geliyor ve asla düşmüyor. Kapalı sınıflar
                // yasaklı dizisine giriyor, yani Kurul onları hiç önermiyor ve o eylemler
                // için Q öğrenmesi de hiç başlamıyor — açıldıklarında temiz başlarlar.
                int kademe = com.photonpulse.candy.beyin.Perde.kademeGuncelle(tp, kurul);
                for (int i = 0; i < eylemler.length; i++) {
                    if (gerekenKademe(eylemler[i]) > kademe) yasakli[i] = true;
                }

                com.photonpulse.candy.beyin.Karar[] gorusler = null;
                com.photonpulse.candy.beyin.Hakem.Sonuc hakemSonuc = null;
                if (kurul != null) {
                    gorusler = kurul.gorusler(sezgiselDeger, yasakli, tp.candyOfke);
                    hakemSonuc = kurul.hakem.karar(gorusler, sezgiselDeger, yasakli, tp.candyOfke);
                }

                Eylem enIyi = null;
                if (hakemSonuc != null && hakemSonuc.eylem >= 0
                    && hakemSonuc.eylem < eylemler.length) {
                    enIyi = eylemler[hakemSonuc.eylem];
                } else {
                    // Kurul henüz hazır değil (sunucu yeni açıldı) — sezgisele düş.
                    double enY = -1;
                    for (int i = 0; i < eylemler.length; i++) {
                        if (yasakli[i]) continue;
                        double u = sezgiselDeger[i] + RNG.nextDouble() * 0.1;
                        if (u > enY) { enY = u; enIyi = eylemler[i]; }
                    }
                }
                if (enIyi == null) { tp.emekliMekanikler.clear(); continue; } // hepsi emekliyse reset

                // Kararı puanlanmak üzere sakla — 10 sn sonra geribildirim ölçecek.
                if (kurul != null && gorusler != null) {
                    kurul.bekleyenKararlar = gorusler;
                    kurul.bekleyenEylem = enIyi.ordinal();
                    // Hakem KENDİ beklentisiyle puanlanır; sezgiselinkiyle değil.
                    kurul.bekleyenBeklenti = (hakemSonuc != null)
                        ? hakemSonuc.beklenenDeger : sezgiselDeger[enIyi.ordinal()];
                }
                double fearTahmin = tahminEt(enIyi, tp, sp); // geribildirim korku beklentisi

                // 4) Kararı UYGULA (overworld saati — SessizlikState tutarlılığı için)
                ServerLevel world = (ServerLevel) player.level();
                uygula(enIyi, player, world, simdi);
                sonProva.put(id, simdi);
                float[] izAnlik = (beyin == null || beyin.girdi == null)
                    ? null : beyin.girdi.clone();
                bekleyen.put(id, new Bekleyen(enIyi, fearTahmin, tp.korkuSkoru, simdi, izAnlik));
                Candy.LOG.info("[Candy] ZihinselProva[{}] kanaat={} mod={} → eylem={} (tahmin={}) | {}",
                    player.getName().getString(), tp.candyKanaati,
                    deneyModu ? "DENEY" : "korku", enIyi, String.format("%.2f", fearTahmin),
                    hakemSonuc == null ? "kurul yok" : hakemSonuc.gerekce);
                if (hakemSonuc != null && hakemSonuc.baskinUye >= 0 && gorusler != null) {
                    var bas = gorusler[hakemSonuc.baskinUye];
                    Candy.LOG.debug("[Candy]   baskin üye u{} → {} · {}",
                        bas.uye, (bas.eylem >= 0 && bas.eylem < eylemler.length)
                            ? eylemler[bas.eylem] : "-", bas.gerekce());
                }

            } catch (Throwable t) {
                Candy.LOG.debug("[Candy] ZihinselProva hata: {}", t.getMessage());
            }
        }
    }

    /** Önceki eylemden ~10 sn sonra gerçek korku değişimini ölç → öğrenme + Silahlanma. */
    private static void geribildirim(ServerPlayer player, TruePersonality tp, UUID id, long simdi) {
        Bekleyen b = bekleyen.get(id);
        if (b == null) return;
        if (simdi - b.uygulamaTick() < 200) return; // 10 sn bekle
        bekleyen.remove(id);

        double gercekDelta = tp.korkuSkoru - b.korkuOnce();

        // ── v4.0: RL DONGUSUNU KAPAT — hata artik AGIRLIKLARA akiyor ──
        // Once yalnizca tp.ogrenmeHizi guncelleniyordu (gosterge degeri); simdi gozlenen
        // sonuc, eylem anindaki duyu vektoruyle birlikte Q basligini egitiyor.
        // NOT: Q başlığı eğitimi artık BURADA DEĞİL, Kurul.sonucOlcum içinde — on bir
        // üyenin HER BİRİ kendi özellik vektörüyle ve kendi hızıyla öğrenir. Burada tek
        // beyni eğitmek, diğer on üyenin başlığını sonsuza dek boş bırakırdı.
        // Ödül yayını ise gövdeye özgüdür: güçlü bir sonuç tüm beynin öğrenme hızını
        // geçici olarak artırır.
        try {
            var beyin = com.photonpulse.candy.beyin.BeyinMotoru.get(id);
            if (beyin != null && Math.abs(gercekDelta) > 6.0) {
                beyin.modulator = Math.min(3.0f, beyin.modulator * 1.6f);
            }
        } catch (Throwable ignored) {}

        // Tahmin (0-1.5 arası utility) → beklenen korku artışı (~0-15)
        double beklenen = b.tahmin() * 10.0;
        double hata = Math.abs(gercekDelta - beklenen);

        // ── v6 KURUL PUANLAMASI: kanıtın üretildiği yer ──
        // Her üyenin tavsiyesi ve biçtiği değer, gözlenen sonuçla karşılaştırılır.
        // Hakemin ağırlıkları buradan — beyandan değil, ölçümden — beslenir.
        try {
            var kr = com.photonpulse.candy.beyin.BeyinMotoru.kurul(id);
            if (kr != null) {
                kr.sonucOlcum(b.eylem().ordinal(),
                    Math.max(-0.5, Math.min(1.5, gercekDelta / 10.0)));
            }
        } catch (Throwable ignored) {}

        // ── v5 YANKI: kararin izi ──
        // Bu tek satir, "daha once verilmis kararlari sorgulayarak anlam cikarma"nin
        // TEK yazma noktasidir; eylem, gozlenen sonuc ve ongoru hatasi zaten kapsamda.
        try {
            var by = com.photonpulse.candy.beyin.BeyinMotoru.get(id);
            if (by != null && by.kararIzi != null) {
                by.kararIzi.kaydet(b.eylem().ordinal(),
                    (float) Math.max(-0.5, Math.min(1.5, gercekDelta / 10.0)), (float) hata);
            }
        } catch (Throwable ignored) {}
        boolean isabetli = hata < 5.0;
        // Öğrenme: isabetliyse Candy'nin öngörüsü güçlenir
        tp.ogrenmeHizi = TruePersonality.kayma(tp.ogrenmeHizi, isabetli ? 100 : 0, 0.15);

        // idea 12: Silahlanma Yarışı — eylem etkisizse oyuncu adapte oldu
        String ea = b.eylem().name();
        if (gercekDelta < 1.5) {
            double a = tp.mekanikAdaptasyon.merge(ea, 1.0, Double::sum);
            if (a >= 3 && !tp.emekliMekanikler.contains(ea)) {
                tp.emekliMekanikler.add(ea);
                if (tp.bossOldurulmeTick == 0) {
                    player.displayClientMessage(Component.translatable("candy.silahlanma." + RNG.nextInt(2))
                        .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC), false);
                }
                Candy.LOG.info("[Candy] Silahlanma[{}] '{}' emekliye ayrıldı — oyuncu adapte oldu",
                    player.getName().getString(), ea);
            }
        } else if (gercekDelta > 4) {
            // Etkili → adaptasyon geriler, emeklilikten çıkabilir (yeniden devreye)
            double a = tp.mekanikAdaptasyon.merge(ea, -1.0, Double::sum);
            if (a < 1) tp.emekliMekanikler.remove(ea);
        }
        Candy.LOG.debug("[Candy] ZihinselProva geri: eylem={} beklenen={} gercek={} isabet={}",
            b.eylem(), String.format("%.1f", beklenen), String.format("%.1f", gercekDelta), isabetli);
    }

    /**
     * idea 3: Eylemin rakip hipotezleri ne kadar AYIRT ettiği (bilgi kazancı proxy).
     * Her eylemin hipotez-başına beklenen tepki vektörü var; bu vektörün, güncel hipotez
     * ağırlıklarına göre VARYANSI yüksekse eylem hipotezleri iyi ayırır → deney için ideal.
     */
    private static double ayirtEdicilik(Eylem e, TruePersonality tp) {
        double[] r = tepkiVektoru(e); // [korkak, cesur, merakli, kayitsiz]
        double wK = tp.hipotezAgirliklari.getOrDefault("korkak", 0.25);
        double wC = tp.hipotezAgirliklari.getOrDefault("cesur", 0.25);
        double wM = tp.hipotezAgirliklari.getOrDefault("merakli", 0.25);
        double wZ = tp.hipotezAgirliklari.getOrDefault("kayitsiz", 0.25);
        double ort = r[0]*wK + r[1]*wC + r[2]*wM + r[3]*wZ;
        return wK*sq(r[0]-ort) + wC*sq(r[1]-ort) + wM*sq(r[2]-ort) + wZ*sq(r[3]-ort);
    }

    private static double sq(double x) { return x * x; }

    /** Eylem → hipotez-başına beklenen korku tepkisi. */
    private static double[] tepkiVektoru(Eylem e) {
        return switch (e) {
            case KLON_ARKA  -> new double[]{1.4, 0.5, 0.8, 0.3};
            case YAKIN_KLON -> new double[]{1.5, 0.6, 0.7, 0.3};
            case KARANLIK   -> new double[]{1.3, 0.6, 1.0, 0.4};
            case SES        -> new double[]{1.0, 0.5, 0.7, 0.4};
            case SESSIZLIK  -> new double[]{0.9, 1.3, 0.6, 0.5};
            case SORU       -> new double[]{0.6, 0.5, 1.1, 0.4};
            // v8.2: kayıtsız oyuncuyu AYIRT EDEN eylemler. Klon/karanlık kayıtsıza
            // hiç işlemiyor (0,3-0,4); bu üçü ona da dokunuyor, yani deney modunda
            // hipotezleri en iyi ayıran seçenekler bunlar.
            case ALTYAZI    -> new double[]{0.8, 0.7, 1.2, 0.9};
            case RUTIN      -> new double[]{1.2, 1.1, 1.0, 0.9};
            case BASLIK     -> new double[]{0.9, 1.0, 1.1, 1.2};
            // IKILIK: meraklıyı AYIRT EDEN eylem. Korkak "bir şey yazdı" diye kaçar,
            // cesur görmezden gelir, kayıtsız hiç bakmaz — ama meraklı oturup çözer
            // ve çözdüğü şey "I SEE YOU" olur. En yüksek değer merakta.
            case IKILIK     -> new double[]{1.0, 0.6, 1.5, 0.5};
        };
    }

    /**
     * HAYALİ TEST — bu eylemin BU oyuncuda ne kadar etki edeceğini profilinden tahmin et.
     * Tetikleyici profili (neyden korkuyor) + tepki profili (nasıl karşılık veriyor) +
     * kanaat + öngörülebilirlik kullanılır.
     */
    private static double tahminEt(Eylem e, TruePersonality tp, SimulatedPersonality sp) {
        double klonT = tetikNorm(tp, "klon");
        double karanlikT = tetikNorm(tp, "karanlik");
        String tepki = tp.baskinTepki();      // saldiri / kacma / donma / gormezden
        String kanaat = tp.candyKanaati;
        double ongoruF = tp.ongorulebilirlik / 100.0;

        // Tepkiye göre klon eyleminin çarpanı
        double klonTepki = switch (tepki) {
            case "saldiri" -> 0.5;   // hep klonları kesiyor → az etkili
            case "kacma" -> 1.4;     // kaçıyor → klon çok etkili
            case "donma" -> 1.2;
            default -> 1.0;          // gormezden → orta
        };

        return switch (e) {
            case KLON_ARKA -> (0.4 + klonT) * klonTepki;
            case KARANLIK  -> (0.3 + karanlikT * 1.2);
            case SES       -> 0.5 + (kanaat.equals("saklaniyor") || kanaat.equals("korkuyor") ? 0.4 : 0);
            case SESSIZLIK -> (kanaat.equals("meydan_okuyor") ? 1.3 : 0.4) + ongoruF * 0.5;
            case SORU      -> 0.5 + (tp.aktifNiyet.equals("bekliyor")
                                     || tp.aktifNiyet.equals("donuyor") ? 0.5 : 0);
            case YAKIN_KLON-> (kanaat.equals("korkuyor") ? 1.4 : 0.7) * klonTepki;
            // ── v8.2 ──
            // ALTYAZI: chat'e güvenmeyi bırakmış oyuncuda değerli. Meta farkındalık
            // yüksekse oyuncu kanalları ayırt ediyor → beklenmedik kanal daha çok iş görür.
            case ALTYAZI   -> 0.45 + (tp.metaFarkindalik / 100.0) * 0.6;
            // RUTIN: tam olarak ÖNGÖRÜLEBİLİRLİĞİ ödüllendiren tek eylem. Rastgele oynayan
            // oyuncuda hatırlanacak rutin yok, değeri düşer; alışkanlık kölesinde zirve.
            case RUTIN     -> 0.2 + ongoruF * 1.3;
            // BASLIK: oyun penceresinin dışına taşan tek etki. Oyuncu "oyunun içinde
            // kalıyor" varsayımını bıraktıysa (meta + paranoya) karşılığı büyük.
            case BASLIK    -> 0.3 + (tp.paranoyaSkoru / 100.0) * 0.5
                                  + (tp.metaFarkindalik / 100.0) * 0.5;
            // IKILIK: şifreyle oynamış oyuncuda değerli. tuzakIlgisi, oyuncunun
            // 0/1 dizilerini çözmeye oturduğunu ölçüyor; oturmayan oyuncu için
            // bu eylem ekranda akıp geçen bir gürültüden fazlası değil.
            case IKILIK    -> 0.3 + Math.max(0.0, Math.min(1.0, tp.tuzakIlgisi)) * 0.9
                                  + (tp.metaFarkindalik / 100.0) * 0.3;
        };
    }

    private static double tetikNorm(TruePersonality tp, String tur) {
        if (tp.korkuTetikleyicileri.isEmpty()) return 0.3;
        double val = tp.korkuTetikleyicileri.getOrDefault(tur, 0.0);
        double max = 1.0;
        for (double v : tp.korkuTetikleyicileri.values()) max = Math.max(max, v);
        return val / max; // 0-1
    }

    // ─── Eylem uygulama (hafif, kendine yeten) ───
    private static void uygula(Eylem e, ServerPlayer player, ServerLevel world, long owSimdi) {
        switch (e) {
            case KLON_ARKA -> klonSpawn(player, world, false);
            case YAKIN_KLON -> klonSpawn(player, world, true);
            case KARANLIK -> {
                player.addEffect(new MobEffectInstance(
                    MobEffects.DARKNESS, 80, 0, true, false, false));
                world.playSound(null, player.blockPosition(),
                    SoundEvents.AMBIENT_CAVE.value(), SoundSource.AMBIENT, 1.0f, 0.6f);
            }
            case SES -> world.playSound(null, player.blockPosition(),
                SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 0.8f, 0.7f);
            case SESSIZLIK -> SessizlikState.baslat(owSimdi, 400); // 20 sn build-up (overworld saati)
            case SORU -> IcDusunce.soruSor(player);
            // ── v8.2 ──
            case ALTYAZI -> com.photonpulse.candy.altyazi.Altyazi.gonder(player,
                RNG.nextInt(com.photonpulse.candy.altyazi.Altyazi.ALTYAZI_SAYISI), 4000);
            case RUTIN -> com.photonpulse.candy.rutin.RutinTekrari.oynat(
                player, PersonalityManager.get(player));
            case BASLIK -> com.photonpulse.candy.altyazi.Altyazi.baslik(player,
                RNG.nextInt(com.photonpulse.candy.altyazi.Altyazi.BASLIK_SAYISI));
            // ── v8.3 ──
            case IKILIK -> com.photonpulse.candy.sifre.IkilikFisilti.fisildat(
                player, PersonalityManager.get(player));
        }
    }

    private static void klonSpawn(ServerPlayer player, ServerLevel world, boolean yakin) {
        // Cap — 8+ klon yakında varken yenisini spawn etme (CloneManager + boss + bu = birikim)
        var kutu = new net.minecraft.world.phys.AABB(player.blockPosition()).inflate(24);
        int mevcut = world.getEntitiesOfClass(CandyDoppelEntity.class, kutu,
            e -> e.isAlive() && e.getTags().contains("candy_clone")).size();
        if (mevcut >= 8) return;

        double yaw = Math.toRadians(player.getYRot());
        double mesafe = yakin ? 3.0 : 6.0;
        // yakin = önünde, uzak = arkasında
        double yon = yakin ? 1.0 : -1.0;

        // ── v8.2: BAKILMAYAN YÖN ──
        // Mirror.txt'nin en güçlü mekaniği. Sürekli arkasını kollayan oyuncuda "arka"
        // artık kör nokta DEĞİL: klonu arkaya koymak onun kontrol ettiği yere koymaktır.
        // Eksen yüksekse yön ters çevrilir — klon öne, yani gerçek kör noktaya gider.
        // Hiç dönmeyen oyuncuda eski davranış aynen kalır.
        TruePersonality tpYon = PersonalityManager.get(player);
        if (tpYon != null && tpYon.arkayaBakma > 0.55
            && RNG.nextDouble() < tpYon.arkayaBakma) {
            yon = -yon;
        }
        double x = player.getX() - Math.sin(yaw) * mesafe * yon;
        double z = player.getZ() + Math.cos(yaw) * mesafe * yon;
        BlockPos pos = BlockPos.containing(x, player.getY(), z);
        var spawned = CandyEntities.DOPPEL.spawn(world, pos, EntitySpawnReason.MOB_SUMMONED);
        if (spawned instanceof CandyDoppelEntity klon) {
            klon.setOwnerUuid(player.getUUID());
            klon.addTag("candy_clone");
            klon.setPersistenceRequired();
        }
    }

    public static void oyuncuAyrildi(UUID uuid) {
        bekleyen.remove(uuid);
        sonProva.remove(uuid);
    }
}
