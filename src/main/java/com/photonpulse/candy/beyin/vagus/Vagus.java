package com.photonpulse.candy.beyin.vagus;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.beyin.BeyinMotoru;
import com.photonpulse.candy.beyin.Kurul;
import com.photonpulse.candy.beyin.UyeSicili;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.core.TruePersonality;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Vagus — kurulun dışından bakan, <b>karara karışmayan</b> yerel gözlemci.
 *
 * <h3>MİMARİ KISIT — BU SINIFIN TEK KURALI</h3>
 * <pre>
 *   Kurul (11 beyin) ──karar──&gt; Hakem ──&gt; ZihinselProva.uygula ──&gt; eylem
 *        |                                                          |
 *        |                      [Vagus bu hatta DEĞİL]              |
 *        +────────────── sonuçlar ──&gt; Vagus &lt;───────────────────────+
 *                                       |
 *                                 (yaklaştığında)
 *                                       v
 *                            ALANLAR 49-51 ──&gt; Kurul   [sadece girdi]
 * </pre>
 * Vagus'un iletisi <b>girdi</b>dir: veto değil, override değil. Karar yolunda Vagus'u
 * <i>okuyan</i> dal yoktur; Vagus yalnızca {@link TruePersonality}'ye <i>yazar</i> ve
 * bir sonraki nabızda (GIRDI_TICK=20) {@code BeyinGirdi.topla} üzerinden normal duyu
 * yoluyla girer. Kurul bu kanalı kullanmayı öğrenebilir de öğrenmeyebilir de.
 *
 * <h3>"YAKLAŞTI" NESNEL BİR OLAYDIR</h3>
 * Modelin kendi guven beyanı kullanılamaz (ölçümde bozuk hipoteze 0,95, düzgün cevaba
 * 0,21 verdi). Ölçüt modun kendi hazır harness'ıdır: Vagus <b>oy kullanmayan 12. üye</b>
 * olarak {@link UyeSicili} ile puanlanır (Brier kalibrasyonu) ve kalibrasyon kendi geçmiş
 * en iyisini bir payla aştığında — yalnız o anda — beyinlere darbe gider.
 *
 * <h3>TAMAMEN OPSİYONEL</h3>
 * Ollama yoksa açılışta bir INFO log düşer, Vagus kapanır ve mod <b>v7 ile birebir aynı</b>
 * davranır: istek atılmaz, {@link #ongoruPuanla} ilk satırda döner.
 *
 * <h3>TICK'İ ASLA BLOKLAMAZ</h3>
 * Modun kanonik şablonu ({@code yuz/YuzHaritasi}): adlandırılmış <b>daemon</b> thread,
 * sonuç final local'e, ana thread'e {@code server.execute(...)}, içinde canlılık
 * kontrolü, hata yutulur. Tek istek havada; oyuncu başına {@link VagusAyar#aralikSn}
 * saniye hız limiti.
 *
 * <h3>GİZLİLİK</h3>
 * Hipotez serbest LLM üretimidir ve oyuncuya <b>asla</b> gösterilmez — yalnızca loglanır.
 * Oyuncuya görünen tek şey davranışın kendisidir.
 */
public final class Vagus {

    private Vagus() {}

    /** Sezgi bundan eskiyse puanlanmaz (90 s) — ölçtüğü durum artık geçerli değil. */
    private static final long SEZGI_OMUR_TICK = 1800L;
    /** Darbe her nabızda bu çarpanla söner — ~7 s'de sıfırlanır. */
    private static final double DARBE_SONUM = 0.90;
    /** Kalibrasyon en iyisini bu paydan fazla aşarsa "yaklaştı" sayılır. */
    private static final double YAKLASMA_PAYI = 0.03;
    /** Darbe için en az bu kadar ölçülmüş tahmin gerekir — şans vuruşu darbe değildir. */
    private static final int EN_AZ_KANIT = 5;

    /** Oyuncu başına Vagus durumu — ana thread'de okunur/yazılır. */
    private static final class Durum {
        final UyeSicili sicil = new UyeSicili();
        final VagusOzet.Gecmis gecmis = new VagusOzet.Gecmis();
        VagusSezgi sezgi;
        long sonIstekTick = Long.MIN_VALUE;
    }

    private static final Map<UUID, Durum> durumlar = new ConcurrentHashMap<>();
    private static final AtomicBoolean havada = new AtomicBoolean(false);

    private static VagusAyar ayar;
    private static VagusIstemci istemci;
    private static boolean yoklamaBasladi = false;
    private static volatile boolean calisiyor = false;
    private static volatile long sonNabizTick = 0L;
    private static int tick = 0;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(Vagus::onTick);
        Candy.LOG.info("[Candy] Vagus kayıtlı — yerel gözlemci (v8.0)");
    }

    // ─────────────────────────────────────────────────────────
    // YAŞAM DÖNGÜSÜ
    // ─────────────────────────────────────────────────────────

    private static void onTick(MinecraftServer server) {
        if (++tick % 20 != 0) return;        // nabız: 1 s

        if (ayar == null) {
            ayar = VagusAyar.yukle();
            if (!ayar.acik) {
                Candy.LOG.info("[Candy] Vagus ayarla kapatılmış — gözlemci yok");
            } else {
                istemci = new VagusIstemci(ayar);
            }
        }
        if (!ayar.acik) return;

        try {
            sonNabizTick = server.overworld().getGameTime();
        } catch (Throwable t) {
            return;                           // overworld yok (kapanış) — bu nabzı atla
        }

        darbeSonumle();

        if (!yoklamaBasladi) {
            yoklamaBasladi = true;
            yoklamaBaslat();
            return;                           // yoklama bitene kadar istek yok
        }
        if (!calisiyor || havada.get()) return;

        ServerPlayer hedef = hedefSec(server);
        if (hedef == null) return;
        istekAt(server, hedef);
    }

    /**
     * Ollama yoklaması — <b>thread'de</b>, çünkü ulaşılamayan bir adrese bağlanmaya
     * çalışmak ana thread'i 2 saniyeye kadar dondurabilirdi.
     */
    private static void yoklamaBaslat() {
        final VagusIstemci ist = istemci;
        Thread t = new Thread(() -> {
            boolean ayakta = false;
            try {
                ayakta = ist.yokla();
            } catch (Throwable ignored) {}
            calisiyor = ayakta;
            if (ayakta) {
                Candy.LOG.info("[Candy] Vagus aktif — {} @ {} (aralık {} s)",
                    ayar.model, ayar.adres, ayar.aralikSn);
            } else {
                Candy.LOG.info("[Candy] Vagus kapalı — yerel gözlemci ulaşılamadı ({})",
                    ayar.adres);
            }
        }, "Candy-Vagus-Yoklama");
        t.setDaemon(true);
        t.start();
    }

    /** Hız limitini geçmiş, kurulumu tamamlanmış, en uzun süredir beklemiş oyuncu. */
    private static ServerPlayer hedefSec(MinecraftServer server) {
        final long simdi = sonNabizTick;
        final long aralik = ayar.aralikTick();
        ServerPlayer enIyi = null;
        long enEski = Long.MAX_VALUE;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            TruePersonality tp = PersonalityManager.get(player);
            if (tp == null || !tp.kurulumTamam) continue;
            Durum d = durum(player.getUUID());
            if (d.sonIstekTick != Long.MIN_VALUE && simdi - d.sonIstekTick < aralik) continue;
            if (d.sonIstekTick < enEski) {
                enEski = d.sonIstekTick;
                enIyi = player;
            }
        }
        return enIyi;
    }

    private static void istekAt(MinecraftServer server, ServerPlayer player) {
        final UUID uid = player.getUUID();
        final Durum d = durum(uid);
        final TruePersonality tp = PersonalityManager.get(player);
        if (tp == null) return;

        Kurul kurul;
        try {
            kurul = BeyinMotoru.kurul(uid);
        } catch (Throwable t) {
            return;
        }

        // Özet ANA THREAD'de kurulur — oyun durumunu okur. Thread'e yalnızca metin gider.
        final String ozet;
        try {
            ozet = VagusOzet.uret(tp, kurul, d.gecmis, d.sezgi, sonNabizTick);
        } catch (Throwable t) {
            Candy.LOG.debug("[Candy] Vagus özeti kurulamadı: {}", t.getMessage());
            return;
        }

        if (!havada.compareAndSet(false, true)) return;
        final long istekTick = sonNabizTick;
        d.sonIstekTick = istekTick;

        final VagusIstemci ist = istemci;
        Thread t = new Thread(() -> {
            try {
                final VagusSezgi sezgi = ist.sor(ozet, istekTick);
                if (sezgi == null) return;
                server.execute(() -> {
                    if (player.isRemoved()) return;
                    try {
                        uygula(uid, sezgi);
                    } catch (Throwable ex) {
                        Candy.LOG.debug("[Candy] Vagus sezgi uygulanamadı: {}", ex.getMessage());
                    }
                });
            } catch (Throwable ignored) {
                // ağ/model hatası — çevrim sessizce atlanır, mod etkilenmez
            } finally {
                havada.set(false);
            }
        }, "Candy-Vagus");
        t.setDaemon(true);
        t.start();
    }

    /** Sezgiyi beyinlerin duyabileceği kanala yazar — ana thread'de. */
    private static void uygula(UUID uid, VagusSezgi sezgi) {
        TruePersonality tp = PersonalityManager.get(uid);
        if (tp == null) return;
        Durum d = durum(uid);
        d.sezgi = sezgi;
        tp.vagusTahmin = sezgi.tahmin();

        // LLM metni YALNIZCA log — oyuncuya giden hiçbir metinde yer almaz.
        Candy.LOG.info("[Candy] Vagus sezgi: tahmin={} beyan={} surukleyen={} | {}",
            String.format(Locale.ROOT, "%.2f", sezgi.tahmin()),
            String.format(Locale.ROOT, "%.2f", sezgi.guven()),
            sezgi.surukleyen(), sezgi.hipotez());
    }

    /** Darbe tetiktir, durum değil: her nabızda söner. */
    private static void darbeSonumle() {
        for (TruePersonality tp : PersonalityManager.tumOyuncular().values()) {
            if (tp == null || tp.vagusDarbe <= 0.0) continue;
            tp.vagusDarbe = (tp.vagusDarbe < 0.01) ? 0.0 : tp.vagusDarbe * DARBE_SONUM;
        }
    }

    // ─────────────────────────────────────────────────────────
    // PUANLAMA — 12. üye, oy kullanmaz
    // ─────────────────────────────────────────────────────────

    /**
     * {@code Kurul.sonucOlcum} her geri bildirimde burayı çağırır. Vagus kapalıysa ilk
     * satırda döner — kurul v7 ile birebir aynı davranır.
     *
     * @param yapilanEylem gerçekten uygulanan eylem (ordinal)
     * @param odulNorm     gözlenen sonuç, kurulun kendi 0..1 ölçeğinde
     */
    public static void ongoruPuanla(UUID oyuncuId, int yapilanEylem, double odulNorm) {
        if (!calisiyor || oyuncuId == null) return;
        Durum d = durumlar.get(oyuncuId);
        if (d == null) return;

        // Geçmiş HER geri bildirimde beslenir: sezgi bayat olsa bile "hangi eylem bu
        // oyuncuda ne getirdi" ölçümü değerlidir, bir sonraki özete girer.
        d.gecmis.kaydet(yapilanEylem, odulNorm);

        VagusSezgi s = d.sezgi;
        if (s == null || s.bayat(sonNabizTick, SEZGI_OMUR_TICK)) return;
        d.sezgi = null;                      // bir sezgi bir kez puanlanır

        d.sicil.ongoruPuanla(s.tahmin(), odulNorm);

        TruePersonality tp = PersonalityManager.get(oyuncuId);
        if (tp == null) return;
        double kal = d.sicil.kalibrasyon();
        tp.vagusKalibrasyon = kal;

        if (d.sicil.toplamDegerlendirme >= EN_AZ_KANIT && kal > tp.vagusEnIyi + YAKLASMA_PAYI) {
            tp.vagusEnIyi = kal;
            tp.vagusDarbe = 1.0;
            Candy.LOG.info("[Candy] Vagus yaklastı — kalibrasyon {} (n={})",
                String.format(Locale.ROOT, "%.0f%%", kal * 100),
                d.sicil.toplamDegerlendirme);
        }
    }

    private static Durum durum(UUID uid) {
        return durumlar.computeIfAbsent(uid, k -> new Durum());
    }

    public static void oyuncuAyrildi(UUID uuid) {
        durumlar.remove(uuid);
    }

    /** Teşhis: Vagus şu an gerçekten çalışıyor mu. */
    public static boolean aktif() {
        return calisiyor;
    }
}
