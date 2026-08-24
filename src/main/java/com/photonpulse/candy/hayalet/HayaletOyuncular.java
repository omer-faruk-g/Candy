package com.photonpulse.candy.hayalet;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.core.TruePersonality;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

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
    private static final int OLUM_SAYI = 7;

    private static String steveKey(int i) { return "candy.hayalet.steve." + i; }
    private static String alexKey(int i) { return "candy.hayalet.alex." + i; }
    private static String koordKey(int i) { return "candy.hayalet.koord." + i; }
    private static String krizKey(int i) { return "candy.hayalet.kriz." + i; }
    private static String cevapKey(int i) { return "candy.hayalet.cevap." + i; }

    public static void register() {
        // İlk oyuncu — fake join başlat
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (joinYazildi || joinBekleyenTick > 0) return;
            joinBekleyenTick = server.getOverworld().getTime() + 60L;
        });

        ServerTickEvents.END_SERVER_TICK.register(HayaletOyuncular::onTick);

        // v1.4.6: player chat mesajı → cevap kuyruğuna ekle (%25)
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            if (RNG.nextDouble() > 0.25) return;
            String isim = secilebilirHayalet(sender.getWorld().getTime());
            if (isim == null) return;
            String cevap = cevapKey(RNG.nextInt(CEVAP_SAYI));
            long trigger = sender.getWorld().getTime() + 100L + RNG.nextInt(200); // 5-15 sn
            cevapKuyrugu.add(new PendingCevap(trigger, isim, cevap));
        });

        // v1.4.6: portal geçiş — %20 karıştırma, 5 dk cooldown
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register(
            (player, origin, destination) -> {
                // Sadece nether ↔ overworld geçişleri
                boolean netherPortal =
                    (origin.getRegistryKey() == World.NETHER && destination.getRegistryKey() == World.OVERWORLD)
                    || (origin.getRegistryKey() == World.OVERWORLD && destination.getRegistryKey() == World.NETHER);
                if (!netherPortal) return;

                long simdi = destination.getTime();
                Long son = portalCooldown.get(player.getUuid());
                if (son != null && simdi - son < 6000L) return; // 5 dk
                if (RNG.nextDouble() > 0.20) return;

                portalCooldown.put(player.getUuid(), simdi);

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
                if (destination.getRegistryKey() == World.OVERWORLD) {
                    int y = destination.getTopY(
                        net.minecraft.world.Heightmap.Type.MOTION_BLOCKING, yeni.getX(), yeni.getZ());
                    yeni = new BlockPos(yeni.getX(), y + 1, yeni.getZ());
                } else {
                    // Nether — güvenli Y ara (5-115 arasında hava + altı katı)
                    BlockPos guvenli = netherGuvenliY(destination, yeni.getX(), yeni.getZ());
                    if (guvenli == null) {
                        portalCooldown.remove(player.getUuid()); // cooldown iade
                        return;
                    }
                    yeni = guvenli;
                }

                player.teleport(destination, yeni.getX() + 0.5, yeni.getY(),
                    yeni.getZ() + 0.5, java.util.Collections.emptySet(),
                    player.getYaw(), player.getPitch(), false);
                Candy.LOG.info("[Candy] PortalKarisikligi[{}] → {}",
                    player.getName().getString(), yeni);

                // Sonra Steve/Alex chat: "sen neredesin"
                String isim = RNG.nextBoolean() ? "Steve" : "Alex";
                if (hayaletAktifMi(isim, destination.getTime())) {
                    long trigger = destination.getTime() + 60L;
                    cevapKuyrugu.add(new PendingCevap(trigger, isim, "candy.hayalet.portal_soru"));
                }
            });

        Candy.LOG.info("[Candy] HayaletOyuncular aktif — Steve/Alex + ölüm/kriz/portal");
    }

    private static void onTick(MinecraftServer server) {
        if (server.getPlayerManager().getPlayerList().isEmpty()) return;
        long simdi = server.getOverworld().getTime();

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
        var donenler = new java.util.ArrayList<Map.Entry<String, Long>>(hayaletDondu.entrySet());
        for (var e : donenler) {
            if (simdi >= e.getValue()) {
                hayaletDondu.remove(e.getKey());
                fakeJoin(server, e.getKey()); // "Steve joined the game"
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
                } else if (rol < 0.30) {
                    // %20 koordinat mesajı
                    koordinatBirakVeSoyle(server, isim);
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
        var hazir = new java.util.ArrayList<PendingCevap>();
        for (PendingCevap c : cevapKuyrugu) {
            if (simdi >= c.triggerTick) hazir.add(c);
        }
        for (PendingCevap c : hazir) {
            cevapKuyrugu.remove(c);
            if (hayaletAktifMi(c.isim, simdi)) {
                fakeChat(server, c.isim, c.langKey);
            }
        }

        // 7) Sign check
        if (server.getTicks() % SIGN_CHECK_ARALIGI == 0) {
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
        Text msg = Text.translatable("multiplayer.player.joined", isim).formatted(Formatting.YELLOW);
        server.getPlayerManager().broadcast(msg, false);
        Candy.LOG.info("[Candy] Hayalet[{}] joined", isim);
    }

    private static void fakeLeave(MinecraftServer server, String isim) {
        Text msg = Text.translatable("multiplayer.player.left", isim).formatted(Formatting.YELLOW);
        server.getPlayerManager().broadcast(msg, false);
    }

    /** v1.9.12: mesaj artık lang key — player'ın dilinde çevrilir. */
    private static void fakeChat(MinecraftServer server, String isim, String langKey) {
        Text formatli = Text.literal("<" + isim + "> ")
            .append(Text.translatable(langKey));
        server.getPlayerManager().broadcast(formatli, false);
        Candy.LOG.info("[Candy] Hayalet[{}]: {}", isim, langKey);
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            var tp = PersonalityManager.get(p);
            if (tp.kurulumTamam && !tp.hayaletChatGorulduMu) {
                tp.hayaletChatGorulduMu = true;
                com.photonpulse.candy.basarim.CandyBasarimlar.tetikle(p, "sahte_dost");
            }
        }
    }

    /** Koordinat mesajı — lang key %s içerir, koord string olarak gelir. */
    private static void fakeChatArg(MinecraftServer server, String isim, String langKey, Object arg) {
        Text formatli = Text.literal("<" + isim + "> ")
            .append(Text.translatable(langKey, arg));
        server.getPlayerManager().broadcast(formatli, false);
        Candy.LOG.info("[Candy] Hayalet[{}]: {} ({})", isim, langKey, arg);
    }

    /** Vanilla death.attack.* lang key kullanır → oyuncu diline göre otomatik çevrilir. */
    private static void fakeVanillaDeath(MinecraftServer server, String langKey, Object... args) {
        Text msg = Text.translatable(langKey, args);
        server.getPlayerManager().broadcast(msg, false);
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
                Text.translatable("entity.minecraft.zombie"));
            case 1 -> fakeVanillaDeath(server, "death.attack.lava", isim);
            case 2 -> fakeVanillaDeath(server, "death.attack.drown", isim);
            case 3 -> fakeVanillaDeath(server, "death.attack.explosion", isim,
                Text.translatable("entity.minecraft.creeper"));
            case 4 -> fakeVanillaDeath(server, "death.attack.fall", isim);
            case 5 -> fakeVanillaDeath(server, "death.attack.arrow", isim,
                Text.translatable("entity.minecraft.skeleton"));
            case 6 -> fakeVanillaDeath(server, "death.attack.mob", isim,
                Text.translatable("entity.minecraft.enderman"));
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
        var players = server.getPlayerManager().getPlayerList();
        if (players.isEmpty()) return false;
        ServerPlayerEntity hedef = players.get(RNG.nextInt(players.size()));
        String parca = com.photonpulse.candy.meta.DonanimBilgisi.rastgeleParca(hedef);
        if (parca == null || parca.isBlank()) return false;
        fakeChatArg(server, isim, "candy.hayalet.donanim", parca);
        Candy.LOG.info("[Candy] Hayalet[{}] donanim: {}", isim, parca);
        return true;
    }

    /**
     * Şehir mesajı — Alex "Steve, ben X şehrinden oynuyorum, peki ya sen?" der.
     * X = rastgele bir online oyuncunun GERÇEK şehri (IP-tabanlı geolocation, client-side).
     * PRIVATE — sadece o oyuncuya gönderilir (broadcast değil, konum sızmasın).
     * @return gönderildiyse true (şehir yoksa false → normal mesaja düş).
     */
    private static boolean sehirMesaji(MinecraftServer server) {
        var players = server.getPlayerManager().getPlayerList();
        if (players.isEmpty()) return false;
        ServerPlayerEntity hedef = players.get(RNG.nextInt(players.size()));
        String sehir = com.photonpulse.candy.meta.SehirBilgisi.sehir(hedef);
        if (sehir == null || sehir.isBlank()) return false;
        hedef.sendMessage(
            net.minecraft.text.Text.literal("<Alex> ")
                .append(net.minecraft.text.Text.translatable("candy.hayalet.sehir", sehir))
                .formatted(net.minecraft.util.Formatting.YELLOW), false);
        Candy.LOG.info("[Candy] Hayalet[Alex] sehir (private): {}", hedef.getName().getString());
        return true;
    }

    /**
     * Koordinat mesajı + o koordinata gerçek küçük yapı bırak.
     */
    private static void koordinatBirakVeSoyle(MinecraftServer server, String isim) {
        var players = server.getPlayerManager().getPlayerList();
        if (players.isEmpty()) return;
        ServerPlayerEntity player = players.get(RNG.nextInt(players.size()));
        ServerWorld world = (ServerWorld) player.getWorld();
        if (world.getRegistryKey() != World.OVERWORLD) return;

        // 100-300 blok yatay ofset
        int mesafe = 100 + RNG.nextInt(200);
        double aci = RNG.nextDouble() * Math.PI * 2;
        int mx = player.getBlockX() + (int) (Math.cos(aci) * mesafe);
        int mz = player.getBlockZ() + (int) (Math.sin(aci) * mesafe);

        if (!world.isChunkLoaded(mx >> 4, mz >> 4)) {
            // Chunk yok, sadece söyle ama yapı yok
            String key = koordKey(RNG.nextInt(KOORDINAT_SAYI));
            fakeChatArg(server, isim, key, mx + " " + player.getBlockY() + " " + mz);
            return;
        }

        int my = world.getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE, mx, mz);
        BlockPos yuzey = new BlockPos(mx, my, mz);

        // 2x2 plank platform + torch + sign
        boolean birseyKondu = false;
        for (int dx = 0; dx < 2; dx++) {
            for (int dz = 0; dz < 2; dz++) {
                BlockPos p = yuzey.add(dx, 0, dz);
                if (world.getBlockState(p).isAir()) {
                    world.setBlockState(p, Blocks.OAK_PLANKS.getDefaultState());
                    birseyKondu = true;
                }
            }
        }
        BlockPos ustPos = yuzey.up();
        if (world.getBlockState(ustPos).isAir()) {
            world.setBlockState(ustPos, Blocks.TORCH.getDefaultState());
        }
        BlockPos signPos = yuzey.add(1, 1, 0);
        if (world.getBlockState(signPos).isAir()) {
            world.setBlockState(signPos, Blocks.OAK_SIGN.getDefaultState());
            BlockEntity be = world.getBlockEntity(signPos);
            if (be instanceof SignBlockEntity sign) {
                SignText metin = new SignText();
                metin = metin.withMessage(0, Text.literal(isim));
                metin = metin.withMessage(1, Text.literal("was here"));
                sign.setText(metin, true);
                sign.markDirty();
            }
        }

        String key = koordKey(RNG.nextInt(KOORDINAT_SAYI));
        String koord = mx + " " + my + " " + mz;
        fakeChatArg(server, isim, key, koord);
        Candy.LOG.info("[Candy] Hayalet[{}] koordinat bıraktı @ {} ({} kondu)",
            isim, yuzey, birseyKondu);
    }

    private static void dunyayiDegistir(MinecraftServer server) {
        var players = server.getPlayerManager().getPlayerList();
        if (players.isEmpty()) return;
        ServerPlayerEntity player = players.get(RNG.nextInt(players.size()));
        if (player.isCreative() || player.isSpectator()) return;

        ServerWorld world = (ServerWorld) player.getWorld();
        // Sadece overworld — nether/end'de yüzey Y güvenilir değil
        if (world.getRegistryKey() != World.OVERWORLD) return;
        BlockPos base = player.getBlockPos();

        int mesafe = 40 + RNG.nextInt(50);
        double aci = RNG.nextDouble() * Math.PI * 2;
        int mx = base.getX() + (int) (Math.cos(aci) * mesafe);
        int mz = base.getZ() + (int) (Math.sin(aci) * mesafe);

        if (!world.isChunkLoaded(mx >> 4, mz >> 4)) return;

        int my = world.getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE, mx, mz);
        BlockPos yuzey = new BlockPos(mx, my, mz);
        BlockPos altYuzey = yuzey.down();

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
    private static BlockPos netherGuvenliY(ServerWorld dunya, int x, int z) {
        if (!dunya.isChunkLoaded(x >> 4, z >> 4)) return null;
        for (int tekrar = 0; tekrar < 30; tekrar++) {
            int y = 10 + RNG.nextInt(105);
            BlockPos p = new BlockPos(x, y, z);
            var st = dunya.getBlockState(p);
            var alt = dunya.getBlockState(p.down());
            var ust = dunya.getBlockState(p.up());
            if (st.isAir() && ust.isAir() && !alt.isAir()
                && alt.isSolidBlock(dunya, p.down())
                && alt.getFluidState().isEmpty()) {
                return p;
            }
        }
        return null;
    }

    private static void agacKes(ServerWorld world, BlockPos yuzey, String isim) {
        for (int r = 0; r < 8; r++) {
            BlockPos p = yuzey.up(r);
            if (world.getBlockState(p).isIn(BlockTags.LOGS)) {
                world.breakBlock(p, true);
                Candy.LOG.info("[Candy] Hayalet[{}] {} log kırdı", isim, p);
                return;
            }
        }
        if (world.getBlockState(yuzey).isAir()) {
            world.setBlockState(yuzey, Blocks.OAK_LOG.getDefaultState());
            Candy.LOG.info("[Candy] Hayalet[{}] {} oak log koydu", isim, yuzey);
        }
    }

    private static void insaatYap(ServerWorld world, BlockPos yuzey, String isim) {
        if (world.getBlockState(yuzey).isAir() && world.getBlockState(yuzey.up()).isAir()) {
            world.setBlockState(yuzey, Blocks.OAK_PLANKS.getDefaultState());
            world.setBlockState(yuzey.up(), Blocks.OAK_PLANKS.getDefaultState());
            Candy.LOG.info("[Candy] Hayalet[{}] {} inşaat", isim, yuzey);
        }
    }

    private static void tasKaz(ServerWorld world, BlockPos altYuzey, String isim) {
        for (int d = 0; d < 5; d++) {
            BlockPos p = altYuzey.down(d);
            Block b = world.getBlockState(p).getBlock();
            if (b == Blocks.STONE || b == Blocks.COAL_ORE || b == Blocks.IRON_ORE
                || b == Blocks.DEEPSLATE || b == Blocks.DEEPSLATE_COAL_ORE
                || b == Blocks.DEEPSLATE_IRON_ORE) {
                world.breakBlock(p, true);
                Candy.LOG.info("[Candy] Hayalet[{}] {} kazma", isim, p);
                return;
            }
        }
    }

    private static void mesaleKoy(ServerWorld world, BlockPos yuzey, BlockPos altYuzey, String isim) {
        if (world.getBlockState(yuzey).isAir()
            && !world.getBlockState(altYuzey).isAir()
            && world.getBlockState(altYuzey).isSolidBlock(world, altYuzey)) {
            world.setBlockState(yuzey, Blocks.TORCH.getDefaultState());
            Candy.LOG.info("[Candy] Hayalet[{}] {} meşale koydu", isim, yuzey);
        }
    }

    private static void netherPortalDeneme(ServerWorld world, BlockPos yuzey, String isim) {
        for (int dx = 0; dx < 4; dx++) {
            BlockPos p = yuzey.add(dx, 0, 0);
            if (!world.getBlockState(p).isAir() && world.getBlockState(p).getBlock() != Blocks.GRASS_BLOCK
                && world.getBlockState(p).getBlock() != Blocks.DIRT) return;
            world.setBlockState(p, Blocks.OBSIDIAN.getDefaultState());
        }
        for (int dy = 1; dy <= 4; dy++) {
            world.setBlockState(yuzey.add(0, dy, 0), Blocks.OBSIDIAN.getDefaultState());
            world.setBlockState(yuzey.add(3, dy, 0), Blocks.OBSIDIAN.getDefaultState());
        }
        for (int dx = 0; dx < 4; dx++) {
            world.setBlockState(yuzey.add(dx, 4, 0), Blocks.OBSIDIAN.getDefaultState());
        }
        for (int dy = 1; dy <= 3; dy++) {
            for (int dx = 1; dx <= 2; dx++) {
                BlockPos ic = yuzey.add(dx, dy, 0);
                if (world.getBlockState(ic).isAir()) {
                    world.setBlockState(ic, Blocks.FIRE.getDefaultState());
                }
            }
        }
        Candy.LOG.info("[Candy] Hayalet[{}] {} nether portal", isim, yuzey);
    }

    private static void spawnerAra(ServerWorld world, BlockPos merkez, String isim) {
        for (int i = 0; i < 30; i++) {
            int dx = RNG.nextInt(33) - 16;
            int dy = RNG.nextInt(33) - 16;
            int dz = RNG.nextInt(33) - 16;
            BlockPos p = merkez.add(dx, dy, dz);
            if (!world.isChunkLoaded(p.getX() >> 4, p.getZ() >> 4)) continue;
            if (world.getBlockState(p).getBlock() == Blocks.SPAWNER) {
                world.breakBlock(p, true);
                Candy.LOG.info("[Candy] Hayalet[{}] {} spawner kırdı", isim, p);
                return;
            }
        }
    }

    private static void sandikYagmala(ServerWorld world, ServerPlayerEntity player, String isim) {
        BlockPos merkez = player.getBlockPos();
        for (int i = 0; i < 40; i++) {
            int dx = RNG.nextInt(65) - 32;
            int dy = RNG.nextInt(17) - 8;
            int dz = RNG.nextInt(65) - 32;
            BlockPos p = merkez.add(dx, dy, dz);
            if (!world.isChunkLoaded(p.getX() >> 4, p.getZ() >> 4)) continue;
            BlockEntity be = world.getBlockEntity(p);
            if (be instanceof net.minecraft.block.entity.ChestBlockEntity chest) {
                int slotSayisi = chest.size();
                int calinacak = 1 + RNG.nextInt(2);
                int calinan = 0;
                for (int deneme = 0; deneme < 10 && calinan < calinacak; deneme++) {
                    int slot = RNG.nextInt(slotSayisi);
                    if (!chest.getStack(slot).isEmpty()) {
                        chest.setStack(slot, net.minecraft.item.ItemStack.EMPTY);
                        calinan++;
                    }
                }
                if (calinan > 0) {
                    chest.markDirty();
                    Candy.LOG.info("[Candy] Hayalet[{}] {} sandık yağma ({} item)",
                        isim, p, calinan);
                }
                return;
            }
        }
    }

    private static void koyYagmala(ServerWorld world, ServerPlayerEntity player, String isim) {
        var koyluler = world.getEntitiesByClass(
            net.minecraft.entity.passive.VillagerEntity.class,
            new net.minecraft.util.math.Box(player.getBlockPos()).expand(80),
            v -> v.isAlive());
        if (koyluler.isEmpty()) return;
        var kurban = koyluler.get(RNG.nextInt(koyluler.size()));

        // v3.0 Phase 3.8: %50 gerçek kill (was: sadece 4 damage)
        if (RNG.nextDouble() < 0.5) {
            String koyluAdi = kurban.hasCustomName()
                ? kurban.getCustomName().getString()
                : "Villager";
            // Instant kill — damage yeterli, ölüm event'i düzgün tetiklensin
            kurban.damage(world, world.getDamageSources().generic(), 1000.0f);
            // Fake zombie death message
            Text fakeMsg = Text.translatable("death.attack.mob", koyluAdi,
                Text.translatable("entity.minecraft.zombie"));
            world.getServer().getPlayerManager().broadcast(fakeMsg, false);

            // Tüm player'lara villager kill sayacı artır (kim'in yakınındaysa)
            for (ServerPlayerEntity p : world.getServer().getPlayerManager().getPlayerList()) {
                if (p.getWorld() != world) continue;
                if (p.squaredDistanceTo(kurban) > 128 * 128) continue;
                TruePersonality tp = PersonalityManager.get(p);
                tp.oldurulenKoyluSayisi++;
                if (tp.oldurulenKoyluSayisi >= 5) {
                    com.photonpulse.candy.basarim.CandyBasarimlar.tetikle(p, "koy_katliami");
                }
            }
            Candy.LOG.info("[Candy] Hayalet[{}] köylü ÖLDÜRDÜ @ {} (fake zombie)",
                isim, kurban.getBlockPos());
        } else {
            kurban.damage(world, world.getDamageSources().generic(), 4.0f);
            Candy.LOG.info("[Candy] Hayalet[{}] köylü hasar @ {}", isim, kurban.getBlockPos());
        }
    }

    /**
     * v1.7.0: Steve/Alex köylü ev hijack.
     * 200 blok radius'ta bell (köy merkezi) ara. Bulursa 2 en yakın kapıyı bul, aralarına
     * plank duvar çek, ortaya kapı bırak, dekorasyon (chest + crafting + torch + sign + bed).
     * Chest'e loot ve %40 ihtimalle yırtık sayfa.
     */
    private static void eveDonme(MinecraftServer server) {
        var players = server.getPlayerManager().getPlayerList();
        if (players.isEmpty()) return;
        ServerPlayerEntity player = players.get(RNG.nextInt(players.size()));
        ServerWorld world = (ServerWorld) player.getWorld();
        if (world.getRegistryKey() != World.OVERWORLD) return;

        String isim = RNG.nextBoolean() ? "Steve" : "Alex";
        if (!hayaletAktifMi(isim, world.getTime())) return;

        // 1) Yakında bell ara (köy merkezi) — 30 random sample
        BlockPos bell = null;
        BlockPos base = player.getBlockPos();
        for (int i = 0; i < 30; i++) {
            int mx = base.getX() + RNG.nextInt(400) - 200;
            int mz = base.getZ() + RNG.nextInt(400) - 200;
            if (!world.isChunkLoaded(mx >> 4, mz >> 4)) continue;
            int my = world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING, mx, mz);
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
            BlockPos p = bell.add(dx, dy, dz);
            var st = world.getBlockState(p);
            var b = st.getBlock();
            if (b == Blocks.OAK_DOOR || b == Blocks.SPRUCE_DOOR
                || b == Blocks.BIRCH_DOOR || b == Blocks.ACACIA_DOOR
                || b == Blocks.DARK_OAK_DOOR || b == Blocks.JUNGLE_DOOR
                || b == Blocks.MANGROVE_DOOR || b == Blocks.CHERRY_DOOR) {
                // Sadece alt yarısı (double-tall block) — deduplicate
                if (!kapilar.contains(p) && !kapilar.contains(p.up()) && !kapilar.contains(p.down())) {
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
                double m = kapilar.get(i).getSquaredDistance(kapilar.get(j));
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
                BlockPos wp = p.up(h);
                if (world.getBlockState(wp).isAir()
                    || world.getBlockState(wp).isReplaceable()) {
                    // Ortaya kapı bırak, kalanı plank
                    if (i == ortaIdx && h == 0) {
                        // Kapı için yer bırak, alt+üst hava
                        world.setBlockState(wp, Blocks.AIR.getDefaultState());
                        continue;
                    }
                    if (i == ortaIdx && h == 1) {
                        world.setBlockState(wp, Blocks.AIR.getDefaultState());
                        continue;
                    }
                    world.setBlockState(wp, Blocks.OAK_PLANKS.getDefaultState());
                    konulanBlok++;
                }
            }
        }

        if (konulanBlok < 3) return; // Duvarın çoğunda yer yoktu, abort

        // 5) Ortada boşluk bırakıldı (kapı iki bloklu — proper placement karmaşık,
        // düz açık geçit daha temiz görünür)

        // 6) Dekorasyon — duvar yanında rastgele boş yerde
        BlockPos dekorMerkez = hat.get(ortaIdx).add(RNG.nextInt(3) - 1, 0, RNG.nextInt(3) - 1);

        // Crafting table
        BlockPos ct = dekorMerkez.add(2, 0, 0);
        if (world.getBlockState(ct).isAir())
            world.setBlockState(ct, Blocks.CRAFTING_TABLE.getDefaultState());

        // Chest + loot
        BlockPos chestPos = dekorMerkez.add(-2, 0, 0);
        if (world.getBlockState(chestPos).isAir()) {
            world.setBlockState(chestPos, Blocks.CHEST.getDefaultState());
            var be = world.getBlockEntity(chestPos);
            if (be instanceof net.minecraft.block.entity.ChestBlockEntity chest) {
                chest.setStack(0, new net.minecraft.item.ItemStack(net.minecraft.item.Items.BREAD, 3));
                chest.setStack(1, new net.minecraft.item.ItemStack(net.minecraft.item.Items.IRON_INGOT, 2));
                chest.setStack(2, new net.minecraft.item.ItemStack(net.minecraft.item.Items.STICK, 5));
                if (RNG.nextDouble() < 0.4) {
                    chest.setStack(3,
                        com.photonpulse.candy.lore.YirtikSayfa.olustur(RNG.nextInt(5)));
                }
                chest.markDirty();
            }
        }

        // Torch (duvarın 2 tarafına)
        BlockPos t1 = dekorMerkez.up(2);
        if (world.getBlockState(t1).isAir())
            world.setBlockState(t1, Blocks.TORCH.getDefaultState());

        // Sign
        BlockPos signPos = dekorMerkez.add(0, 1, 1);
        if (world.getBlockState(signPos).isAir()) {
            world.setBlockState(signPos, Blocks.OAK_SIGN.getDefaultState());
            var be = world.getBlockEntity(signPos);
            if (be instanceof SignBlockEntity sign) {
                SignText metin = new SignText();
                metin = metin.withMessage(0, Text.literal("Steve & Alex"));
                metin = metin.withMessage(1, Text.translatable("candy.hayalet.ev.sign"));
                sign.setText(metin, true);
                sign.markDirty();
            }
        }

        // Ekstra dekorasyon — furnace veya barrel (%50)
        if (RNG.nextDouble() < 0.5) {
            BlockPos ekPos = dekorMerkez.add(0, 0, 2);
            if (world.getBlockState(ekPos).isAir()) {
                world.setBlockState(ekPos,
                    RNG.nextBoolean() ? Blocks.FURNACE.getDefaultState()
                                      : Blocks.BARREL.getDefaultState());
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

    private static void mobSavas(ServerWorld world, ServerPlayerEntity player, String isim) {
        var mobs = world.getEntitiesByClass(
            net.minecraft.entity.mob.HostileEntity.class,
            new net.minecraft.util.math.Box(player.getBlockPos()).expand(60),
            m -> m.isAlive());
        if (mobs.isEmpty()) return;
        var hedef = mobs.get(RNG.nextInt(mobs.size()));
        hedef.damage(world, world.getDamageSources().generic(), 6.0f);
        Candy.LOG.info("[Candy] Hayalet[{}] mob @ {}", isim, hedef.getBlockPos());
    }

    private static void tabelalariKontrolEt(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            TruePersonality tp = PersonalityManager.get(player);
            if (!tp.kurulumTamam) continue;

            ServerWorld world = (ServerWorld) player.getWorld();
            BlockPos merkez = player.getBlockPos();
            Set<Long> cevaplanan = cevaplananTabelalar.computeIfAbsent(
                player.getUuid(), k -> new HashSet<>());

            for (int dx = -16; dx <= 16; dx += 4) {
                for (int dz = -16; dz <= 16; dz += 4) {
                    for (int dy = -8; dy <= 8; dy += 4) {
                        BlockPos p = merkez.add(dx, dy, dz);
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
                            yeni = yeni.withMessage(0, Text.translatable("candy.hayalet.gaslight.0"));
                            yeni = yeni.withMessage(1, Text.translatable("candy.hayalet.gaslight.1"));
                            yeni = yeni.withMessage(2, Text.translatable("candy.hayalet.gaslight.2"));
                            yeni = yeni.withMessage(3, Text.translatable("candy.hayalet.gaslight.3"));

                            if (arkaBos) {
                                sign.setText(yeni, false); // arka yüz
                                sign.markDirty();
                            } else if (onBos) {
                                sign.setText(yeni, true); // ön yüz
                                sign.markDirty();
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
        for (Text line : t.getMessages(false)) {
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
