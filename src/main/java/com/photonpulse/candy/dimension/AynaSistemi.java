package com.photonpulse.candy.dimension;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.basarim.CandyBasarimlar;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.core.TruePersonality;
import com.photonpulse.candy.entity.CandyDoppelEntity;
import com.photonpulse.candy.entity.CandyEntities;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.SpawnReason;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.Heightmap;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * AynaSistemi (v3.2 — idea 13) — Ayna boyutu: kopya + düzenek + takas.
 *
 * Mantık (senin tasarımın):
 *   - Oyuncu overworld'de oynarken hareketi sürekli KAYIT altına alınır (ring buffer).
 *   - Koşullar oluşunca (yüksek korku + özgüven, yüzeyde, cooldown, nadir) Candy TAKAS yapar:
 *       1. Oyuncunun çevresindeki bölgeyi Ayna'ya birebir KOPYALAR (düzenek zemini — kusursuz illüzyon).
 *       2. Oyuncuyu Ayna'ya, AYNI koordinata ışınlar (F3 "overworld" der, gök/sis normal).
 *       3. Ayna zamanını overworld'e senkronlar (gökyüzü tutsun).
 *       4. Bir KOPYA NPC (TAG_AYNA) doğurur — oyuncunun kaydını gecikmeli oynatır ("sen, biraz önce").
 *   - NPC asla farklı bir şey yapmaz; sadece seni aynalar. Candy "elde edemediğini" düzenekle + takasla alır.
 *   - Süre dolunca (veya ölünce) İFŞA: "Bu sefer sen kopyasın." → geri takas.
 *
 * Tüm kayıt/oynatma canlı akıl (persist edilmez); sadece aynada/aynaDonusPoz TruePersonality'de.
 */
public class AynaSistemi {

    private static final boolean DEBUG = Boolean.getBoolean("candy.debug");

    private static final int ARALIK = 20;                       // merak kontrolü 1 sn
    private static final int KAYIT_CAP = 200;                   // 10 sn hareket tamponu
    private static final int ECHO_GECIKME = 40;                 // kopya 2 sn geriden gelir
    private static final long COOLDOWN = DEBUG ? 1200L : 6000L; // 5 dk (debug 1 dk)
    private static final long HAZIRLIK_SURE = 80L;              // Candy düzeneği ~4 sn hazırlar
    private static final long AYNA_SURE = DEBUG ? 400L : 800L;  // Ayna'da kalış ~40 sn
    private static final double MERAK_ESIGI = 0.55;            // hipotez belirsizliği (Candy merak eder)
    private static final double MERAK_IHTIMAL = DEBUG ? 1.0 : 0.25;
    private static final int MIN_VERI = 5;                      // merak edecek kadar öngörü verisi
    private static final int KOPYA_YARICAP = 10;                // düzenek bölge kopya yarıçapı

    private static final Random RNG = new Random();
    private static int tick = 0;

    // Per-player hareket kaydı: her frame float[]{x,y,z,yaw,pitch}
    private static final Map<UUID, Kayit> kayit = new HashMap<>();

    /**
     * Hareket kaydı — sabit boyutlu ring buffer.
     *
     * <h3>NEDEN {@code Deque<float[]>} DEĞİL</h3>
     * Eski hâli oyuncu başına HER TICK bir {@code new float[5]} tahsis ediyordu (saniyede
     * 20, durumdan bağımsız) ve oynatma tarafı {@code Iterator} ile 160 adım yürüyordu.
     * Tampon zaten sabit kapasiteli ({@value #KAYIT_CAP}); sabit bir {@code float[]} ring
     * aynı veriyi sıfır tahsisle ve O(1) erişimle tutar.
     */
    private static final class Kayit {
        final float[] veri = new float[KAYIT_CAP * 5];
        int yazim = 0;      // sıradaki yazım slotu (kare cinsinden)
        int adet = 0;       // dolu kare sayısı (en fazla KAYIT_CAP)

        void ekle(float x, float y, float z, float yaw, float pitch) {
            int t = yazim * 5;
            veri[t] = x; veri[t + 1] = y; veri[t + 2] = z;
            veri[t + 3] = yaw; veri[t + 4] = pitch;
            yazim = (yazim + 1) % KAYIT_CAP;
            if (adet < KAYIT_CAP) adet++;
        }

