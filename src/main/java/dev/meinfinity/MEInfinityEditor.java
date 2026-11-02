package dev.meinfinity;

import appeng.api.storage.StorageCells;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.network.NetworkDirection;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.CreativeModeTab;

@Mod(MEInfinityEditor.ID)
public final class MEInfinityEditor {
    public static final String ID = "me_infinity_editor";
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ID);
    public static final RegistryObject<Item> CELL = ITEMS.register("infinity_cell", InfinityCell::new);
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, ID);
    public static final RegistryObject<RecipeSerializer<CellStonecuttingRecipe>> STONECUTTING = SERIALIZERS.register("stonecutting", CellStonecuttingRecipe.Serializer::new);
    public static final RegistryObject<RecipeSerializer<CellCatalogRecipe>> CATALOG = SERIALIZERS.register("catalog", CellCatalogRecipe.Serializer::new);
    public static final DeferredRegister<RecipeType<?>> TYPES = DeferredRegister.create(ForgeRegistries.RECIPE_TYPES, ID);
    public static final RegistryObject<RecipeType<CellCatalogRecipe>> CATALOG_TYPE = TYPES.register("catalog",() -> new RecipeType<>(){});
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(net.minecraft.core.registries.Registries.CREATIVE_MODE_TAB,ID);
    public static final RegistryObject<CreativeModeTab> TAB=TABS.register("cells",() -> CreativeModeTab.builder().title(Component.literal("ME 无限元件")).icon(() -> new net.minecraft.world.item.ItemStack(CELL.get())).displayItems((p,o) -> o.accept(CELL.get())).build());
    public static final SimpleChannel NETWORK = NetworkRegistry.newSimpleChannel(new ResourceLocation(ID,"main"), () -> "2", "2"::equals, "2"::equals);
    public MEInfinityEditor() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(bus);
        SERIALIZERS.register(bus);
        TYPES.register(bus);TABS.register(bus);
        bus.addListener(this::setup);
        MinecraftForge.EVENT_BUS.addListener(this::commands);
    }
    private void setup(FMLCommonSetupEvent event) {
        NETWORK.messageBuilder(CreateCellPacket.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CreateCellPacket::encode).decoder(CreateCellPacket::decode)
                .consumerMainThread(CreateCellPacket::handle).add();
        event.enqueueWork(() -> StorageCells.addCellHandler(new InfinityCell.Handler()));
    }
    private void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("me_infinity_editor").requires(s -> s.hasPermission(2))
            .then(Commands.literal("help").executes(c -> {
                c.getSource().sendSuccess(() -> Component.literal("F8 打开编辑器；M 标记鼠标下的物品；KubeJS 导出在 kubejs/server_scripts/me_infinity_editor/。"), false);
                return 1;
            })));
    }
}
