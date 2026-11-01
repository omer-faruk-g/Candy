package com.photonpulse.candy.dimension;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.beyin.Beyin;
import com.photonpulse.candy.beyin.BeyinMotoru;
import com.photonpulse.candy.beyin.Hakem;
import com.photonpulse.candy.beyin.Karar;
import com.photonpulse.candy.beyin.Kurul;
import com.photonpulse.candy.beyin.Lob;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.core.TruePersonality;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.util.UUID;

/**
 * DunyaKurulu — kullanıcının istediği çekirdek mekanik:
 *
 * <blockquote>"11 beyin kurulu her 5dk bir, oyuncunun dünyası değiştirilsin mi, değiştirilecekse
 * neyle diye bir rapor hazırlar. Değiştir çıkarsa oraya atılır, değilse aynı yerde kalır."</blockquote>
 *
 * <h3>MEKANİZMA</h3>
 * Overworld'deyken her {@link #KARAR_ARALIGI} (5 dk) kurul toplanır. Her üye 9 seçeneği
 * (KAL + 8 dünya) KENDİ lob çıkışlarından puanlar; {@link Hakem} bu 11 görüşü ÖLÇÜLEN
 * sicillerine göre tartıp tek karar üretir — tıpkı eylem seçimindeki gibi ama farklı bir
 * soru için. KAL çıkarsa oyuncu kalır; bir dünya çıkarsa {@link DunyaGecisi} oraya atar.
 *
 * <p>Bir kurul dünyasında geçirilen süre {@link #DUNYA_SURE} ile sınırlı; dolunca oyuncu
 * otomatik overworld'e döner ve bir sonraki 5 dk kararı orada verilir. Böylece hem tuzağa
 * düşme olmaz hem de pacing korku için doğru kalır: yakalanırsın, bir süre orada kalırsın,
 * dönersin, sonra yine belirsizlik.</p>
 *
 * <p>Karar puanları HARDCODE değil, her üyenin CANLI beyin durumundan gelir; üyeler farklı
 * lob çıkışlarına sahip olduğu için gerçekten ayrışırlar ve hakem anlamlı bir karar üretir.
 * Bu, mevcut komite mimarisini yeni bir karar türüne bağlamaktan ibaret — yeni beyin yok.</p>
 */
public final class DunyaKurulu {

    // Debug'da hızlı döngü (test için): karar 30 sn, dünyada kalış 20 sn.
    private static final boolean DEBUG = Boolean.getBoolean("candy.debug");
    /** Overworld'de iki dünya kararı arası (tick). 6000 = 5 dk. */
    public static final long KARAR_ARALIGI = DEBUG ? 600L : 6000L;
    /** Bir kurul dünyasında en fazla kalış (tick). 6000 = 5 dk. */
    public static final long DUNYA_SURE = DEBUG ? 400L : 6000L;
    /** Kontrol sıklığı (tick). */
    private static final int KONTROL = 100;

    /** KurulDunya sırası — hakem indeksi 1..8 buna karşılık gelir (0 = KAL). */
    private static final KurulDunya[] DUNYALAR = KurulDunya.values();
    private static final int SECENEK = DUNYALAR.length + 1;   // 8 dünya + KAL

    /** Dünya kararı için AYRI hakem — eylem hakeminin sicilini kirletmez. */
    private static final Hakem HAKEM = new Hakem();

    private static int tick = 0;

