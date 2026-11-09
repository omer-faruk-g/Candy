package com.photonpulse.candy.sifre;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.core.JsonDepo;
import com.photonpulse.candy.core.TruePersonality;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.storage.LevelResource;

/**
 * SifreDagitici (v8.1) — ikilik kodlu şifreyi üç kanala bırakır: dünya kaydı, kitap,
 * masaüstü.
 *
 * <h3>DAĞITIM KARARI DOĞRUDAN DEĞİL</h3>
 * Vagus (yerel AI) burada hiç geçmiyor ve kasıtlı olarak geçmiyor. Vagus yalnızca
 * {@code vagusTahmin / vagusKalibrasyon / vagusDarbe} alanlarını yazıyor; o alanlar bir
 * sonraki nabızda kurula sıradan duyu girdisi olarak giriyor ve dağıtım eşiği
 * {@code candyOzguven + candyOfke + vagusDarbe} bileşiminden çıkıyor. Yani AI dosya
 * bırakmıyor — <b>girdiyi kaydırıyor, kararı kurul veriyor</b>. Dış güç sisteme dolaylı
 * dokunuyor; doğrudan müdahale yolu yok.
 *
 * <h3>DOSYA YAZIMI</h3>
 * {@link JsonDepo#yaz} kullanılıyor: ana thread'de yalnızca kuyruğa ekleme, gerçek yazım
 * daemon thread'inde, temp dosya + {@code ATOMIC_MOVE}. Sunucu kapanışında
 * {@code JsonDepo.kapanisBekle()} zaten bekliyor, yani ayrı bir drain gerekmiyor.
 */
public final class SifreDagitici {

    private SifreDagitici() {}

    /** Candy'nin bıraktığı dosyaların ön adı — silerken yalnızca bunu arar. */
    private static final String ON_AD = "candy_";
    private static final String SON_AD = ".txt";

    /** Dosya başlığı. Kasıtlı olarak hiçbir şey açıklamıyor. */
    private static final String BASLIK =
        "- - - - - - - - - - - - - -\r\n";

    private static Path masaustu = null;
    private static boolean masaustuArandi = false;

    /**
     * Oyuncuya bir sonraki şifreyi dağıtır. Zaten dağıtılmış indeksler atlanır.
     *
     * @return dağıtılan indeks, ya da -1 (kapalı / sıra değil / şifre kalmadı)
     */
    public static int dagit(ServerPlayer player, TruePersonality tp) {
        SifreAyar ayar = SifreAyar.al();
        if (!ayar.acik) return -1;
        if (!(player.level() instanceof ServerLevel dunya)) return -1;

        final long simdi = dunya.getGameTime();
        if (tp.sifreSonDagitim != 0L && simdi - tp.sifreSonDagitim < ayar.aralikTick()) return -1;

        int indeks = sonrakiIndeks(tp);
        if (indeks < 0) return -1;

        final long tohum = dunya.getSeed();
        final String[] satirlar = IkilikMetin.satirlar(tohum, indeks);
        final String duz = String.join("\r\n", satirlar);
        final String ikilik = ikilikBlok(satirlar);

        boolean birSey = false;
        if (ayar.haritaDosyasi) birSey |= haritayaBirak(dunya, indeks, ikilik);
        if (ayar.masaustuDosyasi) birSey |= masaustuneBirak(ayar, indeks, ikilik);
        if (ayar.kitap) birSey |= kitabaYaz(player, indeks, ikilik);

        if (!birSey) return -1;

        tp.sifreVerilen |= (1 << indeks);
        tp.sifreSonDagitim = simdi;
        // Düz metin SADECE loga gider — oyuncuya gösterilmez, bulması gerekiyor.
        Candy.LOG.info("[Candy] şifre #{} dağıtıldı [{}] (çözümü: {})",
            indeks, player.getName().getString(), duz.replace("\r\n", " / "));
        return indeks;
    }

    /** Henüz dağıtılmamış en küçük indeks. */
    private static int sonrakiIndeks(TruePersonality tp) {
        for (int i = 0; i < IkilikMetin.MAKS_SIFRE; i++) {
            if ((tp.sifreVerilen & (1 << i)) == 0) return i;
        }
        return -1;
    }

    /** Satırları 8'li ikilik gruplara çevirip satır satır dizer. */
    private static String ikilikBlok(String[] satirlar) {
        StringBuilder sb = new StringBuilder(BASLIK);
        for (String s : satirlar) {
            sb.append(IkilikMetin.ikiliyeCevir(s)).append("\r\n");
        }
        sb.append(BASLIK);
        return sb.toString();
    }

    // ── Kanal 1: dünya kaydı ("haritaya") ───────────────────────────────────────

    private static boolean haritayaBirak(ServerLevel dunya, int indeks, String ikilik) {
        try {
            Path kok = dunya.getServer().getWorldPath(LevelResource.ROOT);
            JsonDepo.yaz(kok.resolve(ON_AD + ikiHane(indeks) + SON_AD), ikilik);
            return true;
        } catch (Throwable t) {
            Candy.LOG.warn("[Candy] harita şifresi bırakılamadı: {}", t.getMessage());
            return false;
        }
    }

    // ── Kanal 2: masaüstü ───────────────────────────────────────────────────────

    private static boolean masaustuneBirak(SifreAyar ayar, int indeks, String ikilik) {
        Path d = masaustuBul(ayar);
        if (d == null) return false;
        try {
            String ad = ON_AD + ikiHane(indeks) + SON_AD;
            eskileriTemizle(d, ayar.maksMasaustuDosya, ad);
            JsonDepo.yaz(d.resolve(ad), ikilik);
            return true;
        } catch (Throwable t) {
            Candy.LOG.warn("[Candy] masaüstü şifresi bırakılamadı: {}", t.getMessage());
            return false;
        }
    }

