package com.virkrast.init;

import com.virkrast.VirkrastMod;
import com.virkrast.block.VirkrastGrassBlock;
import com.virkrast.block.stone.StoneBlock; // Импортируем ваш класс камня
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = 
            DeferredRegister.create(ForgeRegistries.BLOCKS, VirkrastMod.MODID);

    public static final DeferredRegister<Item> ITEMS = 
            DeferredRegister.create(ForgeRegistries.ITEMS, VirkrastMod.MODID);

    public static final RegistryObject<Block> VIRKRAST_GRASS_BLOCK = registerBlock("virkrast_grass_block", 
            () -> new VirkrastGrassBlock());

    // НАШ НОВЫЙ БЛОК КАМНЯ! Регистрируем под ID "virkrast:stone_block"
    public static final RegistryObject<Block> STONE_BLOCK = registerBlock("stone_block", 
            () -> new StoneBlock());

    private static <T extends Block> RegistryObject<T> registerBlock(String name, Supplier<T> blockSupplier) {
        RegistryObject<T> block = BLOCKS.register(name, blockSupplier);
        ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
        return block;
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
        ITEMS.register(eventBus);
    }
}
