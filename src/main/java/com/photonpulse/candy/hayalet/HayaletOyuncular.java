package com.photonpulse.candy.hayalet;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.core.TruePersonality;
import com.photonpulse.candy.platform.DunyaDegisimi;
import com.photonpulse.candy.platform.SunucuTick;
import com.photonpulse.candy.platform.SohbetOlaylari;
import com.photonpulse.candy.platform.SunucuBaglanti;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * HayaletOyuncular — Steve ve Alex (v1.4.6).
 *
 * Görünmeyen "oyuncular" — chat, dünya değişiklik, ölüm/respawn, panik, portal karışıklığı.
 */
public class HayaletOyuncular {

    private static final Random RNG = new Random();

    // Fake join
    private static boolean joinYazildi = false;
    private static long joinBekleyenTick = -1L;

    // Chat
    private static long chatSonrakiTick = 0L;

    // Dünya modifikasyonu
    private static long modifikasyonSonrakiTick = 0L;

    // v1.7.0: Eve dönme — köylü ev hijack
    private static long eveDonmeSonrakiTick = 0L;
    private static final Set<Long> hijackedBellPozisyonlari = new HashSet<>();

    // Ölüm/respawn (v1.4.6)
    private static long olumSonrakiTick = -1L;
    // isim → geri geleceği tick (o zamana kadar suskun)
    private static final Map<String, Long> hayaletDondu = new HashMap<>();
    /** Dönüş kontrolünün yeniden kullanılan tamponu — her tick yeni liste kurmamak için. */
    private static final java.util.List<String> donenScratch = new java.util.ArrayList<>(4);

    // Kriz (v1.4.6)
    private static long krizSonrakiTick = -1L;
    // isim → sessizlik bitiş tick
    private static final Map<String, Long> hayaletSessiz = new HashMap<>();

    // Portal karışıklığı (v1.4.6)
    private static final Map<UUID, Long> portalCooldown = new HashMap<>();

    // Chat cevap kuyruğu (v1.4.6 → v1.9.12: mesaj artık lang key)
    private record PendingCevap(long triggerTick, String isim, String langKey) {}
    private static final List<PendingCevap> cevapKuyrugu = new CopyOnWriteArrayList<>();

    // Sign check
    private static final int SIGN_CHECK_ARALIGI = 600;
    private static final Map<UUID, Set<Long>> cevaplananTabelalar = new HashMap<>();

    // v1.9.12: TÜM MESAJLAR LANG KEY OLDU — player'ın oyun diline göre çevrilir
    private static final int STEVE_MESAJ_SAYI = 16;
    private static final int ALEX_MESAJ_SAYI = 15;
    private static final int KOORDINAT_SAYI = 5;
    private static final int KRIZ_SAYI = 8;
    private static final int CEVAP_SAYI = 15;
    /** v9.1.0: saat bandı başına satır sayısı (her bant, her hayalet için ayrı). */
    private static final int SAAT_SATIR_SAYI = 2;
    private static final int OLUM_SAYI = 7;

    private static String steveKey(int i) { return "candy.hayalet.steve." + i; }
    private static String alexKey(int i) { return "candy.hayalet.alex." + i; }
    private static String koordKey(int i) { return "candy.hayalet.koord." + i; }
    private static String krizKey(int i) { return "candy.hayalet.kriz." + i; }
    private static String cevapKey(int i) { return "candy.hayalet.cevap." + i; }

    public static void register() {
        // İlk oyuncu — fake join başlat
        SunucuBaglanti.JOIN.register((handler, server) -> {
            if (joinYazildi || joinBekleyenTick > 0) return;
            joinBekleyenTick = server.overworld().getGameTime() + 60L;
        });

        SunucuTick.END_SERVER_TICK.register(HayaletOyuncular::onTick);

        // v1.4.6: player chat mesajı → cevap kuyruğuna ekle (%25)
        SohbetOlaylari.CHAT_MESSAGE.register((sender, hamMetin, sunulanMetin) -> {
            if (RNG.nextDouble() > 0.25) return;
            String isim = secilebilirHayalet(sender.level().getGameTime());
            if (isim == null) return;
            String cevap = cevapKey(RNG.nextInt(CEVAP_SAYI));
            long trigger = sender.level().getGameTime() + 100L + RNG.nextInt(200); // 5-15 sn
            cevapKuyrugu.add(new PendingCevap(trigger, isim, cevap));
        });

        // v1.4.6: portal geçiş — %20 karıştırma, 5 dk cooldown
        DunyaDegisimi.AFTER_PLAYER_CHANGE_WORLD.register(
            (player, origin, destination) -> {
                // Sadece nether ↔ overworld geçişleri
                boolean netherPortal =
                    (origin.dimension() == Level.NETHER && destination.dimension() == Level.OVERWORLD)
                    || (origin.dimension() == Level.OVERWORLD && destination.dimension() == Level.NETHER);
                if (!netherPortal) return;

                long simdi = destination.getGameTime();
                Long son = portalCooldown.get(player.getUUID());
                if (son != null && simdi - son < 6000L) return; // 5 dk
                if (RNG.nextDouble() > 0.20) return;

                portalCooldown.put(player.getUUID(), simdi);

                // 200-800 blok random yer
                int mesafe = 200 + RNG.nextInt(600);
                double aci = RNG.nextDouble() * Math.PI * 2;
                int dx = (int) (Math.cos(aci) * mesafe);
                int dz = (int) (Math.sin(aci) * mesafe);
                BlockPos yeni = new BlockPos(
                    player.getBlockX() + dx,
                    player.getBlockY(),
                    player.getBlockZ() + dz
                );

                // Overworld ise yüzey Y
                if (destination.dimension() == Level.OVERWORLD) {
                    int y = destination.getHeight(
                        net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, yeni.getX(), yeni.getZ());
                    yeni = new BlockPos(yeni.getX(), y + 1, yeni.getZ());
                } else {
                    // Nether — güvenli Y ara (5-115 arasında hava + altı katı)
                    BlockPos guvenli = netherGuvenliY(destination, yeni.getX(), yeni.getZ());
                    if (guvenli == null) {
                        portalCooldown.remove(player.getUUID()); // cooldown iade
                        return;
                    }
                    yeni = guvenli;
                }

                player.teleportTo(destination, yeni.getX() + 0.5, yeni.getY(),
                    yeni.getZ() + 0.5, java.util.Collections.emptySet(),
                    player.getYRot(), player.getXRot(), false);
                Candy.LOG.info("[Candy] PortalKarisikligi[{}] → {}",
                    player.getName().getString(), yeni);

                // Sonra Steve/Alex chat: "sen neredesin"
                String isim = RNG.nextBoolean() ? "Steve" : "Alex";
                if (hayaletAktifMi(isim, destination.getGameTime())) {
                    long trigger = destination.getGameTime() + 60L;
                    cevapKuyrugu.add(new PendingCevap(trigger, isim, "candy.hayalet.portal_soru"));
                }
            });

        Candy.LOG.info("[Candy] HayaletOyuncular aktif — Steve/Alex + ölüm/kriz/portal");
    }

