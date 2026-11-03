package com.photonpulse.candy.travma;

import com.photonpulse.candy.Candy;
import com.photonpulse.candy.basarim.CandyBasarimlar;
import com.photonpulse.candy.core.PersonalityManager;
import com.photonpulse.candy.core.TruePersonality;
import com.photonpulse.candy.platform.SunucuTick;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.phys.AABB;
import java.util.Random;

/**
 * PetOlumu (v3.0) — Candy'nin pet öldürme sistemi.
 *
 * Her 4 dk kontrol: kotuluk >= 40, player'ın 32 blok içinde tamed pet var.
 * %7 ihtimalle 1 pet sessizce ölür. Öncelik: Köpek → Kedi → Diğer.
 * Chat: [PetAdı] artık burada değil.
 */
public class PetOlumu {

    // -Dcandy.debug=true → test için hızlı + gevşek koşullar + log
    private static final boolean DEBUG = Boolean.getBoolean("candy.debug");
    private static final int TICK_ARALIGI = DEBUG ? 200 : 4800;      // debug 10sn / normal 4 dk
    private static final double MIN_KOTULUK = DEBUG ? 0.0 : 40.0;
    private static final double IHTIMAL = DEBUG ? 1.0 : 0.07;
    private static final double YARICAP = 32.0;

    private static final Random RNG = new Random();

    public static void register() {
        SunucuTick.END_SERVER_TICK.register(PetOlumu::onTick);
        Candy.LOG.info("[Candy] PetOlumu aktif — {} (v3.0)",
            DEBUG ? "DEBUG (10sn %100 kotuluk>=0)" : "4 dk %7");
    }

    private static void onTick(MinecraftServer server) {
        if (server.getTickCount() % TICK_ARALIGI != 0) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            TruePersonality tp = PersonalityManager.get(player);
            if (!tp.kurulumTamam) {
                if (DEBUG) Candy.LOG.info("[Candy] PetOlumu SKIP: {} kurulum tamamlanmadı",
                    player.getName().getString());
                continue;
            }
            if (tp.psikolojiKotulugu < MIN_KOTULUK) {
                if (DEBUG) Candy.LOG.info("[Candy] PetOlumu SKIP: {} kotuluk={} (<{})",
                    player.getName().getString(), tp.psikolojiKotulugu, MIN_KOTULUK);
                continue;
            }
            if (tp.bossOldurulmeTick > 0) continue; // WIN 30dk sessizlik
            // BAD ENDING → %2 hız (ihtimal 2x)
            double ihtimal = tp.badEndingAktif ? Math.min(1.0, IHTIMAL * 2) : IHTIMAL;
            if (RNG.nextDouble() >= ihtimal) continue;

            ServerLevel world = (ServerLevel) player.level();
            AABB kutu = new AABB(player.blockPosition()).inflate(YARICAP);

            // Tamed pet'ler (köpek/kedi/papağan)
            var petler = world.getEntitiesOfClass(TamableAnimal.class, kutu,
                t -> t.isTame() && t.isAlive() && t.isOwnedBy(player));
            // Tamed atlar (at/eşek/katır/lama) — TameableEntity değil, ayrı sorgu
            var atlar = world.getEntitiesOfClass(AbstractHorse.class, kutu,
                h -> h.isTamed() && h.isAlive() && h.getOwnerReference() != null
                     && player.getUUID().equals(h.getOwnerReference().getUUID()));

            if (petler.isEmpty() && atlar.isEmpty()) {
                if (DEBUG) Candy.LOG.info("[Candy] PetOlumu SKIP: {} 32 blok içinde tamed pet yok",
                    player.getName().getString());
                continue;
            }

            // Öncelik: köpek → kedi → diğer. Her tier içinde Analiz 2.0 bağlanma haritasına
            // göre EN ÇOK sevdiğin (en çok yanında durduğun) hayvanı hedefler.
            Entity kurban = null;
            java.util.List<Entity> kopekler = new java.util.ArrayList<>();
            java.util.List<Entity> kediler = new java.util.ArrayList<>();
            for (var p : petler) {
                if (p instanceof Wolf) kopekler.add(p);
                else if (p instanceof Cat) kediler.add(p);
            }
            if (!kopekler.isEmpty()) kurban = enCokBaglanilan(tp, kopekler);
            else if (!kediler.isEmpty()) kurban = enCokBaglanilan(tp, kediler);
            else {
                java.util.List<Entity> digerleri = new java.util.ArrayList<>();
                digerleri.addAll(petler);
                digerleri.addAll(atlar);
                kurban = enCokBaglanilan(tp, digerleri);
            }

            String ad = kurban.hasCustomName()
                ? kurban.getCustomName().getString()
                : kurban.getType().getDescription().getString();

            kurban.discard(); // sessiz kill — drop yok, death event yok

            player.displayClientMessage(
                Component.translatable("candy.pet.kayip", ad)
                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC),
                false);

            tp.candyOzguven = Math.min(100, tp.candyOzguven + 20);
            CandyBasarimlar.tetikle(player, "dostumun_ardindan");

            Candy.LOG.info("[Candy] PetOlumu: {} → {} sessizce kaybettirdi (bağlanma hedefli)",
                player.getName().getString(), ad);
        }
    }

    /** Verilen adaylar arasından bağlanma haritasında en yüksek ağırlıklı olanı seç. */
    private static Entity enCokBaglanilan(TruePersonality tp, java.util.List<Entity> adaylar) {
        Entity enSik = adaylar.get(0);
        double max = -1;
        for (Entity e : adaylar) {
            double w = tp.baglanmalar.getOrDefault(e.getStringUUID(), 0.0);
            if (w > max) { max = w; enSik = e; }
        }
        return enSik;
    }
}
