package com.photonpulse.candy.arkadas;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.core.TruePersonality;
import com.photonpulse.candy.net.BildirimPayload;
import com.photonpulse.candy.platform.AgKayit;
import com.photonpulse.candy.platform.SunucuAg;
import com.photonpulse.candy.platform.SunucuTick;
import com.photonpulse.candy.sessizlik.SessizlikState;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * TanidikFisilti (v9.0.0) — Candy'nin oyuncuya KENDİSİ hakkında ettiği özel fısıltı.
 *
 * <h3>NEDEN</h3>
 * "Beni tanıyor" korkusunun kaynağı dışarıdan veri çalmak DEĞİL; Candy'nin zaten oyun
 * içinde gözlediği şeyi ({@link TruePersonality}) oyuncuya geri söylemesi. Oyuncu o
 * bilgiyi kendi verdi (tabela cevabı, chat, davranış) ve mod onu hatırlıyor — bu gerçek
 * casusluktan kat kat ürpertici ve {@link ArkadasFisilti} v8.1 gizlilik kuralını hiç
 * zorlamıyor: hepsi oyun-içi veri; şehir/donanım/dosya yolu/uygulama adı YOK.
 *
 * <h3>KANAL</h3>
 * {@link ArkadasFisilti} ile birebir aynı: özel {@code displayClientMessage}, metin
 * lang'de, değişken kısım {@code %s} argümanı. Yeni payload kurulmadı.
 *
 * <h3>CİHAZSAL + DEPOLANMAZ</h3>
 * Yalnızca canlı {@link TruePersonality} okunur; yeni hiçbir şey diske yazılmaz, hiçbir
 * yere gönderilmez. Soğuma haritası yalnız bellekte, sunucu kapanınca sıfırlanır.
 */
public final class TanidikFisilti {

    private TanidikFisilti() {}

    /** Kontrol sıklığı (tick). 200 = 10 sn. */
    private static final int TICK_ARALIGI = 200;
    /** Aynı oyuncuya iki fısıltı arası en az bu kadar tick (~10 dk). Nadir = vurucu. */
    private static final long SOGUMA = 12_000L;
    /** Candy kendini bir parça kanıtlamadan içini dökmez. */
    private static final int MIN_KADEME = 1;
    /** Alıntılanan cevabın en çok uzunluğu (tabela zaten kısa). */
    private static final int MAKS_ALINTI = 48;

    /** Oyuncu UUID -> son fısıltı tick'i. */
    private static final Map<UUID, Long> sonFisilti = new HashMap<>();

    // v9.2.0: DIŞ KANAL (e-posta). Sohbet fısıltısından ÇOK daha nadir ve daha geç —
    // "pencerenin dışına çıktı" anı seyreltildikçe vurur. Sunucu yalnızca anahtar+arg
    // yolluyor; e-postayı istemci kendi config'inden okuyup kendisi gönderiyor. Config
    // boşsa paket istemcide sessizce düşer, yani sunucu oyuncunun adresini hiç görmez.
    /** İki dış-kanal arası en az bu kadar tick (~30 dk). */
    private static final long DIS_SOGUMA = 36_000L;
    /** Dış kanal ancak Candy belirgin bir zemin kazandıysa (perde ≥ 2). */
    private static final int MIN_KADEME_DIS = 2;
    /** Uygun olduğunda bile her fısıltı dışarı taşmaz. */
    private static final double DIS_IHTIMAL = 0.25;
    /** Oyuncu UUID -> son dış-kanal tick'i. */
    private static final Map<UUID, Long> sonDisKanal = new HashMap<>();

    public static void register() {
        try {
            AgKayit.playS2C().register(BildirimPayload.ID, BildirimPayload.CODEC);
        } catch (Throwable t) {
            Candy.LOG.debug("[Candy] Bildirim payload zaten kayıtlı (sunucu): {}", t.getMessage());
        }
        SunucuTick.END_SERVER_TICK.register(TanidikFisilti::onTick);
        Candy.LOG.info("[Candy] TanidikFisilti aktif — Candy seni hatırlıyor");
    }

    private static void onTick(MinecraftServer server) {
        if (server.getTickCount() % TICK_ARALIGI != 0) return;
        long simdi = server.overworld().getGameTime();
        if (SessizlikState.sessizlikte(simdi)) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isCreative() || player.isSpectator()) continue;
            TruePersonality tp = PersonalityManager.get(player);
            if (tp == null || !tp.kurulumTamam) continue;
            if (tp.perdeKademe < MIN_KADEME) continue;

            Long son = sonFisilti.get(player.getUUID());
            if (son != null && simdi - son < SOGUMA) continue;

            // İhtimal: korku arttıkça Candy daha çok konuşur (0.1 .. ~0.6).
            double ihtimal = 0.1 + Math.min(0.5, tp.korkuSkoru / 200.0);
            if (Math.random() > ihtimal) continue;

