package com.photonpulse.candy.tempo;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.tree.TreeBranch;
import com.photonpulse.candy.tree.TreeGrowth;
import com.photonpulse.candy.tree.TreeNode;
import com.photonpulse.candy.platform.SunucuTick;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import java.util.Random;

/**
 * MobSpawnBooster — Veri Açlığı Sistemi (Sistem 1).
 *
 * Candy sabırlı görünüyor ama daha çok senaryo görmek için sabırsız. Bu paradoks:
 *   sabirSeviyesi = 1.0 - TepkiKoku.aktiflik   (Tepki ne kadar az aktifse o kadar sabırlı)
 *
 * sabirSeviyesi yüksek → dünyayı hızlandır → oyuncunun çevresinde ekstra mob
 * biriktir. Mağarada daha agresif, yüzeyde daha yumuşak. Bu mob'lar vanilla —
 * candy_clone ETİKETİ TAŞIMAZLAR.
 */
public class MobSpawnBooster {

    private static final int TICK_ARALIGI = 1200;   // 1 dakika
    private static int tickSayaci = 0;
    private static final Random RNG = new Random();

    // Oyuncu çevresinde bu kadar mob varsa ek spawn yapma (perf koruması)
    private static final int MOB_TAVAN = 40;
    private static final int TAVAN_YARICAP = 32;
    // Ek mob konumu için arama yarıçapı
    private static final int MIN_MESAFE = 16;
    private static final int MAX_MESAFE = 24;

    // Mob havuzları (candy_clone DEĞİL, sıradan vanilla)
    private static final EntityType<?>[] MAGARA_DUSMAN = {
        EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER,
        EntityType.CAVE_SPIDER, EntityType.CREEPER
    };
    private static final EntityType<?>[] GECE_DUSMAN = {
        EntityType.ZOMBIE, EntityType.SKELETON
    };
    private static final EntityType<?>[] GUNDUZ_PASIF = {
        EntityType.SHEEP, EntityType.COW, EntityType.PIG, EntityType.CHICKEN
    };

    public static void register() {
        SunucuTick.END_SERVER_TICK.register(MobSpawnBooster::onTick);
        Candy.LOG.info("[Candy] MobSpawnBooster aktif — veri açlığı");
    }

