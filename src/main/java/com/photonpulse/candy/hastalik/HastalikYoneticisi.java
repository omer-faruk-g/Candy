package com.photonpulse.candy.hastalik;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.core.TruePersonality;
import com.photonpulse.candy.net.HastalikPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Random;

/**
 * HastalikYoneticisi (v1.8.0) — 8 psikolojik hastalık.
 *
 * Her 5 sn (100 tick) player'ın state'ini analiz eder, uygun hastalık varsa uygular.
 * Hastalık değiştiğinde:
 *   - "X'e kapıldın" + tedavi bilgisi chat mesajı (TR/EN)
 *   - Server → client sync (HUD text için)
 *   - Vanilla status effect efektleri sürekli refresh
 *
 * İyileşince "iyileştin" chat mesajı + effect kaldırma.
 *
 * "Hem Candy ile uğraş, hem psikolojini düzgün tut."
 */
public class HastalikYoneticisi {

    private static final int TICK_ARALIGI = 100; // 5 sn
    private static final Random RNG = new Random();

    public static void register() {
        try {
            PayloadTypeRegistry.playS2C().register(HastalikPayload.ID, HastalikPayload.CODEC);
        } catch (Throwable t) {
            Candy.LOG.debug("[Candy] Hastalik payload zaten kayıtlı: {}", t.getMessage());
        }

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % TICK_ARALIGI != 0) return;
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                try { kontrol(player); }
                catch (Throwable t) { Candy.LOG.debug("[Candy] hastalik kontrol: {}", t.getMessage()); }
            }
        });

        // v1.9.15 fix: Ölümden sonra hastalık + korku sıfırla
        //   Bug: player kalp krizinden ölür → respawn → korku hâlâ ≥ 85 + hastalikBaslangicTick
        //   çok eski → panikSuresi > 7 dk → tekrar ölür → sonsuz döngü.
        net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AFTER_RESPAWN
            .register((oldPlayer, newPlayer, alive) -> {
                try {
                    var tp = PersonalityManager.get(newPlayer);
                    tp.aktifHastalik = "";
                    tp.hastalikBaslangicTick = 0L;
                    tp.korkuSkoru = Math.min(tp.korkuSkoru, 30.0);   // korku dibe çek
                    tp.psikolojiKotulugu = Math.max(0, tp.psikolojiKotulugu - 20);
                    tp.ilkKarartmaBaslangicTick = 0L;
                    // Client'a bildir
                    try {
                        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                            newPlayer, new HastalikPayload(""));
                    } catch (Throwable ignored) {}
                    Candy.LOG.info("[Candy] Ölüm sonrası hastalik reset → {}",
                        newPlayer.getName().getString());
                } catch (Throwable t) {
                    Candy.LOG.debug("[Candy] respawn reset hata: {}", t.getMessage());
                }
            });

        Candy.LOG.info("[Candy] HastalikYoneticisi aktif — 8 psikolojik hastalık");
    }

    private static void kontrol(ServerPlayerEntity player) {
        TruePersonality tp = PersonalityManager.get(player);
        if (!tp.kurulumTamam) return;
        if (player.isCreative() || player.isSpectator()) return;

        String eski = tp.aktifHastalik == null ? "" : tp.aktifHastalik;
        String yeni = hesapla(tp, player.getWorld().getTime());

        if (!yeni.equals(eski)) {
            // Değişiklik var
            if (eski.isEmpty() && !yeni.isEmpty()) {
                // Yeni hastalık — Candy güçlenir. Tek satır atmosferik semptom.
                bildir(player, "candy.hastalik.kaptin." + yeni, Formatting.DARK_GRAY);
                tp.hastalikBaslangicTick = player.getWorld().getTime();
                tp.candyOzguven = Math.min(100, tp.candyOzguven + 15);
                tp.kaptigiHastaliklar.add(yeni);
                com.photonpulse.candy.basarim.CandyBasarimlar.tetikle(player, "icindeki_birsey");
                // v1.9.14: her_seyi_gordum — 8 farklı hastalığı görünce
                if (tp.kaptigiHastaliklar.size() >= 8) {
                    com.photonpulse.candy.basarim.CandyBasarimlar.tetikle(player, "her_seyi_gordum");
                }
                Candy.LOG.info("[Candy] {} → {} hastalık | özgüven +15 → {}",
                    player.getName().getString(), yeni,
                    String.format("%.1f", tp.candyOzguven));
            } else if (!eski.isEmpty() && yeni.isEmpty()) {
                // İyileşti — efektleri manuel silmiyoruz (potion çalmasın), 10 sn'de kendi söner
                bildir(player, "candy.hastalik.iyilesti." + eski, Formatting.GREEN);
                com.photonpulse.candy.basarim.CandyBasarimlar.tetikle(player, "iyilesme");
                Candy.LOG.info("[Candy] {} iyileşti ({})",
                    player.getName().getString(), eski);
            } else if (!eski.isEmpty() && !yeni.isEmpty()) {
                // Farklı hastalığa geçti — sadece yeni semptom, tedavi bilgisi yok
                bildir(player, "candy.hastalik.kaptin." + yeni, Formatting.DARK_GRAY);
                tp.hastalikBaslangicTick = player.getWorld().getTime();
                tp.candyOzguven = Math.min(100, tp.candyOzguven + 15);
                tp.kaptigiHastaliklar.add(yeni);
                if (tp.kaptigiHastaliklar.size() >= 8) {
                    com.photonpulse.candy.basarim.CandyBasarimlar.tetikle(player, "her_seyi_gordum");
                }
            }
            tp.aktifHastalik = yeni;
        }

        // v1.8.2 Fix A: her kontrolde sync — reconnect'te HUD görünsün
        senkron(player, tp.aktifHastalik == null ? "" : tp.aktifHastalik);

        if (!yeni.isEmpty()) {
            uygulaEfekt(player, yeni);
        }
    }

    /**
     * Priority sırası önemli — üstteki daha güçlü tetikleyici.
     * State'ten uygun hastalık hesapla.
     */
    private static String hesapla(TruePersonality tp, long simdi) {
        double k = tp.psikolojiKotulugu;
        double f = tp.korkuSkoru;

        if (f >= 85) return PsikolojikHastalik.panik.key();
        if (k >= 80 && f >= 80) return PsikolojikHastalik.sizofreni.key();
        if (k >= 90) return PsikolojikHastalik.kisilik.key();
        if (k >= 60 && f < 40) return PsikolojikHastalik.depresyon.key();
        if (k >= 50 && f >= 50) return PsikolojikHastalik.stres.key();
        if (f >= 60 && k < 30) return PsikolojikHastalik.anksiyete.key();
        // Bipolar — rare random (%2 her checkte, kotuluk 30+ ise)
        if (k >= 30 && RNG.nextDouble() < 0.02) return PsikolojikHastalik.bipolar.key();
        return ""; // sağlıklı
    }

    /**
     * Vanilla status effect ile hastalık hissi.
     * Süre 200 tick (10 sn) — check aralığı 5 sn olduğu için sürekli refresh.
     */
    private static void uygulaEfekt(ServerPlayerEntity player, String hastalik) {
        int sure = 200;
        switch (hastalik) {
            case "panik" -> {
                // v1.9.13: Panik gerçekçi 3 aşama
                //   0-5 dk: adrenalin (SPEED)
                //   5-7 dk: kalp tükeniyor (SPEED + WITHER III)
                //   7+ dk: kalp krizi → custom damage type ile ölüm
                com.photonpulse.candy.core.TruePersonality tp2 =
                    com.photonpulse.candy.core.PersonalityManager.get(player);
                long simdi = player.getWorld().getTime();
                long panikSuresi = simdi - tp2.hastalikBaslangicTick;

                if (panikSuresi < 6000L) {
                    // İlk 5 dk — sadece adrenalin
                    uygula(player, StatusEffects.SPEED, sure, 0);
                    if (RNG.nextDouble() < 0.3)
                        uygula(player, StatusEffects.NAUSEA, 60, 0);
                } else if (panikSuresi < 8400L) {
                    // 5-7 dk — kalp tükeniyor, WITHER III
                    uygula(player, StatusEffects.SPEED, sure, 0);
                    uygula(player, StatusEffects.WITHER, sure, 2);
                    if (RNG.nextDouble() < 0.5)
                        uygula(player, StatusEffects.NAUSEA, 60, 0);
                } else {
                    // 7+ dk — kalp krizi
                    kalpKrizi(player);
                }
            }
            case "sizofreni" -> {
                uygula(player, StatusEffects.DARKNESS, sure, 0);
                if (RNG.nextDouble() < 0.15) {
                    // Rastgele korkutucu ses
                    ((ServerWorld) player.getWorld()).playSound(
                        null, player.getBlockPos(),
                        SoundEvents.ENTITY_WARDEN_NEARBY_CLOSE,
                        SoundCategory.AMBIENT, 0.8f, 0.6f);
                }
            }
            case "kisilik" -> {
                uygula(player, StatusEffects.MINING_FATIGUE, sure, 0);
                uygula(player, StatusEffects.WEAKNESS, sure, 0);
            }
            case "depresyon" -> {
                uygula(player, StatusEffects.SLOWNESS, sure, 0);
                uygula(player, StatusEffects.MINING_FATIGUE, sure, 0);
            }
            case "stres" -> {
                uygula(player, StatusEffects.WEAKNESS, sure, 0);
                uygula(player, StatusEffects.HUNGER, sure, 0);
            }
            case "anksiyete" -> {
                if (RNG.nextDouble() < 0.25)
                    uygula(player, StatusEffects.NAUSEA, 60, 0);
            }
            case "bipolar" -> {
                long day = player.getWorld().getTime() / 24000L;
                if (day % 2 == 0) uygula(player, StatusEffects.SPEED, sure, 0);
                else uygula(player, StatusEffects.SLOWNESS, sure, 0);
            }
            case "okb" -> {
                // OKB nadir, sürekli refresh yok
            }
        }
    }

    private static void uygula(ServerPlayerEntity player,
                                net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.effect.StatusEffect> effect,
                                int sure, int amp) {
        player.addStatusEffect(new StatusEffectInstance(effect, sure, amp, true, false, false));
    }

    /**
     * v1.9.13: Kalp krizi — panik 7 dk sürerse tetiklenir.
     * Custom damage type "candy:kalp_krizi" kullanılır → vanilla death message
     * lang key'inden çekilir: "candy.hastalik.panik_death" mesajı chat'te çıkar,
     * /kill kullanıldığı belli olmaz.
     */
    private static void kalpKrizi(ServerPlayerEntity player) {
        if (!player.isAlive()) return;
        try {
            ServerWorld world = (ServerWorld) player.getWorld();
            var id = net.minecraft.util.Identifier.of("candy", "kalp_krizi");
            var registry = world.getRegistryManager()
                .getOrThrow(net.minecraft.registry.RegistryKeys.DAMAGE_TYPE);
            var entry = registry.getEntry(id).orElseThrow();
            var source = new net.minecraft.entity.damage.DamageSource(entry);
            player.damage(world, source, 1000f);
            Candy.LOG.info("[Candy] Kalp krizi → {}", player.getName().getString());
        } catch (Throwable t) {
            Candy.LOG.warn("[Candy] Kalp krizi hata, fallback health=0: {}", t.getMessage());
            player.setHealth(0.0f);
        }
    }

    // v1.8.2 Fix B: temizleEfektler kaldırıldı — player'ın kendi potion'larını çalmaz.
    // Refresh durunca effect'ler 200 tick (10 sn) içinde doğal söner.

    private static void bildir(ServerPlayerEntity player, String langKey, Formatting renk) {
        player.sendMessage(
            Text.translatable(langKey).formatted(renk, Formatting.ITALIC),
            false);
    }

    private static void senkron(ServerPlayerEntity player, String hastalik) {
        try {
            ServerPlayNetworking.send(player, new HastalikPayload(hastalik == null ? "" : hastalik));
        } catch (Throwable ignored) {}
    }
}
