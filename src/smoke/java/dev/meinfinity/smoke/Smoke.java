package dev.meinfinity.smoke;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.api.storage.StorageCells;
import dev.meinfinity.*;
import dev.meinfinity.client.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import appeng.core.definitions.AEBlocks;
import appeng.blockentity.storage.DriveBlockEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import java.util.*;

@Mod("me_infinity_smoke")
public final class Smoke {
    static int tick,stage;static boolean ready;static BlockPos drivePos;
    public Smoke() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
        MinecraftForge.EVENT_BUS.addListener(this::tick);
        MinecraftForge.EVENT_BUS.addListener(this::recipesUpdated);
    }
    static void check(boolean passed,String message) {if(!passed)throw new AssertionError(message);}
    void setup(FMLCommonSetupEvent e) {e.enqueueWork(() -> {
        try {
            if(Boolean.getBoolean("meInfinity.standaloneSmoke")) {
                check(!net.minecraftforge.fml.ModList.get().isLoaded("me_infinity_editor"),"editor JAR absent");
                CellDocument doc=new CellDocument();doc.id="runtime_smoke";doc.name="独立 KubeJS 无限元件";
                ItemStack a=new ItemStack(Items.DIAMOND);a.getOrCreateTag().putString("variant","a");doc.add(a);
                ItemStack b=a.copy();b.getOrCreateTag().putString("variant","b");doc.add(b);
                for(String type:List.of("shapeless","stonecutting")){var r=new CellDocument.Recipe();r.type=type;r.grid[0]=a;doc.recipes.add(r);}
                DocumentFiles.export(doc);ClientEvents.document=doc;ready=true;return;
            }
            if(Boolean.getBoolean("meInfinity.catalogSmoke")) {
                CellDocument doc=new CellDocument();doc.id="runtime_smoke";doc.name="重载测试无限元件";
                doc.add(new ItemStack(Items.DIAMOND));doc.add(new ItemStack(Items.GOLD_INGOT));
                DocumentFiles.export(doc);ClientEvents.document=doc;ready=true;return;
            }
            CellDocument doc=new CellDocument();doc.id="runtime_smoke";
            ItemStack a=new ItemStack(Items.DIAMOND); a.getOrCreateTag().putString("variant","a");
            ItemStack b=a.copy();b.getOrCreateTag().putString("variant","b");
            check(doc.add(a),"first NBT variant");check(doc.add(b),"second NBT variant");check(!doc.add(a),"deduplicate exact NBT");
            a.getOrCreateTag().putString("variant","mutated");
            check(doc.items.get(0).getTag().getString("variant").equals("a"),"defensive copy");
            doc.add(new ItemStack(Items.NETHERITE_INGOT));doc.add(new ItemStack(Items.REDSTONE));
            var cell=doc.output();var inv=new InfinityCell.Inventory(cell);var key=AEItemKey.of(doc.items.get(0));
            var source=IActionSource.empty();
            for(var mode:Actionable.values()) {
                check(inv.extract(key,Long.MAX_VALUE,mode,source)==Long.MAX_VALUE,"unlimited extract");
                check(inv.extract(AEItemKey.of(a),64,mode,source)==0,"wrong NBT rejected");
                check(inv.insert(key,64,mode,source)==0,"insertion rejected");
            }
            check(inv.getAvailableStacks().size()==4,"advertised types");
            check(inv.getAvailableStacks().get(key)==Long.MAX_VALUE,"int64 advertised amount");
            check(InfinityCell.samples(ItemStack.of(cell.save(new CompoundTag()))).size()==4,"cell serialization");
            check(CellDocument.read(doc.json()).json().equals(doc.json()),"draft roundtrip");
            for(String type:List.of("shaped","shapeless","smelting","blasting","smoking","campfire_cooking","stonecutting","custom")) {
                var r=new CellDocument.Recipe();r.type=type;r.grid[4]=b.copy();doc.recipes.add(r);
                check(!DocumentFiles.script(doc).contains("$OUTPUT"),"replace recipe output");
            }
            DocumentFiles.export(doc);DocumentFiles.save(doc);
            check(DocumentFiles.load(doc.id).json().equals(doc.json()),"disk roundtrip");
            ClientEvents.document=doc;ready=true;
            System.out.println("ME_INFINITY_SMOKE: CORE_NBT_STORAGE_EXPORT_PASS");
        } catch(Exception error) {throw new RuntimeException(error);}
    });}
    void tick(TickEvent.ClientTickEvent e) {
        if(e.phase!=TickEvent.Phase.END || !ready)return;
        var mc=Minecraft.getInstance();
        if(Boolean.getBoolean("meInfinity.standaloneSmoke")){standaloneTick(mc);return;}
        if(Boolean.getBoolean("meInfinity.catalogSmoke")){catalogTick(mc);return;}
        if(stage==0 && mc.screen!=null && mc.getOverlay()==null && ++tick>40) {
            mc.options.guiScale().set(2);mc.resizeDisplay();tick=0;
            if(Boolean.getBoolean("meInfinity.smokeWorld")) {
                stage=4;
                mc.createWorldOpenFlows().createFreshLevel("ME-Infinity-Smoke-"+System.currentTimeMillis(),
                    new LevelSettings("ME Infinity smoke",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),
                    new WorldOptions(0L,false,false),access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions());
            } else {mc.setScreen(new EditorScreen());stage=1;}
        }
        if(stage==4 && mc.player!=null && mc.getSingleplayerServer()!=null && ++tick>40) {
            var server=mc.getSingleplayerServer();
            for(int i=0;i<8;i++) {
                var recipe=server.getRecipeManager().byKey(new ResourceLocation("me_infinity_editor","runtime_smoke_"+i)).orElseThrow();
                var output=recipe.getResultItem(server.registryAccess());
                check(output.is(MEInfinityEditor.CELL.get()),"recipe output item "+i);
                check(InfinityCell.samples(output).size()==4,"recipe output NBT "+i);
                if(i<7) {
                    var ingredient=recipe.getIngredients().get(0);
                    check(ingredient.test(ClientEvents.document.items.get(1)),"recipe matching NBT "+i);
                    check(!ingredient.test(ClientEvents.document.items.get(0)),"recipe rejects different NBT "+i);
                }
            }
            System.out.println("ME_INFINITY_SMOKE: KUBEJS_WORLD_RECIPES_8_PASS");
            MEInfinityEditor.NETWORK.sendToServer(new CreateCellPacket(ClientEvents.document.name,List.copyOf(ClientEvents.document.items)));
            stage=5;tick=0;
        }
        if(stage==5 && ++tick>40) {
            check(java.util.stream.IntStream.range(0,mc.player.getInventory().getContainerSize()).mapToObj(i -> mc.player.getInventory().getItem(i)).anyMatch(s -> s.is(MEInfinityEditor.CELL.get()) && InfinityCell.samples(s).size()==4),"server-authorized generation");
            System.out.println("ME_INFINITY_SMOKE: NETWORK_GENERATE_PASS");
            var server=mc.getSingleplayerServer();
            server.submit(() -> {
                var world=server.overworld();drivePos=server.getPlayerList().getPlayers().get(0).blockPosition().above(4);
                world.setBlockAndUpdate(drivePos,AEBlocks.DRIVE.block().defaultBlockState());
                world.setBlockAndUpdate(drivePos.east(),AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
                ((DriveBlockEntity)world.getBlockEntity(drivePos)).getInternalInventory().setItemDirect(0,ClientEvents.document.output());
            }).join();stage=6;tick=0;
        }
        if(stage==6 && ++tick>100) {
            var server=mc.getSingleplayerServer();
            server.submit(() -> {
                var drive=(DriveBlockEntity)server.overworld().getBlockEntity(drivePos);
                var storage=drive.getMainNode().getGrid().getStorageService().getInventory();
                var key=AEItemKey.of(ClientEvents.document.items.get(0));
                check(storage.getAvailableStacks().get(key)==Long.MAX_VALUE,"ME grid advertises int64 quantity");
                for(int i=0;i<3;i++)check(storage.extract(key,1000000,Actionable.MODULATE,IActionSource.empty())==1000000,"ME drive repeated extraction");
                ItemStack wrong=ClientEvents.document.items.get(0).copy();wrong.getOrCreateTag().putString("variant","missing");
                check(storage.extract(AEItemKey.of(wrong),1,Actionable.MODULATE,IActionSource.empty())==0,"ME drive exact NBT");
                System.out.println("ME_INFINITY_SMOKE: POWERED_ME_DRIVE_EXTRACTION_PASS");
                System.out.println("ME_INFINITY_SMOKE: INT64_QUANTITY_PASS 9223372036854775807");
            }).join();mc.setScreen(new EditorScreen());stage=1;tick=0;
        }
        if(stage==1 && ++tick==80) {
            check(StorageCells.getCellInventory(ClientEvents.document.output(),null)!=null,"registered handler");
            Screenshot.grab(mc.gameDirectory,"me-infinity-editor-content.png",mc.getMainRenderTarget(),c -> System.out.println("ME_INFINITY_SMOKE: "+c.getString()));
            System.out.println("ME_INFINITY_SMOKE: CLIENT_SCREEN_RENDER_PASS");
            ((EditorScreen)mc.screen).children().stream().filter(w -> w instanceof net.minecraft.client.gui.components.Button b && b.getMessage().getString().equals("配方设置")).map(w -> (net.minecraft.client.gui.components.Button)w).findFirst().orElseThrow().onPress();
            stage=2;tick=0;
        }
        if(stage==2 && ++tick==30){Screenshot.grab(mc.gameDirectory,"me-infinity-editor-recipes.png",mc.getMainRenderTarget(),c -> System.out.println("ME_INFINITY_SMOKE: "+c.getString()));stage=3;tick=0;}
        if(stage==3 && ++tick==30) {
            ((EditorScreen)mc.screen).children().stream().filter(w -> w instanceof net.minecraft.client.gui.components.Button b && b.getMessage().getString().equals("存储目录")).map(w -> (net.minecraft.client.gui.components.Button)w).findFirst().orElseThrow().onPress();
            stage=7;tick=0;
        }
        if(stage==7 && ++tick==30) {
            Screenshot.grab(mc.gameDirectory,"me-infinity-editor-paths.png",mc.getMainRenderTarget(),c -> System.out.println("ME_INFINITY_SMOKE: "+c.getString()));
            var key=Arrays.stream(mc.options.keyMappings).filter(k -> k.getName().equals("key.me_infinity_editor.open")).findFirst().orElseThrow();
            check(Arrays.stream(mc.options.keyMappings).anyMatch(k -> k.getName().equals("key.me_infinity_editor.mark")),"mark binding registered");
            var old=key.getKey();
            key.setKey(com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(org.lwjgl.glfw.GLFW.GLFW_KEY_F9));
            net.minecraft.client.KeyMapping.resetMapping();
            var binds=new net.minecraft.client.gui.screens.controls.KeyBindsScreen(mc.screen,mc.options);mc.setScreen(binds);
            var event=new net.minecraftforge.client.event.ScreenEvent.KeyPressed.Pre(binds,org.lwjgl.glfw.GLFW.GLFW_KEY_F9,0,0);
            ClientEvents.key(event);check(!event.isCanceled() && mc.screen==binds,"binding screen not intercepted");
            var parent=new net.minecraft.client.gui.screens.Screen(net.minecraft.network.chat.Component.empty()){};
            ClientEvents.key(new net.minecraftforge.client.event.ScreenEvent.KeyPressed.Pre(parent,org.lwjgl.glfw.GLFW.GLFW_KEY_F9,0,0));
            check(mc.screen instanceof EditorScreen,"rebound key opens editor");
            key.setKey(old);net.minecraft.client.KeyMapping.resetMapping();
            System.out.println("ME_INFINITY_SMOKE: KEY_REBIND_AND_PATHS_PASS");stage=8;tick=0;
        }
        if(stage==8 && ++tick==30)mc.stop();
    }
    static int revisionBefore,totalTicks;
    static int standaloneRevision;
    public void recipesUpdated(net.minecraftforge.client.event.RecipesUpdatedEvent e){standaloneRevision++;}
    void standaloneTick(Minecraft mc){
        if(++totalTicks>2400)throw new AssertionError("standalone timeout");
        if(stage==0 && mc.screen!=null && mc.getOverlay()==null && ++tick>30){
            stage=20;tick=0;
            mc.createWorldOpenFlows().createFreshLevel("ME-Standalone-"+System.currentTimeMillis(),new LevelSettings("Standalone check",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(0,false,false),access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions());
        }
        if(stage==20 && mc.player!=null && ++tick>40){
            var server=mc.getSingleplayerServer();
            for(int i=0;i<2;i++){
                var recipe=server.getRecipeManager().byKey(new ResourceLocation("me_infinity_editor","runtime_smoke_"+i)).orElseThrow();
                check(InfinityCell.samples(recipe.getResultItem(server.registryAccess())).size()==2,"standalone recipe NBT "+i);
            }
            var cell=ClientEvents.document.output();check(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(cell.getItem()).toString().equals("kubejs:me_infinity_cell"),"standalone item");
            var inv=StorageCells.getCellInventory(cell,null);check(inv!=null,"standalone handler");
            var key=AEItemKey.of(ClientEvents.document.items.get(0));
            check(inv.getAvailableStacks().get(key)==Long.MAX_VALUE,"standalone int64");check(inv.extract(key,64,Actionable.MODULATE,IActionSource.empty())==64,"standalone extraction");
            revisionBefore=standaloneRevision;stage=21;tick=0;
            server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"reload"));
        }
        if(stage==21 && standaloneRevision>revisionBefore && ++tick>40){
            var cell=ClientEvents.document.output();
            check(mezz.jei.common.Internal.getJeiRuntime().getIngredientManager().getAllItemStacks().stream().anyMatch(s -> ItemStack.isSameItemSameTags(s,cell)),"standalone JEI reload");
            System.out.println("ME_INFINITY_SMOKE: EDITOR_ABSENT_KUBEJS_RECIPES_INT64_EXTRACTION_RELOAD_JEI_PASS");stage=22;tick=0;
        }
        if(stage==22 && ++tick>10)mc.stop();
    }
    void catalogTick(Minecraft mc) {
        if(++totalTicks>1800)throw new AssertionError("catalogue smoke timeout");
        if(stage==0 && mc.screen!=null && mc.getOverlay()==null && ++tick>40){
            mc.options.guiScale().set(2);mc.resizeDisplay();stage=10;tick=0;
            mc.createWorldOpenFlows().createFreshLevel("ME-Catalog-Smoke-"+System.currentTimeMillis(),
                new LevelSettings("ME catalogue smoke",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),
                new WorldOptions(0L,false,false),access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions());
        }
        if(stage==10 && mc.player!=null && ++tick>40){
            check(ClientCatalog.entries().size()==1,"zero-recipe export is visible in client catalogue");
            revisionBefore=ClientCatalog.revision;stage=11;tick=0;
            var server=mc.getSingleplayerServer();server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"reload"));
        }
        if(stage==11 && ClientCatalog.revision>revisionBefore && JeiIntegration.active!=null && ++tick>40){
            var expected=ClientEvents.document.output();
            check(ClientCatalog.entries().size()==1,"catalogue survives reload");
            check(JeiIntegration.active.getIngredientManager().getAllItemStacks().stream().anyMatch(s -> ItemStack.isSameItemSameTags(s,expected)),"export visible in JEI after reload");
            check(MEInfinityEditor.TAB.get().getDisplayItems().stream().anyMatch(s -> ItemStack.isSameItemSameTags(s,expected)),"creative catalogue after reload");
            System.out.println("ME_INFINITY_SMOKE: ZERO_RECIPE_EXPORT_RELOAD_JEI_CREATIVE_PASS");
            mc.setScreen(new net.minecraft.client.gui.screens.Screen(net.minecraft.network.chat.Component.literal("元件图标检查")){
                public void render(net.minecraft.client.gui.GuiGraphics g,int mx,int my,float dt){
                    renderBackground(g);g.drawString(font,"无限元件 · 物品贴图预览",50,30,0xFFFFFFFF);
                    g.pose().pushPose();g.pose().translate(50,65,0);g.pose().scale(4,4,4);g.renderItem(expected,0,0);g.pose().popPose();
                    g.renderTooltip(font,expected,155,80);
                }
            });stage=12;tick=0;
        }
        if(stage==12 && ++tick==30){Screenshot.grab(mc.gameDirectory,"cell-icons-1.0.2.png",mc.getMainRenderTarget(),c -> System.out.println(c.getString()));stage=13;tick=0;}
        if(stage==13 && ++tick==20)mc.stop();
    }
}
