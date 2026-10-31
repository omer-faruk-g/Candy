package com.photonpulse.candy.audio;

import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;

/**
 * Tek oyuncuya ses. 1.21.9'da Entity.playSoundToPlayer kaldırıldı, bu yüzden
 * paketi doğrudan o oyuncunun bağlantısına yolluyoruz: ses oyuncunun kendi
 * konumundan çalar ve başkası duymaz — eski metodun davranışı buydu.
 *
 * PlaySoundS2CPacket imzası 1.21.7 ve 1.21.11'de birebir aynı, o yüzden
 * burada sürüm ayrımına gerek yok.
 */
public final class SesGonder {

    private SesGonder() {}

    public static void tek(ServerPlayerEntity player, SoundEvent ses,
                           SoundCategory kategori, float siddet, float perde) {
        if (player == null || player.networkHandler == null) return;
        player.networkHandler.sendPacket(new PlaySoundS2CPacket(
            Registries.SOUND_EVENT.getEntry(ses), kategori,
            player.getX(), player.getY(), player.getZ(),
            siddet, perde, player.getRandom().nextLong()));
    }
}
