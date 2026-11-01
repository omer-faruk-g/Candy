package com.photonpulse.candy.dimension;

import com.photonpulse.candy.Candy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * DunyaKlon — "Klon" dünyasının tek şablonu: ne yaparsan HER chunk'a yansır.
 *
 * <h3>FİKİR</h3>
 * Tüm chunk'lar tek bir 16×16 şablonun görüntüsüdür. Oyuncu bir yere blok koyduğunda /
 * kırdığında, o değişiklik (yerel x,z ve y'ye göre) şablona işlenir; şablon da bütün
 * chunk'larda aynı olduğu için değişiklik <b>her yerde</b> belirir. Sen tek bir noktadasın
 * ama teorik olarak dünyanın her yerinde aynı şeyi yapmışsın — bireyselliğin yok oluşu.
 *
 * <h3>ŞABLON</h3>
 * Anahtar = (yerelX 0-15, yerelZ 0-15, y). Varsayılan zemin: y=62 taş, y=63 çim, gerisi
 * hava — oyuncu boşluğa düşmesin. Oyuncu düzenlemeleri {@code override} haritasında tutulur;
 * boyut kalıcılığına {@link com.photonpulse.candy.beyin.BeyinDeposu} gibi ayrı bir dosyayla
 * DEĞİL, dünya klasörüyle gider (henüz diske yazılmıyor — oturum içi; kalıcılık Aşama 2).
 */
public final class DunyaKlon {

    private DunyaKlon() {}

    private static final int ZEMIN_TAS = 62;
    private static final int ZEMIN_CIM = 63;
    /** Değişiklikleri yayarken oyuncu çevresinde kaç chunk yenilensin. */
    private static final int YAYILIM_YARICAP = 8;

    /** Oyuncu düzenlemeleri: pack(yerelX,yerelZ,y) → blok. */
    private static final Map<Long, BlockState> override = new ConcurrentHashMap<>();

    /** Yayma sırasında true — WorldSetBlockMixin'in kendi yaydığımız değişikliği tekrar
     *  yakalayıp sonsuz özyinelemeye girmesini önler. */
    private static final ThreadLocal<Boolean> yayiliyor = ThreadLocal.withInitial(() -> false);

    /** WorldSetBlockMixin bunu okur: şu an biz mi yayıyoruz? */
    public static boolean yayiliyorMu() {
        return Boolean.TRUE.equals(yayiliyor.get());
    }

    private static long anahtar(int yerelX, int yerelZ, int y) {
        return ((long) (yerelX & 15)) | ((long) (yerelZ & 15) << 4) | ((long) (y + 128) << 8);
    }

    /** Chunk üretiminde şablonu bu chunk'a damgalar (zemin + tüm düzenlemeler). */
    public static void damgala(LevelChunk chunk, BlockPos.MutableBlockPos m, int bx, int bz) {
        // Zemin
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                chunk.setBlockState(m.set(bx + lx, ZEMIN_TAS, bz + lz), Blocks.STONE.defaultBlockState());
                chunk.setBlockState(m.set(bx + lx, ZEMIN_CIM, bz + lz), Blocks.GRASS_BLOCK.defaultBlockState());
            }
        }
        // Düzenlemeler
        for (Map.Entry<Long, BlockState> e : override.entrySet()) {
            long k = e.getKey();
            int lx = (int) (k & 15);
            int lz = (int) ((k >> 4) & 15);
            int y = (int) ((k >> 8) - 128);
            chunk.setBlockState(m.set(bx + lx, y, bz + lz), e.getValue());
        }
    }

    /**
     * Oyuncu Klon dünyasında bir bloğu değiştirdi (koydu/kırdı/yaktı).
     * Şablonu güncelle ve oyuncuların çevresindeki tüm chunk'lara yay.
     */
    public static void guncelle(ServerLevel world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null) return;
        int lx = pos.getX() & 15;
        int lz = pos.getZ() & 15;
        int y = pos.getY();
        if (y < -128 || y > 1024) return;

        long k = anahtar(lx, lz, y);
        boolean varsayilanaDondu =
            (y == ZEMIN_TAS && state.is(Blocks.STONE)) ||
            (y == ZEMIN_CIM && state.is(Blocks.GRASS_BLOCK)) ||
            (y != ZEMIN_TAS && y != ZEMIN_CIM && state.isAir());
        if (varsayilanaDondu) override.remove(k);
        else override.put(k, state);

        yay(world, lx, lz, y, state);
    }

    /** Değişikliği, Klon dünyasındaki oyuncuların çevresindeki tüm chunk'lara uygula. */
    private static void yay(ServerLevel world, int lx, int lz, int y, BlockState state) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        yayiliyor.set(true);
        try {
        for (ServerPlayer p : world.players()) {
            int pcx = p.getBlockX() >> 4;
            int pcz = p.getBlockZ() >> 4;
            for (int cx = pcx - YAYILIM_YARICAP; cx <= pcx + YAYILIM_YARICAP; cx++) {
                for (int cz = pcz - YAYILIM_YARICAP; cz <= pcz + YAYILIM_YARICAP; cz++) {
                    m.set((cx << 4) + lx, y, (cz << 4) + lz);
                    // Kendi tetiklediğimiz değişiklik döngüye girmesin diye 0x4 (no-neighbor-update)
                    // + sonradan capture'da re-entrancy guard var.
                    if (!world.getBlockState(m).equals(state)) {
                        world.setBlock(m, state, 0x2 | 0x10);
                    }
                }
            }
        }
        } finally {
            yayiliyor.set(false);
        }
    }

    /** Oturum kapanınca/dünya boşalınca temizlik (kalıcılık henüz yok). */
    public static void sifirla() {
        override.clear();
        Candy.LOG.debug("[Candy] Klon şablonu sıfırlandı");
    }
}