            if (secVeYolla(server, player, tp, simdi)) {
                sonFisilti.put(player.getUUID(), simdi);
            }
        }
    }

    /** Oyuncunun durumundan uygun fısıltıları toplar, birini seçip yollar. */
    private static boolean secVeYolla(MinecraftServer server, ServerPlayer player,
                                      TruePersonality tp, long simdi) {
        List<Aday> adaylar = new ArrayList<>();

        // 1) Oyuncunun KENDİ tabela cevabı — en vurucu: kendi sözleri.
        String cevap = rastgeleGercekCevap(tp);
        if (cevap != null) {
            adaylar.add(new Aday("candy.tanidik.tabela", cevap));
        }
        // 2) Uykusuz geceler.
        if (tp.uykusuzGunSayisi >= 3) {
            adaylar.add(new Aday("candy.tanidik.uykusuz", String.valueOf(tp.uykusuzGunSayisi)));
        }
        // 3) Kendi yüzünü taşıyan klonu öldürdü.
        if (tp.oldurulenKlonSayisi >= 1) {
            adaylar.add(new Aday("candy.tanidik.klon", String.valueOf(tp.oldurulenKlonSayisi)));
        }
        // 4) "Korkmuyorum" diyen tip.
        if (tp.inkarEgilimi >= 0.5) {
            adaylar.add(new Aday("candy.tanidik.inkar"));
        }
        // 5) Hep aynı yöne kaçıyor.
        if (tp.kacOrani >= 0.6) {
            adaylar.add(new Aday("candy.tanidik.kac"));
        }
        // 6) Kendi meşalelerini kırıyor.
        if (tp.kirilanMesaleSayisi >= 3) {
            adaylar.add(new Aday("candy.tanidik.mesale"));
        }
        // 7) Dünyanın adı — hep elde (son çareye yakın).
        String dunya = server.getWorldData().getLevelSettings().levelName();
        if (dunya != null && !dunya.isEmpty()) {
            adaylar.add(new Aday("candy.tanidik.dunya", dunya));
        }
        // 8) Mutlak son çare.
        if (adaylar.isEmpty()) {
            adaylar.add(new Aday("candy.tanidik.gozlem"));
        }

        Aday sec = adaylar.get((int) (Math.random() * adaylar.size()));
        player.displayClientMessage(
            Component.translatable(sec.anahtar, (Object[]) sec.arglar)
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC),
            false);

        tp.metaFarkindalik = Math.min(100.0, tp.metaFarkindalik + 2.0);
        Candy.LOG.debug("[Candy] TanidikFisilti -> {} ({})",
            player.getGameProfile().getName(), sec.anahtar);

        disKanalBelki(player, tp, sec, simdi);
        return true;
    }

    /**
     * v9.2.0 — Aynı fısıltıyı NADİREN dış kanala da taşır (e-posta). Sunucu yalnızca
     * "şu satırı dışarı ver" der; e-postayı istemci kendi config'inden okuyup kendi
     * gönderir, bu yüzden adres sunucuya hiç uğramaz. İstemcide mektup kapalıysa paket
     * sessizce düşer — yani bu çağrı "izin varsa" değil, "belki" çağrısıdır.
     */
    private static void disKanalBelki(ServerPlayer player, TruePersonality tp,
                                      Aday sec, long simdi) {
        if (tp.perdeKademe < MIN_KADEME_DIS) return;
        Long son = sonDisKanal.get(player.getUUID());
        if (son != null && simdi - son < DIS_SOGUMA) return;
        if (Math.random() > DIS_IHTIMAL) return;

        String arg0 = (sec.arglar != null && sec.arglar.length > 0) ? sec.arglar[0] : "";
        try {
            SunucuAg.send(player, new BildirimPayload(sec.anahtar, arg0));
            sonDisKanal.put(player.getUUID(), simdi);
            Candy.LOG.debug("[Candy] TanidikFisilti dış kanal -> {} ({})",
                player.getGameProfile().getName(), sec.anahtar);
        } catch (Throwable ignored) {
            // vanilla/eski istemci kanalı taşımıyor olabilir — sorun değil, sus
        }
    }

    /** tabelaCevaplari içinden yer tutucu olmayan ("<...>") rastgele bir cevap; yoksa null. */
    private static String rastgeleGercekCevap(TruePersonality tp) {
        if (tp.tabelaCevaplari == null || tp.tabelaCevaplari.isEmpty()) return null;
        List<String> uygun = new ArrayList<>();
        for (String c : tp.tabelaCevaplari) {
            if (c == null) continue;
            String t = c.trim();
            if (t.isEmpty() || t.startsWith("<")) continue; // "<sessiz>" vb. işaretçiler
            uygun.add(t.length() > MAKS_ALINTI ? t.substring(0, MAKS_ALINTI) : t);
        }
        if (uygun.isEmpty()) return null;
        return uygun.get((int) (Math.random() * uygun.size()));
    }

    public static void sunucuKapan() {
        sonFisilti.clear();
        sonDisKanal.clear();
    }

    /** Fısıltı adayı: lang anahtarı + {@code %s} argümanları. */
    private static final class Aday {
        final String anahtar;
        final String[] arglar;
        Aday(String anahtar, String... arglar) {
            this.anahtar = anahtar;
            this.arglar = arglar;
        }
    }
}
