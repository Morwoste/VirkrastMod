package com.virkrast.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Базовый кастомный генератор ландшафта для мира Virkrast.
 * Создает простейшую структуру: Бедрок -> Камень -> Трава.
 */
public class VirkrastChunkGenerator extends ChunkGenerator {

    // Кодек необходим Майнкрафту для регистрации генератора и чтения его данных из
    // JSON (датапаков)
    public static final Codec<VirkrastChunkGenerator> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource))
            .apply(instance, VirkrastChunkGenerator::new));

    // Настраиваемая высота поверхности земли (уровень, где будет расти трава)
    public static final int LAND_SURFACE_Y = 64;

    public VirkrastChunkGenerator(BiomeSource biomeSource) {
        super(biomeSource);
    }

    @Override
    protected Codec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    // Инициализируем наш генератор шума (убедись, что путь к пакету совпадает)
    private final com.virkrast.world.noise.NoiseGenerator noise = new com.virkrast.world.noise.NoiseGenerator(1337L);

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Executor executor, Blender blender, RandomState randomState,
            StructureManager structureManager, ChunkAccess chunk) {

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        ChunkPos chunkPos = chunk.getPos();

        // Базовые настройки масштабов для математики шумов
        double voronoiScale = 300.0;
        double perlinScale = 140.0;
        double mountainScale = 220.0;

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {

                int worldX = chunkPos.getMinBlockX() + x;
                int worldZ = chunkPos.getMinBlockZ() + z;

                // Бедрок на самом дне
                chunk.setBlockState(pos.set(x, -64, z), Blocks.BEDROCK.defaultBlockState(), false);

                // ========================================================================
                // 🛠️ ИСПРАВЛЕННЫЙ БЛЕНДИНГ БИОМОВ (Безопасный для отрицательных координат)
                // ========================================================================
                double weightEmpire = 0.0;
                double weightPlains = 0.0;
                double weightOcean = 0.0;
                double totalSamples = 0.0;

                // Проверяем сетку вокруг текущего блока в глобальных мировых координатах
                for (int bx = -4; bx <= 4; bx += 4) {
                    for (int bz = -4; bz <= 4; bz += 4) {
                        int sampleWorldX = worldX + bx;
                        int sampleWorldZ = worldZ + bz;

                        // Используем Math.floorDiv, так как он корректно делит отрицательные числа
                        // (координаты меньше нуля)
                        int biomeX = Math.floorDiv(sampleWorldX, 4);
                        int biomeZ = Math.floorDiv(sampleWorldZ, 4);

                        // Запрашиваем биом у чанка по правильной сетке
                        Holder<net.minecraft.world.level.biome.Biome> sampleHolder = chunk.getNoiseBiome(biomeX,
                                LAND_SURFACE_Y >> 2, biomeZ);
                        String sampleId = sampleHolder.unwrapKey().map(key -> key.location().getPath()).orElse("");

                        switch (sampleId) {
                            case "empire_biome" -> weightEmpire += 1.0;
                            case "plains_biome" -> weightPlains += 1.0;
                            case "ocean_biome" -> weightOcean += 1.0;
                            default -> weightPlains += 1.0;
                        }
                        totalSamples += 1.0;
                    }
                }

                // Нормализуем веса
                weightEmpire /= totalSamples;
                weightPlains /= totalSamples;
                weightOcean /= totalSamples;
                // ========================================================================
                // РАСЧЕТ МАТЕМАТИКИ ШУМОВ
                // ========================================================================
                double plainHills = (noise.getAdvancedPerlin(worldX, worldZ, 4, perlinScale, 0.5, 2.0) + 1.0) / 2.0;
                double sharpMountains = noise.getRidgedMountainNoise(worldX, worldZ, 5, mountainScale);
                double localVoronoi = noise.getVoronoiDistance(worldX, worldZ, voronoiScale);

                // Вычисляем внутренние параметры рельефа для Империи и Равнин отдельно
                // Ландшафт Империи:
                double empireMountainWeight = (localVoronoi < 0.5) ? (1.0 - (localVoronoi / 0.5)) : 0.0;
                double empireHillsWeight = (localVoronoi < 0.5) ? (1.0 - empireMountainWeight)
                        : (1.0 - ((localVoronoi - 0.5) / 0.5));
                double empireHeightResult = (sharpMountains * empireMountainWeight * 95.0)
                        + (plainHills * empireHillsWeight * 30.0);

                // Ландшафт Равнин:
                double plainsHillsWeight = (localVoronoi < 0.7) ? 1.0 : (1.0 - ((localVoronoi - 0.7) / 0.3));
                double plainsHeightResult = plainHills * plainsHillsWeight * 30.0;

                // Ландшафт Океана:
                double oceanHeightResult = plainHills * localVoronoi * 0.4 * 15.0;

                // ========================================================================
                // 🤝 ФИНАЛЬНОЕ СМЕШИВАНИЕ (Интерполяция высот по весам биомов)
                // ========================================================================
                // Смешиваем базовую высоту
                double blendedBaseHeight = (64 * weightEmpire) + (64 * weightPlains) + (35 * weightOcean);

                // Смешиваем надстройки рельефа
                double blendedHeightBonus = (empireHeightResult * weightEmpire) + (plainsHeightResult * weightPlains)
                        + (oceanHeightResult * weightOcean);

                int calculatedSurfaceY = (int) blendedBaseHeight + (int) blendedHeightBonus;
                int targetHeight = Math.min(calculatedSurfaceY, 320);

                // ========================================================================
                // ЗАПОЛНЕНИЕ БЛОКАМИ
                // ========================================================================
                for (int y = -63; y < targetHeight - 2; y++) {
                    chunk.setBlockState(pos.set(x, y, z), Blocks.STONE.defaultBlockState(), false);
                }
                chunk.setBlockState(pos.set(x, targetHeight - 2, z), Blocks.DIRT.defaultBlockState(), false);
                chunk.setBlockState(pos.set(x, targetHeight - 1, z), Blocks.DIRT.defaultBlockState(), false);
                chunk.setBlockState(pos.set(x, targetHeight, z), Blocks.GRASS_BLOCK.defaultBlockState(), false);

            }
        }

        return CompletableFuture.completedFuture(chunk);
    }

    // ========================================================================
    // ОБЯЗАТЕЛЬНЫЕ ЗАГЛУШКИ ДЛЯ ИНТЕРФЕЙСА CHUNKGENERATOR
    // Они пока остаются пустыми, так как мы пишем чистую основу ландшафта
    // ========================================================================

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structureManager, RandomState randomState,
            ChunkAccess chunk) {
    }

    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState randomState, BiomeManager biomeManager,
            StructureManager structureManager, ChunkAccess chunk, GenerationStep.Carving carving) {
    }

    @Override
    public void createStructures(RegistryAccess registryAccess, ChunkGeneratorStructureState structureState,
            StructureManager structureManager, ChunkAccess chunk, StructureTemplateManager templateManager) {
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState randomState) {
        return LAND_SURFACE_Y; // Сообщаем игре базовую высоту нашего мира
    }

    @Override
    public net.minecraft.world.level.NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level,
            RandomState randomState) {
        return new net.minecraft.world.level.NoiseColumn(-64, new net.minecraft.world.level.block.state.BlockState[0]);
    }

    @Override
    public void addDebugScreenInfo(List info, RandomState randomState, BlockPos pos) {
    }

    @Override
    public int getMinY() {
        return -64; // Минимальная глубина нижнего мира (в 1.20.1 это -64)
    }

    @Override
    public int getGenDepth() {
        return 384; // Общая высота генерации от -64 до 320
    }

    @Override
    public int getSeaLevel() {
        return 63; // Уровень океана по умолчанию
    }
}