    private static void onTick(MinecraftServer server) {
        tickSayaci++;
        if (tickSayaci % TICK_ARALIGI != 0) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            oyuncuIcinBoost(player);
        }
    }

    private static void oyuncuIcinBoost(ServerPlayer player) {
        double sabir = sabirSeviyesi(player);
        if (sabir < 0.05) return;   // Candy neredeyse hiç sabırlı değil — boost yok

        ServerLevel world = (ServerLevel) player.level();
        BlockPos merkez = player.blockPosition();

        // Perf tavanı
        AABB aramaKutusu = new AABB(merkez).inflate(TAVAN_YARICAP);
        int mevcutMob = world.getEntitiesOfClass(LivingEntity.class, aramaKutusu, e -> true).size();
        if (mevcutMob >= MOB_TAVAN) return;

        Ortam ortam = ortamSinifi(world, player);
        EntityType<?>[] havuz = havuzSec(ortam);
        if (havuz.length == 0) return;

        // Sabir + ortam tabanlı spawn sayısı: mağara sabırla çarpan yüksek, yüzey düşük
        int kacTane = spawnSayisi(ortam, sabir);
        int spawnedCount = 0;
        for (int i = 0; i < kacTane; i++) {
            BlockPos hedef = uygunSpawnKonumu(world, merkez, ortam);
            if (hedef == null) continue;
            EntityType<?> tur = havuz[RNG.nextInt(havuz.length)];
            Entity spawned = tur.spawn(world, hedef, EntitySpawnReason.NATURAL);
            if (spawned != null) spawnedCount++;
        }

        if (spawnedCount > 0) {
            Candy.LOG.debug("[Candy] Veri açlığı: {} çevresinde {} ekstra spawn (sabır: {}, ortam: {})",
                player.getName().getString(), spawnedCount, String.format("%.2f", sabir), ortam);
        }
    }

    /**
     * sabirSeviyesi = 1 - Tepki kökünün aktifliği. TreeGrowth.get() ve
     * TreeBranch.TEPKI.id public arayüzü — okuma amaçlı, değiştirilmez.
     */
    private static double sabirSeviyesi(ServerPlayer player) {
        TreeNode tepki = TreeGrowth.get(player.getUUID()).nodes.get(TreeBranch.TEPKI.id);
        if (tepki == null) return 0.5;   // fallback — kök yoksa ortalama
        return Math.max(0, Math.min(1, 1.0 - tepki.aktiflik));
    }

    // ── Ortam sınıflandırma ──

    private enum Ortam { MAGARA, YUZEY_GECE, YUZEY_GUNDUZ }

    private static Ortam ortamSinifi(ServerLevel world, ServerPlayer player) {
        BlockPos pos = player.blockPosition();
        int denizSeviyesi = world.getSeaLevel();
        int isik = world.getMaxLocalRawBrightness(pos);
        int yuzeyY = world.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ());
        boolean uzeriKapali = yuzeyY > player.getY() + 3;

        if (pos.getY() < denizSeviyesi && isik < 8 && uzeriKapali) {
            return Ortam.MAGARA;
        }
        long gunSaati = world.getDayTime() % 24000L;
        boolean gece = gunSaati > 13000L && gunSaati < 23000L;
        return gece ? Ortam.YUZEY_GECE : Ortam.YUZEY_GUNDUZ;
    }

    private static EntityType<?>[] havuzSec(Ortam o) {
        return switch (o) {
            case MAGARA -> MAGARA_DUSMAN;
            case YUZEY_GECE -> GECE_DUSMAN;
            case YUZEY_GUNDUZ -> GUNDUZ_PASIF;
        };
    }

    private static int spawnSayisi(Ortam o, double sabir) {
        double taban = switch (o) {
            case MAGARA -> 3.0;      // en agresif
            case YUZEY_GECE -> 1.5;
            case YUZEY_GUNDUZ -> 1.0;
        };
        // Sabır çarpanı — mağarada sabırla lineer, yüzeyde daha yumuşak
        double carpan = 1.0 + sabir * (o == Ortam.MAGARA ? 2.0 : 1.0);
        return (int) Math.round(taban * carpan);
    }

    // ── Konum bulma ──

    private static BlockPos uygunSpawnKonumu(ServerLevel world, BlockPos merkez, Ortam o) {
        for (int deneme = 0; deneme < 10; deneme++) {
            double angle = RNG.nextDouble() * Math.PI * 2;
            double mesafe = MIN_MESAFE + RNG.nextDouble() * (MAX_MESAFE - MIN_MESAFE);
            int x = merkez.getX() + (int) (Math.cos(angle) * mesafe);
            int z = merkez.getZ() + (int) (Math.sin(angle) * mesafe);

            BlockPos aday;
            if (o == Ortam.MAGARA) {
                // Y aralığını oyuncunun yakınında dene — mağara içinde kalsın
                int dy = RNG.nextInt(11) - 5;
                aday = new BlockPos(x, merkez.getY() + dy, z);
            } else {
                int y = world.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                aday = new BlockPos(x, y, z);
            }

            if (!world.getBlockState(aday).isAir()) continue;
            if (!world.getBlockState(aday.above()).isAir()) continue;
            if (!world.getBlockState(aday.below()).isRedstoneConductor(world, aday.below())) continue;

            // Düşman mob'lar için ışık kontrolü — vanilla mekaniğine uy
            EntityType<?>[] havuz = havuzSec(o);
            if (havuz.length > 0 && Monster.class.isAssignableFrom(havuz[0].getBaseClass())) {
                if (world.getMaxLocalRawBrightness(aday) > 7) continue;
            }
            return aday;
        }
        return null;
    }
}
