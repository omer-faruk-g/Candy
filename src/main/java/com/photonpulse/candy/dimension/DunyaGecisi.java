package com.photonpulse.candy.dimension;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.core.TruePersonality;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

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
    public static void gir(MinecraftServer server, ServerPlayerEntity player,
                           TruePersonality tp, KurulDunya dunya) {
        ServerWorld hedef = server.getWorld(dunya.anahtar);
        if (hedef == null) {
            Candy.LOG.warn("[Candy] kurul dünyası yüklenmemiş: {}", dunya.id);
            return;
        }
        // Dönüş koordinatı: şu anki overworld konumu
        ServerWorld ow = server.getOverworld();
        BlockPos donus = player.getWorld() == ow
            ? player.getBlockPos()
            : new BlockPos((int) player.getX(), 80, (int) player.getZ());
        tp.kurulDonusPoz = donus.asLong();

        int x = player.getBlockX();
        int z = player.getBlockZ();
        int y = spawnYuksekligi(dunya);

        // Güvenli platform (void'e düşmesin) — dünyanın kendi zemin bloğuyla
        BlockState zemin = platformBlok(dunya);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                hedef.setBlockState(new BlockPos(x + dx, y - 1, z + dz), zemin, 0x2);
                // Üstünü aç (barrier dünyada bile durabileceği bir cep)
                hedef.setBlockState(new BlockPos(x + dx, y, z + dz), Blocks.AIR.getDefaultState(), 0x2);
                hedef.setBlockState(new BlockPos(x + dx, y + 1, z + dz), Blocks.AIR.getDefaultState(), 0x2);
            }
        }

        player.teleport(hedef, x + 0.5, y, z + 0.5, Collections.emptySet(),
            player.getYaw(), player.getPitch(), false);
        player.setHealth(player.getMaxHealth());

        tp.kurulDunyaId = dunya.name();
        tp.kurulSonDunya = dunya.name();
        tp.kurulDunyaGirisTick = ow.getTime();
        Candy.LOG.info("[Candy] KURUL DÜNYA[{}] → {} @({}, {}, {})",
            player.getName().getString(), dunya.id, x, y, z);
    }

    /** Oyuncuyu overworld'e geri döndür. */
    public static void don(MinecraftServer server, ServerPlayerEntity player, TruePersonality tp) {
        ServerWorld ow = server.getOverworld();
        BlockPos d = BlockPos.fromLong(tp.kurulDonusPoz);
        int x = d.getX(), z = d.getZ();
        int y = ow.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
        player.teleport(ow, x + 0.5, y, z + 0.5, Collections.emptySet(),
            player.getYaw(), player.getPitch(), false);
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
            case KOR -> Blocks.BARRIER.getDefaultState();
            case KORIDOR -> Blocks.SMOOTH_STONE.getDefaultState();
            case ME -> Blocks.GRASS_BLOCK.getDefaultState();
            case SKYGRID -> Blocks.STONE.getDefaultState();
            default -> Blocks.COBBLESTONE.getDefaultState();
        };
    }
}