    private static void onTick(MinecraftServer server) {
        if (server.getPlayerList().getPlayers().isEmpty()) return;
        long simdi = server.overworld().getGameTime();

        // v1.9.0: sessizlik protokolü — tüm hayalet aktivitesi susulur
        if (com.photonpulse.candy.sessizlik.SessizlikState.sessizlikte(simdi)) return;

        // 1) Fake join
        if (!joinYazildi && joinBekleyenTick > 0 && simdi >= joinBekleyenTick) {
            fakeJoin(server, "Steve");
            joinYazildi = true;
            joinBekleyenTick = simdi + 40L;
        }
        if (joinYazildi && joinBekleyenTick > 0 && simdi >= joinBekleyenTick) {
            fakeJoin(server, "Alex");
            joinBekleyenTick = -2L;
            chatSonrakiTick = simdi + rastgeleAralik(1200, 2400);
            modifikasyonSonrakiTick = simdi + 1200L;
            olumSonrakiTick = simdi + rastgeleAralik(6000, 12000);   // 5-10 dk
            krizSonrakiTick = simdi + rastgeleAralik(18000, 24000);  // 15-20 dk
            eveDonmeSonrakiTick = simdi + rastgeleAralik(6000, 12000); // 5-10 dk
        }

        // Eve dönme
        if (joinBekleyenTick == -2L && simdi >= eveDonmeSonrakiTick && eveDonmeSonrakiTick > 0) {
            eveDonme(server);
            eveDonmeSonrakiTick = simdi + rastgeleAralik(6000, 12000);
        }

        // 2) Ölüm sistemi
        if (joinBekleyenTick == -2L && simdi >= olumSonrakiTick && olumSonrakiTick > 0) {
            hayaletOlum(server, simdi);
            olumSonrakiTick = simdi + rastgeleAralik(6000, 12000);
        }
        // Respawn kontrolü — dönüş zamanı gelenler
        //
        // v8: kopya her tick DEĞİL, yalnız kuyruk doluyken ve yeniden kullanılan bir
        // tampona kuruluyor. Kopya gerekli: döngü içinde map'ten silmek ve fakeJoin'in
        // map'e dokunabilmesi ConcurrentModificationException üretirdi.
        if (!hayaletDondu.isEmpty()) {
            donenScratch.clear();
            for (var e : hayaletDondu.entrySet()) {
                if (simdi >= e.getValue()) donenScratch.add(e.getKey());
            }
            for (String isim : donenScratch) {
                hayaletDondu.remove(isim);
                fakeJoin(server, isim); // "Steve joined the game"
            }
        }

        // 3) Kriz sistemi
        if (joinBekleyenTick == -2L && simdi >= krizSonrakiTick && krizSonrakiTick > 0) {
            hayaletKriz(server, simdi);
            krizSonrakiTick = simdi + rastgeleAralik(18000, 24000);
        }

        // 4) Chat mesajı (koordinat de burda)
        if (joinBekleyenTick == -2L && simdi >= chatSonrakiTick) {
            String isim = secilebilirHayalet(simdi);
            if (isim != null) {
                double rol = RNG.nextDouble();
                // %5 ihtimalle (sadece Alex) ŞEHİR mesajı — "Steve, ben X şehrinden oynuyorum..."
                // Private mesaj (broadcast DEĞİL) — konum bilgisi diğer oyunculara sızmasın.
                if (isim.equals("Alex") && rol < 0.05 && sehirMesaji(server)) {
                    // gönderildi
                }
                // %10 ihtimalle DONANIM mesajı — "Ben PC'de <senin parçan> var."
                else if (rol < 0.10 && donanimMesaji(server, isim)) {
                    // gönderildi
                }
                // v8.1: %4 ihtimalle UYGULAMA mesajı — "Sende <açık uygulaman> açık mı?"
                else if (rol < 0.14 && uygulamaMesaji(server, isim)) {
                    // gönderildi
                } else if (rol < 0.30) {
                    // %20 koordinat mesajı
                    koordinatBirakVeSoyle(server, isim);
                }
                // v9.1.0: %8 ihtimalle SAAT mesajı — oyuncunun gerçek duvar saati.
                // Koordinattan SONRA: üstteki pencerelerin hiçbiri daralmıyor.
                else if (rol < 0.38 && saatMesaji(server, isim)) {
                    // gönderildi
                } else {
                    int idx = isim.equals("Steve")
                        ? RNG.nextInt(STEVE_MESAJ_SAYI)
                        : RNG.nextInt(ALEX_MESAJ_SAYI);
                    String key = isim.equals("Steve") ? steveKey(idx) : alexKey(idx);
                    fakeChat(server, isim, key);
                }
            }
            chatSonrakiTick = simdi + rastgeleAralik(1200, 2400);
        }

        // 5) Dünya modifikasyonu
        if (joinBekleyenTick == -2L && simdi >= modifikasyonSonrakiTick) {
            dunyayiDegistir(server);
            modifikasyonSonrakiTick = simdi + 1200L;
        }

        // 6) Cevap kuyruğu
        //
        // v8: ara liste kaldırıldı. cevapKuyrugu bir CopyOnWriteArrayList — iterator'ı
        // anlık görüntü üzerinde çalışır, dolayısıyla döngü içinde silmek güvenlidir ve
        // "önce topla sonra işle" adımı gereksizdi (her tick bir ArrayList tahsisi).
        if (!cevapKuyrugu.isEmpty()) {
            for (PendingCevap c : cevapKuyrugu) {
                if (simdi < c.triggerTick) continue;
                cevapKuyrugu.remove(c);
                if (hayaletAktifMi(c.isim, simdi)) {
                    fakeChat(server, c.isim, c.langKey);
                }
            }
        }

        // 7) Sign check
        if (server.getTickCount() % SIGN_CHECK_ARALIGI == 0) {
            tabelalariKontrolEt(server);
        }
    }

