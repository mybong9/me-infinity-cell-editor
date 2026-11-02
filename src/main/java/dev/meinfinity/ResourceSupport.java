package dev.meinfinity;

import appeng.api.stacks.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.*;
import java.util.*;

/** Optional chemical classes are resolved only when selected; no Mek dependency at class load. */
public final class ResourceSupport {
    public static final String[] TYPES={"item","fluid","gas","infusion","pigment","slurry"};
    public static final String[] NAMES={"物品","流体","气体","灌注","颜料","浆液"};
    public static boolean mekanism(){return net.minecraftforge.fml.ModList.get().isLoaded("mekanism");}
    public static boolean chemicalBridge(){return net.minecraftforge.fml.ModList.get().isLoaded("appmek");}
    public static AEKey decode(CompoundTag tag){
        var id=ResourceLocation.tryParse(tag.getString("#c"));if(id==null)throw new IllegalArgumentException("无效资源类型");
        var type=AEKeyTypes.get(id);
        if(type==null)throw new IllegalArgumentException("缺少资源桥接："+tag.getString("#c"));
        AEKey key=type.loadKeyFromTag(tag.copy());
        if(key==null)throw new IllegalArgumentException("资源不存在："+tag);
        return key;
    }
    public static AEKey keyOf(Object object){
        if(object instanceof ItemStack stack){
            if(stack.isEmpty())return null;
            var generic=GenericStack.fromItemStack(stack);
            return generic==null?AEItemKey.of(stack):generic.what();
        }
        if(object instanceof FluidStack stack)return stack.isEmpty()?null:AEFluidKey.of(stack);
        if(object!=null && mekanism())try{
            var base=Class.forName("mekanism.api.chemical.ChemicalStack");
            if(!base.isInstance(object))return null;
            if(!chemicalBridge())throw new IllegalArgumentException("Mek 化学类型需要 Applied Mekanistics 桥接");
            if((boolean)base.getMethod("isEmpty").invoke(object))return null;
            return (AEKey)Class.forName("me.ramidzkh.mekae2.ae2.MekanismKey").getMethod("of",base).invoke(null,object);
        }catch(ReflectiveOperationException error){throw new IllegalArgumentException("无法读取 Mek 化学类型",error);}
        return null;
    }
    public static boolean accepts(Object value){
        if(value instanceof ItemStack || value instanceof FluidStack)return true;
        return value!=null && mekanism() && isChemical(value);
    }
    private static boolean isChemical(Object value){try{return Class.forName("mekanism.api.chemical.ChemicalStack").isInstance(value);}catch(ClassNotFoundException e){return false;}}
    private static IForgeRegistry<?> chemicalRegistry(String type){
        if(!mekanism())throw new IllegalArgumentException("未安装 Mekanism");
        String method=switch(type){case "gas"->"gasRegistry";case "infusion"->"infuseTypeRegistry";case "pigment"->"pigmentRegistry";case "slurry"->"slurryRegistry";default->throw new IllegalArgumentException("未知资源类型");};
        try{return (IForgeRegistry<?>)Class.forName("mekanism.api.MekanismAPI").getMethod(method).invoke(null);}catch(ReflectiveOperationException e){throw new IllegalArgumentException("无法读取 Mek 注册表",e);}
    }
    public static AEKey manual(String type,String text,CompoundTag nbt){
        ResourceLocation id=ResourceLocation.tryParse(text);
        if(id==null)throw new IllegalArgumentException("无效的资源 ID");
        if(type.equals("item")){
            if(!ForgeRegistries.ITEMS.containsKey(id) || ForgeRegistries.ITEMS.getValue(id)==Items.AIR)throw new IllegalArgumentException("物品不存在");
            return AEItemKey.of(ForgeRegistries.ITEMS.getValue(id),nbt);
        }
        if(type.equals("fluid")){
            if(!ForgeRegistries.FLUIDS.containsKey(id) || ForgeRegistries.FLUIDS.getValue(id)==net.minecraft.world.level.material.Fluids.EMPTY)throw new IllegalArgumentException("流体不存在");
            return AEFluidKey.of(ForgeRegistries.FLUIDS.getValue(id),nbt);
        }
        if(!chemicalBridge())throw new IllegalArgumentException("Mek 化学类型需要安装 Applied Mekanistics 桥接");
        if(nbt!=null && !nbt.isEmpty())throw new IllegalArgumentException("Mek 1.20.1 化学类型本身不携带物品式 NBT");
        var registry=chemicalRegistry(type);var chemical=registry.getValue(id);
        if(chemical==null || id.getPath().equals("empty"))throw new IllegalArgumentException("化学资源不存在");
        String path=switch(type){case "gas"->"gas.GasStack";case "infusion"->"infuse.InfusionStack";case "pigment"->"pigment.PigmentStack";default->"slurry.SlurryStack";};
        try{
            for(var ctor:Class.forName("mekanism.api.chemical."+path).getConstructors()){
                var args=ctor.getParameterTypes();
                if(args.length==2 && args[0].isInstance(chemical) && args[1]==long.class)return keyOf(ctor.newInstance(chemical,1L));
            }
            throw new IllegalArgumentException("不支持的 Mek 化学栈构造接口");
        }catch(ReflectiveOperationException e){throw new IllegalArgumentException("无法创建化学资源",e);}
    }
    public static List<ItemStack> library(String type){
        List<ItemStack> out=new ArrayList<>();
        if(type.equals("fluid")){
            ForgeRegistries.FLUIDS.getValues().stream().filter(f -> f!=net.minecraft.world.level.material.Fluids.EMPTY)
                .filter(f -> !(f instanceof net.minecraft.world.level.material.FlowingFluid flow) || f==flow.getSource())
                .forEach(f -> out.add(AEFluidKey.of(f).wrapForDisplayOrFilter()));
        }else if(!type.equals("item")){
            if(!chemicalBridge())throw new IllegalArgumentException("Mek 化学类型需要 Applied Mekanistics 桥接");
            for(var id:chemicalRegistry(type).getKeys())if(!id.getPath().equals("empty"))out.add(manual(type,id.toString(),null).wrapForDisplayOrFilter());
        }
        return out;
    }
    public static String searchText(ItemStack stack){
        AEKey key=keyOf(stack);
        return stack.getHoverName().getString()+" "+(key==null?"":key.getId().toString());
    }
    public static List<AEKey> keys(ItemStack cell){
        List<AEKey> out=new ArrayList<>();
        if(cell.hasTag()){
            var tags=cell.getTag().getList("Keys",Tag.TAG_COMPOUND);
            for(int i=0;i<Math.min(tags.size(),InfinityCell.MAX_TYPES);i++)try{out.add(decode(tags.getCompound(i)));}catch(IllegalArgumentException ignored){}
        }return out;
    }
    public static List<ItemStack> displaySamples(ItemStack cell){
        List<ItemStack> result=new ArrayList<>(InfinityCell.samples(cell));keys(cell).forEach(k -> result.add(k.wrapForDisplayOrFilter()));return result;
    }
}
