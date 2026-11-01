package com.photonpulse.candy.boss;

import com.photonpulse.candy.Candy;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * GizemliKitapRecipe (v3.1) — özel craft: 8 Yırtık Sayfa + 1 Kırılmış Ruh → Gizemli Kitap.
 *
 * Custom-NBT ingredient (özel isimli item) gerektirdiği için JSON shaped recipe yetmez;
 * SpecialCraftingRecipe ile isim kontrolü yapılır.
 *
 *   [P][P][P]   P = Yırtık Sayfa (paper + "Yırtık Sayfa" isim)
 *   [P][R][P]   R = Kırılmış Ruh (amethyst_shard + "Kırılmış Ruh" isim, merkez)
 *   [P][P][P]
 */
public class GizemliKitapRecipe extends CustomRecipe {

    public static RecipeSerializer<GizemliKitapRecipe> SERIALIZER;
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("candy", "gizemli_kitap");

    public GizemliKitapRecipe(CraftingBookCategory category) {
        super(category);
    }

    public static void register() {
        SERIALIZER = Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, ID,
            new CustomRecipe.Serializer<>(GizemliKitapRecipe::new));
        Candy.LOG.info("[Candy] GizemliKitap recipe serializer kayıtlı");
    }

    @Override
    public boolean matches(CraftingInput input, Level world) {
        // Tam 3x3 dolu olmalı
        if (input.width() != 3 || input.height() != 3) return false;
        int sayfa = 0, ruh = 0;
        for (int i = 0; i < input.size(); i++) {
            ItemStack s = input.getItem(i);
            if (s.isEmpty()) return false;
            if (i == 4) { // merkez = Kırılmış Ruh
                if (kirilmisRuhMu(s)) ruh++; else return false;
            } else {      // kenarlar = Yırtık Sayfa
                if (yirtikSayfaMi(s)) sayfa++; else return false;
            }
        }
        return sayfa == 8 && ruh == 1;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider lookup) {
        return GizemliKitap.olustur();
    }

    @Override
    public RecipeSerializer<? extends CustomRecipe> getSerializer() {
        return SERIALIZER;
    }

    private static boolean yirtikSayfaMi(ItemStack s) {
        if (s.getItem() != Items.PAPER) return false;
        var isim = s.get(DataComponents.CUSTOM_NAME);
        return isim != null && isim.getString().startsWith("Yırtık Sayfa");
    }

    private static boolean kirilmisRuhMu(ItemStack s) {
        if (s.getItem() != Items.AMETHYST_SHARD) return false;
        var isim = s.get(DataComponents.CUSTOM_NAME);
        return isim != null && isim.getString().contains("Kırılmış Ruh");
    }
}
