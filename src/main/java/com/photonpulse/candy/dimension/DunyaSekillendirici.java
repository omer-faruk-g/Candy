package com.photonpulse.candy.dimension;

import com.photonpulse.candy.Candy;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;

/**
 * DunyaSekillendirici — 8 kurul dünyasının desenini chunk üretiminde damgalar.
 *
 * <p>Özel {@code ChunkGenerator} YOK. Boyut JSON'u basit bir vanilla üreteç kullanır;
 * bu sınıf {@code ServerChunkEvents.CHUNK_GENERATE} anında (chunk başına TAM BİR KEZ)
 * {@link KurulDunya.Sekil}'e göre chunk'u şekillendirir. Böylece tüm karmaşık worldgen
 * codec yüzeyi (ve onun bakım/uyumluluk riski) tamamen atlanır.</p>
 *
 * <p>Değer-gürültüsü kendi hash'imizden üretilir (deterministik, dış kütüphane yok):
 * aynı tohum + aynı koordinat = aynı arazi, yeniden üretim tutarlı.</p>
 */
public final class DunyaSekillendirici {

    private DunyaSekillendirici() {}

    public static void register() {
        ServerChunkEvents.CHUNK_GENERATE.register((world, chunk) -> {
            KurulDunya d = KurulDunya.bul(world.getRegistryKey());
            if (d == null) return;
            try {
                sekillendir(world, chunk, d.sekil);
            } catch (Throwable t) {
                Candy.LOG.debug("[Candy] şekillendirme hatası ({}): {}", d.id, t.getMessage());
            }
        });
        Candy.LOG.info("[Candy] DunyaSekillendirici aktif — 8 kurul dünyası şekillendiriliyor");
    }

    private static void sekillendir(ServerWorld world, WorldChunk chunk, KurulDunya.Sekil sekil) {
        final int bx = chunk.getPos().getStartX();
        final int bz = chunk.getPos().getStartZ();
        final int taban = world.getBottomY();
        final int tavan = world.getTopYInclusive();
        final BlockPos.Mutable m = new BlockPos.Mutable();

        switch (sekil) {
            case SKYGRID   -> skyGrid(chunk, m, bx, bz);
            case KORIDOR   -> koridor(chunk, m, bx, bz);
            case KLON      -> DunyaKlon.damgala(chunk, m, bx, bz);
            case KOR       -> kor(chunk, m, bx, bz, taban);
            case FARLANDS  -> farLands(chunk, m, bx, bz, false);
            case FARTHERLANDS -> farLands(chunk, m, bx, bz, true);
            case CORNERLANDS  -> cornerLands(chunk, m, bx, bz);
            case ME        -> { /* düz üreteç JSON'da; şekil gerekmez, dokular client'ta */ }
        }
    }

    // ─────────────────────────────────────────────────────────
    // SKY GRID — boşlukta asılı 3B ızgara (her eksende 4'te 1)
    // ─────────────────────────────────────────────────────────
    private static void skyGrid(WorldChunk chunk, BlockPos.Mutable m, int bx, int bz) {
        for (int lx = 0; lx < 16; lx++) {
            int wx = bx + lx;
            if ((wx & 3) != 0) continue;
            for (int lz = 0; lz < 16; lz++) {
                int wz = bz + lz;
                if ((wz & 3) != 0) continue;
                for (int y = 8; y <= 120; y += 4) {
                    BlockState b = izgaraBlok(wx, y, wz);
                    chunk.setBlockState(m.set(wx, y, wz), b);
                }
            }
        }
    }

    private static BlockState izgaraBlok(int x, int y, int z) {
        int h = hash(x, y, z);
        int r = Math.floorMod(h, 20);
        if (r < 10) return Blocks.STONE.getDefaultState();
        if (r < 14) return Blocks.DIRT.getDefaultState();
        if (r < 16) return Blocks.GRASS_BLOCK.getDefaultState();
        if (r < 18) return Blocks.DEEPSLATE.getDefaultState();
        return Blocks.OAK_LOG.getDefaultState();
    }

