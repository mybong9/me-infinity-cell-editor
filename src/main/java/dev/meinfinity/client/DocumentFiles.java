package dev.meinfinity.client;

import com.google.gson.*;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.*;

public final class DocumentFiles {
    static final Gson GSON=new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    public static Path drafts() { return FMLPaths.GAMEDIR.get().resolve("config/me_infinity_editor/drafts"); }
    public static Path exports() { return FMLPaths.GAMEDIR.get().resolve("kubejs/server_scripts/me_infinity_editor"); }
    public static void save(CellDocument doc) throws IOException {
        checkId(doc.id); write(drafts().resolve(doc.id+".json"), GSON.toJson(doc.json()));
    }
    public static CellDocument load(String id) throws IOException {
        checkId(id); return CellDocument.read(JsonParser.parseString(Files.readString(drafts().resolve(id+".json"))).getAsJsonObject());
    }
    private static void checkId(String id) {
        if(!id.matches("[a-z0-9][a-z0-9_]{0,63}")) throw new IllegalArgumentException("无效 ID：只能使用小写字母、数字、下划线");
    }
    private static void write(Path path,String value) throws IOException {
        Files.createDirectories(path.getParent());
        Path temp=Files.createTempFile(path.getParent(),".editor-",".tmp");
        try {
            Files.writeString(temp,value,StandardCharsets.UTF_8);
            if(Files.exists(path)) Files.copy(path,path.resolveSibling(path.getFileName()+".bak"),StandardCopyOption.REPLACE_EXISTING);
            try { Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE); }
            catch(AtomicMoveNotSupportedException e) { Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
    private static String quote(String value) { return GSON.toJson(value); }
    static String itemExpression(ItemStack stack) {
        String id=ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
        return "Item.of("+quote(id)+(stack.hasTag()?", "+quote(stack.getTag().toString()):"")+")";
    }
    private static JsonObject ingredient(ItemStack stack) {
        JsonObject json=new JsonObject(); json.addProperty("item",ForgeRegistries.ITEMS.getKey(stack.getItem()).toString());
        if(stack.hasTag()) { json.addProperty("type","forge:nbt");json.addProperty("nbt",stack.getTag().toString()); }
        return json;
    }
    static JsonElement replaceOutput(JsonElement value,JsonObject output) {
        if(value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() && value.getAsString().equals("$OUTPUT")) return output.deepCopy();
        if(value.isJsonObject()) { JsonObject obj=new JsonObject();value.getAsJsonObject().entrySet().forEach(e -> obj.add(e.getKey(),replaceOutput(e.getValue(),output)));return obj; }
        if(value.isJsonArray()) { JsonArray arr=new JsonArray();value.getAsJsonArray().forEach(e -> arr.add(replaceOutput(e,output)));return arr; }
        return value.deepCopy();
    }
    private static String recipeExpression(JsonElement value,JsonObject output) {
        if(value.equals(output))return "outputJson";
        if(value.isJsonObject()) {
            List<String> fields=new ArrayList<>();
            value.getAsJsonObject().entrySet().forEach(e -> fields.add(quote(e.getKey())+":"+recipeExpression(e.getValue(),output)));
            return "{"+String.join(",",fields)+"}";
        }
        if(value.isJsonArray()) {
            List<String> entries=new ArrayList<>();value.getAsJsonArray().forEach(e -> entries.add(recipeExpression(e,output)));
            return "["+String.join(",",entries)+"]";
        }
        return GSON.toJson(value);
    }
    public static String script(CellDocument doc) {
        doc.validate(); ItemStack stack=doc.output();
        JsonObject output=new JsonObject();output.addProperty("item","kubejs:me_infinity_cell");output.addProperty("count",1);output.addProperty("nbt",stack.getTag().toString());
        StringBuilder code=new StringBuilder("// Standalone AE2 + KubeJS export. The editor JAR is optional.\n// Install startup/client scripts and restart once; later changes use /reload.\n");
        code.append("// Edit itemSelector in kubejs/config/me_infinity_editor/catalog.json.\nServerEvents.recipes(event => {\n  const catalogue = JsonIO.read('kubejs/config/me_infinity_editor/catalog.json');\n  const output = global.meInfinityCell(catalogue[").append(quote(doc.id)).append("]);\n  const outputJson = {item: 'kubejs:me_infinity_cell', count: 1, nbt: output.nbt.toString()};\n");
        JsonObject catalog=new JsonObject();catalog.addProperty("type","me_infinity_editor:catalog");catalog.add("result",output.deepCopy());
        code.append("  if (Platform.isLoaded('me_infinity_editor')) event.custom(").append(recipeExpression(catalog,output)).append(").id(").append(quote("me_infinity_editor:"+doc.id+"_catalog")).append(");\n");
        for(int i=0;i<doc.recipes.size();i++) {
            var recipe=doc.recipes.get(i); JsonObject json=new JsonObject();
            List<ItemStack> inputs=Arrays.stream(recipe.grid).filter(s -> !s.isEmpty()).toList();
            if(recipe.type.equals("custom")) {
                JsonElement parsed=JsonParser.parseString(recipe.raw);
                if(!parsed.isJsonObject() || !recipe.raw.contains("$OUTPUT")) throw new IllegalArgumentException("自定义配方必须是 JSON 对象，并包含 $OUTPUT 输出占位符");
                json=replaceOutput(parsed,output).getAsJsonObject();
                if(!json.has("type")) throw new IllegalArgumentException("自定义配方缺少 type");
            } else {
                if(inputs.isEmpty()) throw new IllegalArgumentException("配方 "+(i+1)+" 没有材料");
                switch(recipe.type) {
                    case "shaped" -> {
                        json.addProperty("type","minecraft:crafting_shaped");
                        JsonArray pattern=new JsonArray(); JsonObject keys=new JsonObject();
                        int minX=3,minY=3,maxX=0,maxY=0;
                        for(int n=0;n<9;n++) if(!recipe.grid[n].isEmpty()) { minX=Math.min(minX,n%3);maxX=Math.max(maxX,n%3);minY=Math.min(minY,n/3);maxY=Math.max(maxY,n/3); }
                        for(int y=minY;y<=maxY;y++) {
                            StringBuilder row=new StringBuilder();
                            for(int x=minX;x<=maxX;x++) { int n=y*3+x; String symbol=String.valueOf((char)('A'+n)); if(recipe.grid[n].isEmpty()) row.append(' ');else { row.append(symbol); keys.add(symbol,ingredient(recipe.grid[n])); } }
                            pattern.add(row.toString());
                        }
                        json.add("pattern",pattern);json.add("key",keys);
                    }
                    case "shapeless" -> { json.addProperty("type","minecraft:crafting_shapeless");JsonArray arr=new JsonArray();inputs.forEach(s -> arr.add(ingredient(s)));json.add("ingredients",arr); }
                    case "smelting", "blasting", "smoking", "campfire_cooking", "stonecutting" -> {
                        if(inputs.size()!=1) throw new IllegalArgumentException("此配方只能放入一种材料");
                        json.addProperty("type",recipe.type.equals("stonecutting")?"kubejs:me_infinity_stonecutting":"minecraft:"+recipe.type); json.add("ingredient",ingredient(inputs.get(0)));
                        if(!recipe.type.equals("stonecutting")) {
                            if(recipe.time<1 || !Float.isFinite(recipe.xp) || recipe.xp<0) throw new IllegalArgumentException("时间必须大于 0，经验必须为非负数");
                            json.addProperty("cookingtime",recipe.time);json.addProperty("experience",recipe.xp);
                        }
                    }
                    default -> throw new IllegalArgumentException("未知配方类型");
                }
                // KubeJS builders support NBT outputs for vanilla recipes whose vanilla codec is string-only.
                if(Set.of("smelting","blasting","smoking","campfire_cooking").contains(recipe.type)) {
                    String method=recipe.type.equals("campfire_cooking")?"campfireCooking":recipe.type;
                    code.append("  event.").append(method).append("(output, ").append(inputs.get(0).hasTag()?itemExpression(inputs.get(0))+".strongNBT()":quote(ForgeRegistries.ITEMS.getKey(inputs.get(0).getItem()).toString())).append(")");
                    if(!recipe.type.equals("stonecutting")) code.append(".cookingTime(").append(recipe.time).append(").xp(").append(recipe.xp).append(")");
                    code.append(".id(").append(quote("me_infinity_editor:"+doc.id+"_"+i)).append(");\n"); continue;
                }
                json.add("result",output.deepCopy());
            }
            code.append("  event.custom(").append(recipeExpression(json,output)).append(").id(").append(quote("me_infinity_editor:"+doc.id+"_"+i)).append(");\n");
        }
        code.append("});\n");return code.toString();
    }
    public static Path export(CellDocument doc) throws IOException {
        String script=script(doc); save(doc);
        Path root=FMLPaths.GAMEDIR.get();
        writeTemplate(root.resolve("kubejs/startup_scripts/me_infinity_selectors.js"),"selectors.js");
        writeTemplate(root.resolve("kubejs/startup_scripts/me_infinity_runtime.js"),"startup.js");
        writeTemplate(root.resolve("kubejs/client_scripts/me_infinity_catalog.js"),"client.js");
        Path catalogue=root.resolve("kubejs/config/me_infinity_editor/catalog.json");
        JsonObject data=Files.exists(catalogue)?JsonParser.parseString(Files.readString(catalogue)).getAsJsonObject():new JsonObject();
        JsonObject definition=new JsonObject();definition.addProperty("nbt",doc.output().getTag().toString());
        var previous=data.get(doc.id);
        definition.addProperty("itemSelector",previous!=null && previous.isJsonObject() && previous.getAsJsonObject().has("itemSelector")?previous.getAsJsonObject().get("itemSelector").getAsString():"");
        data.add(doc.id,definition);write(catalogue,GSON.toJson(data));
        Path file=exports().resolve(doc.id+".js");
        write(file,script); return file;
    }
    private static void writeTemplate(Path path,String name) throws IOException {
        try(var input=DocumentFiles.class.getResourceAsStream("/exports/"+name)) {
            if(input==null)throw new IOException("缺少导出模板："+name);
            write(path,new String(input.readAllBytes(),StandardCharsets.UTF_8));
        }
    }
}
