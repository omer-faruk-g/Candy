package com.photonpulse.candy.boss;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

/**
 * GizemliKitap — boss Faz 8'de Candy'yi öldüren imzalı kitap.
 * CUSTOM_DATA marker ile tanınır (rename edilse bile çalışır).
 */
public class GizemliKitap {

    public static final String MARKER = "candy_gizemli_kitap";

    public static ItemStack olustur() {
        ItemStack stack = new ItemStack(Items.WRITTEN_BOOK);
        CompoundTag nbt = new CompoundTag();
        nbt.putBoolean(MARKER, true);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
        stack.set(DataComponents.CUSTOM_NAME,
            Component.translatable("candy.item.gizemli_kitap")
                .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
        return stack;
    }

    /** Verilen stack Gizemli Kitap mı? */
    public static boolean esittir(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().contains(MARKER);
    }
}