    /**
     * Masaüstü klasörü. Ayarda yazılıysa o, değilse {@code user.home/Desktop} —
     * Windows'ta OneDrive'a taşınmış olabilir, o yüzden ikinci aday denenir.
     * Bulunamazsa {@code null} ve masaüstü kanalı sessizce atlanır.
     */
    private static Path masaustuBul(SifreAyar ayar) {
        if (masaustuArandi) return masaustu;
        masaustuArandi = true;
        try {
            if (!ayar.masaustuYolu.isBlank()) {
                Path p = Path.of(ayar.masaustuYolu);
                if (Files.isDirectory(p)) { masaustu = p; return masaustu; }
                Candy.LOG.warn("[Candy] ayardaki masaüstü yolu klasör değil: {}", p);
            }
            String ev = System.getProperty("user.home");
            if (ev == null || ev.isBlank()) return null;
            Path[] adaylar = {
                Path.of(ev, "Desktop"),
                Path.of(ev, "OneDrive", "Desktop"),
                Path.of(ev, "Masaüstü"),
            };
            for (Path p : adaylar) {
                if (Files.isDirectory(p)) { masaustu = p; return masaustu; }
            }
        } catch (Throwable t) {
            Candy.LOG.debug("[Candy] masaüstü bulunamadı: {}", t.getMessage());
        }
        return masaustu;
    }

    /**
     * {@code maks} sınırını aşan <b>kendi</b> dosyalarını siler. Yalnızca
     * {@code candy_NN.txt} kalıbına uyanlara dokunur — başka hiçbir dosyaya bakmaz.
     * Yazılacak dosya ({@code yeni}) zaten varsa üstüne yazılacağı için sayılmaz:
     * önceden o durumda sınır dolu sayılıp başka bir Candy dosyası boşuna siliniyordu
     * (yeni dünyada indeks 0'dan başlar, eski dünyanın candy_00'ı masaüstündeyken).
     */
    private static void eskileriTemizle(Path klasor, int maks, String yeni) throws IOException {
        List<Path> benim = new ArrayList<>();
        try (Stream<Path> s = Files.list(klasor)) {
            s.forEach(p -> {
                String ad = p.getFileName().toString();
                if (!ad.startsWith(ON_AD) || !ad.endsWith(SON_AD) || ad.equals(yeni)) return;
                String orta = ad.substring(ON_AD.length(), ad.length() - SON_AD.length());
                if (orta.length() != 2) return;
                for (int i = 0; i < 2; i++) {
                    char c = orta.charAt(i);
                    if (c < '0' || c > '9') return;
                }
                benim.add(p);
            });
        }
        if (benim.size() < maks) return;
        benim.sort(Comparator.comparing(SifreDagitici::degisimZamani));
        int silinecek = benim.size() - maks + 1;
        for (int i = 0; i < silinecek && i < benim.size(); i++) {
            try { Files.deleteIfExists(benim.get(i)); } catch (Throwable ignored) {}
        }
    }

    private static long degisimZamani(Path p) {
        try { return Files.getLastModifiedTime(p).toMillis(); } catch (Throwable t) { return 0L; }
    }

    // ── Kanal 3: yazılı kitap ───────────────────────────────────────────────────

    private static boolean kitabaYaz(ServerPlayer player, int indeks, String ikilik) {
        try {
            ItemStack kitap = new ItemStack(Items.WRITTEN_BOOK);
            List<Filterable<Component>> sayfalar = new ArrayList<>();
            for (String sayfa : sayfalaraBol(ikilik)) {
                sayfalar.add(Filterable.passThrough(Component.literal(sayfa)));
            }
            kitap.set(DataComponents.WRITTEN_BOOK_CONTENT,
                new WrittenBookContent(
                    Filterable.passThrough(ikiHane(indeks)),   // başlık: sadece numara
                    "",                                     // yazar yok
                    0,                                      // generation: orijinal
                    sayfalar,
                    true));                                 // resolved — sunucu çözmeye kalkmasın
            if (!player.addItem(kitap)) {
                player.drop(kitap, false);
            }
            return true;
        } catch (Throwable t) {
            Candy.LOG.warn("[Candy] şifre kitabı verilemedi: {}", t.getMessage());
            return false;
        }
    }

    /**
     * Kitap sayfası ~255 karakter alıyor; ikilik blok bundan uzun. Satır sınırlarında
     * böler, tek satır bile sığmazsa karakterden keser (jeton 12 karakter = 107 bit
     * metni, yani bir satır her zaman sığıyor — kesme yalnızca savunma amaçlı).
     */
    private static List<String> sayfalaraBol(String metin) {
        final int LIMIT = 250;
        List<String> out = new ArrayList<>();
        StringBuilder sayfa = new StringBuilder(LIMIT);
        for (String satir : metin.split("\r\n")) {
            while (satir.length() > LIMIT) {
                if (sayfa.length() > 0) { out.add(sayfa.toString()); sayfa.setLength(0); }
                out.add(satir.substring(0, LIMIT));
                satir = satir.substring(LIMIT);
            }
            if (sayfa.length() + satir.length() + 1 > LIMIT) {
                out.add(sayfa.toString());
                sayfa.setLength(0);
            }
            if (sayfa.length() > 0) sayfa.append('\n');
            sayfa.append(satir);
        }
        if (sayfa.length() > 0) out.add(sayfa.toString());
        if (out.isEmpty()) out.add("");
        return out;
    }

    private static String ikiHane(int n) {
        return n < 10 ? "0" + n : Integer.toString(n);
    }
}
