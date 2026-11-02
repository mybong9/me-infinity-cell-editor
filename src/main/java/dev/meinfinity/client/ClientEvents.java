package dev.meinfinity.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import dev.meinfinity.MEInfinityEditor;
import java.util.function.Supplier;

@Mod.EventBusSubscriber(modid=MEInfinityEditor.ID,value=Dist.CLIENT)
public final class ClientEvents {
    @SubscribeEvent public static void tooltip(net.minecraftforge.client.event.RenderTooltipEvent.GatherComponents e){
        if(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(e.getItemStack().getItem()).toString().equals("kubejs:me_infinity_cell"))
            e.getTooltipElements().add(com.mojang.datafixers.util.Either.right(new dev.meinfinity.CellContentsTooltip(dev.meinfinity.ResourceSupport.displaySamples(e.getItemStack()))));
    }
    public static CellDocument document=new CellDocument();
    public static Supplier<Object> jeiHovered=() -> ItemStack.EMPTY;
    public static String bindingsText(){return OPEN.getTranslatedKeyMessage().getString()+"：打开编辑器；"+MARK.getTranslatedKeyMessage().getString()+"：标记物品（控制设置中可修改）。";}
    static final KeyMapping OPEN=new KeyMapping("key.me_infinity_editor.open",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_F8,"key.categories.me_infinity_editor");
    static final KeyMapping MARK=new KeyMapping("key.me_infinity_editor.mark",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_M,"key.categories.me_infinity_editor");
    @Mod.EventBusSubscriber(modid=MEInfinityEditor.ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Keys {
        @SubscribeEvent public static void register(RegisterKeyMappingsEvent e) { e.register(OPEN);e.register(MARK); }
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        if(e.phase!=TickEvent.Phase.END) return;
        Minecraft mc=Minecraft.getInstance();
        while(OPEN.consumeClick()) if(mc.player!=null && mc.screen==null) mc.setScreen(new EditorScreen());
    }
    @SubscribeEvent public static void key(ScreenEvent.KeyPressed.Pre e) {
        if(e.getScreen() instanceof net.minecraft.client.gui.screens.controls.KeyBindsScreen)return;
        if(OPEN.matches(e.getKeyCode(),e.getScanCode()) && !(e.getScreen() instanceof EditorScreen)) {
            Minecraft.getInstance().setScreen(new EditorScreen());e.setCanceled(true);return;
        }
        if(!MARK.matches(e.getKeyCode(),e.getScanCode()) || e.getScreen() instanceof EditorScreen) return;
        Object stack=jeiHovered.get();
        if((stack==null || stack instanceof ItemStack item && item.isEmpty()) && e.getScreen() instanceof AbstractContainerScreen<?> screen && screen.getSlotUnderMouse()!=null) stack=screen.getSlotUnderMouse().getItem();
        if(dev.meinfinity.ResourceSupport.accepts(stack)) {
            boolean added;
            try{added=document.addObject(stack);}catch(IllegalArgumentException error){if(Minecraft.getInstance().player!=null)Minecraft.getInstance().player.displayClientMessage(Component.literal(error.getMessage()),true);e.setCanceled(true);return;}
            if(added)try{DocumentFiles.save(document);}catch(Exception ignored){}
            var player=Minecraft.getInstance().player;
            if(player!=null) player.displayClientMessage(Component.literal(added?"已标记资源（保留 NBT）":"已标记、资源为空或已达到 256 种上限"),true);
            e.setCanceled(true);
        }
    }
}
