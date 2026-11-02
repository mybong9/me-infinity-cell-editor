package dev.meinfinity;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import java.util.*;
import java.util.function.Supplier;

public record CreateCellPacket(String name, List<ItemStack> items,List<net.minecraft.nbt.CompoundTag> resources) {
    public CreateCellPacket(String name,List<ItemStack> items){this(name,items,List.of());}
    public static void encode(CreateCellPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.name,80); buf.writeVarInt(packet.items.size());
        packet.items.forEach(buf::writeItem);
        buf.writeVarInt(packet.resources.size());packet.resources.forEach(buf::writeNbt);
    }
    public static CreateCellPacket decode(FriendlyByteBuf buf) {
        String name=buf.readUtf(80); int count=buf.readVarInt();
        if(count<0 || count>InfinityCell.MAX_TYPES) throw new IllegalArgumentException("Invalid cell type count");
        List<ItemStack> items=new ArrayList<>();
        for(int i=0;i<count;i++) items.add(buf.readItem());
        int keyCount=buf.readVarInt();if(keyCount<0 || count+keyCount<1 || count+keyCount>InfinityCell.MAX_TYPES)throw new IllegalArgumentException("Invalid resource count");
        List<net.minecraft.nbt.CompoundTag> resources=new ArrayList<>();for(int i=0;i<keyCount;i++){var tag=buf.readNbt();if(tag==null)throw new IllegalArgumentException("Missing key");resources.add(tag);}
        return new CreateCellPacket(name,items,resources);
    }
    public static void handle(CreateCellPacket packet, Supplier<NetworkEvent.Context> context) {
        var player=context.get().getSender();
        if(player==null) return;
        if(!player.hasPermissions(2) && !player.isCreative()) {
            player.displayClientMessage(Component.literal("需要创造模式或管理员权限才能直接生成无限元件。"),false); return;
        }
        try {
            var stack=InfinityCell.create(packet.name,packet.items,packet.resources);
            if (!player.getInventory().add(stack)) player.drop(stack,false);
            player.containerMenu.broadcastChanges();
        } catch(IllegalArgumentException e) { player.displayClientMessage(Component.literal(e.getMessage()),false); }
    }
}
