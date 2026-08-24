package com.photonpulse.candy.dimension;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.basarim.CandyBasarimlar;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.core.TruePersonality;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

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
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(entity instanceof ServerPlayerEntity player)) return;
            TruePersonality tp = PersonalityManager.get(player);
            if (!tp.kurulumTamam) return;

            ServerWorld world = (ServerWorld) player.getWorld();

            // 1) Anti-Mirror'da ölüm → gerçek dünyaya dönüş bekle + kick (deferred)
            if (world.getRegistryKey().equals(CandyDimensions.ANTI_MIRROR)) {
                tp.gercekDunyaDonusBekliyor = true;
                MinecraftServer srv = player.getServer();
                if (srv != null) {
                    // AFTER_DEATH event içinde anında disconnect riskli — bir sonraki tick'e ertele
                    srv.execute(() -> {
                        if (player.networkHandler != null) {
                            player.networkHandler.disconnect(
                                Text.translatable("candy.mirror.anti_kick")
                                    .formatted(Formatting.DARK_RED));
                        }
                    });
                }
                Candy.LOG.info("[Candy] Anti-Mirror ölüm → kick + dönüş bekliyor: {}",
                    player.getName().getString());
                return;
            }

            // 2) Mirror'da ölüm → Anti-Mirror'a
            if (world.getRegistryKey().equals(CandyDimensions.MIRROR)) {
                tp.mirrordaOldu = true;
                Candy.LOG.info("[Candy] Mirror ölüm → Anti-Mirror bekletiyor: {}",
                    player.getName().getString());
                return;
            }

            // 3) Overworld'de Candy/klon tarafından öldürüldü → %25 Mirror
            var attacker = source.getAttacker();
            boolean candyOldurdu = attacker != null
                && attacker.getCommandTags().contains(KLON_ETIKETI);
            if (candyOldurdu && RNG.nextDouble() < MIRROR_GIRIS_IHTIMAL) {
                tp.mirrorGirisBekliyor = true;
                // Gerçek dünya dönüş noktası — öldüğü yer
                tp.sonMirrorGiris = player.getBlockPos().asLong();
                Candy.LOG.info("[Candy] Overworld candy-kill → Mirror bekletiyor: {}",
                    player.getName().getString());
            }
        });

        // Respawn sonrası teleport
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (alive) return;
            TruePersonality tp = PersonalityManager.get(newPlayer);
            MinecraftServer server = newPlayer.getServer();
            if (server == null) return;

            // A) Gerçek dünyaya dönüş (Anti-Mirror ölümü sonrası)
            if (tp.gercekDunyaDonusBekliyor) {
                tp.gercekDunyaDonusBekliyor = false;
                ServerWorld overworld = server.getOverworld();
                BlockPos donus = BlockPos.fromLong(tp.sonMirrorGiris);
                int x = donus.getX();
                int z = donus.getZ();
                int y = overworld.getTopY(
                    net.minecraft.world.Heightmap.Type.MOTION_BLOCKING, x, z);
                newPlayer.teleport(overworld, x + 0.5, y, z + 0.5,
                    Collections.emptySet(), newPlayer.getYaw(), 0f, false);
                newPlayer.setHealth(newPlayer.getMaxHealth());
                Candy.LOG.info("[Candy] Anti-Mirror → gerçek dünya dönüş: {} @({}, {}, {})",
                    newPlayer.getName().getString(), x, y, z);
                return;
            }

            // B) Mirror'a git (overworld candy-kill sonrası)
            if (tp.mirrorGirisBekliyor) {
                tp.mirrorGirisBekliyor = false;
                ServerWorld mirror = server.getWorld(CandyDimensions.MIRROR);
                if (mirror != null) {
                    mirrorTasi(newPlayer, mirror);
                    newPlayer.sendMessage(
                        Text.translatable("candy.mirror.giris")
                            .formatted(Formatting.DARK_PURPLE, Formatting.ITALIC),
                        false);
                    CandyBasarimlar.tetikle(newPlayer, "aynada_gordum");
                }
                return;
            }

            // C) Anti-Mirror'a git (Mirror'da ölüm)
            if (tp.mirrordaOldu) {
                tp.mirrordaOldu = false;
                ServerWorld antiMirror = server.getWorld(CandyDimensions.ANTI_MIRROR);
                if (antiMirror != null) {
                    antiMirrorTasi(newPlayer, antiMirror);
                    tp.antiMirrorGirisTick = server.getOverworld().getTime();
                    newPlayer.sendMessage(
                        Text.translatable("candy.mirror.anti_giris")
                            .formatted(Formatting.DARK_RED, Formatting.ITALIC),
                        false);
                    CandyBasarimlar.tetikle(newPlayer, "ters_yuz");
                }
            }
        });

        Candy.LOG.info("[Candy] MirrorGirisi aktif (v3.0)");
    }

    private static void mirrorTasi(ServerPlayerEntity player, ServerWorld mirror) {
        // Overworld koordinatlarını kullan (X, Z aynı, Y yüzey — overworld gen ile hep katı zemin)
        int x = player.getBlockX();
        int z = player.getBlockZ();
        int y = mirror.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING, x, z);
        if (y <= mirror.getBottomY() + 1) y = 96; // chunk daha üretilmediyse güvenli fallback
        player.teleport(mirror, x + 0.5, y, z + 0.5,
            Collections.emptySet(), player.getYaw(), 0f, false);
        player.setHealth(player.getMaxHealth());
        Candy.LOG.info("[Candy] Mirror teleport: {} → ({}, {}, {})",
            player.getName().getString(), x, y, z);
    }

    private static void antiMirrorTasi(ServerPlayerEntity player, ServerWorld antiMirror) {
        int x = player.getBlockX();
        int z = player.getBlockZ();
        int y = 128; // Anti-Mirror sabit yükseklik

        // Anti-Mirror end-gen'de arbitrer koordinatlar boşluk (void) olabilir.
        // Player'ın altına güvenli platform kur — void'e düşmesin, lottery ile ölsün.
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                antiMirror.setBlockState(new BlockPos(x + dx, y - 1, z + dz),
                    Blocks.BLACKSTONE.getDefaultState(), 3);
            }
        }

        player.teleport(antiMirror, x + 0.5, y, z + 0.5,
            Collections.emptySet(), player.getYaw(), 0f, false);
        player.setHealth(player.getMaxHealth());
        Candy.LOG.info("[Candy] Anti-Mirror teleport: {} → ({}, {}, {}) + platform",
            player.getName().getString(), x, y, z);
    }
}
