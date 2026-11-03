package com.photonpulse.candy.client;

import com.photonpulse.candy.Candy;
//? if fabric {
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
//?}
import net.minecraft.client.Minecraft;

import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.util.Locale;

/**
 * KlavyeIsigi (v9.0.0, istemci) — oyuncunun GERÇEK klavyesindeki ışığı oynatır.
 *
 * <p>{@link PencereBasligi} ile aynı aile: etki oyun penceresinin dışında görünür ama
 * oyun süreci dışına çıkılmıyor. Yeni pencere yok, terminal yok, dosya yok, ağ yok,
 * yeni bağımlılık yok — kullanılan tek şey JDK'nın içindeki {@link Toolkit}.</p>
 *
 * <h3>NE TETİKLENİYOR</h3>
 * Klavyedeki <b>kilit LED'i</b> (Scroll Lock; yoksa Num Lock). {@code setLockingKeyState}
 * tuşun gerçek kilit durumunu değiştirir, işletim sistemi de LED'i ona göre yakar. Yani
 * ışık fiziksel olarak yanıp sönüyor — ekranda bir efekt değil.
 *
 * <h3>FN + F5 NEDEN KULLANILMIYOR</h3>
 * Laptop'lardaki {@code Fn + F5} türü arka aydınlatma tuşu klavyenin gömülü denetleyicisinde
 * (EC) ya da üreticinin hotkey sürücüsünde çözülür; sentetik tuş girişi (AWT {@code Robot}
 * → Windows {@code SendInput}) o katmana hiç ulaşmaz, dolayısıyla arka aydınlatma
 * <b>değişmez</b>. AWT'de {@code VK_FN} diye bir tuş da yok. Yollanan düz {@code F5} ise
 * odaktaki pencereye gider: Minecraft'ta F5 <i>kamera açısı</i> tuşudur — korku anında
 * oyuncuyu üçüncü şahsa atardı; oyuncu alt-tab'daysa tuş bambaşka bir uygulamaya düşerdi.
 * Global sentetik giriş enjeksiyonu ayrıca anticheat ve AV sezgilerinin tam olarak aradığı
 * davranıştır. Kilit LED'i aynı işi (ışığı yakmak) bu üç riskin hiçbirini almadan yapıyor.
 *
 * <p>Dürüst dipnot: {@code setLockingKeyState}'in Windows uygulaması da kilit durumunu OS
 * seviyesinde çevirir (muhtemelen kilit tuşunu kendisi sentezleyerek). Fark mekanizmada
 * değil, <b>hangi tuşta</b>: Scroll Lock'a Minecraft'ta hiçbir şey bağlı değil ve başka
 * uygulamalar da ona tepki vermiyor; F5 ise hem oyunun hem tarayıcının hem IDE'nin tepki
 * verdiği bir tuş. Num Lock yedeği numpad'i etkilediği için {@link #ekranAcik()} kapısıyla
 * yazma anlarında hiç dokunulmuyor.</p>
 *
 * <h3>ŞİDDET</h3>
 * Kilit LED'i tek parlaklıkta yanar, kısılamaz. Bu yüzden "şiddet" genlikle değil
 * <b>frekansla</b> veriliyor: {@link #kademe(int)} yükseldikçe nabız sıklaşır (kademe 1 ≈
 * 5,5 sn'de bir; kademe 10 ≈ 1,5 sn'de bir). RGB klavyelerde gerçek renk ve parlaklık için
 * üreticinin SDK'sı gerekir (Razer Chroma'nın localhost REST'i, SteelSeries GameSense) —
 * o ayrı bir iş, burada yok.
 *
 * <h3>SINIRLAR — dürüst liste</h3>
 * <ul>
 *   <li>Yalnızca <b>Windows</b>. {@code setLockingKeyState} macOS'ta ve çoğu X11
 *       kurulumunda desteklenmiyor; dahası macOS'ta GLFW oyununun içinde AWT'yi ayağa
 *       kaldırmak JVM'i düşürebiliyor. Windows dışında sınıf AWT'ye hiç dokunmadan
 *       kendini kapatıyor.</li>
 *   <li>Klavyede Scroll Lock / Num Lock LED'i yoksa (birçok dizüstü) görünür bir şey
 *       olmaz — mekanik sessizce atlanır, oyun etkilenmez.</li>
 *   <li>Arka aydınlatma (klavyenin altındaki ışık) bu yolla açılamaz; gerekçe yukarıda.</li>
 * </ul>
 *
 * <h3>GERİ ALMA</h3>
 * Her yanıp sönme kendi öncesindeki duruma dönüyor; ayrıca ilk dokunuştan önceki LED
 * durumu {@link #ozgun} içinde tutuluyor ve sunucudan çıkışta / oyun kapanışında
 * {@link #geriAl()} onu zorla geri yazıyor. Diske hiçbir şey yazılmadığı için çökme
 * hâlinde de kalıcı iz kalmıyor: kilit durumu oyuncunun tek tuşla düzelteceği, yeniden
 * başlatmada da sıfırlanan bir şey.
 */
