package com.photonpulse.candy.tempo;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.tree.TreeBranch;
import com.photonpulse.candy.tree.TreeGrowth;
import com.photonpulse.candy.tree.TreeNode;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.Heightmap;

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
        ServerTickEvents.END_SERVER_TICK.register(MobSpawnBooster::onTick);
        Candy.LOG.info("[Candy] MobSpawnBooster aktif — veri açlığı");
    }

    private static void onTick(MinecraftServer server) {
        tickSayaci++;
        if (tickSayaci % TICK_ARALIGI != 0) return;

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            oyuncuIcinBoost(player);
        }
    }

    private static void oyuncuIcinBoost(ServerPlayerEntity player) {
        double sabir = sabirSeviyesi(player);
        if (sabir < 0.05) return;   // Candy neredeyse hiç sabırlı değil — boost yok

        ServerWorld world = (ServerWorld) player.getWorld();
        BlockPos merkez = player.getBlockPos();

        // Perf tavanı
        Box aramaKutusu = new Box(merkez).expand(TAVAN_YARICAP);
        int mevcutMob = world.getEntitiesByClass(LivingEntity.class, aramaKutusu, e -> true).size();
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
            Entity spawned = tur.spawn(world, hedef, SpawnReason.NATURAL);
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
    private static double sabirSeviyesi(ServerPlayerEntity player) {
        TreeNode tepki = TreeGrowth.get(player.getUuid()).nodes.get(TreeBranch.TEPKI.id);
        if (tepki == null) return 0.5;   // fallback — kök yoksa ortalama
        return Math.max(0, Math.min(1, 1.0 - tepki.aktiflik));
    }

    // ── Ortam sınıflandırma ──

    private enum Ortam { MAGARA, YUZEY_GECE, YUZEY_GUNDUZ }

    private static Ortam ortamSinifi(ServerWorld world, ServerPlayerEntity player) {
        BlockPos pos = player.getBlockPos();
        int denizSeviyesi = world.getSeaLevel();
        int isik = world.getLightLevel(pos);
        int yuzeyY = world.getTopY(Heightmap.Type.WORLD_SURFACE, pos.getX(), pos.getZ());
        boolean uzeriKapali = yuzeyY > player.getY() + 3;

        if (pos.getY() < denizSeviyesi && isik < 8 && uzeriKapali) {
            return Ortam.MAGARA;
        }
        long gunSaati = world.getTimeOfDay() % 24000L;
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

    private static BlockPos uygunSpawnKonumu(ServerWorld world, BlockPos merkez, Ortam o) {
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
                int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
                aday = new BlockPos(x, y, z);
            }

            if (!world.getBlockState(aday).isAir()) continue;
            if (!world.getBlockState(aday.up()).isAir()) continue;
            if (!world.getBlockState(aday.down()).isSolidBlock(world, aday.down())) continue;

            // Düşman mob'lar için ışık kontrolü — vanilla mekaniğine uy
            EntityType<?>[] havuz = havuzSec(o);
            if (havuz.length > 0 && HostileEntity.class.isAssignableFrom(havuz[0].getBaseClass())) {
                if (world.getLightLevel(aday) > 7) continue;
            }
            return aday;
        }
        return null;
    }
}
