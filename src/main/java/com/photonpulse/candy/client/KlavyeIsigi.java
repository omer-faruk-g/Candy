package com.photonpulse.candy.client;

import com.photonpulse.candy.Candy;
//? if fabric {
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
//?}
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFWNativeWin32;

import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.util.Locale;

/**
 * KlavyeIsigi (v9.0.0, istemci) — oyuncunun GERÇEK klavyesindeki ışığı oynatır.
 *
 * <p>{@link PencereBasligi} ile aynı aile: etki oyun penceresinin dışında görünür ama
 * oyun süreci dışına çıkılmıyor. Yeni pencere yok, terminal yok, dosya yok, ağ yok,
 * yeni bağımlılık yok — kullanılan tek şey JDK'nın içindeki {@link Toolkit} ve {@link Robot}.</p>
 *
 * <h3>NE TETİKLENİYOR</h3>
 * Klavyedeki <b>kilit LED'i</b>: önce Caps Lock, yoksa Scroll Lock, o da yoksa Num Lock.
 * Kilit tuşuna basılır ({@link Robot} → Windows {@code SendInput}); kilit durumu değişir,
 * işletim sistemi de LED'i ona göre yakar. Yani ışık fiziksel olarak yanıp sönüyor —
 * ekranda bir efekt değil.
 *
 * <p><b>Neden Caps Lock önde:</b> dizüstülerin çoğunda Scroll Lock / Num Lock ışığı yok.
 * Durum yine çevrilir (dış ölçerle görüldü: 14 geçiş) ama yanacak ampul olmadığı için
 * oyuncu hiçbir şey görmez. Caps Lock ışığı ise neredeyse her klavyede var. Bedeli:
 * Caps Lock yazmayı etkiler — bu yüzden yalnızca {@link #yazmaGuvenli()} anlarında
 * oynatılıyor.</p>
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
 * <p>Dürüst dipnot: burada da sentetik tuş basımı var. Fark mekanizmada değil, <b>hangi
 * tuşta</b>: kilit tuşlarına Minecraft'ta hiçbir şey bağlı değil; F5 ise hem oyunun hem
 * tarayıcının hem IDE'nin tepki verdiği bir tuş. Caps ve Num Lock yazmayı etkilediği için
 * {@link #yazmaGuvenli()} kapısı var: Windows'un ön plan penceresi oyunun kendisi değilse
 * ya da bir ekran açıksa (sohbet, tabela, kitap, ölüm ekranı) hiç dokunulmuyor —
 * alt-tab'daki bir uygulamanın şifre alanına ters büyüklükte harf düşmesin.</p>
 *
 * <p><b>Neden durum okunmuyor:</b> ilk sürüm {@code Toolkit.setLockingKeyState} kullanıyordu.
 * O önce durumu okuyor, "zaten öyle" sanırsa tuşa basmıyor — ve Minecraft'ın içinde AWT'nin
 * gördüğü durum gerçeğin gerisinde kaldı. Dış ölçerle görüldü: bir yanıp sönmenin geri
 * dönüşü atlandı, Caps Lock oyuncunun klavyesinde AÇIK kaldı. Şimdi her yanıp sönme tuşa
 * tam iki kez basıyor; başlangıç durumu ne olursa olsun iki basış onu geri getirir.</p>
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
 *   <li>Yalnızca <b>Windows</b>. Kilit tuşu yoklaması ({@code getLockingKeyState}) macOS'ta
 *       ve çoğu X11 kurulumunda desteklenmiyor; dahası macOS'ta GLFW oyununun içinde AWT'yi ayağa
 *       kaldırmak JVM'i düşürebiliyor. Windows dışında sınıf AWT'ye hiç dokunmadan
 *       kendini kapatıyor.</li>
 *   <li>Seçilen kilit tuşunun klavyede ışığı yoksa görünür bir şey olmaz. Yazılım bunu
 *       bilemez: Windows ışığın fiziksel olarak var olup olmadığını bildirmiyor.</li>
 *   <li>Arka aydınlatma (klavyenin altındaki ışık) bu yolla açılamaz; gerekçe yukarıda.
 *       HP'de onun tek yazılım yolu BIOS WMI arayüzü ({@code hpqBIntM}) ve o yönetici
 *       ister — normal kullanıcıyla denendi: "Access denied". Bir Minecraft modu yönetici
 *       olarak çalışmaz, çalışmamalı da.</li>
 * </ul>
 *
 * <h3>GERİ ALMA</h3>
 * Her yanıp sönme iki basış: kendi öncesindeki duruma döner. İlk basıştan sonra
 * {@link #ters} işaretli; sunucudan çıkışta / oyun kapanışında darbe yarıda kaldıysa
 * {@link #geriAl()} eksik basışı yapıyor. Darbe yoksa hiçbir şeye dokunmuyor — oyuncunun
 * oyun sırasında kendi açtığı Caps Lock'u ezmesin. Diske hiçbir şey yazılmadığı için çökme
 * hâlinde de kalıcı iz kalmıyor: kilit durumu oyuncunun tek tuşla düzelteceği, yeniden
 * başlatmada da sıfırlanan bir şey.
 */