//? if fabric {
@Environment(EnvType.CLIENT)
//?}
public final class KlavyeIsigi {

    private KlavyeIsigi() {}

    /** Önce Scroll Lock: hiçbir şey onu kullanmıyor. Num Lock yedek — numpad'i etkiler. */
    private static final int[] ADAYLAR = { KeyEvent.VK_SCROLL_LOCK, KeyEvent.VK_NUM_LOCK };

    /** Nabız aralığı: kademe 1 → 5550 ms, kademe 10 → 1500 ms. */
    private static final long ARALIK_TABAN_MS = 6000L;
    private static final long ARALIK_ADIM_MS  = 450L;
    private static final long ARALIK_ALT_MS   = 1500L;

    /** Yanıp sönme süreleri. Kısa: fark edilir ama kilit durumu neredeyse hiç bozulmaz. */
    private static final long DARBE_MS = 90L;
    private static final long NABIZ_MS = 140L;
    /** Bir istekte en fazla bu kadar yanıp sönme — sunucu ne derse desin strobe olmasın. */
    private static final int DARBE_UST = 3;

    /** İşçi thread'in beklediği kilit; kademe/darbe değişince notify ediliyor. */
    private static final Object KILIT = new Object();

    /** Seçilen kilit tuşu. -1 = henüz seçilmedi / bulunamadı. */
    private static volatile int tus = -1;
    /** Kalıcı devre dışı: kapalı ayar, Windows değil, desteklenmeyen tuş ya da ilk hata. */
    private static volatile boolean kapali = false;
    /** Candy dokunmadan önceki LED durumu. null = henüz dokunulmadı. */
    private static volatile Boolean ozgun = null;

    private static volatile int kademe = 0;
    private static int bekleyenDarbe = 0;

    /**
     * İstemci açılışında bir kez. AWT'ye burada dokunulmuyor: toolkit kurulumu ve tuş
     * yoklaması işçi thread'inde yapılıyor ki render thread'i hiçbir hâlde beklemesin.
     */
    public static void kur() {
        if (!CandyMixinConfig.klavyeIsigi) {
            kapali = true;
            Candy.LOG.info("[Candy] Klavye ışığı kapalı (klavyeIsigi: false)");
            return;
        }
        String os = System.getProperty("os.name", "");
        if (!os.toLowerCase(Locale.ROOT).startsWith("windows")) {
            kapali = true;
            Candy.LOG.info("[Candy] Klavye ışığı atlandı — yalnızca Windows ({})", os);
            return;
        }
        Thread t = new Thread(KlavyeIsigi::dongu, "Candy-KlavyeIsigi");
        t.setDaemon(true);
        t.start();
    }

    /** Ani korku — kısa seri yanıp sönme. Çağıran thread beklemiyor. */
    public static void darbe() {
        darbe(2);
    }

    public static void darbe(int adet) {
        if (kapali || adet <= 0) return;
        synchronized (KILIT) {
            bekleyenDarbe = Math.min(DARBE_UST, Math.max(bekleyenDarbe, adet));
            KILIT.notifyAll();
        }
    }

    /**
     * Sürekli nabız kademesi (0–10) — {@link KaranlikState} ile aynı sayı. 0 = sustur.
     * Oyun içi ışık düştükçe gerçek klavye ışığı da sıklaşan bir nabza giriyor.
     */
    public static void kademe(int k) {
        if (kapali) return;
        int yeni = Math.max(0, Math.min(10, k));
        synchronized (KILIT) {
            if (kademe == yeni) return;
            kademe = yeni;
            KILIT.notifyAll();
        }
    }

