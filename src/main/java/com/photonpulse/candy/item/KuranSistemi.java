package com.photonpulse.candy.item;

import com.photonpulse.candy.Candy;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * KuranSistemi — Kuran-ı Kerim davranışı.
 *
 *   SAĞ TIK  → Ayetel Kürsi (Arapça, çeviri YOK) chat'e yazılır. Kitap AÇILMAZ.
 *   SOL TIK  → kitap tüketilir; önce ÇİLE (5 sn zehir II + körlük),
 *              çile bitince NİMET: "İmanın Gücü" — Kuvvet V, Hız I, Emme IV, Yenilenme III (45 sn).
 *
 * NOT: Çile süresi ekran mesajında belirtilmemişti; makul bir değer olarak 5 sn seçildi.
 */
public class KuranSistemi {

    private static final int CILE_TICK = 100;     // 5 sn zehir II + körlük (çile)
    private static final int NIMET_TICK = 900;    // 45 sn İmanın Gücü
    private static final int OKUMA_COOLDOWN = 100; // 5 sn — Ayetel Kürsi tekrar sınırı

    private static final Map<UUID, Integer> sonOkuma = new HashMap<>();
    private static final Map<UUID, Integer> bekleyenNimet = new HashMap<>();

    // Ayetel Kürsi (Bakara 2:255) — Arapça, çeviri YOK
    private static final String[] AYET = {
        "اللَّهُ لَا إِلَٰهَ إِلَّا هُوَ الْحَيُّ الْقَيُّومُ",
        "لَا تَأْخُذُهُ سِنَةٌ وَلَا نَوْمٌ",
        "لَهُ مَا فِي السَّمَاوَاتِ وَمَا فِي الْأَرْضِ",
        "مَنْ ذَا الَّذِي يَشْفَعُ عِنْدَهُ إِلَّا بِإِذْنِهِ",
        "يَعْلَمُ مَا بَيْنَ أَيْدِيهِمْ وَمَا خَلْفَهُمْ",
        "وَلَا يُحِيطُونَ بِشَيْءٍ مِنْ عِلْمِهِ إِلَّا بِمَا شَاءَ",
        "وَسِعَ كُرْسِيُّهُ السَّمَاوَاتِ وَالْأَرْضَ",
        "وَلَا يَئُودُهُ حِفْظُهُمَا",
        "وَهُوَ الْعَلِيُّ الْعَظِيمُ"
    };

    public static void register() {
        // SAĞ TIK — Ayetel Kürsi (düz book zaten açılmaz)
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClientSide()) return InteractionResult.PASS;
            if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
            if (!KuranKitap.esittir(player.getItemInHand(hand))) return InteractionResult.PASS;
            int now = sp.level().getServer().getTickCount();
            Integer son = sonOkuma.get(sp.getUUID());
            if (son != null && now - son < OKUMA_COOLDOWN) return InteractionResult.SUCCESS;
            sonOkuma.put(sp.getUUID(), now);
            ayetelKursiOku(sp);
            return InteractionResult.SUCCESS;
        });

        // SOL TIK (bloğa) — tüket + çile + nimet
        AttackBlockCallback.EVENT.register((player, world, hand, pos, dir) -> {
            if (world.isClientSide() || hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
            if (!KuranKitap.esittir(sp.getMainHandItem())) return InteractionResult.PASS;
            solTik(sp);
            return InteractionResult.SUCCESS; // blok kırma iptal
        });

        // SOL TIK (varlığa)
        AttackEntityCallback.EVENT.register((player, world, hand, target, hit) -> {
            if (world.isClientSide() || hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
            if (!KuranKitap.esittir(sp.getMainHandItem())) return InteractionResult.PASS;
            solTik(sp);
            return InteractionResult.SUCCESS;
        });

        ServerTickEvents.END_SERVER_TICK.register(KuranSistemi::onTick);
        Candy.LOG.info("[Candy] KuranSistemi aktif — Ayetel Kürsi + İmanın Gücü");
    }

    private static void ayetelKursiOku(ServerPlayer sp) {
        for (String satir : AYET) {
            sp.displayClientMessage(Component.literal(satir).withStyle(ChatFormatting.GOLD), false);
        }
        ((ServerLevel) sp.level()).playSound(null, sp.blockPosition(),
            SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8f, 1.0f);
    }

    private static void solTik(ServerPlayer sp) {
        sp.getMainHandItem().shrink(1); // Kuran'ı tüket
        // Çile — önce zehir II + körlük
        sp.addEffect(new MobEffectInstance(MobEffects.POISON, CILE_TICK, 1, false, true, true));
        sp.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, CILE_TICK, 0, false, true, true));
        ((ServerLevel) sp.level()).playSound(null, sp.blockPosition(),
            SoundEvents.AMBIENT_CAVE.value(), SoundSource.PLAYERS, 1.0f, 0.5f);
        // Nimeti çile bitince uygula
        bekleyenNimet.put(sp.getUUID(), sp.level().getServer().getTickCount() + CILE_TICK);
        Candy.LOG.info("[Candy] Kuran sol-tık[{}] → çile başladı", sp.getName().getString());
    }

    private static void onTick(MinecraftServer server) {
        if (bekleyenNimet.isEmpty()) return;
        int now = server.getTickCount();
        Iterator<Map.Entry<UUID, Integer>> it = bekleyenNimet.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> e = it.next();
            if (now < e.getValue()) continue;
            it.remove();
            ServerPlayer sp = server.getPlayerList().getPlayer(e.getKey());
            if (sp != null && sp.isAlive()) imaninGucu(sp);
        }
    }

    /**
     * İmanın Gücü — HUD'da tek görünür özel efekt; buff'lar (Kuvvet V, Hız I, Emme IV,
     * Yenilenme III) aynı süre boyunca GİZLİ (showIcon=false) vanilla efektlerle sağlanır.
     */
    private static void imaninGucu(ServerPlayer sp) {
        // Görünür işaretleyici efekt (tek HUD ikonu: "İmanın Gücü")
        sp.addEffect(new MobEffectInstance(CandyEfektler.IMANIN_GUCU, NIMET_TICK, 0, false, true, true));
        // Mekanik buff'lar — gizli (ikon yok)
        sp.addEffect(new MobEffectInstance(MobEffects.STRENGTH, NIMET_TICK, 4, false, false, false));
        sp.addEffect(new MobEffectInstance(MobEffects.SPEED, NIMET_TICK, 0, false, false, false));
        sp.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, NIMET_TICK, 3, false, false, false));
        sp.addEffect(new MobEffectInstance(MobEffects.REGENERATION, NIMET_TICK, 2, false, false, false));
        sp.displayClientMessage(Component.literal("✦ İmanın Gücü ✦")
            .withStyle(s -> s.withColor(ChatFormatting.GOLD).withBold(true)), false);
        ((ServerLevel) sp.level()).playSound(null, sp.blockPosition(),
            SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.7f, 1.2f);
        Candy.LOG.info("[Candy] İmanın Gücü verildi: {}", sp.getName().getString());
    }

    public static void oyuncuAyrildi(UUID uuid) {
        sonOkuma.remove(uuid);
        bekleyenNimet.remove(uuid);
    }
}