    // ─────────────────────────────────────────────────────────
    // KORIDOR — dümdüz koridorlar + sağlı sollu dallar (ızgara labirent)
    // ─────────────────────────────────────────────────────────
    private static final int KORIDOR_TABAN = 63;   // zemin
    private static final int KORIDOR_TAVAN = 68;    // tavan (yükseklik 4)

    private static void koridor(WorldChunk chunk, BlockPos.Mutable m, int bx, int bz) {
        for (int lx = 0; lx < 16; lx++) {
            int wx = bx + lx;
            for (int lz = 0; lz < 16; lz++) {
                int wz = bz + lz;
                boolean koridorMu = koridorHucresi(wx, wz);
                // Zemin her yerde
                chunk.setBlockState(m.set(wx, KORIDOR_TABAN, wz), Blocks.SMOOTH_STONE.getDefaultState());
                if (koridorMu) {
                    // Açık koridor: tavan kapat, arası hava
                    chunk.setBlockState(m.set(wx, KORIDOR_TAVAN + 1, wz), Blocks.SMOOTH_STONE.getDefaultState());
                } else {
                    // Duvar: zeminden tavana kadar dolu
                    for (int y = KORIDOR_TABAN + 1; y <= KORIDOR_TAVAN + 1; y++) {
                        chunk.setBlockState(m.set(wx, y, wz), Blocks.STONE_BRICKS.getDefaultState());
                    }
                }
            }
        }
    }

    /** Grid koridor: worldX ya da worldZ 8'lik bantta 4 blok genişliğinde açık = kesişen düz koridorlar. */
    private static boolean koridorHucresi(int wx, int wz) {
        int mx = Math.floorMod(wx, 8);
        int mz = Math.floorMod(wz, 8);
        return mx < 4 || mz < 4;
    }

    // ─────────────────────────────────────────────────────────
    // KÖR — normal arazi barrier'a çevrilir (her katı blok görünmez)
    // ─────────────────────────────────────────────────────────
    // KÖR — GÖRÜNMEZ arazi. İlk tasarım gerçek overworld noise gen + her bloğu barrier'a
    // çevirme idi; ama overworld gen zaten en pahalı üreteç ve üstüne dönüşüm binince tek
    // tick 90 sn'ye çıkıp watchdog patladı (ÖLÇÜLDÜ, forceload testinde). Bunun yerine
    // void tabana KENDİ gürültümüzden bir BARRIER TEPE KABUĞU kuruyoruz: oyuncu görünmez
    // yamaçlara toslar, hiçbir şey göremez — his birebir aynı ama maliyet Sky Grid kadar.
    private static final int KOR_ZEMIN = 56;

