package com.photonpulse.candy.ruya;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.audio.CandySounds;
import com.photonpulse.candy.basarim.CandyBasarimlar;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.core.TruePersonality;
import com.photonpulse.candy.entity.CandyDoppelEntity;
import com.photonpulse.candy.entity.CandyEntities;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * RuyaSistemi v2.0 (v3.0 Phase 3.13) — Yatak Bekçisi.
 *
 * Player uyandığında %40 kabus. Kabus tetiklenirse:
 *   - panic_fear ses (sadece player'a)
 *   - CandyDoppel bed baş ucuna spawn (invulnerable, no AI, silent)
 *   - Chat: "Uyumanı seyrettim."
 *   - DARKNESS 4 sn (baseline)
 *   - Kotuluk +15, Ozguven +5 (baseline)
 *   - Candy 60 sn boyunca durur, sonra despawn
 */
public class RuyaSistemi {

    private static final double KABUS_IHTIMAL = 0.40;
    private static final long BEKCI_SURESI_TICK = 1200L; // 60 sn
    private static final Random RNG = new Random();

    // Aktif yatak bekçileri: candy UUID → despawn tick
    private static final Map<UUID, Long> aktifBekciler = new HashMap<>();

    public static void register() {
        EntitySleepEvents.STOP_SLEEPING.register((entity, sleepPos) -> {
            if (!(entity instanceof ServerPlayerEntity player)) return;
            TruePersonality tp = PersonalityManager.get(player);
            if (!tp.kurulumTamam) return;

            long simdi = player.getWorld().getTime();
            tp.sonUykuTick = simdi;
            tp.uykusuzGunSayisi = 0; // uykuya girdiği için sıfırla

            if (RNG.nextDouble() > KABUS_IHTIMAL) return;

            ServerWorld dunya = (ServerWorld) player.getWorld();

            // 1) Panic fear ses — sadece bu oyuncuya
            player.playSoundToPlayer(
                CandySounds.PANIC_FEAR,
                SoundCategory.AMBIENT,
                1.0f, 1.0f
            );

            // 2) Baseline DARKNESS
            player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.DARKNESS, 80, 0, true, false, false));

            // 3) Kalp atışı ambient (mevcut baseline)
            dunya.playSound(null, player.getBlockPos(),
                SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.AMBIENT,
                0.6f, 0.7f);

            // 4) Yatak bekçisi spawn — AÇIK konum ara (duvara yatılmışsa Candy duvara
            //    gömülmesin, görünür olsun)
            BlockPos yatakPos = sleepPos;
            BlockState yatakState = dunya.getBlockState(yatakPos);
            Direction basYonu = (yatakState.isIn(BlockTags.BEDS)
                    && yatakState.contains(BedBlock.FACING))
                ? yatakState.get(BedBlock.FACING)
                : player.getHorizontalFacing();
            BlockPos bekciPos = guvenliBekciPos(dunya, yatakPos, basYonu, player);

            CandyDoppelEntity candy = CandyEntities.DOPPEL.create(dunya,
                net.minecraft.entity.SpawnReason.MOB_SUMMONED);
            if (candy != null) {
                candy.setPos(bekciPos.getX() + 0.5, bekciPos.getY(), bekciPos.getZ() + 0.5);
                candy.lookAtEntity(player, 180f, 180f);
                candy.setSilent(true);
                candy.setInvulnerable(true);
                candy.setAiDisabled(true);
                candy.setNoGravity(true);
                candy.addCommandTag("candy_yatak_bekci");
                dunya.spawnEntity(candy);
                aktifBekciler.put(candy.getUuid(), dunya.getTime() + BEKCI_SURESI_TICK);
            }

            // 5) Chat + achievement
            player.sendMessage(
                Text.translatable("candy.yatak.mesaj")
                    .formatted(Formatting.DARK_RED, Formatting.ITALIC),
                false);

            tp.psikolojiKotulugu = Math.min(100, tp.psikolojiKotulugu + 15);
            tp.candyOzguven = Math.min(100, tp.candyOzguven + 5);

            // Kabus yaşandı — v1.4.3'ten beri var olan başarım (v2.0 yeniden yazımında düşmüştü)
            CandyBasarimlar.tetikle(player, "ruya_miydi");

            if (!tp.yatakBekcisiGorulduMu) {
                tp.yatakBekcisiGorulduMu = true;
                CandyBasarimlar.tetikle(player, "bas_ucu");
            }

            Candy.LOG.info("[Candy] YatakBekcisi[{}] spawn @{} (kotuluk +15, ozguven +5)",
                player.getName().getString(), bekciPos);
        });

        // Bekçilerin 60 sn dolunca despawn — hala_burada achievement kontrolü
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (aktifBekciler.isEmpty()) return;
            long simdi = server.getOverworld().getTime();
            var iter = aktifBekciler.entrySet().iterator();
            while (iter.hasNext()) {
                var e = iter.next();
                if (simdi < e.getValue()) continue;

                // Bekçi 60 sn boyunca durdu → hala_burada achievement (yakınındaki player'a)
                for (ServerWorld world : server.getWorlds()) {
                    var candy = world.getEntity(e.getKey());
                    if (candy == null) continue;
                    // 6 blok içinde player varsa hala_burada
                    for (ServerPlayerEntity p : world.getPlayers()) {
                        if (p.squaredDistanceTo(candy) < 6 * 6) {
                            CandyBasarimlar.tetikle(p, "hala_burada");
                        }
                    }
                    candy.discard();
                    Candy.LOG.debug("[Candy] YatakBekcisi despawn: {}", e.getKey());
                }
                iter.remove();
            }
        });

        // Artık temizliği — sunucu restart'ında aktifBekciler boşalır ama entity dünyada
        // kalır (invulnerable + AI'sız → sonsuza dek durur). Player yaklaşınca temizle.
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 200 != 0) return;
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                ServerWorld world = (ServerWorld) player.getWorld();
                var kutu = new net.minecraft.util.math.Box(player.getBlockPos()).expand(64);
                var bekciler = world.getEntitiesByClass(CandyDoppelEntity.class, kutu,
                    e -> e.getCommandTags().contains("candy_yatak_bekci"));
                for (var bekci : bekciler) {
                    if (aktifBekciler.containsKey(bekci.getUuid())) continue;
                    bekci.discard(); // takip edilmeyen artık
                    Candy.LOG.debug("[Candy] YatakBekcisi artığı temizlendi: {}", bekci.getUuid());
                }
            }
        });

        // Uykusuz gün sayacı — server günü değişiminde artır (basit)
        // Ayrı tick handler ile — her Minecraft günü sonu (24000 tick)
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 24000 != 0) return;
            long simdi = server.getOverworld().getTime();
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                TruePersonality tp = PersonalityManager.get(player);
                if (!tp.kurulumTamam) continue;
                // Son uykudan bir gün geçti → uykusuz
                if (simdi - tp.sonUykuTick >= 24000L) {
                    tp.uykusuzGunSayisi++;
                    if (tp.uykusuzGunSayisi >= 5) {
                        CandyBasarimlar.tetikle(player, "uykusuz");
                    }
                }
            }
        });

        Candy.LOG.info("[Candy] RuyaSistemi v2.0 aktif — yatak bekçisi (v3.0)");
    }

    /**
     * Yatağın etrafında Candy'nin görünür olacağı AÇIK bir konum bulur.
     * Öncelik: baş yönü → ayak yönü → yanlar → yatağın üstü → player'ın yeri.
     * Açık = o blokta ve bir üstünde katı/dolu blok yok (2 blokluk entity sığar).
     */
    private static BlockPos guvenliBekciPos(ServerWorld dunya, BlockPos yatakPos,
                                            Direction basYonu, ServerPlayerEntity player) {
        java.util.List<BlockPos> adaylar = new java.util.ArrayList<>();
        // 1) Baş yönü (uyanınca oraya bakıyor olur)
        adaylar.add(yatakPos.offset(basYonu, 1));
        // 2) Ayak yönü (ters)
        adaylar.add(yatakPos.offset(basYonu.getOpposite(), 1));
        // 3) Yanlar
        adaylar.add(yatakPos.offset(basYonu.rotateYClockwise(), 1));
        adaylar.add(yatakPos.offset(basYonu.rotateYCounterclockwise(), 1));
        // 4) Baş yönü ama 1 blok yukarıda (duvarın üstünden görünür)
        adaylar.add(yatakPos.offset(basYonu, 1).up());

        for (BlockPos aday : adaylar) {
            if (acikMi(dunya, aday)) return aday;
        }
        // 5) Son çare: yatağın 1 blok üstü (her zaman görünür)
        return yatakPos.up();
    }

    /** Bir konumda 2 bloklık entity sığar mı — burası ve üstü boş/geçilebilir mi? */
    private static boolean acikMi(ServerWorld dunya, BlockPos pos) {
        return !dunya.getBlockState(pos).isSolidBlock(dunya, pos)
            && !dunya.getBlockState(pos.up()).isSolidBlock(dunya, pos.up());
    }

    public static void sunucuKapan() {
        aktifBekciler.clear();
    }
}
