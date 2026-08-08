package com.photonpulse.candy.clone;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.core.TruePersonality;
import com.photonpulse.candy.tree.PersonalityTree;
import com.photonpulse.candy.tree.TreeBranch;
import com.photonpulse.candy.tree.TreeGrowth;
import com.photonpulse.candy.tree.TreeNode;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * CloneManager — Candy'nin klonları.
 *
 * KRİTİK: Gerçek Candy görünmez. Oyuncunun etkileşim ettiği her şey KLON.
 *
 * Her klon:
 *  - TruePersonality'nin O ANDAKİ snapshot'ı ile doğar
 *  - Bir vanilla mob (zombi/iskelet) formunda spawn olur
 *  - "candy_clone" NBT etiketi ile işaretlenir
 *  - Baskın benliğe göre isim alır ("Avcı", "Gözlemci"...)
 *
 * Snapshot mekaniği: klon doğduğu anki verilerle davranmaya devam eder.
 * TruePersonality büyümeye devam eder. Klon geride kalır.
 */
public class CloneManager {

    private static final int KONTROL_ARALIGI = 400;   // 20 saniye
    private static final int MAKS_KLON_PER_OYUNCU = 2;
    private static int tickSayaci = 0;
    private static final Random RNG = new Random();

    private static final String KLON_ETIKETI = "candy_clone";

    // Her oyuncu için aktif klon ID'leri
    private static final Map<UUID, List<UUID>> aktifKlonlar = new HashMap<>();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(CloneManager::onTick);
        Candy.LOG.info("[Candy] CloneManager aktif");
    }

    private static void onTick(MinecraftServer server) {
        tickSayaci++;
        if (tickSayaci % KONTROL_ARALIGI != 0) return;

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            klonKararVer(player);
        }
    }

    /**
     * Oyuncu için klon spawn edelim mi karar ver.
     * Koşullar:
     *  - Kurulum tamamlanmış olmalı
     *  - Korku skoru en az 25
     *  - Aktif klon sayısı MAKS_KLON_PER_OYUNCU'nun altında
     *  - Rastgele düşük olasılık (Candy sabırlı)
     */
    private static void klonKararVer(ServerPlayerEntity player) {
        TruePersonality tp = PersonalityManager.get(player);

        if (!tp.kurulumTamam) return;
        if (tp.korkuSkoru < 25) return;

        List<UUID> mevcut = aktifKlonlar.computeIfAbsent(player.getUuid(), k -> new java.util.ArrayList<>());

        // Ölmüş / uzaklaşmış klonları temizle
        ServerWorld world = (ServerWorld) player.getWorld();
        mevcut.removeIf(uuid -> {
            var e = world.getEntity(uuid);
            return e == null || !e.isAlive();
        });

        if (mevcut.size() >= MAKS_KLON_PER_OYUNCU) return;
        if (RNG.nextDouble() > 0.25) return;

        // Uygun bir konum bul — oyuncunun 20-40 blok ötesinde, karanlıkta tercih
        BlockPos hedef = uygunSpawnKonumu(player);
        if (hedef == null) return;

        LivingEntity klon = klonSpawn(world, hedef, tp);
        if (klon != null) {
            mevcut.add(klon.getUuid());
            Candy.LOG.info("[Candy] klon spawn edildi: {} için @{}",
                player.getName().getString(), hedef);
        }
    }

    /**
     * Klonu spawn et. Klon türü artık ağacın TEPKI kökünün baskın dalının
     * trait'ine göre seçilir — saldirOrani ağırlığı ne yönde eğik?
     */
    private static LivingEntity klonSpawn(ServerWorld world, BlockPos pos, TruePersonality tp) {
        PersonalityTree tree = TreeGrowth.get(tp.oyuncuId);
        TreeNode tepkiBaskin = tree.baskinAlt(TreeBranch.TEPKI);

        // Trait'te saldirOrani ağırlığı + ise klon saldırgan (iskelet), - ise pasif (zombi)
        double saldirTrait = tepkiBaskin != null
            ? tepkiBaskin.trait.getOrDefault("saldirOrani", 0.0)
            : 0.0;
        // Ağırlık * TruePersonality'deki gerçek saldırganlık = klonun "eğilimi"
        double egilim = saldirTrait * tp.saldirOrani;

        EntityType<?> tur = (egilim > 0.1) ? EntityType.SKELETON : EntityType.ZOMBIE;

        Object spawned = tur.spawn(world, pos, SpawnReason.MOB_SUMMONED);
        if (!(spawned instanceof MobEntity klon)) return null;

        klon.addCommandTag(KLON_ETIKETI);
        // Snapshot tarihini etikete gömüyoruz — hangi tick'te doğduğunu unutmuyoruz
        klon.addCommandTag(KLON_ETIKETI + "_t" + tp.toplamGozlemTick);
        // Hangi baskın daldan türediğini de tag'le
        if (tepkiBaskin != null) {
            klon.addCommandTag(KLON_ETIKETI + "_dal_" + tepkiBaskin.id.replace('.', '_'));
        }

        klon.setCustomName(Text.literal("§7[Candy]"));
        klon.setCustomNameVisible(false);
        klon.setPersistent();

        // Klonlar canlı ama zayıf — hızlı ölmesin ama tehdit de olmasın
        klon.setHealth(6.0f);

        return klon;
    }

    /**
     * Oyuncudan uzakta, ama görüş açısı dışında bir spawn konumu bul.
     */
    private static BlockPos uygunSpawnKonumu(ServerPlayerEntity player) {
        ServerWorld world = (ServerWorld) player.getWorld();
        BlockPos merkez = player.getBlockPos();

        for (int deneme = 0; deneme < 15; deneme++) {
            double angle = RNG.nextDouble() * Math.PI * 2;
            double mesafe = 20 + RNG.nextDouble() * 20;
            int x = merkez.getX() + (int)(Math.cos(angle) * mesafe);
            int z = merkez.getZ() + (int)(Math.sin(angle) * mesafe);
            int y = world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING, x, z);
            BlockPos p = new BlockPos(x, y, z);

            if (!world.getBlockState(p).isAir()) continue;
            if (!world.getBlockState(p.up()).isAir()) continue;
            if (!world.getBlockState(p.down()).isSolidBlock(world, p.down())) continue;

            // Işık seviyesi düşük olsun tercihen
            int isik = world.getLightLevel(p);
            if (isik > 8) continue;

            return p;
        }
        return null;
    }

    /**
     * Bir varlık Candy'nin klonu mu?
     */
    public static boolean klonMu(LivingEntity entity) {
        return entity.getCommandTags().contains(KLON_ETIKETI);
    }
}
