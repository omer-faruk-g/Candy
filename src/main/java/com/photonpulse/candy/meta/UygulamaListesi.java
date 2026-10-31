package com.photonpulse.candy.meta;

import java.util.Locale;

/**
 * UygulamaListesi (v8.1) — Candy'nin tanıyabildiği uygulamaların <b>sabit beyaz listesi</b>.
 * İstemci ve sunucu bu listeyi paylaşır; ağda yalnızca indeks taşınır.
 *
 * <h3>BEYAZ LİSTE, SİYAH LİSTE DEĞİL</h3>
 * "Şunları hariç tut, kalanını gönder" yaklaşımı yanlış olurdu: oyuncunun açık olan
 * banka uygulaması, özel bir belge, bir ilaç takip programı — hepsi "kalan"a girer.
 * Burada tersi yapılıyor: <b>listede olmayan hiçbir şey görülmez, taşınmaz, saklanmaz</b>.
 * Liste bilinçli olarak oyun çevresiyle sınırlı ve 12 madde; genişletmek kolay ama her
 * yeni satır bilerek alınmış bir karar olmalı.
 *
 * <h3>EŞLEŞME SADECE SÜREÇ ADINDA</h3>
 * {@code ProcessHandle} komut satırının tamamını verebilir — dosya yolları, açılan
 * belgenin adı, parametreler. Hiçbiri okunmuyor: yalnızca çalıştırılabilir dosyanın
 * <i>adı</i> alınıp bu listeyle karşılaştırılıyor.
 */
public final class UygulamaListesi {

    private UygulamaListesi() {}

    /** Süreç adı anahtarları (küçük harf, uzantısız). Sıra = protokol indeksi, DEĞİŞTİRME. */
    private static final String[] ANAHTAR = {
        "discord",      // 0
        "spotify",      // 1
        "obs64",        // 2
        "steam",        // 3
        "chrome",       // 4
        "firefox",      // 5
        "msedge",       // 6
        "vlc",          // 7
        "telegram",     // 8
        "whatsapp",     // 9
        "notepad",      // 10
        "code",         // 11
    };

    /** Oyun içinde gösterilecek okunur adlar. ANAHTAR ile birebir aynı sırada. */
    private static final String[] AD = {
        "Discord",
        "Spotify",
        "OBS",
        "Steam",
        "Chrome",
        "Firefox",
        "Edge",
        "VLC",
        "Telegram",
        "WhatsApp",
        "Not Defteri",
        "VS Code",
    };

    public static int boyut() {
        return ANAHTAR.length;
    }

    /** Süreç adı listede var mı? Yoksa -1. */
    public static int indeksBul(String surecAdi) {
        if (surecAdi == null || surecAdi.isBlank()) return -1;
        String s = surecAdi.toLowerCase(Locale.ROOT);
        int nokta = s.lastIndexOf('.');
        if (nokta > 0) s = s.substring(0, nokta);          // .exe at
        for (int i = 0; i < ANAHTAR.length; i++) {
            if (s.equals(ANAHTAR[i])) return i;
        }
        return -1;
    }

    /** İndeksin okunur adı, ya da null. */
    public static String ad(int indeks) {
        if (indeks < 0 || indeks >= AD.length) return null;
        return AD[indeks];
    }
}
