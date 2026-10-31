package com.photonpulse.candy.rutin;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.basarim.CandyBasarimlar;
import com.photonpulse.candy.clone.CloneManager;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.core.TruePersonality;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

/**
 * RutinTekrari (v8.2) — Candy oyuncunun kendi rutinini ona geri oynatır.
 *
 * <h3>NEDEN BU ÖZELLİK VAR</h3>
 * Mod 11 beyinli bir Kurul, Brier kalibrasyonu ve 54 duyu girdisi taşıyor; tür lideri
 * korku modlarının hiçbirinde bu kadar makine yok. Ama onların tamamının satış noktası
 * <b>okunabilirlik</b>: oyuncu izlendiğini <i>anlayabiliyor</i>. Candy'de profil
 * çıkarımının oyuncuya dönen bir kanalı yoktu — makine çalışıyor, kimse görmüyordu.
 *
 * <p>Bu sınıf sıfır yeni veri toplar. {@code davranisImzalari} zaten aylardır chunk +
 * aktivite + saat dilimi tutuyor. Yapılan tek şey: en çok tekrarlanan imzayı bulup,
 * <b>o saat diliminde o chunk'a</b> bir klon göndermek. Oyuncu kendi alışkanlığının
 * tam üstünde Candy'yi bulur.</p>
 *
 * <h3>NEDEN TAKİPÇİ DEĞİL</h3>
 * Kovalayan yaratık (Cave Dweller tipi) bilinçli olarak eklenmedi: döngü "tehdit → kaç"a
 * çökerse 54 girdilik profil makinesi anlamsızlaşır. Buradaki klon oyuncuyu aramıyor —
 * oyuncunun kendi gideceği yerde bekliyor. Korku kovalamacadan değil, <i>bilinmekten</i>
 * geliyor.
 */
public final class RutinTekrari {

    private RutinTekrari() {}

    /** Aynı rutin iki kez üst üste oynatılmasın — 1 oyun günü (24.000 tick). */
    private static final long SOGUMA_TICK = 24_000L;
    /** Ring'de en az bu kadar tekrar olmadan rutin "alışkanlık" sayılmaz. */
    private static final int MIN_TEKRAR = 4;
    /** Oyuncu hatırlanan chunk'a bu kadar chunk yakınsa oynatma anlamlı. */
    private static final int MAKS_CHUNK_UZAKLIK = 6;

    /**
     * Rutini oynatmayı dener.
     *
     * @return true: klon hatırlanan konuma kondu
     */
    public static boolean oynat(ServerPlayerEntity player, TruePersonality tp) {
        try {
            if (player == null || tp == null) return false;
            ServerWorld world = (ServerWorld) player.getWorld();
            long simdi = world.getTime();
            if (tp.rutinSonTick > 0 && simdi - tp.rutinSonTick < SOGUMA_TICK) return false;

            long paket = RutinKaydi.enSik(tp, MIN_TEKRAR);
            if (!RutinKaydi.dolu(paket)) return false;

            // ZAMAN ŞARTI: rutin hangi saat diliminde oluştuysa o dilimde oynanır.
            // Bu şart olmadan "aynı yerde klon" sıradan bir spawn olurdu; dilim
            // tutunca oyuncu zamanlamanın da bilindiğini fark eder.
            int simdikiDilim = (int) ((world.getTimeOfDay() % 24000L) / 4000L);
            if (simdikiDilim != RutinKaydi.saatDilimi(paket)) return false;

            int cx = RutinKaydi.chunkX(paket);
            int cz = RutinKaydi.chunkZ(paket);
            // Çok uzaksa chunk yüklü değil — spawn sessizce kaybolur, boşa harcanmış olay.
            int oyuncuCx = player.getBlockX() >> 4;
            int oyuncuCz = player.getBlockZ() >> 4;
            if (Math.abs(cx - oyuncuCx) > MAKS_CHUNK_UZAKLIK
                || Math.abs(cz - oyuncuCz) > MAKS_CHUNK_UZAKLIK) return false;

            int x = (cx << 4) + 8;
            int z = (cz << 4) + 8;
            // Y: oyuncunun seviyesini dene (mağara rutini de var), olmazsa yüzey.
            int y = player.getBlockY();
            if (!world.isChunkLoaded(net.minecraft.util.math.ChunkPos.toLong(cx, cz))) return false;
            BlockPos hedef = new BlockPos(x, y, z);
            if (!CloneManager.zorlaKlonSpawn(player, tp, hedef)) {
                int yuzey = world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
                if (!CloneManager.zorlaKlonSpawn(player, tp, new BlockPos(x, yuzey, z))) {
                    return false;
                }
            }

            tp.rutinSonTick = simdi;
            tp.rutinTekrari++;
            // Oyuncu "beni biliyor" hissini yaşadı: özgüven artar, öngörülebilirlik
            // oyuncunun kendi suçu olduğu için ceza değil GİRDİ olarak kalıyor.
            tp.candyOzguven = Math.min(100, tp.candyOzguven + 6);
            tp.paranoyaSkoru = Math.min(100, tp.paranoyaSkoru + 4);
            CandyBasarimlar.tetikle(player, "ayna");
            Candy.LOG.info("[Candy] RutinTekrari[{}] chunk=({},{}) dilim={} aktivite={} tekrar={}",
                player.getName().getString(), cx, cz, RutinKaydi.saatDilimi(paket),
                RutinKaydi.aktivite(paket), RutinKaydi.enSikTekrar(tp));
            return true;
        } catch (Throwable t) {
            Candy.LOG.debug("[Candy] RutinTekrari hata: {}", t.getMessage());
            return false;
        }
    }
}
