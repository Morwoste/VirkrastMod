package com.virkrast.init;

import com.virkrast.VirkrastMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModCreativeTabs {
    // Создаем "список ожидания" (DeferredRegister) специально для вкладок креативного режима
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = 
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, VirkrastMod.MODID);

    // Создаем и настраиваем нашу вкладку для блоков
    public static final RegistryObject<CreativeModeTab> VIRKRAST_BLOCKS_TAB = CREATIVE_MODE_TABS.register("virkrast_blocks_tab", 
            () -> CreativeModeTab.builder()
                    // Устанавливаем иконку для вкладки (берем наш блок травы)
                    .icon(() -> new ItemStack(ModBlocks.VIRKRAST_GRASS_BLOCK.get()))
                    // Название вкладки для перевода (сделаем его красивым позже через языковой файл)
                    .title(Component.translatable("creativetab.virkrast_blocks"))
                    // А здесь мы перечисляем, какие предметы лежат внутри этой вкладки
                    .displayItems((parameters, output) -> {
                        // Добавляем предмет нашего блока травы во вкладку
                        output.accept(ModBlocks.VIRKRAST_GRASS_BLOCK.get()); 
                    })
                    .build());

    // Этот метод мы вызовем в главном классе мода, чтобы включить вкладки в игру
    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }
}
