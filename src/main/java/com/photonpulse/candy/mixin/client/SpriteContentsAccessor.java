package com.photonpulse.candy.mixin.client;

import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.SpriteContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * SpriteContentsAccessor — {@link SpriteContents}'in özel {@code mipmapLevelsImages} alanına
 * erişim. "ME" dünyasında {@code candy:me} bloğunun atlas sprite'ını oyuncunun yüzüyle
 * yeniden boyamak için ({@link com.photonpulse.candy.client.MeYuz}) her mip seviyesinin
 * NativeImage'ına yazmak gerekir.
 */
@Mixin(SpriteContents.class)
public interface SpriteContentsAccessor {
    @Accessor("mipmapLevelsImages")
    NativeImage[] candy$mipmaps();
}
