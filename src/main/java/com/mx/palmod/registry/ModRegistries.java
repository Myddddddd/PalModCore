package com.mx.palmod.registry;

import com.mx.palmod.Palmod;
import com.mx.palmod.block.PalWorkStationBlock;
import com.mx.palmod.block.PalWorkStationBlockEntity;
import com.mx.palmod.enchantment.FastBallEnchantment;
import com.mx.palmod.enchantment.FollowingEnchantment;
import com.mx.palmod.enchantment.InfiniteEnchantment;
import com.mx.palmod.enchantment.LevelingEnchantment;
import com.mx.palmod.item.FilledPalSphereItem;
import com.mx.palmod.item.PalSphereItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModRegistries {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Palmod.MODID);
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Palmod.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Palmod.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Palmod.MODID);
    public static final DeferredRegister<Enchantment> ENCHANTMENTS = DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, Palmod.MODID);

    // ── Custom EnchantmentCategory for PalSphere items ──────────────────
    public static final EnchantmentCategory PAL_SPHERE_CATEGORY = EnchantmentCategory.create("pal_sphere",
            item -> item instanceof PalSphereItem);

    // ── Sphere items (3 tiers) ───────────────────────────────────────────
    public static final RegistryObject<Item> PAL_SPHERE = ITEMS.register("pal_sphere",
            () -> new PalSphereItem(new Item.Properties().stacksTo(64), 1));

    public static final RegistryObject<Item> PAL_SPHERE_MID = ITEMS.register("pal_sphere_mid",
            () -> new PalSphereItem(new Item.Properties().stacksTo(64), 5));

    public static final RegistryObject<Item> PAL_SPHERE_ADVANCED = ITEMS.register("pal_sphere_advanced",
            () -> new PalSphereItem(new Item.Properties().stacksTo(64), 10));

    public static final RegistryObject<Item> FILLED_PAL_SPHERE = ITEMS.register("filled_pal_sphere",
            () -> new FilledPalSphereItem(new Item.Properties().stacksTo(1)));

    // ── Paldex (standalone in-game guide + mob dictionary book) ─────────────
    public static final RegistryObject<Item> PALDEX = ITEMS.register("paldex",
            () -> new com.mx.palmod.item.PaldexItem(new Item.Properties().stacksTo(1)));

    // ── Enchantments ────────────────────────────────────────────────────
    public static final RegistryObject<Enchantment> ENCHANT_INFINITE = ENCHANTMENTS.register("infinite",
            () -> new InfiniteEnchantment(Enchantment.Rarity.RARE, PAL_SPHERE_CATEGORY));

    public static final RegistryObject<Enchantment> ENCHANT_FOLLOWING = ENCHANTMENTS.register("following",
            () -> new FollowingEnchantment(Enchantment.Rarity.UNCOMMON, PAL_SPHERE_CATEGORY));

    public static final RegistryObject<Enchantment> ENCHANT_FASTBALL = ENCHANTMENTS.register("fastball",
            () -> new FastBallEnchantment(Enchantment.Rarity.UNCOMMON, PAL_SPHERE_CATEGORY));

    public static final RegistryObject<Enchantment> ENCHANT_LEVELING = ENCHANTMENTS.register("leveling",
            () -> new LevelingEnchantment(Enchantment.Rarity.COMMON, PAL_SPHERE_CATEGORY));

    public static final RegistryObject<Enchantment> ENCHANT_WARP_TETHER = ENCHANTMENTS.register("warp_tether",
            () -> new com.mx.palmod.enchantment.WarpTetherEnchantment(Enchantment.Rarity.RARE, PAL_SPHERE_CATEGORY));

    public static final RegistryObject<Block> PAL_WORK_STATION = BLOCKS.register("pal_work_station",
            () -> new PalWorkStationBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(1.0f, 2.0f)
                    .sound(SoundType.WOOD)
                    .noOcclusion()));

    public static final RegistryObject<Item> PAL_WORK_STATION_ITEM = ITEMS.register("pal_work_station",
            () -> new BlockItem(PAL_WORK_STATION.get(), new Item.Properties()));

    public static final RegistryObject<BlockEntityType<PalWorkStationBlockEntity>> PAL_WORK_STATION_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("pal_work_station",
                    () -> BlockEntityType.Builder.of(PalWorkStationBlockEntity::new, PAL_WORK_STATION.get()).build(null));

    public static final RegistryObject<Block> PAL_FEEDER = BLOCKS.register("pal_feeder",
            () -> new com.mx.palmod.block.PalFeederBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(1.5f, 3.0f)
                    .sound(SoundType.WOOD)
                    .noOcclusion()));

    public static final RegistryObject<Item> PAL_FEEDER_ITEM = ITEMS.register("pal_feeder",
            () -> new BlockItem(PAL_FEEDER.get(), new Item.Properties()));

    public static final RegistryObject<BlockEntityType<com.mx.palmod.block.PalFeederBlockEntity>> PAL_FEEDER_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("pal_feeder",
                    () -> BlockEntityType.Builder.of(com.mx.palmod.block.PalFeederBlockEntity::new, PAL_FEEDER.get()).build(null));

    public static final RegistryObject<EntityType<com.mx.palmod.entity.PalSphereProjectile>> PAL_SPHERE_PROJECTILE = ENTITY_TYPES.register("pal_sphere",
            () -> EntityType.Builder.<com.mx.palmod.entity.PalSphereProjectile>of(com.mx.palmod.entity.PalSphereProjectile::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(4)
                    .updateInterval(10)
                    .build("pal_sphere"));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
        BLOCKS.register(eventBus);
        BLOCK_ENTITY_TYPES.register(eventBus);
        ENTITY_TYPES.register(eventBus);
        ENCHANTMENTS.register(eventBus);
    }
}

