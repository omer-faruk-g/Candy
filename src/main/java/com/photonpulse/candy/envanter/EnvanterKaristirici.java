package com.photonpulse.candy.envanter;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.core.Psikoloji;
import com.photonpulse.candy.core.TruePersonality;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * EnvanterKaristirici — player uzun süre AFK kalırsa Candy 2 envanter slotunu değişir.
 *
 * "AFK" tespiti: 60 sn hareketsizlik (aynı blok pozisyonunda).
 * Süre dolunca kontrol: kotuluk > 30 ise %30 ihtimalle 2 rastgele slot (hotbar hariç,
 * main inventory slot 9-35) yer değişir. Player fark ederse çok rahatsız edici.
 *
 * NOT: Sadece main inventory (9-35), hotbar (0-8) ve armor (36-39) etkilenmez.
 * Kritik itemler yerinde kalır.
 */
public class EnvanterKaristirici {

    private static final int TICK_ARALIGI = 100;                // 5 sn kontrol
    private static final long AFK_ESIK_TICK = 1200L;            // 60 sn hareketsizlik
    private static final long OYUNCU_COOLDOWN_TICK = 6000L;     // aynı player min 5 dk arayla
    private static final double MIN_KOTULUK = 30.0;
    private static final double SWAP_IHTIMAL = 0.30;            // AFK sonrası %30

    private static int tickSayaci = 0;
    private static final Random RNG = new Random();
    private static final Map<UUID, Vec3> sonPozisyon = new HashMap<>();
    private static final Map<UUID, Long> sonHareketTick = new HashMap<>();
    private static final Map<UUID, Long> sonSwapTick = new HashMap<>();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(EnvanterKaristirici::onTick);
        Candy.LOG.info("[Candy] EnvanterKaristirici aktif — AFK sonrası slot swap");
    }

    public static void oyuncuAyrildi(UUID uuid) {
        sonPozisyon.remove(uuid);
        sonHareketTick.remove(uuid);
        sonSwapTick.remove(uuid);
    }

    public static void sunucuKapan() {
        sonPozisyon.clear();
        sonHareketTick.clear();
        sonSwapTick.clear();
        tickSayaci = 0;
    }

    private static void onTick(MinecraftServer server) {
        tickSayaci++;
        if (tickSayaci % TICK_ARALIGI != 0) return;

        long simdi = server.overworld().getGameTime();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID uid = player.getUUID();
            Vec3 pos = player.position();

            // Hareket takip
            Vec3 oncekiPos = sonPozisyon.get(uid);
            if (oncekiPos == null || pos.distanceTo(oncekiPos) > 1.0) {
                sonPozisyon.put(uid, pos);
                sonHareketTick.put(uid, simdi);
                continue;
            }

            // AFK kontrol
            Long sonHar = sonHareketTick.get(uid);
            if (sonHar == null) continue;
            if (simdi - sonHar < AFK_ESIK_TICK) continue;

            // Sadece kurulum sonrası + kötü psikoloji
            TruePersonality tp = PersonalityManager.get(player);
            if (!tp.kurulumTamam) continue;
            if (Psikoloji.kotulukSkoru(tp) < MIN_KOTULUK) continue;

            // Cooldown
            Long sonSwap = sonSwapTick.get(uid);
            if (sonSwap != null && simdi - sonSwap < OYUNCU_COOLDOWN_TICK) continue;

            if (RNG.nextDouble() > SWAP_IHTIMAL) continue;

            if (slotSwap(player)) {
                sonSwapTick.put(uid, simdi);
                sonHareketTick.put(uid, simdi);   // AFK sayacını sıfırla
            }
        }
    }

    /**
     * Main inventory'de 2 rastgele dolu slot bul, yer değişir. Başarılıysa true döner.
     */
    private static boolean slotSwap(ServerPlayer player) {
        var inv = player.getInventory();
        // Main inventory: slot 9-35 (36 slot toplam envanter, 9-35 arası main)
        int slot1 = -1, slot2 = -1;
        int denemeMax = 20;
        for (int i = 0; i < denemeMax; i++) {
            int s = 9 + RNG.nextInt(27);
            if (!inv.getItem(s).isEmpty()) {
                if (slot1 == -1) slot1 = s;
                else if (s != slot1) { slot2 = s; break; }
            }
        }
        if (slot1 == -1 || slot2 == -1) return false;

        ItemStack a = inv.getItem(slot1);
        ItemStack b = inv.getItem(slot2);
        inv.setItem(slot1, b);
        inv.setItem(slot2, a);

        Candy.LOG.info("[Candy] EnvanterKaristirici: {} slot {}↔{} swap",
            player.getName().getString(), slot1, slot2);
        // v1.9.3: envanter_kabusu başarımı — 5 kez karıştırılınca
        var tp = com.photonpulse.candy.core.PersonalityManager.get(player);
        tp.envanterKaristirmaSayisi++;
        if (tp.envanterKaristirmaSayisi >= 5) {
            com.photonpulse.candy.basarim.CandyBasarimlar.tetikle(player, "envanter_kabusu");
        }
        return true;
    }
}
