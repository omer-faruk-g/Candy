package com.photonpulse.candy.dimension;

import com.photonpulse.candy.Candy;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * CandyBloklar — modun tek özel bloğu: {@code candy:me}.
 *
 * <p>"ME" dünyası bu bloktan örülür. Blok, istemci tarafında {@link com.photonpulse.candy.client.MeYuz}
 * ile oyuncunun YÜZÜNE boyanır (skin'in yüz bölgesi bu bloğun atlas sprite'ının üstüne
 * yazılır). Özel blok olmasının tek sebebi budur: vanilla bir bloğun sprite'ını ezmek tüm
 * overworld'ü de bozardı; kendi bloğumuz yalnızca ME'de görünür.</p>
 */
public final class CandyBloklar {

    public static final ResourceLocation ME_ID = ResourceLocation.fromNamespaceAndPath(Candy.MOD_ID, "me");
    public static Block ME;

    private CandyBloklar() {}

    public static void register() {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, ME_ID);
        ME = new Block(BlockBehaviour.Properties.of()
            .setId(key)
            .strength(50.0f, 1200.0f)       // zor kırılır ama imkânsız değil
            .sound(SoundType.WOOL));  // sessiz, tuhaf; tam katı küp
        Registry.register(BuiltInRegistries.BLOCK, key, ME);
        Candy.LOG.info("[Candy] candy:me bloğu kayıtlı (ME dünyası)");
    }
}
