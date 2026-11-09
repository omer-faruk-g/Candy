package com.photonpulse.candy.client;

import com.photonpulse.candy.Candy;
//? if fabric {
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
//?}
import com.photonpulse.candy.platform.IstemciTick;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;

/**
 * CandyOtoTest — <b>yalnızca test</b>: v9.1.0/v10.0.0 yollarını <b>klavye olmadan</b>
 * kendi gerçek kod yollarından koşturur.
 *
 * <h3>NİYE VAR</h3>
 * Mekaniklerin bir kısmı insan eline bağlı: ekran-haritası F12'ye, dış kanal da saatlerce
 * oynamaya (perde kademesi + ~30 dk soğuma + %25 ihtimal). Otomatik bir koşumda — CI'da,
 * uzaktan, ya da elin başka işte olduğu her an — hiçbiri denenemiyordu. Bu sınıf tuşa
 * basan ve komut yazan eli taklit ediyor, çıktıyı da tamamen log'a döküyor; böylece koşum
 * ekrana bakmadan değerlendirilebiliyor.
 *
 * <h3>NİYE GERÇEK YOL</h3>
 * <ul>
 *   <li>Çekim: {@link EkranGonder#devBasim()} yalnızca "tuşa basıldı" diyor; bekleme
 *       süresi, {@code canSend} ve GPU okuma kapısı aynen işliyor.</li>
 *   <li>Komutlar: {@code sendCommand} ile gidiyor — oyuncu sohbete yazmış gibi, aynı
 *       paket. Sunucu tarafı test kancası değil kendi Brigadier ağacı.</li>
 *   <li>Dış kanal: {@code /candy mektup} yalnızca <b>sunucu</b> seyreltmesini atlıyor
 *       (ihtimal/soğuma/kademe). İstemci kapıları ({@code mektup}, {@code mektupEposta},
 *       {@code mektupUc}) olduğu gibi duruyor, yani config boşsa tek istek bile çıkmaz.</li>
 * </ul>
 *
 * <h3>NİYE OYUNCUYA SIZMAZ</h3>
 * İki sistem özelliği birlikte gerekiyor: {@code -Dcandy.debug=true} <b>ve</b>
 * {@code -Dcandy.ototest=true}; ikisi de JVM başlangıcında verilir, oyun içinden
 * değiştirilemez. Normal oyunda sınıf hiç kayıt bile olmuyor.
 *
 * <h3>SIRA (dünyaya girdikten sonraki tick)</h3>
 * <pre>
 *    60  config durumu
 *    70  /difficulty peaceful — oyuncu ölmesin: ölüm ekranı LED kapısını kapatır
 *    80  /candy perde 2      — dış kanal kapısı (MIN_KADEME_DIS=2)
 *   100  /candy durum
 *   140  /candy saat         — join paketi geldi mi + diyalog hangi dakikayı veriyor
 *   160  klavye LED darbe(3) — Caps Lock 3 kez hızlı yanıp söner
 *   180  klavye LED kademe 10 — ~1,6 sn'de bir nabız
 *   200  1. çekim            — sunucu haritayı BASMALI
 *   300  2. çekim (5 sn)     — istemci 3 sn kapısı geçer, SUNUCU 15 sn kapısı TUTMALI
 *   380  /candy mektup       — dış kanal: istemci yerel uca POST atmalı
 *   460  /candy saat sil     — istemci saati hiç bildirmemiş gibi
 *   500  /candy saat         — artık ESKİ YOLA düşmeli
 *   520  klavye LED kademe 0 — nabız durur
 *   540  /candy sifre        — GERÇEK masaüstüne candy_NN.txt
 *   560  bitti
 *   her tick (560'a kadar): duraklatma menüsü odakta 2 sn açık kalırsa ESC — oyuncu döndü
 * </pre>
 */
//? if fabric {
@Environment(EnvType.CLIENT)
//?}
public final class CandyOtoTest {

    private CandyOtoTest() {}

    private static final boolean AKTIF =
        Boolean.getBoolean("candy.debug") && Boolean.getBoolean("candy.ototest");

    /** Dünyada geçen tick; dünyadan çıkınca sıfırlanır. */
    private static int tick = -1;

    /** Dünya DIŞINDA geçen tick (menü/onay ekranları); dünyaya girince sıfırlanır. */
    private static int menuTick = -1;

    /** Son log'lanan menü ekranı — aynı ekranı tekrar tekrar yazmamak için. */
    private static String sonEkran = "";

