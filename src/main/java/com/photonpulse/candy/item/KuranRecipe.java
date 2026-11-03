package com.photonpulse.candy.item;

import com.photonpulse.candy.Candy;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
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
 * KuranRecipe — shaped özel craft → Kuran-ı Kerim.
 *
 * Sonuç özel bileşenli (CUSTOM_DATA marker) olduğu için JSON shaped recipe yetmez;
 * SpecialCraftingRecipe ile desen + sonuç kontrol edilir.
 *
 *   [E][K][E]   E = Büyülü Altın Elma (enchanted_golden_apple)
 *   [D][B][D]   K = Elmas Kılıç (diamond_sword), D = Elmas, B = Kitap (düz)
 *   [E][H][E]   H = Kalkan (shield)
 */
public class KuranRecipe extends CustomRecipe {

    public static RecipeSerializer<KuranRecipe> SERIALIZER;
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("candy", "kuran");

    // 26.x'te CustomRecipe ctor'u parametresiz; craft kitabi kategorisi artik
    // category() metodu ile veriliyor (CustomRecipe varsayilanini kullaniyoruz).
    //? if >=26 {
    /*public KuranRecipe() {}
    */
    //?}
    //? if <26 {
    public KuranRecipe(CraftingBookCategory category) {
        super(category);
    }
    //?}

    public static void register() {
        // 26.x: CustomRecipe.Serializer kaldirildi, RecipeSerializer artik
        // (MapCodec, StreamCodec) record'u. Tarifin alani olmadigi icin ikisi de unit.
        //? if >=26 {
        /*SERIALIZER = Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, ID,
            new RecipeSerializer<>(
                com.mojang.serialization.MapCodec.unit(new KuranRecipe()),
                net.minecraft.network.codec.StreamCodec.unit(new KuranRecipe())));
        */
        //?}
        //? if <26 {
        SERIALIZER = Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, ID,
            new CustomRecipe.Serializer<>(KuranRecipe::new));
        //?}
        Candy.LOG.info("[Candy] Kuran recipe serializer kayıtlı");
    }

    @Override
    public boolean matches(CraftingInput input, Level world) {
        if (input.width() != 3 || input.height() != 3) return false;
        for (int i = 0; i < 9; i++) {
            if (!slotDogru(i, input.getItem(i))) return false;
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

    // 26.x: Recipe.assemble(T) -- HolderLookup.Provider parametresi dustu.
    //? if >=26 {
    /*@Override
    public ItemStack assemble(CraftingInput input) {
        return KuranKitap.olustur();
    }
    */
    //?}
    //? if <26 {
    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider lookup) {
        return KuranKitap.olustur();
    }
    //?}

    @Override
    public RecipeSerializer<? extends CustomRecipe> getSerializer() {
        return SERIALIZER;
    }
}