    /** Aktif (yaşayan ve krizde olmayan) hayalet döndürür veya null. */
    private static String secilebilirHayalet(long simdi) {
        boolean steveAktif = hayaletAktifMi("Steve", simdi);
        boolean alexAktif = hayaletAktifMi("Alex", simdi);
        if (steveAktif && alexAktif) return RNG.nextBoolean() ? "Steve" : "Alex";
        if (steveAktif) return "Steve";
        if (alexAktif) return "Alex";
        return null;
    }

    private static boolean hayaletAktifMi(String isim, long simdi) {
        Long donus = hayaletDondu.get(isim);
        if (donus != null && simdi < donus) return false;
        Long sessizBitis = hayaletSessiz.get(isim);
        if (sessizBitis != null && simdi < sessizBitis) return false;
        return true;
    }

    private static long rastgeleAralik(int min, int max) {
        return min + RNG.nextInt(max - min);
    }

    private static void fakeJoin(MinecraftServer server, String isim) {
        Component msg = Component.translatable("multiplayer.player.joined", isim).withStyle(ChatFormatting.YELLOW);
        server.getPlayerList().broadcastSystemMessage(msg, false);
        Candy.LOG.info("[Candy] Hayalet[{}] joined", isim);
    }

    private static void fakeLeave(MinecraftServer server, String isim) {
        Component msg = Component.translatable("multiplayer.player.left", isim).withStyle(ChatFormatting.YELLOW);
        server.getPlayerList().broadcastSystemMessage(msg, false);
    }

