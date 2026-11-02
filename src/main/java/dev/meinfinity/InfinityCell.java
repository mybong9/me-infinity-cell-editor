package dev.meinfinity;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.api.storage.cells.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import java.util.*;

public final class InfinityCell extends Item {
    public static final int MAX_TYPES = 256;
    public InfinityCell() { super(new Item.Properties().stacksTo(1).fireResistant()); }
    public static ItemStack create(String name, List<ItemStack> samples) {
        return create(name,samples,List.of());
    }
    public static ItemStack create(String name,List<ItemStack> samples,List<CompoundTag> resources){
        if (samples.size()+resources.size()<1 || samples.size()+resources.size()>MAX_TYPES) throw new IllegalArgumentException("请选择 1–256 种资源");
        var standalone=net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation("kubejs","me_infinity_cell"));
        ItemStack result = new ItemStack(standalone!=null && standalone!=net.minecraft.world.item.Items.AIR?standalone:MEInfinityEditor.CELL.get());
        ListTag list = new ListTag();
        Set<AEItemKey> seen = new LinkedHashSet<>();
        for (ItemStack sample : samples) {
            AEItemKey key = AEItemKey.of(sample);
            if (key != null && seen.add(key)) list.add(key.toStack(1).save(new CompoundTag()));
        }
        ListTag keys=new ListTag();
        Set<AEKey> seenResources=new LinkedHashSet<>();
        for(var tag:resources){var key=ResourceSupport.decode(tag);if(key instanceof AEItemKey)throw new IllegalArgumentException("额外资源应为流体或化学类型");if(seenResources.add(key))keys.add(key.toTagGeneric());}
        if(list.isEmpty() && keys.isEmpty())throw new IllegalArgumentException("资源列表为空");
        result.getOrCreateTag().put("Items", list);
        result.getOrCreateTag().put("Keys",keys);
        if (!name.isBlank()) result.setHoverName(Component.literal(name));
        return result;
    }
    public static List<ItemStack> samples(ItemStack cell) {
        if (!cell.hasTag()) return List.of();
        ListTag list = cell.getTag().getList("Items", Tag.TAG_COMPOUND);
        List<ItemStack> result = new ArrayList<>();
        for (int i=0; i<Math.min(MAX_TYPES,list.size()); i++) {
            ItemStack stack = ItemStack.of(list.getCompound(i));
            if (!stack.isEmpty()) { stack.setCount(1); result.add(stack); }
        }
        return result;
    }
    @Override public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        var entries = ResourceSupport.displaySamples(stack);
        lines.add(Component.literal("§d无限供给 · " + entries.size() + " 种资源 · 精确匹配 NBT"));
        if(entries.size()>64)lines.add(Component.literal("§7图标预览前 64 种，共 "+entries.size()+" 种"));
    }
    @Override public Optional<net.minecraft.world.inventory.tooltip.TooltipComponent> getTooltipImage(ItemStack stack){return Optional.of(new CellContentsTooltip(ResourceSupport.displaySamples(stack)));}
    public static final class Handler implements ICellHandler {
        public boolean isCell(ItemStack stack) { return stack.is(MEInfinityEditor.CELL.get()); }
        public StorageCell getCellInventory(ItemStack stack, ISaveProvider host) { return isCell(stack) ? new Inventory(stack) : null; }
    }
    public static final class Inventory implements StorageCell {
        private final Set<AEKey> keys = new LinkedHashSet<>();
        private final Component name;
        public Inventory(ItemStack stack) { name=stack.getHoverName(); samples(stack).forEach(s -> keys.add(AEItemKey.of(s)));keys.addAll(ResourceSupport.keys(stack)); }
        public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
            return amount>0 && keys.contains(what) ? amount : 0;
        }
        // Source-only like the infinite water cell: never void inserted items.
        public long insert(AEKey what, long amount, Actionable mode, IActionSource source) { return 0; }
        public void getAvailableStacks(KeyCounter out) { keys.forEach(key -> out.add(key, Long.MAX_VALUE)); }
        public CellState getStatus() { return keys.isEmpty() ? CellState.EMPTY : CellState.NOT_EMPTY; }
        public double getIdleDrain() { return 0; }
        public boolean canFitInsideCell() { return false; }
        public Component getDescription() { return name; }
        public void persist() {}
    }
}
