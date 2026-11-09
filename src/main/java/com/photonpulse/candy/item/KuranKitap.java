package com.photonpulse.candy.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

/**
 * KuranKitap — Kuran-ı Kerim eşyası.
 *
 * Düz {@code minecraft:book} tabanı kullanılır — sağ tıkta AÇILMAZ (yazılı kitap değil).
 * CUSTOM_DATA marker ile tanınır (rename edilse bile). İsmi her dilde AYNI ("Kuran-ı Kerim")
 * — çeviri yok, modun tek İngilizcesiz eşyası.
 *
 * Davranış {@link KuranSistemi}'nde: sağ tık → Ayetel Kürsi (Arapça), sol tık → tüketilir,
 * önce çile (zehir + körlük), sonra "İmanın Gücü" nimeti.
 */
public class KuranKitap {

    public static final String MARKER = "candy_kuran";

    public static ItemStack olustur() {
        ItemStack stack = new ItemStack(Items.BOOK); // düz kitap — açılmaz
        CompoundTag nbt = new CompoundTag();
        nbt.putBoolean(MARKER, true);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
        // İsim her dilde aynı — çeviri yok
        stack.set(DataComponents.CUSTOM_NAME,
            Component.literal("Kuran-ı Kerim")
                .withStyle(s -> s.withColor(ChatFormatting.GOLD).withBold(true).withItalic(false)));
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true); // kutsal parıltı
        stack.set(DataComponents.MAX_STACK_SIZE, 1);
        return stack;
    }

    /** Verilen stack Kuran-ı Kerim mi? */
    public static boolean esittir(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getItem() != Items.BOOK) return false;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().contains(MARKER);
    }
}
