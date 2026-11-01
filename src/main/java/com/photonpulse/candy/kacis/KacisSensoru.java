package com.photonpulse.candy.kacis;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.core.TruePersonality;
import com.photonpulse.candy.platform.SunucuTick;
import com.photonpulse.candy.platform.AgKayit;
import com.photonpulse.candy.platform.SunucuAg;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * KacisSensoru (v8.2) — "oyundan değil, Candy'den kaçma" davranışını ölçer.
 *
 * <h3>NEDEN YENİ SİSTEM DEĞİL, YENİ SENSÖR</h3>
 * The Archivist creative/spectator'a kaçışı <b>cezalandırıyor</b>. Candy'de ceza yanlış
 * olurdu: kaçış zaten modellenmiş bir duygunun (ihmal → {@code candyOfke}) kaynağı.
 * Burada yapılan tek şey mevcut duyguya yeni bir besleme kanalı açmak — oyuncu ayarla
 * oynadığında Candy bunu <i>hisseder</i>, engellemez.
 *
 * <h3>NE SAYILIR</h3>
 * <ul>
 *   <li>Survival dışı mod (creative / spectator / adventure)</li>
 *   <li>Peaceful zorluk</li>
 *   <li>Cheat komutu denemesi ({@code CommandManagerMixin} haber verir)</li>
 *   <li>F3 hata ayıklama ekranı (istemci bildirir — {@link KacisPayload})</li>
 *   <li>Modu kaldırıp geri koyma (dünya saati Candy'siz ilerlemiş)</li>
 * </ul>
 *
 * <h3>NE SAYILMAZ</h3>
 * Oyunu kapatmak, ölmek, uyumak. Bunlar oyunun parçası; Candy'den kaçma değil. Mod
 * korkutmak için oyuncunun oyunu BIRAKMASINI istemiyor — tam tersine profil makinesi
 * oyuncunun aynı dünyada kalmasına bağlı.
 */
public final class KacisSensoru {

    private KacisSensoru() {}

    /** Durum taraması aralığı — 5 saniye yeterli, mod/zorluk sık değişmez. */
    private static final int ARALIK = 100;
    /** Eksenin yürüme hızı. */
    private static final double KAYMA = 0.05;
    /** Tek olayın eksene kattığı anlık darbe. */
    private static final double DARBE = 0.25;
    /** Darbe sönümü — olay bitince eksen kendiliğinden geri iner. */
    private static final double SONUM = 0.97;
    /**
     * Modsuz oynama eşiği: dünya saati Candy çalışmadan bu kadar ilerlediyse mod
     * kaldırılmış demektir. 2 oyun günü — tek bir çökme/geri yükleme yanlış suçlanmasın.
     */
    private static final long MODSUZ_ESIK = 48_000L;

    /** Olay darbeleri (ölçüm tick'leri arasında birikir). */
    private static final Map<UUID, Double> darbeler = new ConcurrentHashMap<>();

    private static int tick = 0;

    public static void register() {
        try {
            AgKayit.playC2S().register(KacisPayload.ID, KacisPayload.CODEC);
        } catch (Throwable t) {
            Candy.LOG.debug("[Candy] Kacis C2S zaten kayıtlı: {}", t.getMessage());
        }
        SunucuAg.registerGlobalReceiver(KacisPayload.ID, (payload, context) -> {
            // İstemciden gelen SAYI, metin değil — hiçbir kişisel veri taşımıyor.
            int f3 = Math.max(0, Math.min(20_000, payload.f3Tick()));
            if (f3 <= 0) return;
            ServerPlayer player = context.player();
            context.server().execute(() -> olay(player, "f3", DARBE * Math.min(1.0, f3 / 1200.0)));
        });
        SunucuTick.END_SERVER_TICK.register(KacisSensoru::onTick);
        Candy.LOG.info("[Candy] KacisSensoru aktif — kaçış arayışı ekseni ölçülüyor");
    }

    private static void onTick(MinecraftServer server) {
        tick++;
        if (tick % ARALIK != 0) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            try {
                tara(player);
            } catch (Throwable t) {
                Candy.LOG.debug("[Candy] KacisSensoru hata: {}", t.getMessage());
            }
        }
    }

    private static void tara(ServerPlayer player) {
        TruePersonality tp = PersonalityManager.get(player);
        if (tp == null || !tp.kurulumTamam) return;
        ServerLevel world = (ServerLevel) player.level();

        double anlik = 0.0;
        GameType mod = player.gameMode.getGameModeForPlayer();
        if (mod == GameType.CREATIVE || mod == GameType.SPECTATOR) anlik += 0.6;
        else if (mod == GameType.ADVENTURE) anlik += 0.2;
        if (world.getDifficulty() == Difficulty.PEACEFUL) anlik += 0.4;

        UUID id = player.getUUID();
        double birikmis = darbeler.getOrDefault(id, 0.0);
        darbeler.put(id, birikmis * SONUM);

        double hedef = Math.min(1.0, anlik + birikmis);
        double onceki = tp.kacisArayisi;
        tp.kacisArayisi = TruePersonality.kayma(tp.kacisArayisi, hedef, KAYMA);

        // Eksen YÜKSELİYORSA öfke beslenir. Düşerken beslenmez — kaçışın bırakılması
        // ödül değil, sadece olayın bitmesi.
        if (tp.kacisArayisi > onceki + 0.001) {
            tp.candyOfke = Math.min(1.0, tp.candyOfke + (tp.kacisArayisi - onceki) * 0.5);
        }
        // Kalıcı kaçış (sürekli creative) ihmal sayılır — tabela öfkesiyle aynı kanal.
        if (tp.kacisArayisi > 0.7 && tick % (ARALIK * 12) == 0) {
            tp.ihmalSayisi++;
        }
    }

    /** Tek seferlik kaçış olayı — darbe olarak eksene girer. */
    public static void olay(ServerPlayer player, String tur, double siddet) {
        try {
            if (player == null) return;
            TruePersonality tp = PersonalityManager.get(player);
            if (tp == null) return;
            tp.kacisDenemesi++;
            darbeler.merge(player.getUUID(), Math.max(0.0, siddet), Double::sum);
            Candy.LOG.debug("[Candy] Kacis olayi: {} → {} (toplam {})",
                player.getName().getString(), tur, tp.kacisDenemesi);
        } catch (Throwable t) {
            Candy.LOG.debug("[Candy] Kacis olay hatası: {}", t.getMessage());
        }
    }

    /** Cheat komutu denemesi — {@code CommandManagerMixin} çağırır. */
    public static void komutDenemesi(ServerPlayer player) {
        olay(player, "komut", DARBE);
    }

    /**
     * Katılışta "mod kaldırılıp geri konmuş mu" kontrolü.
     *
     * <p>Tek oyunculuda dünya saati yalnızca oyuncu oynarken ilerler. Candy'nin en son
     * gördüğü tick ile şimdiki tick arasında büyük bir boşluk varsa, o dünya Candy
     * yüklü değilken oynanmıştır. Adanmış sunucuda saat oyuncu yokken de ilerlediği
     * için bu çıkarım orada geçersiz — kasıtlı olarak atlanıyor.</p>
     */
    public static void katilis(ServerPlayer player) {
        try {
            TruePersonality tp = PersonalityManager.get(player);
            if (tp == null) return;
            ServerLevel world = (ServerLevel) player.level();
            MinecraftServer server = world.getServer();
            long simdi = world.getGameTime();

            if (server.isSingleplayer() && tp.sonGorulenDunyaTick > 0
                && simdi - tp.sonGorulenDunyaTick > MODSUZ_ESIK) {
                tp.modsuzOynandi++;
                olay(player, "modsuz", 0.8);
                tp.candyOfke = Math.min(1.0, tp.candyOfke + 0.25);
                Candy.LOG.info("[Candy] Modsuz oynama tespit: {} — {} tick boşluk (toplam {})",
                    player.getName().getString(), simdi - tp.sonGorulenDunyaTick,
                    tp.modsuzOynandi);
            }
            tp.sonGorulenDunyaTick = simdi;
        } catch (Throwable t) {
            Candy.LOG.debug("[Candy] Kacis katılış hatası: {}", t.getMessage());
        }
    }

    /** Ayrılışta son görülen tick'i yaz — bir sonraki katılış buna bakacak. */
    public static void ayrilis(ServerPlayer player) {
        try {
            TruePersonality tp = PersonalityManager.get(player);
            if (tp != null) tp.sonGorulenDunyaTick = player.level().getGameTime();
        } catch (Throwable ignored) {}
        if (player != null) darbeler.remove(player.getUUID());
    }

    public static void oyuncuAyrildi(UUID uuid) { darbeler.remove(uuid); }

    public static void sunucuKapan() { darbeler.clear(); }
}
