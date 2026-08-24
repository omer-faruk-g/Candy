package com.photonpulse.candy.boss;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * GizemliKitap — boss Faz 8'de Candy'yi öldüren imzalı kitap.
 * CUSTOM_DATA marker ile tanınır (rename edilse bile çalışır).
 */
public class GizemliKitap {

    public static final String MARKER = "candy_gizemli_kitap";

    public static ItemStack olustur() {
        ItemStack stack = new ItemStack(Items.WRITTEN_BOOK);
        NbtCompound nbt = new NbtCompound();
        nbt.putBoolean(MARKER, true);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        stack.set(DataComponentTypes.CUSTOM_NAME,
            Text.translatable("candy.item.gizemli_kitap")
                .formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD));
        return stack;
    }

    /** Verilen stack Gizemli Kitap mı? */
    public static boolean esittir(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
        return data != null && data.contains(MARKER);
    }
}
