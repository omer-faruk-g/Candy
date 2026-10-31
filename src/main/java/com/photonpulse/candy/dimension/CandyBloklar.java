package com.photonpulse.candy.dimension;

import com.photonpulse.candy.Candy;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

/**
 * CandyBloklar — modun tek özel bloğu: {@code candy:me}.
 *
 * <p>"ME" dünyası bu bloktan örülür. Blok, istemci tarafında {@link com.photonpulse.candy.client.MeYuz}
 * ile oyuncunun YÜZÜNE boyanır (skin'in yüz bölgesi bu bloğun atlas sprite'ının üstüne
 * yazılır). Özel blok olmasının tek sebebi budur: vanilla bir bloğun sprite'ını ezmek tüm
 * overworld'ü de bozardı; kendi bloğumuz yalnızca ME'de görünür.</p>
 */
public final class CandyBloklar {

    public static final Identifier ME_ID = Identifier.of(Candy.MOD_ID, "me");
    public static Block ME;

    private CandyBloklar() {}

    public static void register() {
        RegistryKey<Block> key = RegistryKey.of(RegistryKeys.BLOCK, ME_ID);
        ME = new Block(AbstractBlock.Settings.create()
            .registryKey(key)
            .strength(50.0f, 1200.0f)       // zor kırılır ama imkânsız değil
            .sounds(BlockSoundGroup.WOOL));  // sessiz, tuhaf; tam katı küp
        Registry.register(Registries.BLOCK, key, ME);
        Candy.LOG.info("[Candy] candy:me bloğu kayıtlı (ME dünyası)");
    }
}
