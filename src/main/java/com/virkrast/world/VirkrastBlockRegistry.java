package com.virkrast.world;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import java.util.ArrayList;
import java.util.List;

public class VirkrastBlockRegistry {

    // Вспомогательный класс-структура для описания одного правила микса блоков
    public static class BlockRule {
        public final double maxNoiseThreshold; // До какого порога шума (от 0.0 до 1.0) применяется блок
        public final BlockState surface; // Верхний блок
        public final BlockState subSurface; // Подложка под верхним блоком

        public BlockRule(double maxNoiseThreshold, BlockState surface, BlockState subSurface) {
            this.maxNoiseThreshold = maxNoiseThreshold;
            this.surface = surface;
            this.subSurface = subSurface;
        }
    }

    // Списки правил для каждого биома суши
    public static final List<BlockRule> EMPIRE_BLOCKS = new ArrayList<>();
    public static final List<BlockRule> PLAINS_BLOCKS = new ArrayList<>();

    // Списки блоков для дна (подводные блоки)
    public static final List<BlockRule> EMPIRE_UNDERWATER = new ArrayList<>();
    public static final List<BlockRule> PLAINS_UNDERWATER = new ArrayList<>();
    public static final List<BlockRule> OCEAN_UNDERWATER = new ArrayList<>();

    // Статический блок инициализации — СЮДА вы будете прописывать свои блоки!
    static {
        // =========================================================================
        // ИНСТРУКЦИЯ: НАСТРОЙКА БЛОКОВ СУШИ ДЛЯ EMPIRE
        // Если вы добавите кастомный блок в свой мод, например
        // MOD_BLOCKS.MY_GRASS.get(),
        // вы сможете написать вместо Blocks.SAND ->
        // MOD_BLOCKS.MY_GRASS.get().defaultBlockState()
        // =========================================================================
        EMPIRE_BLOCKS.add(new BlockRule(0.40, Blocks.SAND.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState()));
        EMPIRE_BLOCKS.add(
                new BlockRule(0.70, Blocks.RED_SAND.defaultBlockState(), Blocks.RED_SANDSTONE.defaultBlockState()));
        EMPIRE_BLOCKS.add(new BlockRule(1.00, Blocks.COARSE_DIRT.defaultBlockState(), Blocks.DIRT.defaultBlockState())); // 1.00
                                                                                                                         // означает
                                                                                                                         // "все
                                                                                                                         // остальное"

        // НАСТРОЙКА БЛОКОВ СУШИ ДЛЯ РАВНИН (PLAINS)
        PLAINS_BLOCKS.add(new BlockRule(0.65, Blocks.GRASS_BLOCK.defaultBlockState(), Blocks.DIRT.defaultBlockState()));
        PLAINS_BLOCKS.add(new BlockRule(0.85, Blocks.COARSE_DIRT.defaultBlockState(), Blocks.DIRT.defaultBlockState()));
        PLAINS_BLOCKS.add(new BlockRule(1.00, Blocks.MOSS_BLOCK.defaultBlockState(), Blocks.DIRT.defaultBlockState()));

        // НАСТРОЙКА ДНА ДЛЯ РАЗНЫХ БИОМОВ
        EMPIRE_UNDERWATER
                .add(new BlockRule(1.00, Blocks.SAND.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState()));

        PLAINS_UNDERWATER.add(new BlockRule(0.50, Blocks.SAND.defaultBlockState(), Blocks.DIRT.defaultBlockState()));
        PLAINS_UNDERWATER.add(new BlockRule(1.00, Blocks.CLAY.defaultBlockState(), Blocks.DIRT.defaultBlockState()));

        OCEAN_UNDERWATER.add(new BlockRule(0.60, Blocks.GRAVEL.defaultBlockState(), Blocks.STONE.defaultBlockState()));
        OCEAN_UNDERWATER
                .add(new BlockRule(0.90, Blocks.SAND.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState()));
        OCEAN_UNDERWATER.add(
                new BlockRule(1.00, Blocks.MAGMA_BLOCK.defaultBlockState(), Blocks.NETHERRACK.defaultBlockState()));
    }

    /**
     * Универсальная функция, которая ищет подходящий блок в списке по значению шума
     */
    public static BlockRule selectRule(List<BlockRule> rules, double noiseValue) {
        for (BlockRule rule : rules) {
            if (noiseValue <= rule.maxNoiseThreshold) {
                return rule;
            }
        }
        // Защита от багов: если список пуст или пороги настроены неправильно, вернет
        // базовую траву
        return new BlockRule(1.0, Blocks.GRASS_BLOCK.defaultBlockState(), Blocks.DIRT.defaultBlockState());
    }
}
