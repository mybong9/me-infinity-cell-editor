package dev.meinfinity;

import com.google.gson.JsonObject;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.crafting.CraftingHelper;

/** Reloadable, server-synced catalogue entry. It never creates a free crafting recipe. */
public record CellCatalogRecipe(ResourceLocation id,ItemStack output) implements Recipe<Container> {
    public boolean matches(Container c,Level level){return false;}
    public ItemStack assemble(Container c,RegistryAccess access){return output.copy();}
    public boolean canCraftInDimensions(int w,int h){return false;}
    public ItemStack getResultItem(RegistryAccess access){return output.copy();}
    public boolean isSpecial(){return true;}
    public ResourceLocation getId(){return id;}
    public RecipeSerializer<?> getSerializer(){return MEInfinityEditor.CATALOG.get();}
    public RecipeType<?> getType(){return MEInfinityEditor.CATALOG_TYPE.get();}
    public static final class Serializer implements RecipeSerializer<CellCatalogRecipe> {
        public CellCatalogRecipe fromJson(ResourceLocation id,JsonObject json){
            ItemStack output=CraftingHelper.getItemStack(json.getAsJsonObject("result"),true);
            if(InfinityCell.samples(output).isEmpty() && ResourceSupport.keys(output).isEmpty())throw new IllegalArgumentException("Catalogue requires a configured infinite cell");
            return new CellCatalogRecipe(id,output);
        }
        public CellCatalogRecipe fromNetwork(ResourceLocation id,FriendlyByteBuf buf){return new CellCatalogRecipe(id,buf.readItem());}
        public void toNetwork(FriendlyByteBuf buf,CellCatalogRecipe recipe){buf.writeItem(recipe.output);}
    }
}