//? if fabric {
@Environment(EnvType.CLIENT)
//?}
public final class KlavyeIsigi {

    private KlavyeIsigi() {}

    /** Önce Caps Lock: ışığı neredeyse her klavyede var. Scroll / Num Lock yedek. */
    private static final int[] ADAYLAR = {
        KeyEvent.VK_CAPS_LOCK, KeyEvent.VK_SCROLL_LOCK, KeyEvent.VK_NUM_LOCK };

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
    /** Tuşa basan; işçi thread'inde, tuş seçildikten sonra kuruluyor. */
    private static volatile Robot robot = null;
    /** İlk basış yapıldı, geri dönüş basışı henüz yapılmadı. {@link #TUS_KILIDI} altında. */
    private static boolean ters = false;
    /** İşçi ile {@link #geriAl()} aynı geri dönüşü iki kez yapmasın. */
    private static final Object TUS_KILIDI = new Object();

    private static volatile int kademe = 0;
    private static int bekleyenDarbe = 0;

    /** Oyun penceresinin Win32 tutamacı (HWND); işçi thread'inde bir kez okunur. 0 = yok. */
    private static long pencere = 0L;
    /** Ön plan denetimi hiç yapılamıyorsa bunu bir kez, görünür biçimde söyle. */
    private static boolean onPlanUyarisi = false;

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
        // Minecraft kendi main'inde java.awt.headless=true yapiyor (macOS'ta AWT/GLFW
        // catismasini onlemek icin). Headless bir Toolkit kilit LED'ini SURMEZ:
        // getLockingKeyState HeadlessException atar. Burasi yalnizca Windows'ta
        // calisiyor ve Toolkit'e ilk dokunus asagidaki is parcacigi, yani bayragi
        // simdi geri cevirmek guvenli -- X11'siz Linux senaryosu zaten yukarida elendi.
        if (Boolean.getBoolean("java.awt.headless")) {
            System.setProperty("java.awt.headless", "false");
            Candy.LOG.info("[Candy] java.awt.headless=false yapildi (kilit LED'i icin)");
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
        synchronized (TUS_KILIDI) {
            if (ters && bas()) {
                ters = false;
                Candy.LOG.info("[Candy] Klavye ışığı yarıda kalan darbeden geri alındı");
            }
        }
    }

    // ------------------------------------------------------------------ işçi