    private DunyaKurulu() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(DunyaKurulu::onTick);
        Candy.LOG.info("[Candy] DunyaKurulu aktif — kurul her 5dk dünya kararı verir ({} seçenek)", SECENEK);
    }

    private static void onTick(MinecraftServer server) {
        tick++;
        if (tick % KONTROL != 0) return;
        long simdi = server.overworld().getGameTime();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            try {
                TruePersonality tp = PersonalityManager.get(player);
                if (tp == null || !tp.kurulumTamam) continue;
                UUID id = player.getUUID();

                // 1) Bir kurul dünyasındaysa: süre dolduysa geri dön
                if (tp.kurulDunyaId != null && !tp.kurulDunyaId.isEmpty()) {
                    if (simdi - tp.kurulDunyaGirisTick >= DUNYA_SURE) {
                        DunyaGecisi.don(server, player, tp);
                        tp.kurulSonKarar = simdi;   // dönüşte sayaç sıfırlanır
                    }
                    continue;   // dünyadayken yeni karar yok
                }

                // 2) Overworld'de: 5 dk geçtiyse kurul kararı ver
                if (simdi - tp.kurulSonKarar < KARAR_ARALIGI) continue;
                if (tp.bossOldurulmeTick > 0) continue;   // WIN sessizliği
                tp.kurulSonKarar = simdi;

                Kurul kurul = BeyinMotoru.kurul(id);
                Hakem.Sonuc sonuc = kararVer(kurul, tp);
                if (sonuc == null) continue;

                if (sonuc.eylem == 0) {
                    Candy.LOG.info("[Candy] DÜNYA KARARI[{}] → KAL | {}",
                        player.getName().getString(), sonuc.gerekce);
                } else {
                    KurulDunya secilen = DUNYALAR[sonuc.eylem - 1];
                    Candy.LOG.info("[Candy] DÜNYA KARARI[{}] → DEĞİŞ: {} | {}",
                        player.getName().getString(), secilen.id, sonuc.gerekce);
                    DunyaGecisi.gir(server, player, tp, secilen);
                    // Nadir, teknik-olmayan sızıntı (isteğe bağlı his)
                    if (Math.random() < 0.5) {
                        player.displayClientMessage(Component.translatable("candy.dunya." + secilen.id)
                            .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC), false);
                    }
                }
            } catch (Throwable t) {
                Candy.LOG.debug("[Candy] DunyaKurulu hata: {}", t.getMessage());
            }
        }
    }

    /** Kurulu topla, hakemle karar ver. Kurul hazır değilse gövde sezgiselinden karar. */
    private static Hakem.Sonuc kararVer(Kurul kurul, TruePersonality tp) {
        double[] sezgisel = new double[SECENEK];
        boolean[] yasakli = new boolean[SECENEK];

        if (kurul == null) return null;
        Beyin[] uyeler = kurul.uyeler();
        Beyin govde = kurul.govde();
        if (govde != null) sezgisel = skorla(govde, tp);

        // Her üyeden bir görüş — KENDİ lob çıkışlarından
        Karar[] kararlar = new Karar[uyeler.length];
        for (int u = 0; u < uyeler.length; u++) {
            Beyin b = uyeler[u];
            Karar k = new Karar(u, 0L);
            kararlar[u] = k;
            if (b == null) { k.gecersiz = true; continue; }
            double[] d = skorla(b, tp);
            k.deger = new float[SECENEK];
            int en = -1, ikinci = -1;
            for (int e = 0; e < SECENEK; e++) {
                k.deger[e] = (float) d[e];
                if (en < 0 || d[e] > d[en]) { ikinci = en; en = e; }
                else if (ikinci < 0 || d[e] > d[ikinci]) ikinci = e;
            }
            k.eylem = en;
            double ayrim = (ikinci < 0) ? 0.5 : Math.min(1.0, (d[en] - d[ikinci]) / 0.4);
            k.guven = (float) Math.max(0.0, Math.min(1.0, 0.4 + 0.6 * ayrim));
            k.sicil = (float) b.sicil.guvenilirlik();
            k.kalibrasyon = (float) b.sicil.kalibrasyon();
        }
        return HAKEM.karar(kararlar, sezgisel, yasakli);
    }

    /**
     * Bir üyenin 9 seçeneği (0=KAL, 1..8=dünyalar) kendi CANLI beyin durumundan puanlaması.
     *
     * Her dünya tematik bir "sürücü sinyale" bağlı; sinyaller üyenin kendi lob çıkışlarından
     * geldiği için üyeler doğal olarak ayrışır. Değerler ~0..1.
     */
    private static double[] skorla(Beyin b, TruePersonality tp) {
        double[] s = new double[SECENEK];

        // Canlı beyin sinyalleri (0..1, 0.5 nötr)
        double golge   = b.lobCikisi(Lob.GOLGE);
        double beyincik = b.lobCikisi(Lob.BEYINCIK);
        double yanki   = b.lobCikisi(Lob.YANKI);
        double tarti   = b.lobCikisi(Lob.TARTI);
        double oncul   = b.lobCikisi(Lob.ONCUL);
        double sakak   = b.lobCikisi(Lob.SAKAK);
        double ardil   = b.lobCikisi(Lob.ARDIL);
        double isabet  = (b.oyuncuHaritasi != null) ? b.oyuncuHaritasi.isabetOrani() : 0.5;
        double rehavet = (b.zamanIzi != null) ? b.zamanIzi.rehavet() : 0.0;
        double tutar   = (b.ozModel != null) ? b.ozModel.tutarlilik : 0.5;

        double kesif = tp != null ? clamp01(tp.kesfetmeIstegi) : 0.5;
        double guvenGuc = Math.max(isabet - 0.5, 0) * 2;        // 0..1: ne kadar anlıyorum

        // 0) KAL — VARSAYILAN. Dünya değiştirmek NADİR ve şaşırtıcı olmalı; sürekli değişim
        //    monotonlaşır. KAL orta-yüksek (0,70) başlar, rahatladıkça biraz düşer. Bir
        //    dünya, kendi TEK sürücü sinyali belirgin şekilde yükselince KAL'ı geçer.
        s[0] = clamp01(0.70 - 0.22 * rehavet);

        // 1..8: her dünya DÜŞÜK taban (0,15) + TEK güçlü sürücü sinyal (0,72 kazanç).
        // Sürücü ~0,8'e çıkınca skor ~0,73 olur ve KAL'ı geçer; nötr durumda ~0,15 kalır.
        // Böylece değişim, gerçekten yükselmiş bir sinyalin sonucudur — rastgele değil.
        final double G = 0.72;
        for (KurulDunya d : DUNYALAR) {
            int i = d.ordinal() + 1;
            double surucu = switch (d) {
                case FARLANDS     -> poz(golge) * (0.7 + 0.3 * kesif);              // bastırılanı göster
                case FARTHERLANDS -> poz(yanki);                                    // geçmişi sorgula → öteye
                case CORNERLANDS  -> poz(tarti);                                    // meta-değerlendirme
                case SKYGRID      -> poz(ardil) * (0.7 + 0.3 * kesif);              // duyusal + keşif
                case KORIDOR      -> guvenGuc;                                       // öngörülebilirlik
                case KLON         -> 0.5 * poz(golge) + 0.5 * clamp01(tutar - 0.2);  // "özel değilsin"
                case ME           -> clamp01(tutar - 0.15) + 0.3 * poz(sakak);       // kimlik
                case KOR          -> rehavet;                                        // fazla rahatladın
            };
            s[i] = clamp01(0.15 + G * clamp01(surucu));
        }
        // TEKRAR CEZASI: aynı dünyaya arka arkaya göndermek monotonlaşır. Son ziyaret
        // edilen dünyanın skorunu kıs — komite ısrar ederse yine gider ama varsayılan çeşitlilik.
        if (tp != null && tp.kurulSonDunya != null) {
            for (KurulDunya d : DUNYALAR) {
                if (d.name().equals(tp.kurulSonDunya)) { s[d.ordinal() + 1] *= 0.45; break; }
            }
        }
        return s;
    }

    /** Sinyalin nötrden (0,5) YUKARI sapması, 0..1'e ölçekli. */
    private static double poz(double x) {
        return clamp01((x - 0.5) * 2.0);
    }

    private static double clamp01(double x) {
        if (!Double.isFinite(x)) return 0.0;
        return Math.max(0.0, Math.min(1.0, x));
    }
}
