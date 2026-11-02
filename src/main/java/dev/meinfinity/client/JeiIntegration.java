package dev.meinfinity.client;

import dev.meinfinity.MEInfinityEditor;
import mezz.jei.api.*;
import mezz.jei.api.gui.handlers.*;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.*;

@JeiPlugin
public final class JeiIntegration implements IModPlugin {
    public static IJeiRuntime active;
    public void registerItemSubtypes(mezz.jei.api.registration.ISubtypeRegistration r){r.useNbtForSubtypes(MEInfinityEditor.CELL.get());}
    public ResourceLocation getPluginUid(){return new ResourceLocation(MEInfinityEditor.ID,"jei");}
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        active=runtime;
        var cells=ClientCatalog.entries().stream().filter(s -> s.is(MEInfinityEditor.CELL.get())).toList();
        if(!cells.isEmpty())runtime.getIngredientManager().addIngredientsAtRuntime(mezz.jei.api.constants.VanillaTypes.ITEM_STACK,cells);
        ClientEvents.jeiHovered=() -> {
            Object stack=runtime.getIngredientListOverlay().getIngredientUnderMouse().map(i -> (Object)i.getIngredient()).orElse(ItemStack.EMPTY);
            return stack==null || stack instanceof ItemStack s && s.isEmpty()?runtime.getBookmarkOverlay().getIngredientUnderMouse().map(i -> (Object)i.getIngredient()).orElse(ItemStack.EMPTY):stack;
        };
    }
    public void onRuntimeUnavailable(){active=null;ClientEvents.jeiHovered=() -> ItemStack.EMPTY;}
    public void registerGuiHandlers(IGuiHandlerRegistration r){
        r.addGuiScreenHandler(EditorScreen.class,s -> s.width<=1 || s.height<=1 || s.bounds().getWidth()<=1 || s.bounds().getHeight()<=1?null:new Props(s.getClass(),s.bounds(),s.width,s.height));
        r.addGhostIngredientHandler(EditorScreen.class,new IGhostIngredientHandler<EditorScreen>() {
            public <I> List<Target<I>> getTargetsTyped(EditorScreen screen, ITypedIngredient<I> ingredient,boolean start) {
                if(!dev.meinfinity.ResourceSupport.accepts(ingredient.getIngredient()))return List.of();
                return screen.drops().stream().filter(drop -> drop.resources() || ingredient.getIngredient() instanceof ItemStack).<Target<I>>map(drop -> new Target<>(){
                    public Rect2i getArea(){return drop.area();}
                    public void accept(I value){drop.accept().accept(value);}
                }).toList();
            }
            public void onComplete(){}
        });
    }
    private record Props(Class<? extends Screen> screen,Rect2i area,int width,int height) implements IGuiProperties {
        public Class<? extends Screen> getScreenClass(){return screen;}
        public int getGuiLeft(){return area.getX();}public int getGuiTop(){return area.getY();}
        public int getGuiXSize(){return area.getWidth();}public int getGuiYSize(){return area.getHeight();}
        public int getScreenWidth(){return width;}public int getScreenHeight(){return height;}
    }
}
