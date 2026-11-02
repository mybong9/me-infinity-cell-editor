package dev.meinfinity;

import com.google.gson.JsonObject;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraftforge.common.crafting.CraftingHelper;

/** Vanilla stonecutter behavior with an NBT-aware output codec. */
public final class CellStonecuttingRecipe extends StonecutterRecipe {
    public CellStonecuttingRecipe(ResourceLocation id,String group,Ingredient input,ItemStack output) {super(id,group,input,output);}
    @Override public RecipeSerializer<?> getSerializer(){return MEInfinityEditor.STONECUTTING.get();}
    public static final class Serializer implements RecipeSerializer<CellStonecuttingRecipe> {
        public CellStonecuttingRecipe fromJson(ResourceLocation id,JsonObject json) {
            return new CellStonecuttingRecipe(id,GsonHelper.getAsString(json,"group",""),Ingredient.fromJson(json.get("ingredient")),CraftingHelper.getItemStack(GsonHelper.getAsJsonObject(json,"result"),true));
        }
        public CellStonecuttingRecipe fromNetwork(ResourceLocation id,FriendlyByteBuf buf) {
            return new CellStonecuttingRecipe(id,buf.readUtf(),Ingredient.fromNetwork(buf),buf.readItem());
        }
        public void toNetwork(FriendlyByteBuf buf,CellStonecuttingRecipe recipe) {
            buf.writeUtf(recipe.getGroup());recipe.getIngredients().get(0).toNetwork(buf);buf.writeItem(recipe.getResultItem(RegistryAccess.EMPTY));
        }
    }
}
