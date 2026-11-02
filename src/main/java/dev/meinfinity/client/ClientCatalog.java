package dev.meinfinity.client;

import dev.meinfinity.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=MEInfinityEditor.ID,value=Dist.CLIENT)
public final class ClientCatalog {
    public static int revision;
    public static List<ItemStack> entries(){
        var mc=Minecraft.getInstance();
        if(mc.level==null)return List.of();
        return mc.level.getRecipeManager().getAllRecipesFor(MEInfinityEditor.CATALOG_TYPE.get()).stream()
            .sorted(Comparator.comparing(r -> r.id().toString())).map(r -> r.output().copy()).toList();
    }
    @SubscribeEvent public static void recipes(net.minecraftforge.client.event.RecipesUpdatedEvent e){
        revision++;
        Minecraft.getInstance().execute(() -> {
            var mc=Minecraft.getInstance();
            if(mc.level!=null) MEInfinityEditor.TAB.get().buildContents(new CreativeModeTab.ItemDisplayParameters(mc.level.enabledFeatures(),true,mc.level.registryAccess()));
        });
    }
    @Mod.EventBusSubscriber(modid=MEInfinityEditor.ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Tabs {
        @SubscribeEvent public static void contents(net.minecraftforge.event.BuildCreativeModeTabContentsEvent e){
            if(e.getTab()==MEInfinityEditor.TAB.get()) entries().forEach(e::accept);
        }
    }
}
