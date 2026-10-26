package com.virkrast;

import com.mojang.logging.LogUtils;
import com.virkrast.init.ModBlocks; // Импортируем наш новый класс с блоками
import com.virkrast.init.ModCreativeTabs; //
import com.virkrast.world.ModDimensions; //
import com.virkrast.world.VirkrastBiomeSource; //
import com.virkrast.world.VirkrastChunkGenerator; //

import net.minecraft.core.Registry; //
import net.minecraft.core.registries.BuiltInRegistries; //
import net.minecraftforge.common.MinecraftForge; //
import net.minecraftforge.eventbus.api.IEventBus; //
import net.minecraftforge.fml.common.Mod; //
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent; //
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext; //
import org.slf4j.Logger; //

@Mod(VirkrastMod.MODID) //
public class VirkrastMod {
    public static final String MODID = "virkrast"; //
    public static final Logger LOGGER = LogUtils.getLogger(); //

    public VirkrastMod() {
        // Получаем шину событий мода (Mod Event Bus) для регистрации контента
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus(); //
        
        // Регистрируем базовые вещи мода
        ModDimensions.register(modEventBus); //
        ModBlocks.register(modEventBus); // ПОДКЛЮЧАЕМ НАШИ БЛОКИ К ИГРЕ!
        ModCreativeTabs.register(modEventBus); // ПОДКЛЮЧАЕМ НАШИ КРЕАТИВНЫЕ ТАБЫ!
        
        // Регистрируем метод первичной настройки (только ОДИН раз)
        modEventBus.addListener(this::commonSetup);

        // Подключаем главную шину событий Forge (Forge Event Bus)
        // Она отвечает за игровые события: клики, тики сервера, разрушение блоков и т.д.
        MinecraftForge.EVENT_BUS.register(this); //
    }

    private void commonSetup(final FMLCommonSetupEvent event) { //
        event.enqueueWork(() -> { //
            // Наша старая регистрация биомов
            Registry.register(BuiltInRegistries.BIOME_SOURCE, 
                new net.minecraft.resources.ResourceLocation("virkrast", "virkrast_biomes"), //
                VirkrastBiomeSource.CODEC //
            );

            // НОВАЯ: Регистрируем наш собственный генератор блоков ландшафта!
            Registry.register(BuiltInRegistries.CHUNK_GENERATOR,
                new net.minecraft.resources.ResourceLocation("virkrast", "virkrast_generator"), //
                VirkrastChunkGenerator.CODEC //
            );
        });
    }
}
