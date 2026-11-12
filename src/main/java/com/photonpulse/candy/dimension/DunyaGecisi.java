package com.photonpulse.candy.dimension;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.core.TruePersonality;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import java.util.Collections;

/**
 * DunyaGecisi — kurul dünyalarına ışınlanma ve geri dönüş.
 *
 * <p>{@link DunyaKurulu} "seni oraya at" dediğinde {@link #gir} çağrılır; oyuncu bir süre
 * ({@link DunyaKurulu#DUNYA_SURE}) orada kalır ve {@link #don} ile overworld'e döner.
 * Void tabanlı dünyalarda oyuncu boşluğa düşmesin diye altına küçük bir güvenli platform
 * damgalanır (chunk şekillendirmesi arka planda tamamlanana kadar).</p>
 */
public final class DunyaGecisi {

    private DunyaGecisi() {}

    /** Oyuncuyu seçilen kurul dünyasına al. */
    public static void gir(MinecraftServer server, ServerPlayer player,
                           TruePersonality tp, KurulDunya dunya) {
        ServerLevel hedef = server.getLevel(dunya.anahtar);
        if (hedef == null) {
            Candy.LOG.warn("[Candy] kurul dünyası yüklenmemiş: {}", dunya.id);
            return;
        }
        // Dönüş koordinatı: şu anki overworld konumu
        ServerLevel ow = server.overworld();
        BlockPos donus = player.level() == ow
            ? player.blockPosition()
            : new BlockPos((int) player.getX(), 80, (int) player.getZ());
        tp.kurulDonusPoz = donus.asLong();

        int x = player.getBlockX();
        int z = player.getBlockZ();
        int y = spawnYuksekligi(dunya);

        // Güvenli platform (void'e düşmesin) — dünyanın kendi zemin bloğuyla
        BlockState zemin = platformBlok(dunya);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                hedef.setBlock(new BlockPos(x + dx, y - 1, z + dz), zemin, 0x2);
                // Üstünü aç (barrier dünyada bile durabileceği bir cep)
                hedef.setBlock(new BlockPos(x + dx, y, z + dz), Blocks.AIR.defaultBlockState(), 0x2);
                hedef.setBlock(new BlockPos(x + dx, y + 1, z + dz), Blocks.AIR.defaultBlockState(), 0x2);
            }
        }

        player.teleportTo(hedef, x + 0.5, y, z + 0.5, Collections.emptySet(),
            player.getYRot(), player.getXRot(), false);
        player.setHealth(player.getMaxHealth());

        tp.kurulDunyaId = dunya.name();
        tp.kurulSonDunya = dunya.name();
        tp.kurulDunyaGirisTick = ow.getGameTime();
        Candy.LOG.info("[Candy] KURUL DÜNYA[{}] → {} @({}, {}, {})",
            player.getName().getString(), dunya.id, x, y, z);
    }

    /** Oyuncuyu overworld'e geri döndür. */
    public static void don(MinecraftServer server, ServerPlayer player, TruePersonality tp) {
        ServerLevel ow = server.overworld();
        BlockPos d = BlockPos.of(tp.kurulDonusPoz);
        int x = d.getX(), z = d.getZ();
        ow.getChunk((x) >> 4, (z) >> 4); // chunk yüklü değilse getHeight minY döndürür (oyuncu -64'e ışınlanır)
        int y = ow.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        player.teleportTo(ow, x + 0.5, y, z + 0.5, Collections.emptySet(),
            player.getYRot(), player.getXRot(), false);
        player.setHealth(player.getMaxHealth());
        tp.kurulDunyaId = null;
        tp.kurulDunyaGirisTick = 0L;
        Candy.LOG.info("[Candy] KURUL DÜNYA[{}] → overworld dönüş @({}, {}, {})",
            player.getName().getString(), x, y, z);
    }

    /** Dünya başına güvenli spawn yüksekliği. */
    private static int spawnYuksekligi(KurulDunya d) {
        return switch (d.sekil) {
            case KORIDOR -> 65;   // zemin 63
            case ME      -> 65;   // düz yüzey ~64
            case KLON    -> 65;   // zemin 63
            case KOR     -> 100;  // barrier platform üstünde
            default      -> 96;   // void aileler: platform + üstü açık
        };
    }

    private static BlockState platformBlok(KurulDunya d) {
        return switch (d.sekil) {
            case KOR -> Blocks.BARRIER.defaultBlockState();
            case KORIDOR -> Blocks.SMOOTH_STONE.defaultBlockState();
            case ME -> Blocks.GRASS_BLOCK.defaultBlockState();
            case SKYGRID -> Blocks.STONE.defaultBlockState();
            default -> Blocks.COBBLESTONE.defaultBlockState();
        };
    }
}
