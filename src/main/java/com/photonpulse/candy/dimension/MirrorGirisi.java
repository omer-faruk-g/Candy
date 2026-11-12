package com.photonpulse.candy.dimension;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.basarim.CandyBasarimlar;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.core.TruePersonality;
import com.photonpulse.candy.platform.CanliOlaylari;
import com.photonpulse.candy.platform.OyuncuOlaylari;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import java.util.Collections;
import java.util.Random;

/**
 * MirrorGirisi (v3.0 Phase 2) — Mirror & Anti-Mirror teleport akışı.
 *
 * Overworld'de Candy/klon öldürürse: %25 → mirrorGirisBekliyor → respawn Mirror'a taşı
 * Mirror'da ölürse: mirrordaOldu → respawn Anti-Mirror'a taşı
 * Anti-Mirror'da ölürse: gercekDunyaDonusBekliyor + kick → respawn gerçek dünyaya dön
 *
 * Gerçek dünya dönüş koordinatı tp.sonMirrorGiris'te (asLong) kayıtlı.
 */
public class MirrorGirisi {

    private static final Random RNG = new Random();
    // Debug'da klon-kill sonrası %100 Mirror (komutsuz boss testi için)
    private static final double MIRROR_GIRIS_IHTIMAL =
        Boolean.getBoolean("candy.debug") ? 1.0 : 0.25;
    private static final String KLON_ETIKETI = "candy_clone";

