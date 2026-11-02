package dev.meinfinity.client;
import net.minecraft.client.gui.GuiGraphics;

/** Four-by-four coverage sampling smooths rounded corners and their borders. */
final class RoundedSurface {
    static void draw(GuiGraphics g,int x,int y,int w,int h,int radius,int fill,int border) {
        if(w<2 || h<2)return;
        int r=Math.min(radius,Math.min(w,h)/2);
        g.fill(x+r,y,x+w-r,y+h,border);g.fill(x,y+r,x+w,y+h-r,border);
        g.fill(x+r,y+1,x+w-r,y+h-1,fill);g.fill(x+1,y+r,x+w-1,y+h-r,fill);
        for(int cy=0;cy<r;cy++)for(int cx=0;cx<r;cx++) {
            int outer=0,inner=0;
            for(int sy=0;sy<4;sy++)for(int sx=0;sx<4;sx++) {
                double dx=r-cx-(sx+.5)/4,dy=r-cy-(sy+.5)/4,d=dx*dx+dy*dy;
                if(d<=r*r)outer++;
                if(d<=(r-1)*(r-1))inner++;
            }
            if(outer==0)continue;
            int color=0;
            for(int shift=0;shift<=16;shift+=8)color|=((((fill>>>shift)&255)*inner+((border>>>shift)&255)*(outer-inner))/outer)<<shift;
            color|=((((fill>>>24)*inner+(border>>>24)*(outer-inner))+8)/16)<<24;
            g.fill(x+cx,y+cy,x+cx+1,y+cy+1,color);
            g.fill(x+w-cx-1,y+cy,x+w-cx,y+cy+1,color);
            g.fill(x+cx,y+h-cy-1,x+cx+1,y+h-cy,color);
            g.fill(x+w-cx-1,y+h-cy-1,x+w-cx,y+h-cy,color);
        }
    }
}
