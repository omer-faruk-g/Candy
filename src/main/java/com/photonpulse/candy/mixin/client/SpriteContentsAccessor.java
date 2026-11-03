package com.photonpulse.candy.mixin.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.SpriteContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * SpriteContentsAccessor — {@link SpriteContents}'in özel {@code byMipLevel} alanına
 * erişim. "ME" dünyasında {@code candy:me} bloğunun atlas sprite'ını oyuncunun yüzüyle
 * yeniden boyamak için ({@link com.photonpulse.candy.client.MeYuz}) her mip seviyesinin
 * NativeImage'ına yazmak gerekir.
 */
@Mixin(SpriteContents.class)
public interface SpriteContentsAccessor {
    @Accessor("byMipLevel")
    NativeImage[] candy$mipmaps();
}