    public static void register() {
        CanliOlaylari.AFTER_DEATH.register((entity, source) -> {
            if (!(entity instanceof ServerPlayer player)) return;
            TruePersonality tp = PersonalityManager.get(player);
            if (!tp.kurulumTamam) return;

            ServerLevel world = (ServerLevel) player.level();

            // 1) Anti-Mirror'da ölüm → gerçek dünyaya dönüş bekle + kick (deferred)
            if (world.dimension().equals(CandyDimensions.ANTI_MIRROR)) {
                tp.gercekDunyaDonusBekliyor = true;
                MinecraftServer srv = player.level().getServer();
                if (srv != null) {
                    // AFTER_DEATH event içinde anında disconnect riskli — bir sonraki tick'e ertele
                    srv.execute(() -> {
                        if (player.connection != null) {
                            player.connection.disconnect(
                                Component.translatable("candy.mirror.anti_kick")
                                    .withStyle(ChatFormatting.DARK_RED));
                        }
                    });
                }
                Candy.LOG.info("[Candy] Anti-Mirror ölüm → kick + dönüş bekliyor: {}",
                    player.getName().getString());
                return;
            }

            // 2) Mirror'da ölüm → Anti-Mirror'a
            if (world.dimension().equals(CandyDimensions.MIRROR)) {
                tp.mirrordaOldu = true;
                Candy.LOG.info("[Candy] Mirror ölüm → Anti-Mirror bekletiyor: {}",
                    player.getName().getString());
                return;
            }

            // 3) Overworld'de Candy/klon tarafından öldürüldü → %25 Mirror
            var attacker = source.getEntity();
            boolean candyOldurdu = attacker != null
                && attacker.getTags().contains(KLON_ETIKETI);
            if (candyOldurdu && RNG.nextDouble() < MIRROR_GIRIS_IHTIMAL) {
                tp.mirrorGirisBekliyor = true;
                // Gerçek dünya dönüş noktası — öldüğü yer
                tp.sonMirrorGiris = player.blockPosition().asLong();
                Candy.LOG.info("[Candy] Overworld candy-kill → Mirror bekletiyor: {}",
                    player.getName().getString());
            }
        });

        // Respawn sonrası teleport
        OyuncuOlaylari.AFTER_RESPAWN.register((newPlayer, alive) -> {
            if (alive) return;
            TruePersonality tp = PersonalityManager.get(newPlayer);
            MinecraftServer server = newPlayer.level().getServer();
            if (server == null) return;

            // A) Gerçek dünyaya dönüş (Anti-Mirror ölümü sonrası)
            if (tp.gercekDunyaDonusBekliyor) {
                tp.gercekDunyaDonusBekliyor = false;
                ServerLevel overworld = server.overworld();
                BlockPos donus = BlockPos.of(tp.sonMirrorGiris);
                int x = donus.getX();
                int z = donus.getZ();
                overworld.getChunk((x) >> 4, (z) >> 4); // chunk yüklü değilse getHeight minY döndürür (oyuncu -64'e ışınlanır)
                int y = overworld.getHeight(
                    net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z);
                newPlayer.teleportTo(overworld, x + 0.5, y, z + 0.5,
                    Collections.emptySet(), newPlayer.getYRot(), 0f, false);
                newPlayer.setHealth(newPlayer.getMaxHealth());
                Candy.LOG.info("[Candy] Anti-Mirror → gerçek dünya dönüş: {} @({}, {}, {})",
                    newPlayer.getName().getString(), x, y, z);
                return;
            }

            // B) Mirror'a git (overworld candy-kill sonrası)
            if (tp.mirrorGirisBekliyor) {
                tp.mirrorGirisBekliyor = false;
                ServerLevel mirror = server.getLevel(CandyDimensions.MIRROR);
                if (mirror != null) {
                    mirrorTasi(newPlayer, mirror);
                    newPlayer.displayClientMessage(
                        Component.translatable("candy.mirror.giris")
                            .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC),
                        false);
                    CandyBasarimlar.tetikle(newPlayer, "aynada_gordum");
                }
                return;
            }

            // C) Anti-Mirror'a git (Mirror'da ölüm)
            if (tp.mirrordaOldu) {
                tp.mirrordaOldu = false;
                ServerLevel antiMirror = server.getLevel(CandyDimensions.ANTI_MIRROR);
                if (antiMirror != null) {
                    antiMirrorTasi(newPlayer, antiMirror);
                    tp.antiMirrorGirisTick = server.overworld().getGameTime();
                    newPlayer.displayClientMessage(
                        Component.translatable("candy.mirror.anti_giris")
                            .withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC),
                        false);
                    CandyBasarimlar.tetikle(newPlayer, "ters_yuz");
                }
            }
        });

        Candy.LOG.info("[Candy] MirrorGirisi aktif (v3.0)");
    }

    private static void mirrorTasi(ServerPlayer player, ServerLevel mirror) {
        // Overworld koordinatlarını kullan (X, Z aynı, Y yüzey — overworld gen ile hep katı zemin)
        int x = player.getBlockX();
        int z = player.getBlockZ();
        int y = mirror.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z);
        if (y <= mirror.getMinY() + 1) y = 96; // chunk daha üretilmediyse güvenli fallback
        player.teleportTo(mirror, x + 0.5, y, z + 0.5,
            Collections.emptySet(), player.getYRot(), 0f, false);
        player.setHealth(player.getMaxHealth());
        Candy.LOG.info("[Candy] Mirror teleport: {} → ({}, {}, {})",
            player.getName().getString(), x, y, z);
    }

    private static void antiMirrorTasi(ServerPlayer player, ServerLevel antiMirror) {
        int x = player.getBlockX();
        int z = player.getBlockZ();
        int y = 128; // Anti-Mirror sabit yükseklik

        // Anti-Mirror end-gen'de arbitrer koordinatlar boşluk (void) olabilir.
        // Player'ın altına güvenli platform kur — void'e düşmesin, lottery ile ölsün.
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                antiMirror.setBlock(new BlockPos(x + dx, y - 1, z + dz),
                    Blocks.BLACKSTONE.defaultBlockState(), 3);
            }
        }

        player.teleportTo(antiMirror, x + 0.5, y, z + 0.5,
            Collections.emptySet(), player.getYRot(), 0f, false);
        player.setHealth(player.getMaxHealth());
        Candy.LOG.info("[Candy] Anti-Mirror teleport: {} → ({}, {}, {}) + platform",
            player.getName().getString(), x, y, z);
    }
}
