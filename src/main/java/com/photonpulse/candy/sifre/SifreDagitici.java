package com.photonpulse.candy.sifre;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.core.JsonDepo;
import com.photonpulse.candy.core.TruePersonality;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.RawFilteredPair;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

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
    public static int dagit(ServerPlayerEntity player, TruePersonality tp) {
        SifreAyar ayar = SifreAyar.al();
        if (!ayar.acik) return -1;
        if (!(player.getWorld() instanceof ServerWorld dunya)) return -1;

        final long simdi = dunya.getTime();
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

    private static boolean haritayaBirak(ServerWorld dunya, int indeks, String ikilik) {
        try {
            Path kok = dunya.getServer().getSavePath(WorldSavePath.ROOT);
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
            eskileriTemizle(d, ayar.maksMasaustuDosya);
            JsonDepo.yaz(d.resolve(ON_AD + ikiHane(indeks) + SON_AD), ikilik);
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
     */
    private static void eskileriTemizle(Path klasor, int maks) throws IOException {
        List<Path> benim = new ArrayList<>();
        try (Stream<Path> s = Files.list(klasor)) {
            s.forEach(p -> {
                String ad = p.getFileName().toString();
                if (!ad.startsWith(ON_AD) || !ad.endsWith(SON_AD)) return;
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

    private static boolean kitabaYaz(ServerPlayerEntity player, int indeks, String ikilik) {
        try {
            ItemStack kitap = new ItemStack(Items.WRITTEN_BOOK);
            List<RawFilteredPair<Text>> sayfalar = new ArrayList<>();
            for (String sayfa : sayfalaraBol(ikilik)) {
                sayfalar.add(RawFilteredPair.of(Text.literal(sayfa)));
            }
            kitap.set(DataComponentTypes.WRITTEN_BOOK_CONTENT,
                new WrittenBookContentComponent(
                    RawFilteredPair.of(ikiHane(indeks)),   // başlık: sadece numara
                    "",                                     // yazar yok
                    0,                                      // generation: orijinal
                    sayfalar,
                    true));                                 // resolved — sunucu çözmeye kalkmasın
            if (!player.giveItemStack(kitap)) {
                player.dropItem(kitap, false);
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
