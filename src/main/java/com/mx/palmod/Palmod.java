package com.mx.palmod;

import com.mojang.logging.LogUtils;
import com.mx.palmod.registry.ModRegistries;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(Palmod.MODID)
public class Palmod
{
    // Define mod id in a common place for everything to reference
    public static final String MODID = "palmod";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();
    // Create a Deferred Register to hold Blocks which will all be registered under the "examplemod" namespace
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    // Create a Deferred Register to hold Items which will all be registered under the "examplemod" namespace
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    // Create a Deferred Register to hold CreativeModeTabs which will all be registered under the "examplemod" namespace
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    // Creates a new Block with the id "examplemod:example_block", combining the namespace and path
    public static final RegistryObject<Block> EXAMPLE_BLOCK = BLOCKS.register("example_block", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.STONE)));
    // Creates a new BlockItem with the id "examplemod:example_block", combining the namespace and path
    public static final RegistryObject<Item> EXAMPLE_BLOCK_ITEM = ITEMS.register("example_block", () -> new BlockItem(EXAMPLE_BLOCK.get(), new Item.Properties()));

    // Creates a new food item with the id "examplemod:example_id", nutrition 1 and saturation 2
    public static final RegistryObject<Item> EXAMPLE_ITEM = ITEMS.register("example_item", () -> new Item(new Item.Properties().food(new FoodProperties.Builder()
            .alwaysEat().nutrition(1).saturationMod(2f).build())));

    // Creates a creative tab for all Palmod items
    public static final RegistryObject<CreativeModeTab> PALMOD_TAB = CREATIVE_MODE_TABS.register("palmod_tab", () -> CreativeModeTab.builder()
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> ModRegistries.PAL_SPHERE.get().getDefaultInstance())
            .title(net.minecraft.network.chat.Component.translatable("itemGroup.palmod"))
            .displayItems((parameters, output) -> {
                // Spheres
                output.accept(ModRegistries.PAL_SPHERE.get());
                output.accept(ModRegistries.PAL_SPHERE_MID.get());
                output.accept(ModRegistries.PAL_SPHERE_ADVANCED.get());
                output.accept(ModRegistries.FILLED_PAL_SPHERE.get());
                // Paldex (in-game guide + mob dictionary)
                output.accept(ModRegistries.PALDEX.get());
                // Pal Compass (vanilla compass + NBT, points at catchable wild mobs)
                output.accept(com.mx.palmod.pal.WildCatchManager.createPalCompass());
                // Blocks
                output.accept(ModRegistries.PAL_FEEDER_ITEM.get());
                output.accept(ModRegistries.PAL_WORK_STATION_ITEM.get());
            }).build());

    public Palmod()
    {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);
        
        ModRegistries.register(modEventBus);

        // Register the Deferred Register to the mod event bus so blocks get registered
        BLOCKS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so items get registered
        ITEMS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so tabs get registered
        CREATIVE_MODE_TABS.register(modEventBus);

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);

        // Register the item to a creative tab
        modEventBus.addListener(this::addCreative);

        // Register our mod's ForgeConfigSpec so that Forge can create and load the config file for us
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event)
    {
        // Some common setup code
        LOGGER.info("HELLO FROM COMMON SETUP");

        if (Config.logDirtBlock)
            LOGGER.info("DIRT BLOCK >> {}", ForgeRegistries.BLOCKS.getKey(Blocks.DIRT));

        LOGGER.info(Config.magicNumberIntroduction + Config.magicNumber);

        Config.items.forEach((item) -> LOGGER.info("ITEM >> {}", item.toString()));
    }

    // Legacy hook – still adds spheres to the Tools tab for quick access
    private void addCreative(BuildCreativeModeTabContentsEvent event)
    {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModRegistries.PAL_SPHERE);
            event.accept(ModRegistries.PAL_SPHERE_MID);
            event.accept(ModRegistries.PAL_SPHERE_ADVANCED);
            event.accept(ModRegistries.FILLED_PAL_SPHERE);
            event.accept(ModRegistries.PALDEX);
            event.accept(ModRegistries.PAL_FEEDER_ITEM);
            event.accept(ModRegistries.PAL_WORK_STATION_ITEM);
        }
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event)
    {
        // Do something when the server starts
        LOGGER.info("HELLO from server starting");

        if (Config.allowVanillaClients && event.getServer().isDedicatedServer()) {
            allowVanillaConnections();
        }
    }

    /**
     * Patches every registered Forge network channel to accept vanilla clients.
     * Some mods (e.g. Alex's Mobs, Citadel) register channels that reject clients
     * without the mod installed; this rewrites their client-version predicates so
     * vanilla clients and protocol bots can still join the dedicated server.
     */
    private static void allowVanillaConnections()
    {
        try {
            Class<?> registryClass = Class.forName("net.minecraftforge.network.NetworkRegistry");
            java.lang.reflect.Field instancesField = registryClass.getDeclaredField("instances");
            java.util.Map<?, ?> instances;
            try {
                instancesField.setAccessible(true);
                instances = (java.util.Map<?, ?>) instancesField.get(null);
            } catch (RuntimeException | IllegalAccessException reflectionBlocked) {
                // Module system refused plain reflection — go through Unsafe
                java.lang.reflect.Field theUnsafe = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
                theUnsafe.setAccessible(true);
                sun.misc.Unsafe unsafe = (sun.misc.Unsafe) theUnsafe.get(null);
                Object base = unsafe.staticFieldBase(instancesField);
                long offset = unsafe.staticFieldOffset(instancesField);
                instances = (java.util.Map<?, ?>) unsafe.getObject(base, offset);
            }

            Class<?> instanceClass = Class.forName("net.minecraftforge.network.NetworkInstance");
            // The server-side vanilla check (tryClientVersionOnServer) reads
            // serverAcceptedVersions; patch clientAcceptedVersions too for symmetry.
            java.lang.reflect.Field[] versionFields = {
                    instanceClass.getDeclaredField("serverAcceptedVersions"),
                    instanceClass.getDeclaredField("clientAcceptedVersions")
            };
            java.util.function.Predicate<String> acceptAll = version -> true;

            int patched = 0;
            for (Object channel : instances.values()) {
                for (java.lang.reflect.Field versionField : versionFields) {
                    try {
                        versionField.setAccessible(true);
                        versionField.set(channel, acceptAll);
                    } catch (RuntimeException | IllegalAccessException reflectionBlocked) {
                        net.minecraftforge.fml.unsafe.UnsafeHacks.setField(versionField, channel, acceptAll);
                    }
                }
                patched++;
            }
            LOGGER.info("allowVanillaClients: patched {} network channel(s) to accept vanilla connections", patched);
        } catch (Throwable t) {
            LOGGER.error("allowVanillaClients: failed to patch network channels — vanilla clients will still be rejected", t);
        }
    }

    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents
    {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event)
        {
            // Some client setup code
            LOGGER.info("HELLO FROM CLIENT SETUP");
            LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
            
            net.minecraft.client.renderer.entity.EntityRenderers.register(
                com.mx.palmod.registry.ModRegistries.PAL_SPHERE_PROJECTILE.get(),
                net.minecraft.client.renderer.entity.ThrownItemRenderer::new
            );
        }

        /**
         * Tints the filled sphere's egg-inset layers (tintindex 1 & 2) with the
         * captured mob's spawn-egg colors; desaturated+darkened while the pal
         * is released so all three sphere states read at a glance.
         */
        @SubscribeEvent
        public static void onItemColors(net.minecraftforge.client.event.RegisterColorHandlersEvent.Item event)
        {
            event.register((stack, tintIndex) -> {
                if (tintIndex == 0) return 0xFFFFFF;
                net.minecraft.nbt.CompoundTag tag = stack.getTag();
                net.minecraft.world.entity.EntityType<?> type = null;
                if (tag != null && tag.contains("CapturedEntity")) {
                    type = net.minecraft.world.entity.EntityType.by(tag.getCompound("CapturedEntity")).orElse(null);
                }
                // ForgeSpawnEggItem keeps its own map — SpawnEggItem.byId never
                // sees modded (Alex's Mobs) eggs; fromEntityType checks both.
                net.minecraft.world.item.SpawnEggItem egg =
                        net.minecraftforge.common.ForgeSpawnEggItem.fromEntityType(type);
                int color = egg != null ? egg.getColor(tintIndex - 1)
                        : (tintIndex == 1 ? 0x9A9A9A : 0x666666);
                if (tag != null && tag.getBoolean("IsReleased")) {
                    int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
                    int gray = (r + g + b) / 3;
                    r = (int) ((r * 0.3 + gray * 0.7) * 0.5);
                    g = (int) ((g * 0.3 + gray * 0.7) * 0.5);
                    b = (int) ((b * 0.3 + gray * 0.7) * 0.5);
                    color = (r << 16) | (g << 8) | b;
                }
                return color;
            }, ModRegistries.FILLED_PAL_SPHERE.get());
        }
    }
}