    /** Duraklatma menüsü açık VE pencere odaktayken geçen tick; değilse -1. */
    private static int duraklatmaTick = -1;

    public static void register() {
        if (!AKTIF) return;
        IstemciTick.END_CLIENT_TICK.register(CandyOtoTest::istemciTick);
        Candy.LOG.warn("[Candy][ototest] AKTIF — dunyaya girince sira kendiliginden kosulacak");
    }

    private static void istemciTick(Minecraft mc) {
        if (mc == null || mc.player == null || mc.level == null) {
            tick = -1;
            menuSur(mc);
            return;
        }
        menuTick = -1;
        tick++;
        duraklatmaSur(mc);

        switch (tick) {
            case 60 -> Candy.LOG.info(
                "[Candy][ototest] config: ekranHaritasi={} tus={} mektup={} epostaDolu={} ucDolu={} uc={}",
                CandyMixinConfig.ekranHaritasi,
                CandyMixinConfig.ekranHaritasiTus,
                CandyMixinConfig.mektup,
                dolu(CandyMixinConfig.mektupEposta),
                dolu(CandyMixinConfig.mektupUc),
                CandyMixinConfig.mektupUc);

            // Test dunyasindaki zombiler oyuncuyu oldurebiliyor (12:20:13'te oldu); olum
            // ekrani acikken klavye isigi kapisi kapali kalir ve LED adimi yarim gorunur.
            // Komut izni yoksa sunucu hata yazar, sira yine de devam eder.
            case 70 -> komut(mc, "difficulty peaceful");
            case 80 -> komut(mc, "candy perde 2");
            case 100 -> komut(mc, "candy durum");
            case 140 -> komut(mc, "candy saat");

            case 160 -> {
                Candy.LOG.warn("[Candy][ototest] ADIM klavye-LED: ŞİMDİ GERÇEK klavyendeki "
                    + "Caps Lock ışığına bak — 3 kez yanıp sönecek (oyun penceresi odaktayken)");
                KlavyeIsigi.darbe(3);
            }
            case 180 -> {
                Candy.LOG.warn("[Candy][ototest] ADIM klavye-LED: sürekli nabız (kademe 10) "
                    + "— LED hızlı aralıklarla yanıp sönmeye devam edecek");
                KlavyeIsigi.kademe(10);
            }

            case 200 -> {
                Candy.LOG.info("[Candy][ototest] ADIM ekran-1: cekim tetikleniyor — "
                    + "sunucu log'unda 'kare haritaya basildi' BEKLENIYOR");
                EkranGonder.devBasim();
            }

            case 300 -> {
                Candy.LOG.info("[Candy][ototest] ADIM ekran-2: 5 sn sonra ikinci cekim — "
                    + "istemci 3 sn kapisi GECER, sunucu 15 sn kapisi TUTMALI "
                    + "(ikinci harita BEKLENMIYOR)");
                EkranGonder.devBasim();
            }

            case 380 -> {
                Candy.LOG.info("[Candy][ototest] ADIM mektup: dis kanal tetikleniyor");
                komut(mc, "candy mektup");
            }

            case 460 -> {
                Candy.LOG.info("[Candy][ototest] ADIM saat-geri-dusme: kayit siliniyor");
                komut(mc, "candy saat sil");
            }
            case 500 -> komut(mc, "candy saat");
            case 520 -> {
                Candy.LOG.info("[Candy][ototest] ADIM klavye-LED: nabız durduruluyor (kademe 0)");
                KlavyeIsigi.kademe(0);
            }

            case 540 -> {
                Candy.LOG.warn("[Candy][ototest] ADIM masaustu-sifre: GERCEK masaustune "
                    + "candy_NN.txt birakiliyor — SILINMEYECEK");
                komut(mc, "candy sifre");
            }

            case 560 -> Candy.LOG.warn("[Candy][ototest] SIRA BITTI");

            default -> { }
        }
    }

