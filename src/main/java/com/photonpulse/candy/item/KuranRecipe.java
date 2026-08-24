package com.photonpulse.candy.item;

import com.photonpulse.candy.Candy;
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
 * KuranRecipe — shaped özel craft → Kuran-ı Kerim.
 *
 * Sonuç özel bileşenli (CUSTOM_DATA marker) olduğu için JSON shaped recipe yetmez;
 * SpecialCraftingRecipe ile desen + sonuç kontrol edilir.
 *
 *   [E][K][E]   E = Büyülü Altın Elma (enchanted_golden_apple)
 *   [D][B][D]   K = Elmas Kılıç (diamond_sword), D = Elmas, B = Kitap (düz)
 *   [E][H][E]   H = Kalkan (shield)
 */
public class KuranRecipe extends SpecialCraftingRecipe {

    public static RecipeSerializer<KuranRecipe> SERIALIZER;
    public static final Identifier ID = Identifier.of("candy", "kuran");

    public KuranRecipe(CraftingRecipeCategory category) {
        super(category);
    }

    public static void register() {
        SERIALIZER = Registry.register(Registries.RECIPE_SERIALIZER, ID,
            new SpecialCraftingRecipe.SpecialRecipeSerializer<>(KuranRecipe::new));
        Candy.LOG.info("[Candy] Kuran recipe serializer kayıtlı");
    }

    @Override
    public boolean matches(CraftingRecipeInput input, World world) {
        if (input.getWidth() != 3 || input.getHeight() != 3) return false;
        for (int i = 0; i < 9; i++) {
            if (!slotDogru(i, input.getStackInSlot(i))) return false;
        }
        return true;
    }

    private static boolean slotDogru(int i, ItemStack s) {
        if (s.isEmpty()) return false;
        return switch (i) {
            case 0, 2, 6, 8 -> s.getItem() == Items.ENCHANTED_GOLDEN_APPLE; // köşeler
            case 1          -> s.getItem() == Items.DIAMOND_SWORD;          // üst orta
            case 3, 5       -> s.getItem() == Items.DIAMOND;                // yanlar
            case 4          -> s.getItem() == Items.BOOK && !KuranKitap.esittir(s); // merkez düz kitap
            case 7          -> s.getItem() == Items.SHIELD;                 // alt orta = kalkan
            default         -> false;
        };
    }

    @Override
    public ItemStack craft(CraftingRecipeInput input, RegistryWrapper.WrapperLookup lookup) {
        return KuranKitap.olustur();
    }

    @Override
    public RecipeSerializer<? extends SpecialCraftingRecipe> getSerializer() {
        return SERIALIZER;
    }
}
