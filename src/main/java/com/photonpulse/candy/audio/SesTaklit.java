package com.photonpulse.candy.audio;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.core.Psikoloji;
import com.photonpulse.candy.core.TruePersonality;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * SesTaklit — Candy oyuncunun ses "hafızasını" tutar ve rastgele çalar.
 *
 * Player yürüyünce/vurunca/yiyince sesler kaydedilir (tick-based tracking).
 * Sonra rastgele aralıklarla Candy o sesleri oyuncunun 8-16 blok ötesinde çalar.
 * "Yakınında başka biri var" hissi — halüsinasyon.
 *
 * Basitleştirme: Gerçek ses dosyası kaydetmek yerine, oyuncunun YAPTIĞI eylem tipini
 * (footstep, sword swing, eat) hafızada tut → generic player sound çal.
 */
public class SesTaklit {

    private static final int TICK_ARALIGI = 200;                // 10 sn kontrol
    private static final long OYUNCU_COOLDOWN_TICK = 800L;      // min 40 sn
    private static final double MIN_KORKU = 30.0;
    private static final double BASE_IHTIMAL = 0.15;            // her kontrolde %15

    private static int tickSayaci = 0;
    private static final Random RNG = new Random();
    private static final Map<UUID, Long> sonSesTick = new HashMap<>();

    // Player sesleri — jenerik pool. Gerçek recording yerine sabit ses seti.
    // Karışık düzen — footstep + swing + eat + hurt = "biri" hissi.
    private static SoundEvent se(Object o) {
        if (o instanceof RegistryEntry<?> re) return (SoundEvent) re.value();
        return (SoundEvent) o;
    }

    private static SoundEvent[] PLAYER_SESLERI;

    public static void register() {
        // Static init sırasında SoundEvents hazır olmayabilir, register'da doldur.
        PLAYER_SESLERI = new SoundEvent[]{
            se(SoundEvents.BLOCK_GRASS_STEP),        // footstep on grass
            se(SoundEvents.BLOCK_STONE_STEP),        // footstep on stone
            se(SoundEvents.BLOCK_GRAVEL_STEP),       // footstep on gravel
            se(SoundEvents.ENTITY_PLAYER_ATTACK_STRONG),
            se(SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP),
            se(SoundEvents.ENTITY_PLAYER_BURP),      // yeme
            se(SoundEvents.ENTITY_PLAYER_HURT),      // yaralanma
            se(SoundEvents.ENTITY_PLAYER_BREATH),    // nefes
            se(SoundEvents.ENTITY_ITEM_PICKUP),
            se(SoundEvents.ENTITY_PLAYER_SMALL_FALL)
        };

        ServerTickEvents.END_SERVER_TICK.register(SesTaklit::onTick);
        Candy.LOG.info("[Candy] SesTaklit aktif — Candy player seslerini çalıyor");
    }

    public static void oyuncuAyrildi(UUID uuid) {
        sonSesTick.remove(uuid);
        sonSesId.remove(uuid);
    }

    public static void sunucuKapan() {
        sonSesTick.clear();
        sonSesId.clear();
        tickSayaci = 0;
    }

    private static void onTick(MinecraftServer server) {
        tickSayaci++;
        if (tickSayaci % TICK_ARALIGI != 0) return;

        long simdi = server.getOverworld().getTime();
        // v1.9.0: sessizlik protokolü — hiç ses çıkarma
        if (com.photonpulse.candy.sessizlik.SessizlikState.sessizlikte(simdi)) return;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            TruePersonality tp = PersonalityManager.get(player);
            if (!tp.kurulumTamam) continue;
            if (tp.korkuSkoru < MIN_KORKU) continue;

            Long son = sonSesTick.get(player.getUuid());
            double agresivlik = Psikoloji.agresivlikCarpani(tp);
            long cooldown = (long) (OYUNCU_COOLDOWN_TICK / agresivlik);
            if (son != null && simdi - son < cooldown) continue;
            if (RNG.nextDouble() > BASE_IHTIMAL * agresivlik) continue;

            sesCal(player);
            sonSesTick.put(player.getUuid(), simdi);
        }
    }

    // v1.9.14: taklit başarımı — aynı sesi 2 kez peş peşe player'a çalarsa
    private static final java.util.Map<java.util.UUID, String> sonSesId = new java.util.HashMap<>();

    private static void sesCal(ServerPlayerEntity player) {
        ServerWorld world = (ServerWorld) player.getWorld();
        SoundEvent ses = PLAYER_SESLERI[RNG.nextInt(PLAYER_SESLERI.length)];
        String sesId = ses.id().toString();
        String onceki = sonSesId.get(player.getUuid());
        if (onceki != null && onceki.equals(sesId)) {
            com.photonpulse.candy.basarim.CandyBasarimlar.tetikle(player, "taklit");
        }
        sonSesId.put(player.getUuid(), sesId);

        // 8-16 blok ötesinde, rastgele yön
        double angle = RNG.nextDouble() * Math.PI * 2;
        double mesafe = 8 + RNG.nextDouble() * 8;
        double x = player.getX() + Math.cos(angle) * mesafe;
        double z = player.getZ() + Math.sin(angle) * mesafe;
        double y = player.getY() + (RNG.nextDouble() - 0.5) * 3;

        world.playSound(null, x, y, z, ses, SoundCategory.PLAYERS,
            0.7f, 0.9f + RNG.nextFloat() * 0.2f);
        Candy.LOG.info("[Candy] SesTaklit: {} çevresinde player sesi ({}) @{},{},{}",
            player.getName().getString(), ses.id(), (int) x, (int) y, (int) z);
    }
}
