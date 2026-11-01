package com.photonpulse.candy.ruya;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.audio.CandySounds;
import com.photonpulse.candy.audio.SesGonder;
import com.photonpulse.candy.basarim.CandyBasarimlar;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.core.TruePersonality;
import com.photonpulse.candy.entity.CandyDoppelEntity;
import com.photonpulse.candy.entity.CandyEntities;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
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
            if (!(entity instanceof ServerPlayer player)) return;
            TruePersonality tp = PersonalityManager.get(player);
            if (!tp.kurulumTamam) return;

            long simdi = player.level().getGameTime();
            tp.sonUykuTick = simdi;
            tp.uykusuzGunSayisi = 0; // uykuya girdiği için sıfırla

            if (RNG.nextDouble() > KABUS_IHTIMAL) return;

            ServerLevel dunya = (ServerLevel) player.level();

            // 1) Panic fear ses — sadece bu oyuncuya
            SesGonder.tek(player,
                CandySounds.PANIC_FEAR,
                SoundSource.AMBIENT,
                1.0f, 1.0f
            );

            // 2) Baseline DARKNESS
            player.addEffect(new MobEffectInstance(
                MobEffects.DARKNESS, 80, 0, true, false, false));

            // 3) Kalp atışı ambient (mevcut baseline)
            dunya.playSound(null, player.blockPosition(),
                SoundEvents.WARDEN_HEARTBEAT, SoundSource.AMBIENT,
                0.6f, 0.7f);

            // 4) Yatak bekçisi spawn — AÇIK konum ara (duvara yatılmışsa Candy duvara
            //    gömülmesin, görünür olsun)
            BlockPos yatakPos = sleepPos;
            BlockState yatakState = dunya.getBlockState(yatakPos);
            Direction basYonu = (yatakState.is(BlockTags.BEDS)
                    && yatakState.hasProperty(BedBlock.FACING))
                ? yatakState.getValue(BedBlock.FACING)
                : player.getDirection();
            BlockPos bekciPos = guvenliBekciPos(dunya, yatakPos, basYonu, player);

            CandyDoppelEntity candy = CandyEntities.DOPPEL.create(dunya,
                net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
            if (candy != null) {
                candy.setPosRaw(bekciPos.getX() + 0.5, bekciPos.getY(), bekciPos.getZ() + 0.5);
                candy.lookAt(player, 180f, 180f);
                candy.setSilent(true);
                candy.setInvulnerable(true);
                candy.setNoAi(true);
                candy.setNoGravity(true);
                candy.addTag("candy_yatak_bekci");
                dunya.addFreshEntity(candy);
                aktifBekciler.put(candy.getUUID(), dunya.getGameTime() + BEKCI_SURESI_TICK);
            }

            // 5) Chat + achievement
            player.displayClientMessage(
                Component.translatable("candy.yatak.mesaj")
                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC),
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
            long simdi = server.overworld().getGameTime();
            var iter = aktifBekciler.entrySet().iterator();
            while (iter.hasNext()) {
                var e = iter.next();
                if (simdi < e.getValue()) continue;

                // Bekçi 60 sn boyunca durdu → hala_burada achievement (yakınındaki player'a)
                for (ServerLevel world : server.getAllLevels()) {
                    var candy = world.getEntity(e.getKey());
                    if (candy == null) continue;
                    // 6 blok içinde player varsa hala_burada
                    for (ServerPlayer p : world.players()) {
                        if (p.distanceToSqr(candy) < 6 * 6) {
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
            if (server.getTickCount() % 200 != 0) return;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                ServerLevel world = (ServerLevel) player.level();
                var kutu = new net.minecraft.world.phys.AABB(player.blockPosition()).inflate(64);
                var bekciler = world.getEntitiesOfClass(CandyDoppelEntity.class, kutu,
                    e -> e.getTags().contains("candy_yatak_bekci"));
                for (var bekci : bekciler) {
                    if (aktifBekciler.containsKey(bekci.getUUID())) continue;
                    bekci.discard(); // takip edilmeyen artık
                    Candy.LOG.debug("[Candy] YatakBekcisi artığı temizlendi: {}", bekci.getUUID());
                }
            }
        });

        // Uykusuz gün sayacı — server günü değişiminde artır (basit)
        // Ayrı tick handler ile — her Minecraft günü sonu (24000 tick)
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 24000 != 0) return;
            long simdi = server.overworld().getGameTime();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
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
    private static BlockPos guvenliBekciPos(ServerLevel dunya, BlockPos yatakPos,
                                            Direction basYonu, ServerPlayer player) {
        java.util.List<BlockPos> adaylar = new java.util.ArrayList<>();
        // 1) Baş yönü (uyanınca oraya bakıyor olur)
        adaylar.add(yatakPos.relative(basYonu, 1));
        // 2) Ayak yönü (ters)
        adaylar.add(yatakPos.relative(basYonu.getOpposite(), 1));
        // 3) Yanlar
        adaylar.add(yatakPos.relative(basYonu.getClockWise(), 1));
        adaylar.add(yatakPos.relative(basYonu.getCounterClockWise(), 1));
        // 4) Baş yönü ama 1 blok yukarıda (duvarın üstünden görünür)
        adaylar.add(yatakPos.relative(basYonu, 1).above());

        for (BlockPos aday : adaylar) {
            if (acikMi(dunya, aday)) return aday;
        }
        // 5) Son çare: yatağın 1 blok üstü (her zaman görünür)
        return yatakPos.above();
    }

    /** Bir konumda 2 bloklık entity sığar mı — burası ve üstü boş/geçilebilir mi? */
    private static boolean acikMi(ServerLevel dunya, BlockPos pos) {
        return !dunya.getBlockState(pos).isRedstoneConductor(dunya, pos)
            && !dunya.getBlockState(pos.above()).isRedstoneConductor(dunya, pos.above());
    }

    public static void sunucuKapan() {
        aktifBekciler.clear();
    }
}
