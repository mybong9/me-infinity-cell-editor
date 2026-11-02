package dev.meinfinity;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
public record CellContentsTooltip(List<ItemStack> items) implements TooltipComponent {}
