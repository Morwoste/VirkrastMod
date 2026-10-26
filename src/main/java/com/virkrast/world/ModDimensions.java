package com.virkrast.world;

import net.minecraft.core.registries.Registries;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import com.mojang.serialization.Codec;
import net.minecraft.world.level.biome.BiomeSource;

public class ModDimensions {
    // Создаем регистратор для источников биомов
    public static final DeferredRegister<Codec<? extends BiomeSource>> BIOME_SOURCES = 
            DeferredRegister.create(Registries.BIOME_SOURCE, "virkrast");

    // Регистрируем наш кодек под именем "virkrast_biomes"
    public static final RegistryObject<Codec<VirkrastBiomeSource>> VIRKRAST_BIOME_SOURCE = 
            BIOME_SOURCES.register("virkrast_biomes", () -> VirkrastBiomeSource.CODEC);

    public static void register(IEventBus eventBus) {
        BIOME_SOURCES.register(eventBus);
    }
}