    private static void kor(WorldChunk chunk, BlockPos.Mutable m, int bx, int bz, int taban) {
        final BlockState barrier = Blocks.BARRIER.getDefaultState();
        for (int lx = 0; lx < 16; lx++) {
            int wx = bx + lx;
            for (int lz = 0; lz < 16; lz++) {
                int wz = bz + lz;
                // Yumuşak tepeler: 60..92 arası görünmez yüzey
                int h = 64 + (int) (gurultu2(wx * 0.018, wz * 0.018) * 34);
                for (int y = KOR_ZEMIN; y <= h; y++) {
                    chunk.setBlockState(m.set(wx, y, wz), barrier);
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────
    // FAR LANDS / FARTHER LANDS — süngerimsi dikey duvarlar
    // ─────────────────────────────────────────────────────────
    private static void farLands(WorldChunk chunk, BlockPos.Mutable m, int bx, int bz, boolean farther) {
        // Far Lands ÇOĞUNLUKLA KATI, içi delik deşik dev duvarlardır — tersi değil.
        // Farther: bir eksende çok daha düşük frekans → gerilmiş, akışkan arazi.
        final double fx = farther ? 0.010 : 0.045;
        final double fz = farther ? 0.055 : 0.045;
        final int altY = 8, ustY = 190;
        for (int lx = 0; lx < 16; lx++) {
            int wx = bx + lx;
            for (int lz = 0; lz < 16; lz++) {
                int wz = bz + lz;
                double duvar = gurultu2(wx * fx, wz * fz);   // 0..1 — duvar yoğunluğu
                if (duvar < 0.34) continue;                  // boşluk (azınlık)
                for (int y = altY; y <= ustY; y++) {
                    // Süngerimsi dikey delikler
                    double delik = gurultu3(wx * 0.07, y * 0.06, wz * 0.07);
                    if (delik < 0.30) continue;              // delik (tünel)
                    chunk.setBlockState(m.set(wx, y, wz), farLandsBlok(y));
                }
            }
        }
    }

    private static BlockState farLandsBlok(int y) {
        if (y < 40) return Blocks.DEEPSLATE.getDefaultState();
        if (y < 120) return Blocks.STONE.getDefaultState();
        return Blocks.COBBLESTONE.getDefaultState();
    }

    // ─────────────────────────────────────────────────────────
    // CORNER FAR LANDS — dümdüz, üst üste yığılı yatay katmanlar
    // ─────────────────────────────────────────────────────────
    private static void cornerLands(WorldChunk chunk, BlockPos.Mutable m, int bx, int bz) {
        // Dümdüz, üst üste yığılı yatay platolar: her 8 Y'de bir "kat", katın dolu bölgesi
        // yumuşak 2B gürültüden. Biome geçişi çökünce oluşan basamaklı platoların taklidi.
        for (int lx = 0; lx < 16; lx++) {
            int wx = bx + lx;
            for (int lz = 0; lz < 16; lz++) {
                int wz = bz + lz;
                double plato = gurultu2(wx * 0.03, wz * 0.03);   // 0..1
                for (int y = 8; y <= 170; y++) {
                    if (Math.floorMod(y, 8) >= 5) continue;      // kat arası boşluk (basamak)
                    // Kat yükseldikçe plato küçülür — yığın gibi
                    double esik = 0.32 + (y / 170.0) * 0.30;
                    if (plato > esik) {
                        chunk.setBlockState(m.set(wx, y, wz), farLandsBlok(y));
                    }
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────
    // Değer-gürültüsü (deterministik hash tabanlı, dış kütüphane yok)
    // ─────────────────────────────────────────────────────────
    private static int hash(int x, int y, int z) {
        // TUZLAMA (kritik): tuzsuz hash(0,0,0)=0 idi → orijin kalıcı ölü bölge oldu,
        // eşik-tabanlı dünyalar (far_lands/corner_lands) spawn'da BOŞ üretildi (ÖLÇÜLDÜ).
        int h = (x + 0x9E3779B9) * 374761393
              + (y + 0x85EBCA6B) * 668265263
              + (z + 0xC2B2AE35) * 2147483647;
        h ^= 0x27D4EB2F;
        h = (h ^ (h >>> 15)) * 0x2C1B3C6D;
        h = (h ^ (h >>> 13)) * 0x297A2D39;
        return h ^ (h >>> 16);
    }

    private static double deger(int x, int y, int z) {
        return (hash(x, y, z) & 0xFFFF) / 65535.0;
    }

    /** 2B pürüzsüz değer-gürültüsü, 0..1. */
    private static double gurultu2(double x, double z) {
        int x0 = (int) Math.floor(x), z0 = (int) Math.floor(z);
        double tx = x - x0, tz = z - z0;
        double a = deger(x0, 0, z0), b = deger(x0 + 1, 0, z0);
        double c = deger(x0, 0, z0 + 1), d = deger(x0 + 1, 0, z0 + 1);
        double sx = tx * tx * (3 - 2 * tx), sz = tz * tz * (3 - 2 * tz);
        return (a + (b - a) * sx) * (1 - sz) + (c + (d - c) * sx) * sz;
    }

    /** 3B pürüzsüz değer-gürültüsü, 0..1. */
    private static double gurultu3(double x, double y, double z) {
        int y0 = (int) Math.floor(y);
        double ty = y - y0;
        double sy = ty * ty * (3 - 2 * ty);
        double a = gurultu2(x, z) * 0.5 + deger((int) Math.floor(x), y0, (int) Math.floor(z)) * 0.5;
        double b = gurultu2(x, z) * 0.5 + deger((int) Math.floor(x), y0 + 1, (int) Math.floor(z)) * 0.5;
        return a + (b - a) * sy;
    }
}
