package dev.meinfinity.client;

import com.google.gson.*;
import dev.meinfinity.InfinityCell;
import dev.meinfinity.ResourceSupport;
import appeng.api.stacks.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import java.util.*;

public final class CellDocument {
    public String id="my_infinity_cell", name="自定义无限元件";
    public final List<ItemStack> items=new ArrayList<>();
    public final List<CompoundTag> resources=new ArrayList<>();
    public final List<Recipe> recipes=new ArrayList<>();
    public boolean add(ItemStack input) {
        if(input.isEmpty() || size()>=InfinityCell.MAX_TYPES) return false;
        if(items.stream().anyMatch(s -> ItemStack.isSameItemSameTags(s,input))) return false;
        items.add(input.copyWithCount(1)); return true;
    }
    public int size(){return items.size()+resources.size();}
    public boolean addObject(Object value){return addKey(ResourceSupport.keyOf(value));}
    public boolean addKey(AEKey key){
        if(key==null || size()>=InfinityCell.MAX_TYPES)return false;
        if(key instanceof AEItemKey item)return add(item.toStack());
        CompoundTag tag=key.toTagGeneric();if(resources.contains(tag))return false;resources.add(tag);return true;
    }
    public List<ItemStack> displays(){
        List<ItemStack> all=new ArrayList<>(items);
        for(var tag:resources)try{all.add(ResourceSupport.decode(tag).wrapForDisplayOrFilter());}catch(IllegalArgumentException e){all.add(new ItemStack(net.minecraft.world.item.Items.BARRIER));}
        return all;
    }
    public void remove(int index){if(index<items.size())items.remove(index);else resources.remove(index-items.size());}
    public ItemStack output() { return InfinityCell.create(name,items,resources); }
    public void validate() {
        if(!id.matches("[a-z0-9][a-z0-9_]{0,63}")) throw new IllegalArgumentException("ID 只能使用小写字母、数字、下划线，长度 1–64");
        if(name.length()>80) throw new IllegalArgumentException("名称不能超过 80 字符");
        output();
    }
    static JsonObject stack(ItemStack stack) {
        JsonObject json=new JsonObject(); json.addProperty("snbt",stack.save(new CompoundTag()).toString()); return json;
    }
    static ItemStack stack(JsonObject json) {
        try { return ItemStack.of(TagParser.parseTag(json.get("snbt").getAsString())); }
        catch(Exception e) { throw new IllegalArgumentException("草稿中的物品 NBT 无效",e); }
    }
    public JsonObject json() {
        JsonObject json=new JsonObject(); json.addProperty("version",1); json.addProperty("id",id); json.addProperty("name",name);
        JsonArray entries=new JsonArray(); items.forEach(s -> entries.add(stack(s))); json.add("items",entries);
        JsonArray keys=new JsonArray();resources.forEach(t -> keys.add(t.toString()));json.add("resources",keys);
        JsonArray recipeArray=new JsonArray(); recipes.forEach(r -> recipeArray.add(r.json())); json.add("recipes",recipeArray); return json;
    }
    public static CellDocument read(JsonObject json) {
        if(json.get("version").getAsInt()!=1) throw new IllegalArgumentException("不支持的草稿版本");
        CellDocument doc=new CellDocument(); doc.id=json.get("id").getAsString(); doc.name=json.get("name").getAsString();
        if(json.getAsJsonArray("items").size()>InfinityCell.MAX_TYPES) throw new IllegalArgumentException("物品超过 256 种");
        json.getAsJsonArray("items").forEach(v -> { ItemStack s=stack(v.getAsJsonObject()); if(s.isEmpty()) throw new IllegalArgumentException("草稿物品缺失，请检查模组依赖"); doc.add(s); });
        if(json.has("resources"))for(var v:json.getAsJsonArray("resources"))try{doc.resources.add(TagParser.parseTag(v.getAsString()));}catch(Exception e){throw new IllegalArgumentException("资源 NBT 无效",e);}
        if(doc.size()>InfinityCell.MAX_TYPES)throw new IllegalArgumentException("资源总数超过 256");
        json.getAsJsonArray("recipes").forEach(v -> doc.recipes.add(Recipe.read(v.getAsJsonObject()))); return doc;
    }
    public static final class Recipe {
        public String type="shaped", raw="{\"type\":\"minecraft:crafting_shapeless\",\"ingredients\":[{\"item\":\"minecraft:diamond\"}],\"result\":\"$OUTPUT\"}";
        public int time=200; public float xp=0;
        public final ItemStack[] grid=new ItemStack[9];
        public Recipe() { Arrays.fill(grid,ItemStack.EMPTY); }
        JsonObject json() {
            JsonObject json=new JsonObject(); json.addProperty("type",type);json.addProperty("time",time);json.addProperty("xp",xp);json.addProperty("raw",raw);
            JsonArray slots=new JsonArray(); for(var s:grid) slots.add(stack(s)); json.add("grid",slots); return json;
        }
        static Recipe read(JsonObject json) {
            Recipe r=new Recipe();r.type=json.get("type").getAsString();r.time=json.get("time").getAsInt();r.xp=json.get("xp").getAsFloat();r.raw=json.get("raw").getAsString();
            for(int i=0;i<9;i++) r.grid[i]=stack(json.getAsJsonArray("grid").get(i).getAsJsonObject()); return r;
        }
    }
}