    /**
     * Dünya dışındayken ekranı <b>sürer</b>: hangi ekranda beklediğimizi log'a yazar ve
     * dünya açılışını kesen onay/uyarı ekranlarının ilk düğmesine basar.
     *
     * <p>Niye gerekli: {@code --quickPlaySingleplayer} dünyayı açmaya başlıyor, ama araya
     * bir onay ekranı girerse (deneysel bayrak/eksik datapack onayı, yedek-uyarısı,
     * sürüm uyarısı) oyun <b>sessizce</b> o ekranda duruyor — log'a tek satır düşmüyor.
     * Klavyesiz koşumda o tıklamayı atacak kimse yok, koşum sonsuza kadar bekliyordu.
     *
     * <p>Yalnız adı onay/uyarı ekranına benzeyen ekranlara dokunuyor; ana menüye,
     * dünya listesine, ayarlara karışmıyor.
     */
    private static void menuSur(Minecraft mc) {
        if (mc == null) return;
        menuTick++;
        Screen ekran = mc.screen;
        String ad = ekran == null ? "yok" : ekran.getClass().getSimpleName();
        if (!ad.equals(sonEkran)) {
            sonEkran = ad;
            Candy.LOG.info("[Candy][ototest] menu ekrani: {}", ad);
        }
        // 2 saniyede bir dene: ekran kurulurken ilk tick'te düğmeler henüz yok olabiliyor.
        if (ekran == null || menuTick % 40 != 0 || !onayEkrani(ad)) return;
        for (GuiEventListener c : ekran.children()) {
            if (c instanceof Button d) {
                Candy.LOG.warn("[Candy][ototest] onay ekrani '{}' -> '{}' dugmesine basiliyor",
                    ad, d.getMessage().getString());
                // 1.21.9'da girdi olaylari kayit oldu: onPress artik tetikleyen olayi istiyor.
                //? if >=1.21.9 {
                /*d.onPress(new net.minecraft.client.input.KeyEvent(
                    org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, 0, 0));
                */
                //?}
                //? if <1.21.9 {
                d.onPress();
                //?}
                return;
            }
        }
        Candy.LOG.warn("[Candy][ototest] onay ekrani '{}' ama basilacak dugme bulunamadi", ad);
    }

    /**
     * Odak geri gelince duraklatma menüsünü kapatır — oyuncunun oyuna dönüp ESC'ye basması.
     *
     * <p>Niye gerekli: oyun odağı kaybedince ({@code pauseOnLostFocus}, vanilla varsayılanı
     * açık) yarım saniye içinde duraklatma menüsünü açıyor; menü açıkken klavye ışığı kapısı
     * da kapalı. Gerçek oyuncu dönünce ESC'ye basar, klavyesiz koşumda o eli bu metot
     * taklit ediyor: {@link Screen#onClose()} ESC'nin çağırdığı yolun aynısı. 2 sn bekliyor
     * ki ölçer "oyun önde ama menü açık, ışık yok" aralığını da görebilsin. Zaman damgası
     * kapatmadan ÖNCE alınıyor: ondan sonraki her yanıp sönme menü kapandıktan sonradır.
     * Sıra bitince (tick 560) menüye hiç dokunmuyor.
     */
    private static void duraklatmaSur(Minecraft mc) {
        if (tick >= 560 || !(mc.screen instanceof PauseScreen) || !mc.isWindowActive()) {
            duraklatmaTick = -1;
            return;
        }
        if (++duraklatmaTick < 40) return;
        duraklatmaTick = -1;
        String an = String.format("%1$tT.%1$tL", System.currentTimeMillis());
        mc.screen.onClose();
        Candy.LOG.warn("[Candy][ototest] ADIM odak-donusu {}: duraklatma menusu odakta 2 sn "
            + "acikti -> ESC (oyuncu oyuna dondu)", an);
    }

    /** Ana menü/dünya listesi değil, "devam et?" diye soran ekran mı? */
    private static boolean onayEkrani(String ad) {
        return ad.contains("Confirm")
            || ad.contains("Experimental")
            || ad.contains("Backup")
            || ad.contains("Warning")
            || ad.contains("Alert")
            || ad.contains("Notice");
    }

    private static boolean dolu(String s) {
        return s != null && !s.isBlank();
    }

    /** Komutu oyuncu sohbete yazmış gibi yollar (aynı paket, aynı Brigadier yolu). */
    private static void komut(Minecraft mc, String k) {
        try {
            if (mc.player == null || mc.player.connection == null) {
                Candy.LOG.warn("[Candy][ototest] baglanti yok, komut atlandi: /{}", k);
                return;
            }
            mc.player.connection.sendCommand(k);
            Candy.LOG.info("[Candy][ototest] komut -> /{}", k);
        } catch (Throwable t) {
            Candy.LOG.warn("[Candy][ototest] komut '/{}' yollanamadi: {}", k, t.getMessage());
        }
    }
}
