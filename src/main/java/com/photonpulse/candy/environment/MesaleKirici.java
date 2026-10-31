package com.photonpulse.candy.environment;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.core.Psikoloji;
import com.photonpulse.candy.core.TruePersonality;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * MesaleKirici — mağarada Candy tüm meşaleleri patlatır.
 *
 * Player mağarada (Y<seaLevel + upside covered + BLOCK light düşük genelde)
 * ve kurulum tamamsa: her 6 dk kontrol, %10 ihtimalle 24 blok yarıçapında
 * TÜM meşaleleri anında havaya çevir. Ürkütücü: gece döndüğünde base karanlık.
 *
 * Etki: kotuluk katlanarak artar (agresivlik ile ihtimal artar).
 */
public class MesaleKirici {

    private static final int TICK_ARALIGI = 6000;               // 5 dakika kontrol
    private static final long OYUNCU_COOLDOWN_TICK = 12000L;    // min 10 dk arayla
    private static final double MIN_KORKU = 25.0;
    private static final double BASE_IHTIMAL = 0.10;            // %10
    private static final int YARICAP = 24;

    private static int tickSayaci = 0;
    private static final Random RNG = new Random();
    private static final Map<UUID, Long> sonPatlamaTick = new HashMap<>();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(MesaleKirici::onTick);
        Candy.LOG.info("[Candy] MesaleKirici aktif — mağara meşale patlaması");
    }

    public static void oyuncuAyrildi(UUID uuid) {
        sonPatlamaTick.remove(uuid);
    }

    public static void sunucuKapan() {
        sonPatlamaTick.clear();
        tickSayaci = 0;
    }

    private static void onTick(MinecraftServer server) {
        tickSayaci++;
        if (tickSayaci % TICK_ARALIGI != 0) return;

        long simdi = server.getOverworld().getTime();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            TruePersonality tp = PersonalityManager.get(player);
            if (!tp.kurulumTamam) continue;
            if (tp.korkuSkoru < MIN_KORKU) continue;

            // Mağara kontrolü
            ServerWorld world = (ServerWorld) player.getWorld();
            BlockPos pos = player.getBlockPos();
            int seaLevel = world.getSeaLevel();
            int yuzeyY = world.getTopY(Heightmap.Type.WORLD_SURFACE, pos.getX(), pos.getZ());
            boolean uzeriKapali = yuzeyY > player.getY() + 3;
            if (!(player.getY() < seaLevel && uzeriKapali)) continue;

            Long son = sonPatlamaTick.get(player.getUuid());
            double agresivlik = Psikoloji.agresivlikCarpani(tp);
            long cooldown = (long) (OYUNCU_COOLDOWN_TICK / agresivlik);
            if (son != null && simdi - son < cooldown) continue;

            if (RNG.nextDouble() > BASE_IHTIMAL * agresivlik) continue;

            int patlayan = mesaleleriPatlat(world, pos);
            if (patlayan > 0) {
                sonPatlamaTick.put(player.getUuid(), simdi);
                // v1.9.3: elveda_mesale başarımı — toplam 10 meşale kırılınca
                tp.kirilanMesaleSayisi += patlayan;
                if (tp.kirilanMesaleSayisi >= 10) {
                    com.photonpulse.candy.basarim.CandyBasarimlar.tetikle(player, "elveda_mesale");
                }
                Candy.LOG.info("[Candy] MesaleKirici: {} çevresinde {} meşale patladı",
                    player.getName().getString(), patlayan);
            }
        }
    }

    /**
     * Verilen merkez etrafında YARICAP içindeki tüm meşale/wall_torch/soul_torch'ları
     * havaya çevirir. Sayısını döner.
     */
    private static int mesaleleriPatlat(ServerWorld world, BlockPos merkez) {
        int sayac = 0;
        for (int dx = -YARICAP; dx <= YARICAP; dx++) {
            for (int dy = -8; dy <= 8; dy++) {
                for (int dz = -YARICAP; dz <= YARICAP; dz++) {
                    BlockPos p = merkez.add(dx, dy, dz);
                    var b = world.getBlockState(p).getBlock();
                    if (b == Blocks.TORCH || b == Blocks.WALL_TORCH
                        || b == Blocks.SOUL_TORCH || b == Blocks.SOUL_WALL_TORCH) {
                        world.setBlockState(p, Blocks.AIR.getDefaultState());
                        // Söndürme sesi
                        world.playSound(null, p, SoundEvents.BLOCK_FIRE_EXTINGUISH,
                            SoundCategory.BLOCKS, 0.4f, 0.8f);
                        sayac++;
                    }
                }
            }
        }
        return sayac;
    }
}