    /** Sunucudan çıkışta ve oyun kapanışında: nabzı durdur, LED'i özgün durumuna getir. */
    public static void geriAl() {
        synchronized (KILIT) {
            kademe = 0;
            bekleyenDarbe = 0;
            KILIT.notifyAll();
        }
        Boolean g = ozgun;
        if (g == null || tus < 0) return;
        try {
            Toolkit.getDefaultToolkit().setLockingKeyState(tus, g);
            ozgun = null;
            Candy.LOG.debug("[Candy] Klavye ışığı geri alındı");
        } catch (Throwable t) {
            Candy.LOG.debug("[Candy] Klavye ışığı geri alınamadı: {}", t.getMessage());
        }
    }

    // ------------------------------------------------------------------ işçi

    private static void dongu() {
        if (!tusSec()) {
            kapali = true;
            Candy.LOG.info("[Candy] Klavye ışığı atlandı — kilit LED'i yok/desteklenmiyor");
            return;
        }
        Candy.LOG.info("[Candy] Klavye ışığı aktif — kilit LED'i (tus {})", tus);
        try {
            while (!kapali) {
                int darbeSayisi;
                int k;
                synchronized (KILIT) {
                    while (!kapali && bekleyenDarbe == 0 && kademe == 0) KILIT.wait();
                    darbeSayisi = bekleyenDarbe;
                    bekleyenDarbe = 0;
                    k = kademe;
                }
                if (kapali) break;

                for (int i = 0; i < darbeSayisi && !kapali; i++) {
                    yanipSon(DARBE_MS);
                    if (i + 1 < darbeSayisi) Thread.sleep(70L);
                }

                if (!kapali && kademe > 0) {
                    yanipSon(NABIZ_MS);
                    long bekle = Math.max(ARALIK_ALT_MS,
                        ARALIK_TABAN_MS - (long) k * ARALIK_ADIM_MS);
                    synchronized (KILIT) {
                        if (bekleyenDarbe == 0 && kademe > 0) KILIT.wait(bekle);
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Throwable t) {
            Candy.LOG.debug("[Candy] Klavye ışığı döngüsü durdu: {}", t.getMessage());
        }
    }

    /** Desteklenen ilk kilit tuşunu seçer. Toolkit ilk kez burada ayağa kalkıyor. */
    private static boolean tusSec() {
        for (int aday : ADAYLAR) {
            try {
                Toolkit.getDefaultToolkit().getLockingKeyState(aday);
                tus = aday;
                return true;
            } catch (Throwable ignored) {
                // UnsupportedOperationException / HeadlessException — sıradaki adaya geç
            }
        }
        return false;
    }

    /** Bir kez yanıp söner: durumu tersine çevirip geri koyar, yani kilit durumu korunur. */
    private static void yanipSon(long sureMs) throws InterruptedException {
        if (tus == KeyEvent.VK_NUM_LOCK && ekranAcik()) return;
        Boolean onceki = oku();
        if (onceki == null) return;
        yaz(!onceki);
        Thread.sleep(sureMs);
        yaz(onceki);
    }

    /**
     * Num Lock yedeğinde: bir ekran (sohbet, tabela, kitap) açıkken dokunma — numpad'le
     * yazan oyuncunun tuşu kaybolmasın. Scroll Lock'ta bu kapı gereksiz, hiçbir şeyi
     * etkilemiyor. Alan render thread'inde yazılıyor, burada okunuyor: bayat değer
     * okumanın tek bedeli bir nabzın atlanması, o yüzden kilit gerekmiyor.
     */
    private static boolean ekranAcik() {
        try {
            Minecraft mc = Minecraft.getInstance();
            return mc != null && mc.screen != null;
        } catch (Throwable t) {
            return true;
        }
    }

    private static Boolean oku() {
        try {
            return Toolkit.getDefaultToolkit().getLockingKeyState(tus);
        } catch (Throwable t) {
            kapali = true;
            Candy.LOG.debug("[Candy] Kilit LED'i okunamadı, kapatıldı: {}", t.getMessage());
            return null;
        }
    }

    private static void yaz(boolean deger) {
        try {
            Toolkit kit = Toolkit.getDefaultToolkit();
            if (ozgun == null) ozgun = kit.getLockingKeyState(tus);
            kit.setLockingKeyState(tus, deger);
        } catch (Throwable t) {
            kapali = true;
            Candy.LOG.debug("[Candy] Kilit LED'i yazılamadı, kapatıldı: {}", t.getMessage());
        }
    }
}
