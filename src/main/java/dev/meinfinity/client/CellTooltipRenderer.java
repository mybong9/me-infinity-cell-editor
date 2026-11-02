package dev.meinfinity.client;

import dev.meinfinity.*;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=MEInfinityEditor.ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class CellTooltipRenderer implements ClientTooltipComponent {
    private final CellContentsTooltip data;
    public CellTooltipRenderer(CellContentsTooltip data){this.data=data;}
    @SubscribeEvent public static void register(net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent e){e.register(CellContentsTooltip.class,CellTooltipRenderer::new);}
    public int getHeight(){return ((Math.min(64,data.items().size())+7)/8)*20+2;}
    public int getWidth(Font font){return Math.min(8,data.items().size())*20;}
    public void renderImage(Font font,int x,int y,GuiGraphics g){
        for(int i=0;i<Math.min(64,data.items().size());i++){
            int px=x+i%8*20,py=y+i/8*20;
            RoundedSurface.draw(g,px,py,19,19,4,0xFF283344,0xFF3A475A);g.renderItem(data.items().get(i),px+1,py+1);
        }
    }
}
