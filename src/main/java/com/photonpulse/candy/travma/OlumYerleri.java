package com.photonpulse.candy.travma;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.audio.CandySounds;
import com.photonpulse.candy.basarim.CandyBasarimlar;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.core.TruePersonality;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import com.photonpulse.candy.platform.SunucuTick;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import java.util.Random;
import java.util.UUID;

/**
 * OlumYerleri (v3.0) — Ölüm Yerleri Hafızası.
 *
 * Player öldüğünde konum kaydedilir (max 20). Her 10 sn player'ın 8 blok içinde
 * eski ölüm yeri varsa %40 ihtimalle Candy hatırlatır. 3+ ziyaret = travma döngüsü.
 *
 * Sadece kurulum tamamlanmış oyuncular için.
 */
public class OlumYerleri {

    private static final int TICK_ARALIGI = 200;          // 10 sn
    private static final int YAKINLIK_KARE = 8 * 8;       // 8 blok yatay
    private static final int Y_TOLERANS = 3;              // Y ekseni ±3
    private static final double HATIRLATMA_IHTIMAL = 0.40;
    private static final long OYUNCU_COOLDOWN_TICK = 600L; // min 30 sn iki hatırlatma arası
    private static final int MAX_HAFIZA = 20;
    private static final int TRAVMA_ESIGI = 3;             // 3+ ziyaret = travma döngüsü

    private static final Random RNG = new Random();

    public static void register() {
        // Player ölümü — konumu kaydet
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(entity instanceof ServerPlayer player)) return;
            TruePersonality tp = PersonalityManager.get(player);
            if (!tp.kurulumTamam) return;

            BlockPos p = player.blockPosition();
            int[] konum = new int[]{p.getX(), p.getY(), p.getZ()};

            // Halihazırda 8 blok içinde başka bir ölüm yeri varsa onu birleştir (yeni entry açma)
            long anahtar = anahtarBul(tp, p);
            if (anahtar != -1L) {
                tp.olumYeriTekrar.merge(anahtar, 1, Integer::sum);
                Candy.LOG.info("[Candy] OlumYerleri: {} tekrar öldü @{} ({}. kez)",
                    player.getName().getString(), p, tp.olumYeriTekrar.get(anahtar));
            } else {
                tp.olumYerleri.add(konum);
                if (tp.olumYerleri.size() > MAX_HAFIZA) {
                    int[] cikan = tp.olumYerleri.remove(0);
                    // Eski entry'nin tekrar sayısı da temizlenebilir
                    tp.olumYeriTekrar.remove(BlockPos.asLong(cikan[0], cikan[1], cikan[2]));
                }
                tp.olumYeriTekrar.put(BlockPos.asLong(p.getX(), p.getY(), p.getZ()), 1);
                Candy.LOG.info("[Candy] OlumYerleri: {} yeni ölüm @{}",
                    player.getName().getString(), p);
            }
        });

        // Proximity kontrolü
        SunucuTick.END_SERVER_TICK.register(OlumYerleri::onTick);

        Candy.LOG.info("[Candy] OlumYerleri aktif — travma hafızası (v3.0)");
    }

    private static void onTick(MinecraftServer server) {
        if (server.getTickCount() % TICK_ARALIGI != 0) return;
        long simdi = server.overworld().getGameTime();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            TruePersonality tp = PersonalityManager.get(player);
            if (!tp.kurulumTamam) continue;
            if (tp.olumYerleri.isEmpty()) continue;
            if (simdi - tp.sonOlumYeriHatirlatma < OYUNCU_COOLDOWN_TICK) continue;

            int px = player.getBlockX();
            int py = player.getBlockY();
            int pz = player.getBlockZ();

            // Yakındaki ölüm yerlerinden birini seç
            int[] yakin = null;
            for (int[] konum : tp.olumYerleri) {
                int dx = konum[0] - px;
                int dy = konum[1] - py;
                int dz = konum[2] - pz;
                if (Math.abs(dy) > Y_TOLERANS) continue;
                if (dx * dx + dz * dz > YAKINLIK_KARE) continue;
                yakin = konum;
                break;
            }
            if (yakin == null) continue;
            if (RNG.nextDouble() > HATIRLATMA_IHTIMAL) {
                tp.sonOlumYeriHatirlatma = simdi;
                continue;
            }

            long anahtar = BlockPos.asLong(yakin[0], yakin[1], yakin[2]);
            int tekrar = tp.olumYeriTekrar.getOrDefault(anahtar, 1);

            if (tekrar >= TRAVMA_ESIGI) {
                // Travma döngüsü — reverse bhoot + özel mesaj
                player.displayClientMessage(Component.translatable("candy.olum_yeri.tekrar")
                    .withStyle(net.minecraft.ChatFormatting.DARK_RED,
                               net.minecraft.ChatFormatting.ITALIC), false);
                try {
                    ServerLevel dunya = (ServerLevel) player.level();
                    // Reverse hissi için pitch düşür
                    dunya.playSound(null, player.blockPosition(),
                        CandySounds.BHOOT, SoundSource.AMBIENT, 0.6f, 0.55f);
                } catch (Throwable ignored) {}
                CandyBasarimlar.tetikle(player, "travma_dongusu");
                Candy.LOG.info("[Candy] OlumYerleri: {} travma döngüsü @{} ({}. ziyaret)",
                    player.getName().getString(), yakin, tekrar);
            } else {
                // Normal hatırlatma — 4 varyant
                int idx = 1 + RNG.nextInt(4);
                player.displayClientMessage(Component.translatable("candy.olum_yeri." + idx)
                    .withStyle(net.minecraft.ChatFormatting.GRAY,
                               net.minecraft.ChatFormatting.ITALIC), false);
                Candy.LOG.info("[Candy] OlumYerleri: {} hatırlatma {} @{}",
                    player.getName().getString(), idx, yakin);
            }

            // Ziyaret sayısını artır — proximity'e girmek = travma pekiştirme
            tp.olumYeriTekrar.merge(anahtar, 1, Integer::sum);
            tp.sonOlumYeriHatirlatma = simdi;
        }
    }

    /** Verilen konuma yakın (YAKINLIK_KARE, Y_TOLERANS) mevcut bir kayıt varsa asLong anahtarını döndür. */
    private static long anahtarBul(TruePersonality tp, BlockPos p) {
        for (int[] konum : tp.olumYerleri) {
            int dx = konum[0] - p.getX();
            int dy = konum[1] - p.getY();
            int dz = konum[2] - p.getZ();
            if (Math.abs(dy) > Y_TOLERANS) continue;
            if (dx * dx + dz * dz > YAKINLIK_KARE) continue;
            return BlockPos.asLong(konum[0], konum[1], konum[2]);
        }
        return -1L;
    }

    public static void oyuncuAyrildi(UUID uuid) {
        // Per-player state PersonalityManager tarafından persist ediliyor.
    }
}
