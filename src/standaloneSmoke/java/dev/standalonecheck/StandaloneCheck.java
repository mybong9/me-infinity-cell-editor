package dev.standalonecheck;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.StorageCells;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.TagParser;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import java.nio.file.*;

@Mod("me_infinity_standalone_check")
public final class StandaloneCheck {
    int stage,ticks,total,revision,before;
    ItemStack expected;
    public StandaloneCheck(){MinecraftForge.EVENT_BUS.addListener(this::tick);MinecraftForge.EVENT_BUS.addListener(this::recipes);}
    void recipes(net.minecraftforge.client.event.RecipesUpdatedEvent e){revision++;}
    void check(boolean condition,String label){if(!condition)throw new AssertionError(label);}
    ItemStack expected() throws Exception {
        if(expected==null){
            var json=com.google.gson.JsonParser.parseString(Files.readString(Path.of("kubejs/config/me_infinity_editor/catalog.json"))).getAsJsonObject();
            expected=new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("kubejs","me_infinity_cell")));
            expected.setTag(TagParser.parseTag(json.get("runtime_smoke").getAsString()));
        }return expected;
    }
    void tick(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;
        try{
            var mc=Minecraft.getInstance();if(++total>2400)throw new AssertionError("standalone timeout");
            if(stage==0 && mc.screen!=null && mc.getOverlay()==null && ++ticks>30){
                check(!net.minecraftforge.fml.ModList.get().isLoaded("me_infinity_editor"),"editor absent");
                stage=1;ticks=0;
                mc.createWorldOpenFlows().createFreshLevel("Standalone-"+System.currentTimeMillis(),new LevelSettings("Standalone check",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(0,false,false),access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions());
            }
            if(stage==1 && mc.player!=null && ++ticks>40){
                var server=mc.getSingleplayerServer();
                for(int i=0;i<2;i++){
                    var recipe=server.getRecipeManager().byKey(new ResourceLocation("me_infinity_editor","runtime_smoke_"+i)).orElseThrow();
                    check(ItemStack.isSameItemSameTags(recipe.getResultItem(server.registryAccess()),expected()),"standalone recipe output "+i);
                }
                var inv=StorageCells.getCellInventory(expected(),null);check(inv!=null,"scripted cell handler");
                var sample=new ItemStack(Items.DIAMOND);sample.getOrCreateTag().putString("variant","a");var key=AEItemKey.of(sample);
                var counts=inv.getAvailableStacks();System.out.println("ME_STANDALONE: AMOUNT="+counts.get(key)+" EXPECTED_KEY="+key+" FIRST="+counts.getFirstEntry());
                check(counts.get(key)==Long.MAX_VALUE,"int64 quantity");
                check(inv.extract(key,64,Actionable.MODULATE,IActionSource.empty())==64,"infinite extraction");
                sample.getOrCreateTag().putString("variant","missing");check(inv.extract(AEItemKey.of(sample),1,Actionable.MODULATE,IActionSource.empty())==0,"NBT distinction");
                before=revision;stage=2;ticks=0;server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"reload"));
            }
            if(stage==2 && revision>before && ++ticks>40){
                check(mezz.jei.common.Internal.getJeiRuntime().getIngredientManager().getAllItemStacks().stream().anyMatch(s -> ItemStack.isSameItemSameTags(s,expected)),"JEI after reload");
                System.out.println("ME_STANDALONE: EDITOR_ABSENT_RECIPES_NBT_INT64_EXTRACTION_RELOAD_JEI_PASS");stage=3;ticks=0;
            }
            if(stage==3 && ++ticks>10)mc.stop();
        }catch(Exception exception){throw new RuntimeException(exception);}
    }
}