        /** {@code geriAdim} kare önceki kareyi hedefe yazar; 0 = en yeni kare. */
        boolean kare(int geriAdim, float[] hedef) {
            if (geriAdim < 0 || geriAdim >= adet) return false;
            int slot = ((yazim - 1 - geriAdim) % KAYIT_CAP + KAYIT_CAP) % KAYIT_CAP;
            System.arraycopy(veri, slot * 5, hedef, 0, 5);
            return true;
        }
    }

    /** Oynatma karesinin yeniden kullanılan tamponu — tek thread (sunucu) kullanır. */
    private static final float[] kareScratch = new float[5];
    // Aktif Ayna kopyası NPC'si
    private static final Map<UUID, UUID> kopyaEntity = new HashMap<>();
    // Hazırlık fazı: düzenek kuruldu, bu tick'e gelince oyuncu Ayna'ya alınacak
    private static final Map<UUID, Long> hazirlikBitis = new HashMap<>();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(AynaSistemi::onTick);
        Candy.LOG.info("[Candy] AynaSistemi aktif — kopya + düzenek + takas (v3.2)");
    }

    private static void onTick(MinecraftServer server) {
        tick++;
        long simdi = server.getOverworld().getTime();

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            try {
                TruePersonality tp = PersonalityManager.get(player);
                if (!tp.kurulumTamam) continue;
                UUID id = player.getUuid();

                // 1) Her tick hareketi kaydet (ring buffer)
                Kayit buf = kayit.computeIfAbsent(id, k -> new Kayit());
                buf.ekle((float) player.getX(), (float) player.getY(), (float) player.getZ(),
                    player.getYaw(), player.getPitch());

                // 2) Hazırlık fazı: düzenek kuruldu → süre dolunca oyuncuyu Ayna'ya AL
                Long hb = hazirlikBitis.get(id);
                if (hb != null) {
                    if (simdi >= hb) {
                        hazirlikBitis.remove(id);
                        spawnla(server, player, tp);
                    }
                    continue;
                }

                // 3) Ayna'daysa: kopyayı sür + süre dolunca ifşa & geri takas
                if (tp.aynada) {
                    tp.aynaToplamSure++;
                    kopyayiSur(server, player, buf);
                    boolean sureDoldu = simdi - tp.aynaGirisTick > AYNA_SURE;
                    boolean yanlisBoyut = !player.getWorld().getRegistryKey().equals(CandyDimensions.AYNA);
                    if (sureDoldu || yanlisBoyut) {
                        geriTakas(server, player, tp);
                    }
                    continue;
                }

                // 4) Merak kontrolü (1 sn'de bir) — Candy "acaba bu nasıl olurdu?" diye düşünür
                if (tick % ARALIK != 0) continue;
                if (tp.bossOldurulmeTick > 0) continue;              // WIN sessizliği
                if (!player.getWorld().getRegistryKey().equals(server.getOverworld().getRegistryKey()))
                    continue;                                        // sadece overworld'den
                if (simdi - tp.sonAynaTakas < COOLDOWN) continue;    // 5 dk cooldown
                if (tp.kehanetToplam < MIN_VERI) continue;           // merak edecek kadar veri
                if (tp.hipotezBelirsizligi() < MERAK_ESIGI) continue;// belirsizse Candy merak eder
                if (RNG.nextDouble() > MERAK_IHTIMAL) continue;

                // Candy merak etti → düzeneği HAZIRLA (oyuncu hâlâ overworld'de)
                hazirla(server, player, tp, simdi);

            } catch (Throwable t) {
                Candy.LOG.debug("[Candy] AynaSistemi hata: {}", t.getMessage());
            }
        }
    }

    /**
     * Candy merak edince düzeneği HAZIRLAR — oyuncu hâlâ overworld'de.
     * Çevreyi Ayna'ya kopyalar, kopya NPC + sessiz bekçiyi kurar, HAZIRLIK_SURE sonra
     * oyuncu {@link #spawnla} ile sahneye alınır. "Acaba bu nasıl olurdu... görelim."
     */
    private static void hazirla(MinecraftServer server, ServerPlayerEntity player,
                                TruePersonality tp, long simdi) {
        ServerWorld ayna = server.getWorld(CandyDimensions.AYNA);
        ServerWorld ow = (ServerWorld) player.getWorld();
        if (ayna == null) return;

        BlockPos merkez = player.getBlockPos();
        tp.aynaDonusPoz = merkez.asLong();

        // Candy'nin iç sesi — merak ettiğini duyurur (bir düşünce, tehdit değil)
        if (tp.bossOldurulmeTick == 0) {
            player.sendMessage(Text.translatable("candy.ayna.dusunce")
                .formatted(Formatting.DARK_GRAY, Formatting.ITALIC), false);
        }

        // Düzenek ZEMİNİ: çevreyi Ayna'ya birebir kopyala (kusursuz illüzyon).
        // NPC'ler oyuncu geldiğinde (spawnla) doğurulur — aksi halde oyuncusuz Ayna chunk'ı
        // yüklenmeden kalır ve kopya/bekçi kaybolabilir.
        bolgeKopyala(ow, ayna, merkez);
        ayna.setTimeOfDay(ow.getTimeOfDay());  // gökyüzü senkron

        hazirlikBitis.put(player.getUuid(), simdi + HAZIRLIK_SURE);
        tp.sonAynaTakas = simdi;               // cooldown karar anından işler
        Candy.LOG.info("[Candy] AYNA HAZIRLIK[{}] düzenek kuruluyor @({}, {}, {}) — {} tick sonra spawn",
            player.getName().getString(), merkez.getX(), merkez.getY(), merkez.getZ(), HAZIRLIK_SURE);
    }

    /** Hazırlık bitince oyuncuyu hazırlanan Ayna sahnesine AL (F3 "overworld" gösterir). */
    private static void spawnla(MinecraftServer server, ServerPlayerEntity player, TruePersonality tp) {
        ServerWorld ayna = server.getWorld(CandyDimensions.AYNA);
        if (ayna == null) return;
        BlockPos hedef = BlockPos.fromLong(tp.aynaDonusPoz);
        ayna.setTimeOfDay(server.getOverworld().getTimeOfDay()); // son senkron
        player.teleport(ayna, hedef.getX() + 0.5, hedef.getY(), hedef.getZ() + 0.5,
            Collections.emptySet(), player.getYaw(), player.getPitch(), false);
        // Oyuncu artık Ayna'da (chunk yüklü) → kopya NPC + sessiz bekçiyi şimdi doğur
        kopyaDogur(ayna, player);              // kopya — kaydını gecikmeli oynatacak
        duzenekKur(ayna, player);              // arkasında sessiz bekçi
        tp.aynada = true;
        tp.aynaGirisTick = server.getOverworld().getTime();
        Candy.LOG.info("[Candy] AYNA SPAWN[{}] → hazır sahneye alındı @({}, {}, {})",
            player.getName().getString(), hedef.getX(), hedef.getY(), hedef.getZ());
    }

    /** Ayna → overworld geri takas + İFŞA. */
    private static void geriTakas(MinecraftServer server, ServerPlayerEntity player, TruePersonality tp) {
        ServerWorld ow = server.getOverworld();
        BlockPos donus = BlockPos.fromLong(tp.aynaDonusPoz);
        int x = donus.getX(), z = donus.getZ();
        int y = ow.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
        player.teleport(ow, x + 0.5, y, z + 0.5, Collections.emptySet(),
            player.getYaw(), player.getPitch(), false);
        player.setHealth(player.getMaxHealth());

        kopyayiSil(server, player, BlockPos.fromLong(tp.aynaDonusPoz));
        tp.aynada = false;

        // İFŞA: "Bu sefer sen kopyasın." — bir kez, kanıtla birlikte
        if (!tp.aynaIfsaEdildi && tp.bossOldurulmeTick == 0) {
            tp.aynaIfsaEdildi = true;
            player.sendMessage(Text.translatable("candy.ayna.ifsa")
                .formatted(Formatting.DARK_GRAY, Formatting.ITALIC), false);
        } else if (tp.bossOldurulmeTick == 0) {
            player.sendMessage(Text.translatable("candy.ayna.geri")
                .formatted(Formatting.DARK_GRAY, Formatting.ITALIC), false);
        }
        CandyBasarimlar.tetikle(player, "kim_gercek");
        Candy.LOG.info("[Candy] AYNA GERİ[{}] → overworld @({}, {}, {})",
            player.getName().getString(), x, y, z);
    }

    /** Kopya NPC'yi kaydın ECHO_GECIKME kadar geçmişindeki frame'e taşı (gecikmeli eko). */
    private static void kopyayiSur(MinecraftServer server, ServerPlayerEntity player, Kayit buf) {
        UUID npcId = kopyaEntity.get(player.getUuid());
        if (npcId == null) return;
        ServerWorld ayna = server.getWorld(CandyDimensions.AYNA);
        if (ayna == null) return;
        var e = ayna.getEntity(npcId);
        if (e == null) return; // chunk henüz yüklenmemiş olabilir — haritadan SİLME, bekle
        if (!(e instanceof CandyDoppelEntity kopya) || !kopya.isAlive()) {
            kopyaEntity.remove(player.getUuid());
            return;
        }
        // Buffer'dan ECHO_GECIKME frame geriyi al
        // ECHO_GECIKME kare geriyi al — eskiden 160 adımlık Iterator yürüyüşüydü.
        if (buf.adet <= ECHO_GECIKME) return;
        final float[] frame = kareScratch;
        if (!buf.kare(ECHO_GECIKME, frame)) return;
        kopya.refreshPositionAndAngles(frame[0], frame[1], frame[2], frame[3], frame[4]);
        kopya.setHeadYaw(frame[3]);
        kopya.setBodyYaw(frame[3]);
    }

    private static void kopyaDogur(ServerWorld ayna, ServerPlayerEntity player) {
        BlockPos pos = player.getBlockPos();
        var spawned = CandyEntities.DOPPEL.spawn(ayna, pos, SpawnReason.MOB_SUMMONED);
        if (spawned instanceof CandyDoppelEntity kopya) {
            kopya.setOwnerUuid(player.getUuid());
            kopya.addCommandTag(CandyDoppelEntity.TAG_AYNA);
            kopya.setPersistent();
            kopya.setNoGravity(true);
            kopyaEntity.put(player.getUuid(), kopya.getUuid());
        }
    }

    /** Düzenek: kopyanın arkasında sessiz bir bekçi — senden alamadığını Candy burada kurar. */
    private static void duzenekKur(ServerWorld ayna, ServerPlayerEntity player) {
        double yaw = Math.toRadians(player.getYaw());
        double x = player.getX() + Math.sin(yaw) * 4.0;   // arkaya
        double z = player.getZ() - Math.cos(yaw) * 4.0;
        BlockPos pos = BlockPos.ofFloored(x, player.getY(), z);
        var spawned = CandyEntities.DOPPEL.spawn(ayna, pos, SpawnReason.MOB_SUMMONED);
        if (spawned instanceof CandyDoppelEntity bekci) {
            bekci.setOwnerUuid(player.getUuid());
            bekci.addCommandTag(CandyDoppelEntity.TAG_BEKCI);
            bekci.setPersistent();
        }
    }

    /** Overworld çevresini Ayna'ya birebir kopyala — kusursuz illüzyon zemini. */
    private static void bolgeKopyala(ServerWorld kaynak, ServerWorld hedef, BlockPos merkez) {
        int cx = merkez.getX(), cy = merkez.getY(), cz = merkez.getZ();
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int dx = -KOPYA_YARICAP; dx <= KOPYA_YARICAP; dx++) {
            for (int dz = -KOPYA_YARICAP; dz <= KOPYA_YARICAP; dz++) {
                for (int dy = -3; dy <= 18; dy++) {
                    m.set(cx + dx, cy + dy, cz + dz);
                    var st = kaynak.getBlockState(m);
                    // flag 2 = client'a gönder ama komşu güncellemesi yok (hafif)
                    hedef.setBlockState(m, st, 2);
                }
            }
        }
    }

    private static void kopyayiSil(MinecraftServer server, ServerPlayerEntity player, BlockPos merkez) {
        UUID npcId = kopyaEntity.remove(player.getUuid());
        ServerWorld ayna = server.getWorld(CandyDimensions.AYNA);
        if (ayna == null) return;
        if (npcId != null) {
            var e = ayna.getEntity(npcId);
            if (e != null) e.discard();
        }
        // Owner eşleşen TAG_AYNA/TAG_BEKCI'leri Ayna'da GİRİŞ konumu çevresinde temizle
        // (oyuncu artık overworld'de — o yüzden dönüş koordinatını merkez al; restart yetimleri dahil)
        Box kutu = new Box(merkez).expand(48);
        ayna.getEntitiesByClass(CandyDoppelEntity.class, kutu, d -> {
            var o = d.getOwnerUuid();
            return o.isPresent() && o.get().equals(player.getUuid())
                && (d.getCommandTags().contains(CandyDoppelEntity.TAG_AYNA)
                    || d.getCommandTags().contains(CandyDoppelEntity.TAG_BEKCI));
        }).forEach(net.minecraft.entity.Entity::discard);
    }

    public static void oyuncuAyrildi(UUID uuid) {
        kayit.remove(uuid);
        kopyaEntity.remove(uuid);
        hazirlikBitis.remove(uuid);
    }
}
