package com.photonpulse.candy.audio;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.core.Psikoloji;
import com.photonpulse.candy.core.TruePersonality;
import com.photonpulse.candy.platform.SunucuTick;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
        if (o instanceof Holder<?> re) return (SoundEvent) re.value();
        return (SoundEvent) o;
    }

    private static SoundEvent[] PLAYER_SESLERI;

    public static void register() {
        // Static init sırasında SoundEvents hazır olmayabilir, register'da doldur.
        PLAYER_SESLERI = new SoundEvent[]{
            se(SoundEvents.GRASS_STEP),        // footstep on grass
            se(SoundEvents.STONE_STEP),        // footstep on stone
            se(SoundEvents.GRAVEL_STEP),       // footstep on gravel
            se(SoundEvents.PLAYER_ATTACK_STRONG),
            se(SoundEvents.PLAYER_ATTACK_SWEEP),
            se(SoundEvents.PLAYER_BURP),      // yeme
            se(SoundEvents.PLAYER_HURT),      // yaralanma
            se(SoundEvents.PLAYER_BREATH),    // nefes
            se(SoundEvents.ITEM_PICKUP),
            se(SoundEvents.PLAYER_SMALL_FALL)
        };

        SunucuTick.END_SERVER_TICK.register(SesTaklit::onTick);
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

        long simdi = server.overworld().getGameTime();
        // v1.9.0: sessizlik protokolü — hiç ses çıkarma
        if (com.photonpulse.candy.sessizlik.SessizlikState.sessizlikte(simdi)) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            TruePersonality tp = PersonalityManager.get(player);
            if (!tp.kurulumTamam) continue;
            if (tp.korkuSkoru < MIN_KORKU) continue;

            Long son = sonSesTick.get(player.getUUID());
            double agresivlik = Psikoloji.agresivlikCarpani(tp);
            long cooldown = (long) (OYUNCU_COOLDOWN_TICK / agresivlik);
            if (son != null && simdi - son < cooldown) continue;
            if (RNG.nextDouble() > BASE_IHTIMAL * agresivlik) continue;

            sesCal(player);
            sonSesTick.put(player.getUUID(), simdi);
        }
    }

    // v1.9.14: taklit başarımı — aynı sesi 2 kez peş peşe player'a çalarsa
    private static final java.util.Map<java.util.UUID, String> sonSesId = new java.util.HashMap<>();

    private static void sesCal(ServerPlayer player) {
        ServerLevel world = (ServerLevel) player.level();
        SoundEvent ses = PLAYER_SESLERI[RNG.nextInt(PLAYER_SESLERI.length)];
        String sesId = ses.location().toString();
        String onceki = sonSesId.get(player.getUUID());
        if (onceki != null && onceki.equals(sesId)) {
            com.photonpulse.candy.basarim.CandyBasarimlar.tetikle(player, "taklit");
        }
        sonSesId.put(player.getUUID(), sesId);

        // 8-16 blok ötesinde, rastgele yön
        double angle = RNG.nextDouble() * Math.PI * 2;
        double mesafe = 8 + RNG.nextDouble() * 8;
        double x = player.getX() + Math.cos(angle) * mesafe;
        double z = player.getZ() + Math.sin(angle) * mesafe;
        double y = player.getY() + (RNG.nextDouble() - 0.5) * 3;

        world.playSound(null, x, y, z, ses, SoundSource.PLAYERS,
            0.7f, 0.9f + RNG.nextFloat() * 0.2f);
        Candy.LOG.info("[Candy] SesTaklit: {} çevresinde player sesi ({}) @{},{},{}",
            player.getName().getString(), ses.location(), (int) x, (int) y, (int) z);
    }
}
