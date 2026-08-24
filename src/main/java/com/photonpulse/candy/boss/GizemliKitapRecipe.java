package com.photonpulse.candy.boss;

import com.photonpulse.candy.Candy;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

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
public class GizemliKitapRecipe extends SpecialCraftingRecipe {

    public static RecipeSerializer<GizemliKitapRecipe> SERIALIZER;
    public static final Identifier ID = Identifier.of("candy", "gizemli_kitap");

    public GizemliKitapRecipe(CraftingRecipeCategory category) {
        super(category);
    }

    public static void register() {
        SERIALIZER = Registry.register(Registries.RECIPE_SERIALIZER, ID,
            new SpecialCraftingRecipe.SpecialRecipeSerializer<>(GizemliKitapRecipe::new));
        Candy.LOG.info("[Candy] GizemliKitap recipe serializer kayıtlı");
    }

    @Override
    public boolean matches(CraftingRecipeInput input, World world) {
        // Tam 3x3 dolu olmalı
        if (input.getWidth() != 3 || input.getHeight() != 3) return false;
        int sayfa = 0, ruh = 0;
        for (int i = 0; i < input.size(); i++) {
            ItemStack s = input.getStackInSlot(i);
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
    public ItemStack craft(CraftingRecipeInput input, RegistryWrapper.WrapperLookup lookup) {
        return GizemliKitap.olustur();
    }

    @Override
    public RecipeSerializer<? extends SpecialCraftingRecipe> getSerializer() {
        return SERIALIZER;
    }

    private static boolean yirtikSayfaMi(ItemStack s) {
        if (s.getItem() != Items.PAPER) return false;
        var isim = s.get(DataComponentTypes.CUSTOM_NAME);
        return isim != null && isim.getString().startsWith("Yırtık Sayfa");
    }

    private static boolean kirilmisRuhMu(ItemStack s) {
        if (s.getItem() != Items.AMETHYST_SHARD) return false;
        var isim = s.get(DataComponentTypes.CUSTOM_NAME);
        return isim != null && isim.getString().contains("Kırılmış Ruh");
    }
}
