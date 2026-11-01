package com.photonpulse.candy.travma;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.basarim.CandyBasarimlar;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.core.TruePersonality;
import com.photonpulse.candy.entity.CandyDoppelEntity;
import com.photonpulse.candy.entity.CandyEntities;
import com.photonpulse.candy.platform.SunucuTick;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * TotemIptali (v3.0 Phase 3.11) — Candy totem'i işlevsizleştirir.
 *
 * Vanilla totem aktivasyonu (LivingEntityMixin) → baslat() çağrılır:
 *   1. Player 3 sn freeze (slowness 255 + blindness)
 *   2. CandyDoppel 4 blok karşıya spawn, invulnerable + no AI
 *   3. Chat: "Bu senin değil." / "Totem işe yaramaz." / "Tanrıcılık oynayamazsın."
 *   4. Envanterdeki tüm totemler silinir
 *   5. 3 sn sonra player kick
 *   6. Rejoin: totemBekliyor flag → 1 HP + hunger 0 (v3.1'de Mirror'a taşınır)
 */
public class TotemIptali {

    private static final Random RNG = new Random();
    private static final String[] MESAJLAR = {
        "candy.totem.mesaj.1",
        "candy.totem.mesaj.2",
        "candy.totem.mesaj.3"
    };

    /** Player UUID → kick tick zamanı */
    private static final Map<UUID, Long> kickKuyrugu = new HashMap<>();
    /** Player UUID → spawn edilen totem_zeus Candy'nin UUID'si (kick anında discard) */
    private static final Map<UUID, UUID> spawnlananCandy = new HashMap<>();

    public static void register() {
        SunucuTick.END_SERVER_TICK.register(server -> {
            if (kickKuyrugu.isEmpty()) return;
            long simdi = server.overworld().getGameTime();
            var iter = kickKuyrugu.entrySet().iterator();
            while (iter.hasNext()) {
                var e = iter.next();
                if (simdi < e.getValue()) continue;
                UUID playerId = e.getKey();

                // Spawn edilen Candy'yi discard et (leak engelle)
                UUID candyId = spawnlananCandy.remove(playerId);
                if (candyId != null) {
                    for (ServerLevel w : server.getAllLevels()) {
                        var c = w.getEntity(candyId);
                        if (c != null) { c.discard(); break; }
                    }
                }

                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player != null && player.connection != null) {
                    player.connection.disconnect(
                        Component.translatable("candy.totem.kick_mesaj")
                            .withStyle(ChatFormatting.DARK_RED));
                }
                iter.remove();
            }
        });

        // Rejoin sonrası — 1 HP + hunger 0
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            var player = handler.player;
            TruePersonality tp = PersonalityManager.get(player);
            if (!tp.totemBekliyor) return;
            tp.totemBekliyor = false;

            // 1 tick sonra (join tam oturunca) uygula
            server.execute(() -> {
                if (player.isRemoved()) return;
                player.setHealth(1.0f);
                player.getFoodData().setFoodLevel(0);
                player.displayClientMessage(
                    Component.translatable("candy.totem.rejoin_mesaj")
                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC),
                    false);
            });
        });

        Candy.LOG.info("[Candy] TotemIptali aktif (v3.0)");
    }

    public static void baslat(ServerPlayer player) {
        try {
            TruePersonality tp = PersonalityManager.get(player);
            if (!tp.kurulumTamam) return;
            // Zaten iptal ediliyorsa tekrar başlatma
            if (kickKuyrugu.containsKey(player.getUUID())) return;

            ServerLevel world = (ServerLevel) player.level();

            // 1) Freeze frame — 3 sn (60 tick)
            player.addEffect(new MobEffectInstance(
                MobEffects.SLOWNESS, 60, 255, false, false, false));
            player.addEffect(new MobEffectInstance(
                MobEffects.BLINDNESS, 60, 0, false, false, false));
            player.addEffect(new MobEffectInstance(
                MobEffects.WEAKNESS, 60, 255, false, false, false));

            // 2) Candy spawn — 4 blok karşıda
            double aci = Math.toRadians(player.getYRot());
            Vec3 hedef = player.position().add(-Math.sin(aci) * 4, 0, Math.cos(aci) * 4);
            CandyDoppelEntity candy = CandyEntities.DOPPEL.create(world,
                net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
            if (candy != null) {
                candy.setPosRaw(hedef.x, hedef.y, hedef.z);
                candy.lookAt(player, 180f, 180f);
                candy.setSilent(true);
                candy.setInvulnerable(true);
                candy.setNoAi(true);
                candy.setNoGravity(true);   // freeze sırasında yerinde kalsın
                candy.addTag("candy_totem_zeus");
                world.addFreshEntity(candy);
                spawnlananCandy.put(player.getUUID(), candy.getUUID());
            }

            // 3) Chat mesajı
            String key = MESAJLAR[RNG.nextInt(MESAJLAR.length)];
            world.getServer().getPlayerList().broadcastSystemMessage(
                Component.translatable(key)
                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
                false);

            // 4) Envanterdeki tüm totemleri sil
            int silinen = 0;
            var envanter = player.getInventory();
            for (int i = 0; i < envanter.getContainerSize(); i++) {
                ItemStack stack = envanter.getItem(i);
                if (stack.getItem() == Items.TOTEM_OF_UNDYING) {
                    envanter.setItem(i, ItemStack.EMPTY);
                    silinen += stack.getCount();
                }
            }

            // 5) Kick kuyruğu — 3 sn sonra
            long kickTick = world.getGameTime() + 60L;
            kickKuyrugu.put(player.getUUID(), kickTick);

            // 6) Rejoin flag
            tp.totemBekliyor = true;
            tp.iptalEdilenTotemSayisi++;
            tp.candyOzguven = Math.min(100, tp.candyOzguven + 30);

            // Achievements
            CandyBasarimlar.tetikle(player, "totem_hicligi");
            if (tp.iptalEdilenTotemSayisi >= 3) {
                CandyBasarimlar.tetikle(player, "inanilmaz_inatci");
            }

            Candy.LOG.info("[Candy] TotemIptali: {} — {} totem silindi, 3sn'de kick",
                player.getName().getString(), silinen);
        } catch (Throwable t) {
            Candy.LOG.warn("[Candy] TotemIptali hata: {}", t.getMessage());
        }
    }

    public static void sunucuKapan() {
        kickKuyrugu.clear();
        spawnlananCandy.clear();
    }
}
