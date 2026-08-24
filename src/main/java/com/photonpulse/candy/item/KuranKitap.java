package com.photonpulse.candy.item;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

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
        NbtCompound nbt = new NbtCompound();
        nbt.putBoolean(MARKER, true);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        // İsim her dilde aynı — çeviri yok
        stack.set(DataComponentTypes.CUSTOM_NAME,
            Text.literal("Kuran-ı Kerim")
                .styled(s -> s.withColor(Formatting.GOLD).withBold(true).withItalic(false)));
        stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true); // kutsal parıltı
        stack.set(DataComponentTypes.MAX_STACK_SIZE, 1);
        return stack;
    }

    /** Verilen stack Kuran-ı Kerim mi? */
    public static boolean esittir(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getItem() != Items.BOOK) return false;
        NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
        return data != null && data.contains(MARKER);
    }
}
