package com.photonpulse.candy.item;

import com.photonpulse.candy.Candy;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;

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
            if (world.isClient()) return ActionResult.PASS;
            if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
            if (!KuranKitap.esittir(player.getStackInHand(hand))) return ActionResult.PASS;
            int now = sp.getWorld().getServer().getTicks();
            Integer son = sonOkuma.get(sp.getUuid());
            if (son != null && now - son < OKUMA_COOLDOWN) return ActionResult.SUCCESS;
            sonOkuma.put(sp.getUuid(), now);
            ayetelKursiOku(sp);
            return ActionResult.SUCCESS;
        });

        // SOL TIK (bloğa) — tüket + çile + nimet
        AttackBlockCallback.EVENT.register((player, world, hand, pos, dir) -> {
            if (world.isClient() || hand != Hand.MAIN_HAND) return ActionResult.PASS;
            if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
            if (!KuranKitap.esittir(sp.getMainHandStack())) return ActionResult.PASS;
            solTik(sp);
            return ActionResult.SUCCESS; // blok kırma iptal
        });

        // SOL TIK (varlığa)
        AttackEntityCallback.EVENT.register((player, world, hand, target, hit) -> {
            if (world.isClient() || hand != Hand.MAIN_HAND) return ActionResult.PASS;
            if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
            if (!KuranKitap.esittir(sp.getMainHandStack())) return ActionResult.PASS;
            solTik(sp);
            return ActionResult.SUCCESS;
        });

        ServerTickEvents.END_SERVER_TICK.register(KuranSistemi::onTick);
        Candy.LOG.info("[Candy] KuranSistemi aktif — Ayetel Kürsi + İmanın Gücü");
    }

    private static void ayetelKursiOku(ServerPlayerEntity sp) {
        for (String satir : AYET) {
            sp.sendMessage(Text.literal(satir).formatted(Formatting.GOLD), false);
        }
        ((ServerWorld) sp.getWorld()).playSound(null, sp.getBlockPos(),
            SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.8f, 1.0f);
    }

    private static void solTik(ServerPlayerEntity sp) {
        sp.getMainHandStack().decrement(1); // Kuran'ı tüket
        // Çile — önce zehir II + körlük
        sp.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, CILE_TICK, 1, false, true, true));
        sp.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, CILE_TICK, 0, false, true, true));
        ((ServerWorld) sp.getWorld()).playSound(null, sp.getBlockPos(),
            SoundEvents.AMBIENT_CAVE.value(), SoundCategory.PLAYERS, 1.0f, 0.5f);
        // Nimeti çile bitince uygula
        bekleyenNimet.put(sp.getUuid(), sp.getWorld().getServer().getTicks() + CILE_TICK);
        Candy.LOG.info("[Candy] Kuran sol-tık[{}] → çile başladı", sp.getName().getString());
    }

    private static void onTick(MinecraftServer server) {
        if (bekleyenNimet.isEmpty()) return;
        int now = server.getTicks();
        Iterator<Map.Entry<UUID, Integer>> it = bekleyenNimet.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> e = it.next();
            if (now < e.getValue()) continue;
            it.remove();
            ServerPlayerEntity sp = server.getPlayerManager().getPlayer(e.getKey());
            if (sp != null && sp.isAlive()) imaninGucu(sp);
        }
    }

    /**
     * İmanın Gücü — HUD'da tek görünür özel efekt; buff'lar (Kuvvet V, Hız I, Emme IV,
     * Yenilenme III) aynı süre boyunca GİZLİ (showIcon=false) vanilla efektlerle sağlanır.
     */
    private static void imaninGucu(ServerPlayerEntity sp) {
        // Görünür işaretleyici efekt (tek HUD ikonu: "İmanın Gücü")
        sp.addStatusEffect(new StatusEffectInstance(CandyEfektler.IMANIN_GUCU, NIMET_TICK, 0, false, true, true));
        // Mekanik buff'lar — gizli (ikon yok)
        sp.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, NIMET_TICK, 4, false, false, false));
        sp.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, NIMET_TICK, 0, false, false, false));
        sp.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, NIMET_TICK, 3, false, false, false));
        sp.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, NIMET_TICK, 2, false, false, false));
        sp.sendMessage(Text.literal("✦ İmanın Gücü ✦")
            .styled(s -> s.withColor(Formatting.GOLD).withBold(true)), false);
        ((ServerWorld) sp.getWorld()).playSound(null, sp.getBlockPos(),
            SoundEvents.ITEM_TOTEM_USE, SoundCategory.PLAYERS, 0.7f, 1.2f);
        Candy.LOG.info("[Candy] İmanın Gücü verildi: {}", sp.getName().getString());
    }

    public static void oyuncuAyrildi(UUID uuid) {
        sonOkuma.remove(uuid);
        bekleyenNimet.remove(uuid);
    }
}