    /** v1.9.12: mesaj artık lang key — player'ın dilinde çevrilir. */
    private static void fakeChat(MinecraftServer server, String isim, String langKey) {
        Component formatli = Component.literal("<" + isim + "> ")
            .append(Component.translatable(langKey));
        server.getPlayerList().broadcastSystemMessage(formatli, false);
        Candy.LOG.info("[Candy] Hayalet[{}]: {}", isim, langKey);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            var tp = PersonalityManager.get(p);
            if (tp.kurulumTamam && !tp.hayaletChatGorulduMu) {
                tp.hayaletChatGorulduMu = true;
                com.photonpulse.candy.basarim.CandyBasarimlar.tetikle(p, "sahte_dost");
            }
        }
    }

    /** Koordinat mesajı — lang key %s içerir, koord string olarak gelir. */
    private static void fakeChatArg(MinecraftServer server, String isim, String langKey, Object arg) {
        Component formatli = Component.literal("<" + isim + "> ")
            .append(Component.translatable(langKey, arg));
        server.getPlayerList().broadcastSystemMessage(formatli, false);
        Candy.LOG.info("[Candy] Hayalet[{}]: {} ({})", isim, langKey, arg);
    }

    /** Vanilla death.attack.* lang key kullanır → oyuncu diline göre otomatik çevrilir. */
    private static void fakeVanillaDeath(MinecraftServer server, String langKey, Object... args) {
        Component msg = Component.translatable(langKey, args);
        server.getPlayerList().broadcastSystemMessage(msg, false);
        Candy.LOG.info("[Candy] Hayalet ölüm: {}", msg.getString());
    }

    /**
     * Random Steve veya Alex ölür → death message + 30 sn sonra respawn.
     */
    private static void hayaletOlum(MinecraftServer server, long simdi) {
        // Halihazırda ölü olanı seçme
        boolean steveDiri = !hayaletDondu.containsKey("Steve");
        boolean alexDiri = !hayaletDondu.containsKey("Alex");
        String isim;
        if (steveDiri && alexDiri) isim = RNG.nextBoolean() ? "Steve" : "Alex";
        else if (steveDiri) isim = "Steve";
        else if (alexDiri) isim = "Alex";
        else return;

        // v1.9.12: vanilla death.attack.* lang key'leri — otomatik lokalizasyon
        int olumIdx = RNG.nextInt(OLUM_SAYI);
        switch (olumIdx) {
            case 0 -> fakeVanillaDeath(server, "death.attack.mob", isim,
                Component.translatable("entity.minecraft.zombie"));
            case 1 -> fakeVanillaDeath(server, "death.attack.lava", isim);
            case 2 -> fakeVanillaDeath(server, "death.attack.drown", isim);
            case 3 -> fakeVanillaDeath(server, "death.attack.explosion", isim,
                Component.translatable("entity.minecraft.creeper"));
            case 4 -> fakeVanillaDeath(server, "death.attack.fall", isim);
            case 5 -> fakeVanillaDeath(server, "death.attack.arrow", isim,
                Component.translatable("entity.minecraft.skeleton"));
            case 6 -> fakeVanillaDeath(server, "death.attack.mob", isim,
                Component.translatable("entity.minecraft.enderman"));
            default -> fakeVanillaDeath(server, "death.attack.generic", isim);
        }
        fakeLeave(server, isim);
        hayaletDondu.put(isim, simdi + 600L); // 30 sn sonra rejoin
    }

    /**
     * Random hayalet kriz → mesaj + 5-10 dk sessizlik.
     */
    private static void hayaletKriz(MinecraftServer server, long simdi) {
        String isim = secilebilirHayalet(simdi);
        if (isim == null) return;
        String mesaj = krizKey(RNG.nextInt(KRIZ_SAYI));
        fakeChat(server, isim, mesaj);
        long sessizlik = 6000L + RNG.nextInt(6001); // 5-10 dk
        hayaletSessiz.put(isim, simdi + sessizlik);
    }

    /**
     * Donanım mesajı — rastgele bir online oyuncunun GERÇEK PC parçasını seç,
     * hayalet "Ben PC'de %s var." diyerek aslında o oyuncunun donanımını söyler.
     * @return gönderildiyse true (parça yoksa false → normal mesaja düş).
     */
    private static boolean donanimMesaji(MinecraftServer server, String isim) {
        var players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) return false;
        ServerPlayer hedef = players.get(RNG.nextInt(players.size()));
        String parca = com.photonpulse.candy.meta.DonanimBilgisi.rastgeleParca(hedef);
        if (parca == null || parca.isBlank()) return false;
        // v8.1: ÖZEL mesaj — eskiden broadcast'ti ve çok oyunculuda bir oyuncunun GPU'su
        // diğerlerine sızıyordu. Kişisel veri yalnızca sahibine gösterilir (sehirMesaji ile
        // aynı kural). Log'a da parça adı DEĞİL, yalnızca oyuncu adı yazılır.
        hedef.displayClientMessage(
            net.minecraft.network.chat.Component.literal("<" + isim + "> ")
                .append(net.minecraft.network.chat.Component.translatable("candy.hayalet.donanim", parca))
                .withStyle(net.minecraft.ChatFormatting.YELLOW), false);
        Candy.LOG.info("[Candy] Hayalet[{}] donanim (private): {}",
            isim, hedef.getName().getString());
        return true;
    }

    /**
     * Uygulama mesajı — hayalet "Sende {@code %s} açık mı?" der. Beyaz listede eşleşen
     * uygulama {@link com.photonpulse.candy.meta.UygulamaBilgisi}'nde RAM'de duruyor.
     *
     * <p>Candy olarak değil <b>Steve/Alex olarak</b> soruluyor: sıradan bir oyuncu
     * sorusu gibi dursun, bir sistemin kişisel veri okuduğu anlaşılmasın.</p>
     *
     * <p>ÖZEL mesaj — veri yalnızca sahibine gösterilir.</p>
     *
     * @return gönderildiyse true (eşleşme yoksa false → normal mesaja düş)
     */
    private static boolean uygulamaMesaji(MinecraftServer server, String isim) {
        var players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) return false;
        ServerPlayer hedef = players.get(RNG.nextInt(players.size()));
        String ad = com.photonpulse.candy.meta.UygulamaBilgisi.ad(hedef);
        if (ad == null || ad.isBlank()) return false;
        hedef.displayClientMessage(
            net.minecraft.network.chat.Component.literal("<" + isim + "> ")
                .append(net.minecraft.network.chat.Component.translatable("candy.hayalet.uygulama", ad))
                .withStyle(net.minecraft.ChatFormatting.YELLOW), false);
        Candy.LOG.info("[Candy] Hayalet[{}] uygulama (private): {}",
            isim, hedef.getName().getString());
        return true;
    }

    /**
     * Saat mesajı (v9.1.0) — hayalet, oyuncunun <b>gerçek duvar saatine</b> göre konuşur.
     *
     * <p>Saat istemciden geliyor ({@link com.photonpulse.candy.meta.SaatBilgisi}), sunucu
     * JVM'inin saatinden değil: korku "sen gecenin üçünde oynuyorsun" cümlesinde, sunucuyu
     * barındıran makinenin saat diliminde değil.</p>
     *
     * <p>Diyalog günün bandına göre seçiliyor (ölü saatler / şafak / sabah / öğlen / akşam
     * / gece) ve Steve ile Alex'in bandı ayrı: Steve sıradan bir oyuncu gibi laf atıyor,
     * Alex saati bildiğini belli ediyor. Saat 03 ise "üç" özel satırı öne geçiyor.</p>
     *
     * <p>ÖZEL mesaj — saat kişisel veri, yalnızca sahibine gösterilir (şehir ve donanım
     * ile aynı kural). Log'a saat DEĞİL, yalnızca oyuncu adı yazılır.</p>
     *
     * @return gönderildiyse true (saat bilinmiyorsa false → normal mesaja düş)
     */
    private static boolean saatMesaji(MinecraftServer server, String isim) {
        var players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) return false;
        ServerPlayer hedef = players.get(RNG.nextInt(players.size()));

        int saat = com.photonpulse.candy.meta.SaatBilgisi.saat(hedef);
        String saatMetni = com.photonpulse.candy.meta.SaatBilgisi.metin(hedef);
        if (saat < 0 || saatMetni == null) return false;

        String kim = isim.equals("Steve") ? "steve" : "alex";
        String anahtar;
        Object arg = saatMetni;

        String dilim = com.photonpulse.candy.meta.SaatBilgisi.dilim(hedef);
        if (dilim != null && RNG.nextDouble() < 0.10) {
            // %10: saat dilimi adının kendisi — "Europe/Istanbul? benim saatim başka diyor."
            anahtar = "candy.hayalet.saat.dilim";
            arg = dilim;
        } else if (saat == 3 && RNG.nextDouble() < 0.6) {
            anahtar = "candy.hayalet.saat." + kim + ".uc";
        } else {
            anahtar = "candy.hayalet.saat." + kim + "." + saatBandi(saat)
                + "." + RNG.nextInt(SAAT_SATIR_SAYI);
        }

        hedef.displayClientMessage(
            Component.literal("<" + isim + "> ")
                .append(Component.translatable(anahtar, arg))
                .withStyle(ChatFormatting.YELLOW), false);
        Candy.LOG.info("[Candy] Hayalet[{}] saat (private): {}",
            isim, hedef.getName().getString());
        return true;
    }

    /** Gerçek saat → diyalog bandı. */
    private static String saatBandi(int saat) {
        if (saat <= 4) return "olu";        // 00–04 — ölü saatler
        if (saat <= 7) return "safak";      // 05–07
        if (saat <= 11) return "sabah";     // 08–11
        if (saat <= 16) return "oglen";     // 12–16
        if (saat <= 20) return "aksam";     // 17–20
        return "gece";                      // 21–23
    }

    /**
     * Şehir mesajı — Alex "Steve, ben X şehrinden oynuyorum, peki ya sen?" der.
     * X = rastgele bir online oyuncunun GERÇEK şehri (IP-tabanlı geolocation, client-side).
     * PRIVATE — sadece o oyuncuya gönderilir (broadcast değil, konum sızmasın).
     * @return gönderildiyse true (şehir yoksa false → normal mesaja düş).
     */
    private static boolean sehirMesaji(MinecraftServer server) {
        var players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) return false;
        ServerPlayer hedef = players.get(RNG.nextInt(players.size()));
        String sehir = com.photonpulse.candy.meta.SehirBilgisi.sehir(hedef);
        if (sehir == null || sehir.isBlank()) return false;
        hedef.displayClientMessage(
            net.minecraft.network.chat.Component.literal("<Alex> ")
                .append(net.minecraft.network.chat.Component.translatable("candy.hayalet.sehir", sehir))
                .withStyle(net.minecraft.ChatFormatting.YELLOW), false);
        Candy.LOG.info("[Candy] Hayalet[Alex] sehir (private): {}", hedef.getName().getString());
        return true;
    }

    /**
     * Koordinat mesajı + o koordinata gerçek küçük yapı bırak.
     */
    private static void koordinatBirakVeSoyle(MinecraftServer server, String isim) {
        var players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) return;
        ServerPlayer player = players.get(RNG.nextInt(players.size()));
        ServerLevel world = (ServerLevel) player.level();
        if (world.dimension() != Level.OVERWORLD) return;

        // 100-300 blok yatay ofset
        int mesafe = 100 + RNG.nextInt(200);
        double aci = RNG.nextDouble() * Math.PI * 2;
        int mx = player.getBlockX() + (int) (Math.cos(aci) * mesafe);
        int mz = player.getBlockZ() + (int) (Math.sin(aci) * mesafe);

        if (!world.hasChunk(mx >> 4, mz >> 4)) {
            // Chunk yok, sadece söyle ama yapı yok
            String key = koordKey(RNG.nextInt(KOORDINAT_SAYI));
            fakeChatArg(server, isim, key, mx + " " + player.getBlockY() + " " + mz);
            return;
        }

        int my = world.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, mx, mz);
        BlockPos yuzey = new BlockPos(mx, my, mz);

        // 2x2 plank platform + torch + sign
        boolean birseyKondu = false;
        for (int dx = 0; dx < 2; dx++) {
            for (int dz = 0; dz < 2; dz++) {
                BlockPos p = yuzey.offset(dx, 0, dz);
                if (world.getBlockState(p).isAir()) {
                    world.setBlockAndUpdate(p, Blocks.OAK_PLANKS.defaultBlockState());
                    birseyKondu = true;
                }
            }
        }
        BlockPos ustPos = yuzey.above();
        if (world.getBlockState(ustPos).isAir()) {
            world.setBlockAndUpdate(ustPos, Blocks.TORCH.defaultBlockState());
        }
        BlockPos signPos = yuzey.offset(1, 1, 0);
        if (world.getBlockState(signPos).isAir()) {
            world.setBlockAndUpdate(signPos, Blocks.OAK_SIGN.defaultBlockState());
            BlockEntity be = world.getBlockEntity(signPos);
            if (be instanceof SignBlockEntity sign) {
                SignText metin = new SignText();
                metin = metin.setMessage(0, Component.literal(isim));
                metin = metin.setMessage(1, Component.literal("was here"));
                sign.setText(metin, true);
                sign.setChanged();
            }
        }

        String key = koordKey(RNG.nextInt(KOORDINAT_SAYI));
        String koord = mx + " " + my + " " + mz;
        fakeChatArg(server, isim, key, koord);
        Candy.LOG.info("[Candy] Hayalet[{}] koordinat bıraktı @ {} ({} kondu)",
            isim, yuzey, birseyKondu);
    }

    private static void dunyayiDegistir(MinecraftServer server) {
        var players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) return;
        ServerPlayer player = players.get(RNG.nextInt(players.size()));
        if (player.isCreative() || player.isSpectator()) return;

        ServerLevel world = (ServerLevel) player.level();
        // Sadece overworld — nether/end'de yüzey Y güvenilir değil
        if (world.dimension() != Level.OVERWORLD) return;
        BlockPos base = player.blockPosition();

        int mesafe = 40 + RNG.nextInt(50);
        double aci = RNG.nextDouble() * Math.PI * 2;
        int mx = base.getX() + (int) (Math.cos(aci) * mesafe);
        int mz = base.getZ() + (int) (Math.sin(aci) * mesafe);

        if (!world.hasChunk(mx >> 4, mz >> 4)) return;

        int my = world.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, mx, mz);
        BlockPos yuzey = new BlockPos(mx, my, mz);
        BlockPos altYuzey = yuzey.below();

        int aksiyon = RNG.nextInt(9);
        String isim = RNG.nextBoolean() ? "Steve" : "Alex";
        // Aktif değilse yine de aksiyon yap (dünya değişiklikleri süreklidir)

        switch (aksiyon) {
            case 0 -> agacKes(world, yuzey, isim);
            case 1 -> insaatYap(world, yuzey, isim);
            case 2 -> tasKaz(world, altYuzey, isim);
            case 3 -> mesaleKoy(world, yuzey, altYuzey, isim);
            case 4 -> netherPortalDeneme(world, yuzey, isim);
            case 5 -> spawnerAra(world, yuzey, isim);
            case 6 -> sandikYagmala(world, player, isim);
            case 7 -> koyYagmala(world, player, isim);
            case 8 -> mobSavas(world, player, isim);
        }
    }

    /**
     * Nether'da güvenli Y bul: 10-115 arasında hava, altı katı.
     * null dönerse teleport iptal edilir.
     */
    private static BlockPos netherGuvenliY(ServerLevel dunya, int x, int z) {
        if (!dunya.hasChunk(x >> 4, z >> 4)) return null;
        for (int tekrar = 0; tekrar < 30; tekrar++) {
            int y = 10 + RNG.nextInt(105);
            BlockPos p = new BlockPos(x, y, z);
            var st = dunya.getBlockState(p);
            var alt = dunya.getBlockState(p.below());
            var ust = dunya.getBlockState(p.above());
            if (st.isAir() && ust.isAir() && !alt.isAir()
                && alt.isRedstoneConductor(dunya, p.below())
                && alt.getFluidState().isEmpty()) {
                return p;
            }
        }
        return null;
    }

    private static void agacKes(ServerLevel world, BlockPos yuzey, String isim) {
        for (int r = 0; r < 8; r++) {
            BlockPos p = yuzey.above(r);
            if (world.getBlockState(p).is(BlockTags.LOGS)) {
                world.destroyBlock(p, true);
                Candy.LOG.info("[Candy] Hayalet[{}] {} log kırdı", isim, p);
                return;
            }
        }
        if (world.getBlockState(yuzey).isAir()) {
            world.setBlockAndUpdate(yuzey, Blocks.OAK_LOG.defaultBlockState());
            Candy.LOG.info("[Candy] Hayalet[{}] {} oak log koydu", isim, yuzey);
        }
    }

    private static void insaatYap(ServerLevel world, BlockPos yuzey, String isim) {
        if (world.getBlockState(yuzey).isAir() && world.getBlockState(yuzey.above()).isAir()) {
            world.setBlockAndUpdate(yuzey, Blocks.OAK_PLANKS.defaultBlockState());
            world.setBlockAndUpdate(yuzey.above(), Blocks.OAK_PLANKS.defaultBlockState());
            Candy.LOG.info("[Candy] Hayalet[{}] {} inşaat", isim, yuzey);
        }
    }

    private static void tasKaz(ServerLevel world, BlockPos altYuzey, String isim) {
        for (int d = 0; d < 5; d++) {
            BlockPos p = altYuzey.below(d);
            Block b = world.getBlockState(p).getBlock();
            if (b == Blocks.STONE || b == Blocks.COAL_ORE || b == Blocks.IRON_ORE
                || b == Blocks.DEEPSLATE || b == Blocks.DEEPSLATE_COAL_ORE
                || b == Blocks.DEEPSLATE_IRON_ORE) {
                world.destroyBlock(p, true);
                Candy.LOG.info("[Candy] Hayalet[{}] {} kazma", isim, p);
                return;
            }
        }
    }

    private static void mesaleKoy(ServerLevel world, BlockPos yuzey, BlockPos altYuzey, String isim) {
        if (world.getBlockState(yuzey).isAir()
            && !world.getBlockState(altYuzey).isAir()
            && world.getBlockState(altYuzey).isRedstoneConductor(world, altYuzey)) {
            world.setBlockAndUpdate(yuzey, Blocks.TORCH.defaultBlockState());
            Candy.LOG.info("[Candy] Hayalet[{}] {} meşale koydu", isim, yuzey);
        }
    }

    private static void netherPortalDeneme(ServerLevel world, BlockPos yuzey, String isim) {
        for (int dx = 0; dx < 4; dx++) {
            BlockPos p = yuzey.offset(dx, 0, 0);
            if (!world.getBlockState(p).isAir() && world.getBlockState(p).getBlock() != Blocks.GRASS_BLOCK
                && world.getBlockState(p).getBlock() != Blocks.DIRT) return;
            world.setBlockAndUpdate(p, Blocks.OBSIDIAN.defaultBlockState());
        }
        for (int dy = 1; dy <= 4; dy++) {
            world.setBlockAndUpdate(yuzey.offset(0, dy, 0), Blocks.OBSIDIAN.defaultBlockState());
            world.setBlockAndUpdate(yuzey.offset(3, dy, 0), Blocks.OBSIDIAN.defaultBlockState());
        }
        for (int dx = 0; dx < 4; dx++) {
            world.setBlockAndUpdate(yuzey.offset(dx, 4, 0), Blocks.OBSIDIAN.defaultBlockState());
        }
        for (int dy = 1; dy <= 3; dy++) {
            for (int dx = 1; dx <= 2; dx++) {
                BlockPos ic = yuzey.offset(dx, dy, 0);
                if (world.getBlockState(ic).isAir()) {
                    world.setBlockAndUpdate(ic, Blocks.FIRE.defaultBlockState());
                }
            }
        }
        Candy.LOG.info("[Candy] Hayalet[{}] {} nether portal", isim, yuzey);
    }

    private static void spawnerAra(ServerLevel world, BlockPos merkez, String isim) {
        for (int i = 0; i < 30; i++) {
            int dx = RNG.nextInt(33) - 16;
            int dy = RNG.nextInt(33) - 16;
            int dz = RNG.nextInt(33) - 16;
            BlockPos p = merkez.offset(dx, dy, dz);
            if (!world.hasChunk(p.getX() >> 4, p.getZ() >> 4)) continue;
            if (world.getBlockState(p).getBlock() == Blocks.SPAWNER) {
                world.destroyBlock(p, true);
                Candy.LOG.info("[Candy] Hayalet[{}] {} spawner kırdı", isim, p);
                return;
            }
        }
    }

    private static void sandikYagmala(ServerLevel world, ServerPlayer player, String isim) {
        BlockPos merkez = player.blockPosition();
        for (int i = 0; i < 40; i++) {
            int dx = RNG.nextInt(65) - 32;
            int dy = RNG.nextInt(17) - 8;
            int dz = RNG.nextInt(65) - 32;
            BlockPos p = merkez.offset(dx, dy, dz);
            if (!world.hasChunk(p.getX() >> 4, p.getZ() >> 4)) continue;
            BlockEntity be = world.getBlockEntity(p);
            if (be instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest) {
                int slotSayisi = chest.getContainerSize();
                int calinacak = 1 + RNG.nextInt(2);
                int calinan = 0;
                for (int deneme = 0; deneme < 10 && calinan < calinacak; deneme++) {
                    int slot = RNG.nextInt(slotSayisi);
                    if (!chest.getItem(slot).isEmpty()) {
                        chest.setItem(slot, net.minecraft.world.item.ItemStack.EMPTY);
                        calinan++;
                    }
                }
                if (calinan > 0) {
                    chest.setChanged();
                    Candy.LOG.info("[Candy] Hayalet[{}] {} sandık yağma ({} item)",
                        isim, p, calinan);
                }
                return;
            }
        }
    }

    private static void koyYagmala(ServerLevel world, ServerPlayer player, String isim) {
        var koyluler = world.getEntitiesOfClass(
            net.minecraft.world.entity.npc.Villager.class,
            new net.minecraft.world.phys.AABB(player.blockPosition()).inflate(80),
            v -> v.isAlive());
        if (koyluler.isEmpty()) return;
        var kurban = koyluler.get(RNG.nextInt(koyluler.size()));

        // v3.0 Phase 3.8: %50 gerçek kill (was: sadece 4 damage)
        if (RNG.nextDouble() < 0.5) {
            String koyluAdi = kurban.hasCustomName()
                ? kurban.getCustomName().getString()
                : "Villager";
            // Instant kill — damage yeterli, ölüm event'i düzgün tetiklensin
            kurban.hurtServer(world, world.damageSources().generic(), 1000.0f);
            // Fake zombie death message
            Component fakeMsg = Component.translatable("death.attack.mob", koyluAdi,
                Component.translatable("entity.minecraft.zombie"));
            world.getServer().getPlayerList().broadcastSystemMessage(fakeMsg, false);

            // Tüm player'lara villager kill sayacı artır (kim'in yakınındaysa)
            for (ServerPlayer p : world.getServer().getPlayerList().getPlayers()) {
                if (p.level() != world) continue;
                if (p.distanceToSqr(kurban) > 128 * 128) continue;
                TruePersonality tp = PersonalityManager.get(p);
                tp.oldurulenKoyluSayisi++;
                if (tp.oldurulenKoyluSayisi >= 5) {
                    com.photonpulse.candy.basarim.CandyBasarimlar.tetikle(p, "koy_katliami");
                }
            }
            Candy.LOG.info("[Candy] Hayalet[{}] köylü ÖLDÜRDÜ @ {} (fake zombie)",
                isim, kurban.blockPosition());
        } else {
            kurban.hurtServer(world, world.damageSources().generic(), 4.0f);
            Candy.LOG.info("[Candy] Hayalet[{}] köylü hasar @ {}", isim, kurban.blockPosition());
        }
    }

    /**
     * v1.7.0: Steve/Alex köylü ev hijack.
     * 200 blok radius'ta bell (köy merkezi) ara. Bulursa 2 en yakın kapıyı bul, aralarına
     * plank duvar çek, ortaya kapı bırak, dekorasyon (chest + crafting + torch + sign + bed).
     * Chest'e loot ve %40 ihtimalle yırtık sayfa.
     */
    private static void eveDonme(MinecraftServer server) {
        var players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) return;
        ServerPlayer player = players.get(RNG.nextInt(players.size()));
        ServerLevel world = (ServerLevel) player.level();
        if (world.dimension() != Level.OVERWORLD) return;

        String isim = RNG.nextBoolean() ? "Steve" : "Alex";
        if (!hayaletAktifMi(isim, world.getGameTime())) return;

        // 1) Yakında bell ara (köy merkezi) — 30 random sample
        BlockPos bell = null;
        BlockPos base = player.blockPosition();
        for (int i = 0; i < 30; i++) {
            int mx = base.getX() + RNG.nextInt(400) - 200;
            int mz = base.getZ() + RNG.nextInt(400) - 200;
            if (!world.hasChunk(mx >> 4, mz >> 4)) continue;
            int my = world.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, mx, mz);
            for (int dy = -12; dy <= 4; dy++) {
                BlockPos p = new BlockPos(mx, my + dy, mz);
                if (world.getBlockState(p).getBlock() == Blocks.BELL) {
                    bell = p;
                    break;
                }
            }
            if (bell != null) break;
        }
        if (bell == null) return;

        // Aynı köy tekrar hijack olmasın
        if (hijackedBellPozisyonlari.contains(bell.asLong())) return;

        // 2) Kapı ara — 40 blok radius'ta 400 sample
        java.util.List<BlockPos> kapilar = new java.util.ArrayList<>();
        for (int i = 0; i < 400; i++) {
            int dx = RNG.nextInt(81) - 40;
            int dy = RNG.nextInt(21) - 10;
            int dz = RNG.nextInt(81) - 40;
            BlockPos p = bell.offset(dx, dy, dz);
            var st = world.getBlockState(p);
            var b = st.getBlock();
            if (b == Blocks.OAK_DOOR || b == Blocks.SPRUCE_DOOR
                || b == Blocks.BIRCH_DOOR || b == Blocks.ACACIA_DOOR
                || b == Blocks.DARK_OAK_DOOR || b == Blocks.JUNGLE_DOOR
                || b == Blocks.MANGROVE_DOOR || b == Blocks.CHERRY_DOOR) {
                // Sadece alt yarısı (double-tall block) — deduplicate
                if (!kapilar.contains(p) && !kapilar.contains(p.above()) && !kapilar.contains(p.below())) {
                    kapilar.add(p);
                }
            }
        }
        if (kapilar.size() < 2) return;

        // 3) 2 en yakın kapıyı bul (birbirine yakın)
        BlockPos k1 = null, k2 = null;
        double enYakin = Double.MAX_VALUE;
        for (int i = 0; i < kapilar.size(); i++) {
            for (int j = i + 1; j < kapilar.size(); j++) {
                double m = kapilar.get(i).distSqr(kapilar.get(j));
                if (m > 9 && m < enYakin && m < 400) { // min 3 blok, max 20 blok
                    enYakin = m;
                    k1 = kapilar.get(i);
                    k2 = kapilar.get(j);
                }
            }
        }
        if (k1 == null) return;

        // 4) İki kapı arasına plank duvar çek (Bresenham)
        java.util.List<BlockPos> hat = hatCiz(k1, k2);
        if (hat.size() < 3 || hat.size() > 25) return;

        int konulanBlok = 0;
        int ortaIdx = hat.size() / 2;
        for (int i = 0; i < hat.size(); i++) {
            BlockPos p = hat.get(i);
            // Zeminde durup 2 blok üste plank koy (duvar)
            for (int h = 0; h < 2; h++) {
                BlockPos wp = p.above(h);
                if (world.getBlockState(wp).isAir()
                    || world.getBlockState(wp).canBeReplaced()) {
                    // Ortaya kapı bırak, kalanı plank
                    if (i == ortaIdx && h == 0) {
                        // Kapı için yer bırak, alt+üst hava
                        world.setBlockAndUpdate(wp, Blocks.AIR.defaultBlockState());
                        continue;
                    }
                    if (i == ortaIdx && h == 1) {
                        world.setBlockAndUpdate(wp, Blocks.AIR.defaultBlockState());
                        continue;
                    }
                    world.setBlockAndUpdate(wp, Blocks.OAK_PLANKS.defaultBlockState());
                    konulanBlok++;
                }
            }
        }

        if (konulanBlok < 3) return; // Duvarın çoğunda yer yoktu, abort

        // 5) Ortada boşluk bırakıldı (kapı iki bloklu — proper placement karmaşık,
        // düz açık geçit daha temiz görünür)

        // 6) Dekorasyon — duvar yanında rastgele boş yerde
        BlockPos dekorMerkez = hat.get(ortaIdx).offset(RNG.nextInt(3) - 1, 0, RNG.nextInt(3) - 1);

        // Crafting table
        BlockPos ct = dekorMerkez.offset(2, 0, 0);
        if (world.getBlockState(ct).isAir())
            world.setBlockAndUpdate(ct, Blocks.CRAFTING_TABLE.defaultBlockState());

        // Chest + loot
        BlockPos chestPos = dekorMerkez.offset(-2, 0, 0);
        if (world.getBlockState(chestPos).isAir()) {
            world.setBlockAndUpdate(chestPos, Blocks.CHEST.defaultBlockState());
            var be = world.getBlockEntity(chestPos);
            if (be instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest) {
                chest.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD, 3));
                chest.setItem(1, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 2));
                chest.setItem(2, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK, 5));
                if (RNG.nextDouble() < 0.4) {
                    chest.setItem(3,
                        com.photonpulse.candy.lore.YirtikSayfa.olustur(RNG.nextInt(5)));
                }
                chest.setChanged();
            }
        }

        // Torch (duvarın 2 tarafına)
        BlockPos t1 = dekorMerkez.above(2);
        if (world.getBlockState(t1).isAir())
            world.setBlockAndUpdate(t1, Blocks.TORCH.defaultBlockState());

        // Sign
        BlockPos signPos = dekorMerkez.offset(0, 1, 1);
        if (world.getBlockState(signPos).isAir()) {
            world.setBlockAndUpdate(signPos, Blocks.OAK_SIGN.defaultBlockState());
            var be = world.getBlockEntity(signPos);
            if (be instanceof SignBlockEntity sign) {
                SignText metin = new SignText();
                metin = metin.setMessage(0, Component.literal("Steve & Alex"));
                metin = metin.setMessage(1, Component.translatable("candy.hayalet.ev.sign"));
                sign.setText(metin, true);
                sign.setChanged();
            }
        }

        // Ekstra dekorasyon — furnace veya barrel (%50)
        if (RNG.nextDouble() < 0.5) {
            BlockPos ekPos = dekorMerkez.offset(0, 0, 2);
            if (world.getBlockState(ekPos).isAir()) {
                world.setBlockAndUpdate(ekPos,
                    RNG.nextBoolean() ? Blocks.FURNACE.defaultBlockState()
                                      : Blocks.BARREL.defaultBlockState());
            }
        }

        hijackedBellPozisyonlari.add(bell.asLong());

        // 7) Chat mesajı — 4 varyant, lang key
        fakeChat(server, isim, "candy.hayalet.ev." + RNG.nextInt(4));

        Candy.LOG.info("[Candy] Hayalet[{}] köy hijack @ bell={} kapılar={},{} blok={}",
            isim, bell, k1, k2, konulanBlok);
    }

    /** Bresenham 2D — iki pozisyon arasında blok listesi (Y = a.getY()). */
    private static java.util.List<BlockPos> hatCiz(BlockPos a, BlockPos b) {
        java.util.List<BlockPos> pts = new java.util.ArrayList<>();
        int x0 = a.getX(), z0 = a.getZ();
        int x1 = b.getX(), z1 = b.getZ();
        int dx = Math.abs(x1 - x0);
        int dz = Math.abs(z1 - z0);
        int sx = x0 < x1 ? 1 : -1;
        int sz = z0 < z1 ? 1 : -1;
        int err = dx - dz;
        int y = a.getY();
        int guvenlik = 0;
        while (guvenlik++ < 40) {
            pts.add(new BlockPos(x0, y, z0));
            if (x0 == x1 && z0 == z1) break;
            int e2 = 2 * err;
            if (e2 > -dz) { err -= dz; x0 += sx; }
            if (e2 < dx) { err += dx; z0 += sz; }
        }
        return pts;
    }

    private static void mobSavas(ServerLevel world, ServerPlayer player, String isim) {
        var mobs = world.getEntitiesOfClass(
            net.minecraft.world.entity.monster.Monster.class,
            new net.minecraft.world.phys.AABB(player.blockPosition()).inflate(60),
            m -> m.isAlive());
        if (mobs.isEmpty()) return;
        var hedef = mobs.get(RNG.nextInt(mobs.size()));
        hedef.hurtServer(world, world.damageSources().generic(), 6.0f);
        Candy.LOG.info("[Candy] Hayalet[{}] mob @ {}", isim, hedef.blockPosition());
    }

    private static void tabelalariKontrolEt(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            TruePersonality tp = PersonalityManager.get(player);
            if (!tp.kurulumTamam) continue;

            ServerLevel world = (ServerLevel) player.level();
            BlockPos merkez = player.blockPosition();
            Set<Long> cevaplanan = cevaplananTabelalar.computeIfAbsent(
                player.getUUID(), k -> new HashSet<>());

            for (int dx = -16; dx <= 16; dx += 4) {
                for (int dz = -16; dz <= 16; dz += 4) {
                    for (int dy = -8; dy <= 8; dy += 4) {
                        BlockPos p = merkez.offset(dx, dy, dz);
                        BlockEntity be = world.getBlockEntity(p);
                        if (!(be instanceof SignBlockEntity sign)) continue;
                        if (cevaplanan.contains(p.asLong())) continue;

                        String metin = (metinYuz(sign.getFrontText())
                            + " " + metinYuz(sign.getBackText())).toLowerCase();

                        if (metin.contains("steve") || metin.contains("alex")) {
                            // v1.7.3: Candy chat'ten konuşmaz. Cevabı TABELA'nın arka yüzüne yaz.
                            // Boş bir yüz varsa oraya yaz, yoksa tabelayı değiştirme.
                            SignText onYuz = sign.getFrontText();
                            SignText arkaYuz = sign.getBackText();
                            boolean arkaBos = arkaYuz.getMessages(false)[0].getString().isEmpty()
                                && arkaYuz.getMessages(false)[1].getString().isEmpty()
                                && arkaYuz.getMessages(false)[2].getString().isEmpty()
                                && arkaYuz.getMessages(false)[3].getString().isEmpty();
                            boolean onBos = onYuz.getMessages(false)[0].getString().isEmpty()
                                && onYuz.getMessages(false)[1].getString().isEmpty()
                                && onYuz.getMessages(false)[2].getString().isEmpty()
                                && onYuz.getMessages(false)[3].getString().isEmpty();

                            SignText yeni = new SignText();
                            yeni = yeni.setMessage(0, Component.translatable("candy.hayalet.gaslight.0"));
                            yeni = yeni.setMessage(1, Component.translatable("candy.hayalet.gaslight.1"));
                            yeni = yeni.setMessage(2, Component.translatable("candy.hayalet.gaslight.2"));
                            yeni = yeni.setMessage(3, Component.translatable("candy.hayalet.gaslight.3"));

                            if (arkaBos) {
                                sign.setText(yeni, false); // arka yüz
                                sign.setChanged();
                            } else if (onBos) {
                                sign.setText(yeni, true); // ön yüz
                                sign.setChanged();
                            }
                            // İki yüz de doluysa — dokunma, sadece kaydet
                            cevaplanan.add(p.asLong());
                            Candy.LOG.info("[Candy] Sign gaslighting → tabela cevap yazıldı ({})",
                                player.getName().getString());
                            return;
                        }
                    }
                }
            }
        }
    }

    private static String metinYuz(SignText t) {
        StringBuilder sb = new StringBuilder();
        for (Component line : t.getMessages(false)) {
            sb.append(line.getString()).append(' ');
        }
        return sb.toString();
    }

    public static void oyuncuAyrildi(UUID uuid) {
        cevaplananTabelalar.remove(uuid);
        portalCooldown.remove(uuid);
    }

    public static void sunucuKapan() {
        joinYazildi = false;
        joinBekleyenTick = -1L;
        chatSonrakiTick = 0L;
        modifikasyonSonrakiTick = 0L;
        olumSonrakiTick = -1L;
        krizSonrakiTick = -1L;
        eveDonmeSonrakiTick = 0L;
        hayaletDondu.clear();
        hayaletSessiz.clear();
        portalCooldown.clear();
        cevapKuyrugu.clear();
        cevaplananTabelalar.clear();
        hijackedBellPozisyonlari.clear();
    }
}