    private static void dongu() {
        if (!tusSec()) {
            kapali = true;
            Candy.LOG.info("[Candy] Klavye ışığı atlandı — kilit LED'i yok/desteklenmiyor");
            return;
        }
        try {
            robot = new Robot();
        } catch (Throwable t) {
            kapali = true;
            Candy.LOG.warn("[Candy] Klavye ışığı atlandı — tuşa basılamıyor: {}", t.toString());
            return;
        }
        Candy.LOG.info("[Candy] Klavye ışığı aktif — kilit LED'i: {} (tus {})",
            KeyEvent.getKeyText(tus), tus);
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
            } catch (Throwable t) {
                // UnsupportedOperationException / HeadlessException — sıradaki adaya geç
                Candy.LOG.warn("[Candy] kilit tusu {} elenedi: {}: {}", aday,
                    t.getClass().getName(), t.getMessage());
            }
        }
        return false;
    }

    /** Bir kez yanıp söner: tuşa iki kez basar — kilit durumu ne olursa olsun başa döner. */
    private static void yanipSon(long sureMs) throws InterruptedException {
        if (tus != KeyEvent.VK_SCROLL_LOCK && !yazmaGuvenli()) return;
        synchronized (TUS_KILIDI) {
            if (kapali || !bas()) return;
            ters = true;
        }
        try {
            Thread.sleep(sureMs);
        } finally {
            synchronized (TUS_KILIDI) {
                if (ters && bas()) ters = false;
            }
        }
    }

    /**
     * Caps / Num Lock yazmayı etkiler: yalnızca Windows'un ön plan penceresi OYUNUN KENDİSİYKEN
     * ve hiçbir ekran (sohbet, tabela, kitap, duraklatma) açık DEĞİLKEN dokunulur. O an
     * oyuncu harf yazmıyordur; alt-tab'daki başka bir uygulamaya da hiçbir şey düşmez.
     * Scroll Lock'ta kapı gereksiz, hiçbir şeyi etkilemiyor. {@code screen} render
     * thread'inde yazılıyor, burada okunuyor: bayat değerin tek bedeli bir nabzın atlanması.
     * Denetim yapılamıyorsa cevap "güvenli değil": ışık yanmaz, yazı bozulmaz.
     */
    private static boolean yazmaGuvenli() {
        try {
            Minecraft mc = Minecraft.getInstance();
            return mc != null && mc.isWindowActive() && mc.screen == null && onPlanda(mc);
        } catch (Throwable t) {
            if (!onPlanUyarisi) {
                onPlanUyarisi = true;
                Candy.LOG.warn("[Candy] Ön plan denetimi yapılamıyor, kilit LED'i oynatılmayacak: {}",
                    t.toString());
            }
            return false;
        }
    }

    /**
     * Oyun penceresi GERÇEKTEN ön planda mı — Windows'a sorulur. {@code isWindowActive()}
     * tek başına yetmiyor: GLFW odağı iş parçacığının yerel girdi durumundan okuyor;
     * Windows pencereyi öne getirmeyi reddettiğinde (oyuncu o an başka pencerede yazarken)
     * oyun kendini yine "odakta" sanıyor. Dış ölçerle görüldü: başka pencere öndeyken
     * ~17 sn boyunca Caps Lock'a basıldı. Tutamaç sabit (pencere oturum boyunca aynı).
     */
    private static boolean onPlanda(Minecraft mc) {
        long h = pencere;
        if (h == 0L) {
            //? if >=1.21.9 {
            /*h = GLFWNativeWin32.glfwGetWin32Window(mc.getWindow().handle());
            */
            //?}
            //? if <1.21.9 {
            h = GLFWNativeWin32.glfwGetWin32Window(mc.getWindow().getWindow());
            //?}
            if (h == 0L) return false;
            pencere = h;
        }
        WinDef.HWND on = User32.INSTANCE.GetForegroundWindow();
        return on != null && Pointer.nativeValue(on.getPointer()) == h;
    }

    /** Kilit tuşuna bir kez basıp bırakır (SendInput). Hata = kalıcı kapanış, görünür log. */
    private static boolean bas() {
        Robot r = robot;
        if (r == null) return false;
        try {
            r.keyPress(tus);
            r.keyRelease(tus);
            return true;
        } catch (Throwable t) {
            kapali = true;
            Candy.LOG.warn("[Candy] Kilit tuşuna basılamadı, klavye ışığı kapatıldı: {}",
                t.toString());
            return false;
        }
    }
}
