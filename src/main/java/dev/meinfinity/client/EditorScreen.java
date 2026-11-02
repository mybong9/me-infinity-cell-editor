package dev.meinfinity.client;

import dev.meinfinity.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;
import java.util.function.Consumer;

public final class EditorScreen extends Screen {
    private CellDocument doc=ClientEvents.document;
    private int left,top,w,h,tab=0,libraryPage=0,selectionPage=0,recipeIndex=0;
    private boolean light=false,inventoryOnly=false;
    private int resourceType;
    private String query="", status="左侧点击添加；右侧点击移除。支持 JEI 拖放及背包物品 NBT。";
    private EditBox id,name,search,itemId,nbt,time,xp;
    private ItemStack brush=ItemStack.EMPTY;
    private final List<Hit> hits=new ArrayList<>();
    private final List<Drop> drops=new ArrayList<>();
    private final List<Label> labels=new ArrayList<>();
    private static final String[] TYPES={"shaped","shapeless","smelting","blasting","smoking","campfire_cooking","stonecutting","custom"};
    private static final String[] NAMES={"有序合成","无序合成","熔炉","高炉","烟熏炉","营火","切石机","自定义机器 JSON"};
    private record Hit(Rect2i area,ItemStack stack,Runnable action) {}
    public record Drop(Rect2i area, Consumer<Object> accept,boolean resources) {
        public Drop(Rect2i area,Consumer<ItemStack> accept){this(area,v -> {if(v instanceof ItemStack s)accept.accept(s.copy());},false);}
    }
    private record Label(String value,int x,int y) {}
    public EditorScreen() { super(Component.literal("ME无限原件编辑")); }
    private int fg() {return light?0xFF253140:0xFFE8ECF3;}
    private int muted() {return light?0xFF657284:0xFFA0ADC0;}
    private void label(String text,int x,int y) { labels.add(new Label(text,x,y)); }
    private Button button(String text,int x,int y,int width,Runnable action) {
        return addRenderableWidget(new ThemeButton(text,x,y,width,action));
    }
    private final class ThemeButton extends Button {
        ThemeButton(String text,int x,int y,int width,Runnable action) {super(x,y,width,20,Component.literal(text),b -> action.run(),DEFAULT_NARRATION);}
        @Override protected void renderWidget(GuiGraphics g,int mx,int my,float dt) {
            int fill=isHoveredOrFocused()?(light?0xFFDCE5F3:0xFF35445A):(light?0xFFECEFF4:0xFF283344);
            surface(g,getX(),getY(),getWidth(),getHeight(),fill,light?0xFFD1D8E2:0xFF3A475A);
            g.drawCenteredString(font,getMessage(),getX()+getWidth()/2,getY()+6,fg());
        }
    }
    private static void surface(GuiGraphics g,int x,int y,int width,int height,int fill,int border) {
        RoundedSurface.draw(g,x,y,width,height,height>100?10:5,fill,border);
    }
    private EditBox field(String value,int x,int y,int width,int max,Consumer<String> changed) {
        EditBox box=new EditBox(font,x+5,y+5,width-10,8,Component.literal(value)) {
            @Override public void renderWidget(GuiGraphics g,int mx,int my,float dt) {
                surface(g,getX()-5,getY()-5,getWidth()+10,18,light?0xFFFFFFFF:0xFF111720,isFocused()?0xFF93ACF6:(light?0xFFD1D8E2:0xFF3A475A));
                setTextColor(fg());setTextColorUneditable(muted());super.renderWidget(g,mx,my,dt);
            }
        };
        box.setBordered(false);box.setMaxLength(max);box.setValue(value);box.setResponder(changed);addRenderableWidget(box);return box;
    }
    @Override protected void init() {
        hits.clear();drops.clear();labels.clear();
        w=Math.min(640,width-(width>=600?180:16));h=Math.min(350,height-16);left=(width-w)/2;top=(height-h)/2;
        if(w<390 || h<270) {
            label("请在视频设置中减小 GUI 缩放，以显示完整编辑器。",left+12,top+44);
            button("关闭",left+12,top+80,80,this::onClose);return;
        }
        int x=left+12,y=top+35,main=left+164,mw=w-176;
        label("ME无限原件编辑",x,top+13);
        button("帮助",left+w-164,top+6,48,() -> minecraft.setScreen(new HelpScreen(this)));
        button("按键",left+w-112,top+6,48,() -> minecraft.setScreen(new net.minecraft.client.gui.screens.controls.KeyBindsScreen(this,minecraft.options)));
        button(light?"浅色":"深色",left+w-60,top+6,48,() -> {light=!light;rebuildWidgets();});
        button("元件内容",main,y,76,() -> {tab=0;rebuildWidgets();});
        button("配方设置",main+80,y,76,() -> {tab=1;rebuildWidgets();});
        button("存储目录",main+160,y,76,() -> {tab=2;rebuildWidgets();});
        label(inventoryOnly?"背包（保留 NBT）":"资源库 / 搜索",x,y+2);
        search=field(query,x,y+24,138,200,s -> {query=s;libraryPage=0;refreshLibrary();});
        button(ResourceSupport.NAMES[resourceType]+" ▸",x,y+48,88,() -> {resourceType=(resourceType+1)%(ResourceSupport.mekanism()?6:2);inventoryOnly=false;libraryPage=0;rebuildWidgets();});
        button(inventoryOnly?"资源":"背包",x+92,y+48,46,() -> {inventoryOnly=!inventoryOnly;resourceType=0;libraryPage=0;rebuildWidgets();});
        button("上一页",x,top+h-82,65,() -> {libraryPage=Math.max(0,libraryPage-1);rebuildWidgets();});
        button("下一页",x+70,top+h-82,68,() -> {libraryPage++;rebuildWidgets();});
        refreshLibrary();
        if(tab==0) content(main,y+30,mw); else if(tab==1) recipes(main,y+30,mw);else locations(main,y+30,mw);
        button("保存草稿",x,top+h-53,76,() -> run(() -> {DocumentFiles.save(doc);status="草稿已保存："+doc.id;}));
        button("读取 ID",x+80,top+h-53,72,() -> run(() -> {doc=DocumentFiles.load(doc.id);ClientEvents.document=doc;recipeIndex=0;selectionPage=0;rebuildWidgets();status="已读取草稿";}));
        button("导出 KubeJS",x+156,top+h-53,92,() -> run(() -> {var file=DocumentFiles.export(doc);status="已导出："+file.getFileName()+"；首次独立导出请重启，之后 /reload";}));
        button("生成元件",x+252,top+h-53,76,() -> run(() -> {doc.validate();MEInfinityEditor.NETWORK.sendToServer(new CreateCellPacket(doc.name,List.copyOf(doc.items),List.copyOf(doc.resources)));status="已发送生成请求（创造模式 / 管理员）";}));
    }
    private List<ItemStack> library() {
        List<ItemStack> result=new ArrayList<>();
        if(resourceType!=0){try{result.addAll(ResourceSupport.library(ResourceSupport.TYPES[resourceType]));}catch(IllegalArgumentException e){status=e.getMessage();}}
        else if(inventoryOnly && minecraft.player!=null) {
            for(int i=0;i<minecraft.player.getInventory().getContainerSize();i++) {var stack=minecraft.player.getInventory().getItem(i);if(!stack.isEmpty()) result.add(stack.copy());}
        } else ForgeRegistries.ITEMS.getValues().stream().filter(i -> i!=Items.AIR).forEach(i -> result.add(new ItemStack(i)));
        String q=query.toLowerCase(Locale.ROOT);
        result.removeIf(s -> !ResourceSupport.searchText(s).toLowerCase(Locale.ROOT).contains(q));
        return result;
    }
    private void refreshLibrary() {
        hits.removeIf(hit -> hit.area.getX()<left+155);
        if(h<270 || w<390)return;
        List<ItemStack> list=library();int rows=Math.max(1,(h-192)/23),count=6*rows;
        libraryPage=Math.min(libraryPage,Math.max(0,(list.size()-1)/count));
        for(int i=0;i<count && libraryPage*count+i<list.size();i++) {
            var stack=list.get(libraryPage*count+i);
            hits.add(new Hit(new Rect2i(left+12+(i%6)*23,top+110+(i/6)*23,21,21),stack,() -> {
                if(tab==0) accept(stack);else if(tab==1){if(resourceType!=0){status="流体和化学配方请使用自定义机器 JSON。";return;}brush=stack.copyWithCount(1);status="配方材料画笔："+stack.getHoverName().getString()+"；点击配方槽填入";}
            }));
        }
    }
    private void accept(ItemStack stack) {
        acceptResource(stack);
    }
    private void acceptResource(Object value){
        run(() -> {status=doc.addObject(value)?"已添加资源（NBT 已保留）":"资源已存在、为空或已达到 256 种上限";rebuildWidgets();});
    }
    private void content(int x,int y,int width) {
        int half=(width-8)/2;
        label("元件 ID / 草稿文件名",x,y);
        id=field(doc.id,x,y+13,half,64,s -> doc.id=s);
        label("显示名称",x+half+8,y);
        name=field(doc.name,x+half+8,y+13,half,80,s -> doc.name=s);
        label("无限供给列表："+doc.size()+" / 256（可混合，点击移除）",x,y+39);
        int cols=Math.max(1,width/23),rows=Math.max(1,(h-252)/23),size=cols*rows;
        selectionPage=Math.min(selectionPage,Math.max(0,(doc.size()-1)/size));
        Rect2i drop=new Rect2i(x,y+53,width,rows*23);
        drops.add(new Drop(drop,this::acceptResource,true));
        var displays=doc.displays();
        for(int i=0;i<size;i++) {
            int index=selectionPage*size+i;
            ItemStack stack=index<displays.size()?displays.get(index):ItemStack.EMPTY;
            hits.add(new Hit(new Rect2i(x+(i%cols)*23,y+53+(i/cols)*23,21,21),stack,() -> {if(index<doc.size()){doc.remove(index);rebuildWidgets();}}));
        }
        int cy=y+56+rows*23;
        button("<",x,cy,22,() -> {selectionPage=Math.max(0,selectionPage-1);rebuildWidgets();});
        button(">",x+26,cy,22,() -> {selectionPage++;rebuildWidgets();});
        button("添加主手",x+54,cy,70,() -> {if(minecraft.player!=null)accept(minecraft.player.getMainHandItem());});
        button("清空",x+128,cy,48,() -> minecraft.setScreen(new ConfirmScreen(yes -> {if(yes){doc.items.clear();doc.resources.clear();}minecraft.setScreen(this);},Component.literal("清空元件内容？"),Component.literal("此操作只清空当前草稿的供给列表。"))));
        label("手动："+ResourceSupport.NAMES[resourceType]+" ID + NBT（可空）",x,cy+28);
        itemId=field(resourceType==0?"minecraft:stone":resourceType==1?"minecraft:water":"mekanism:hydrogen",x,cy+41,half,150,s -> {});
        nbt=field("",x+half+8,cy+41,Math.max(40,half-54),16000,s -> {});
        button("添加",x+width-50,cy+40,50,() -> run(() -> {
            CompoundTag tag=nbt.getValue().isBlank()?null:TagParser.parseTag(nbt.getValue());
            status=doc.addKey(ResourceSupport.manual(ResourceSupport.TYPES[resourceType],itemId.getValue(),tag))?"已添加资源（精确匹配 NBT）":"资源已存在或超过上限";rebuildWidgets();
        }));
    }
    private CellDocument.Recipe recipe() {return doc.recipes.get(recipeIndex);}
    private void locations(int x,int y,int width) {
        java.nio.file.Path[] paths={net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("kubejs"),DocumentFiles.drafts(),DocumentFiles.exports()};
        String[] names={"独立 KubeJS 脚本总目录","草稿保存目录","KubeJS 配方导出目录"};
        int step=Math.min(72,(h-135)/3);
        if(step<57){paths=new java.nio.file.Path[]{DocumentFiles.drafts(),DocumentFiles.exports()};names=new String[]{"草稿保存目录","KubeJS 配方导出目录"};step=62;}
        for(int i=0;i<paths.length;i++) {
            final var path=paths[i].toAbsolutePath().normalize();int row=y+i*step;
            label(names[i],x,row);
            var box=field(path.toString(),x,row+13,width,32767,s -> {});box.setEditable(false);
            box.setCursorPosition(0);
            box.setHighlightPos(0);
            box.setTooltip(Tooltip.create(Component.literal(path.toString())));
            button("复制地址",x,row+35,76,() -> {minecraft.keyboardHandler.setClipboard(path.toString());status="已复制："+path;});
            button("打开目录",x+80,row+35,76,() -> run(() -> {java.nio.file.Files.createDirectories(path);net.minecraft.Util.getPlatform().openFile(path.toFile());status="已打开目录："+path;}));
        }
        status="这里显示当前客户端的实际目录；多人游戏需将导出脚本复制到服务端。";
    }
    private void recipes(int x,int y,int width) {
        if(doc.recipes.isEmpty()) {
            label("尚无配方。可为同一元件添加多个配方。",x,y+12);
            button("添加配方",x,y+35,100,() -> {doc.recipes.add(new CellDocument.Recipe());recipeIndex=0;rebuildWidgets();});return;
        }
        recipeIndex=Math.min(recipeIndex,doc.recipes.size()-1);var r=recipe();
        button("<",x,y,22,() -> {recipeIndex=Math.max(0,recipeIndex-1);rebuildWidgets();});
        button((recipeIndex+1)+" / "+doc.recipes.size()+" >",x+26,y,65,() -> {recipeIndex=(recipeIndex+1)%doc.recipes.size();rebuildWidgets();});
        button("新增",x+95,y,46,() -> {doc.recipes.add(new CellDocument.Recipe());recipeIndex=doc.recipes.size()-1;rebuildWidgets();});
        button("删除",x+145,y,46,() -> {doc.recipes.remove(recipeIndex);recipeIndex=0;rebuildWidgets();});
        int type=Arrays.asList(TYPES).indexOf(r.type);
        button(NAMES[Math.max(0,type)]+" ▸",x,y+27,Math.min(width,195),() -> {r.type=TYPES[(type+1)%TYPES.length];rebuildWidgets();});
        if(r.type.equals("custom")) {
            label("从剪贴板粘贴机器配方 JSON。",x,y+62);
            label("将输出对象替换为字符串 \"$OUTPUT\"。",x,y+77);
            button("粘贴 JSON",x,y+96,92,() -> run(() -> {
                String raw=minecraft.keyboardHandler.getClipboard();
                if(raw.length()>1000000)throw new IllegalArgumentException("JSON 过大");
                com.google.gson.JsonParser.parseString(raw).getAsJsonObject();r.raw=raw;status="已粘贴自定义 JSON；导出时会检查输出占位符";
            }));
            button("复制 JSON",x+98,y+96,92,() -> {minecraft.keyboardHandler.setClipboard(r.raw);status="已复制 JSON，可在文本编辑器修改后粘贴";});
            return;
        }
        label("左侧选材料，再点槽位；右键清空；支持 JEI 拖入",x,y+62);
        for(int i=0;i<9;i++) {
            final int n=i;Rect2i area=new Rect2i(x+(i%3)*24,y+78+(i/3)*24,22,22);
            hits.add(new Hit(area,r.grid[i],() -> {r.grid[n]=brush.copyWithCount(1);rebuildWidgets();}));
            drops.add(new Drop(area,s -> {r.grid[n]=s.copyWithCount(1);rebuildWidgets();}));
        }
        label("→ 无限元件",x+90,y+106);
        if(doc.size()>0) hits.add(new Hit(new Rect2i(x+110,y+78,22,22),doc.output(),() -> {}));
        label("耗时（tick）",x+180,y+80);
        time=field(Integer.toString(r.time),x+180,y+95,Math.min(90,width-180),9,s -> {try{r.time=Integer.parseInt(s);}catch(Exception ignored){r.time=0;}});
        label("经验值",x+180,y+120);
        xp=field(Float.toString(r.xp),x+180,y+135,Math.min(90,width-180),10,s -> {try{r.xp=Float.parseFloat(s);}catch(Exception ignored){r.xp=-1;}});
        if(h>=300)label("合成槽支持精确 NBT。单材料配方只填写一个槽。",x,y+161);
    }
    public Rect2i bounds() {return new Rect2i(left,top,w,h);}
    public List<Drop> drops() {return List.copyOf(drops);}
    private interface Work {void run() throws Exception;}
    private void run(Work work) {try {work.run();}catch(Exception e){status="错误："+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage());}}
    @Override public void render(GuiGraphics g,int mx,int my,float dt) {
        renderBackground(g);
        surface(g,left,top,w,h,light?0xFFF5F6F8:0xFF151B24,light?0xFFD1D8E2:0xFF3A475A);
        surface(g,left+6,top+30,149,h-90,light?0xFFEAEDF2:0xFF1E2733,light?0xFFEAEDF2:0xFF1E2733);
        surface(g,left+160,top+60,w-166,h-120,light?0xFFEAEDF2:0xFF1E2733,light?0xFFEAEDF2:0xFF1E2733);
        super.render(g,mx,my,dt);
        for(var label:labels)g.drawString(font,font.plainSubstrByWidth(label.value,Math.max(10,left+w-10-label.x)),label.x,label.y,fg(),false);
        for(var hit:hits) {
            var a=hit.area;
            surface(g,a.getX(),a.getY(),a.getWidth(),a.getHeight(),a.contains(mx,my)?0xFF596F89:(light?0xFFD1D8E2:0xFF35445A),light?0xFFD1D8E2:0xFF3A475A);
            if(!hit.stack.isEmpty())g.renderItem(hit.stack,a.getX()+3,a.getY()+3);
        }
        g.drawString(font,font.plainSubstrByWidth(status,w-24),left+12,top+h-24,muted(),false);
        for(var hit:hits)if(hit.area.contains(mx,my) && !hit.stack.isEmpty())g.renderTooltip(font,hit.stack,mx,my);
    }
    @Override public boolean mouseClicked(double x,double y,int button) {
        for(var hit:List.copyOf(hits))if(hit.area.contains((int)x,(int)y)) {
            if(button==1 && tab==1) {
                for(int i=0;i<drops.size();i++)if(drops.get(i).area.contains((int)x,(int)y)){recipe().grid[i]=ItemStack.EMPTY;rebuildWidgets();return true;}
            }
            if(button==0){hit.action.run();return true;}
        }
        return super.mouseClicked(x,y,button);
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose() {
        ClientEvents.document=doc;
        try{DocumentFiles.save(doc);}catch(Exception e){if(minecraft.player!=null)minecraft.player.displayClientMessage(Component.literal("草稿未保存："+e.getMessage()),false);}
        super.onClose();
    }
    static final class HelpScreen extends Screen {
        final Screen parent;HelpScreen(Screen parent){super(Component.literal("使用帮助"));this.parent=parent;}
        protected void init(){addRenderableWidget(Button.builder(Component.literal("返回"),b -> onClose()).bounds(width/2-40,height-32,80,20).build());}
        public void render(GuiGraphics g,int mx,int my,float dt){renderBackground(g);super.render(g,mx,my,dt);
            String[] lines={ClientEvents.bindingsText(),"左侧物品库支持名称和 ID 搜索；切换背包以选取带 NBT 的实物。","内容页：可拖入 JEI 物品，多种 NBT 视为不同类型，最多 256 种。","配方页：选材料后点击槽位，右键清空；可添加多个配方。","机器配方：选择自定义 JSON，将完整输出对象改成字符串 $OUTPUT。","保存 / 读取：config/me_infinity_editor/drafts/<ID>.json。","导出：kubejs/server_scripts/me_infinity_editor/<ID>.js。","执行 /reload 加载配方；多人游戏需将脚本安装到服务端。","生成元件需要创造模式或管理员。将元件放进 ME 驱动器使用。","更改草稿不会修改已生成的元件。旧文件覆盖前保留 .bak。"};
            int y=30;for(String line:lines){for(var text:font.split(Component.literal(line),width-40)){g.drawString(font,text,20,y,0xFFE8ECF3);y+=12;}y+=5;}}
        public void onClose(){minecraft.setScreen(parent);}public boolean isPauseScreen(){return false;}
    }
}
