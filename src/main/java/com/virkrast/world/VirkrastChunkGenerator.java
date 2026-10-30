package com.virkrast.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
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

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class VirkrastChunkGenerator extends ChunkGenerator {

    public static final Codec<VirkrastChunkGenerator> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource))
            .apply(instance, VirkrastChunkGenerator::new));

    public static final int LAND_SURFACE_Y = 64;
    private final com.virkrast.world.noise.NoiseGenerator noise = new com.virkrast.world.noise.NoiseGenerator(1337L);

    public VirkrastChunkGenerator(BiomeSource biomeSource) {
        super(biomeSource);
    }

    @Override
    protected Codec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Executor executor, Blender blender, RandomState randomState,
            StructureManager structureManager, ChunkAccess chunk) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        ChunkPos chunkPos = chunk.getPos();

        double voronoiScale = 300.0;
        double perlinScale = 140.0;
        double mountainScale = 220.0;
        int blendRadius = 12;

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunkPos.getMinBlockX() + x;
                int worldZ = chunkPos.getMinBlockZ() + z;

                // Бедрок на самое дно
                chunk.setBlockState(pos.set(x, -64, z), Blocks.BEDROCK.defaultBlockState(), false);

                double weightEmpire = 0.0;
                double weightPlains = 0.0;
                double weightOcean = 0.0;
                double totalWeights = 0.0;

                // Сканируем область вокруг блока по картинке для плавного сглаживания биомов
                for (int bx = -blendRadius; bx <= blendRadius; bx++) {
                    for (int bz = -blendRadius; bz <= blendRadius; bz++) {
                        int sampleX = worldX + bx;
                        int sampleZ = worldZ + bz;

                        int chunkCoordX = Math.floorDiv(sampleX, 16);
                        int chunkCoordZ = Math.floorDiv(sampleZ, 16);
                        int pixelX = chunkCoordX + 256;
                        int pixelY = chunkCoordZ + 256;

                        String currentBiome = "plains"; // По умолчанию

                        if (VirkrastBiomeSource.INSTANCE != null) {
                            BufferedImage img = VirkrastBiomeSource.INSTANCE.getMapImage();
                            if (img != null && pixelX >= 0 && pixelX < 512 && pixelY >= 0 && pixelY < 512) {
                                int rgb = img.getRGB(pixelX, pixelY) & 0xFFFFFF;

                                if (rgb == 0x06DD06 || rgb == 0x0B770B) {
                                    currentBiome = "empire_biome";
                                } else {
                                    currentBiome = "ocean_biome";
                                }
                            } else {
                                currentBiome = "ocean_biome";
                            }
                        }

                        double distanceSq = (bx * bx + bz * bz);
                        double distanceWeight = 1.0 / (1.0 + distanceSq * 0.1);

                        if ("empire_biome".equals(currentBiome)) {
                            weightEmpire += distanceWeight;
                        } else if ("ocean_biome".equals(currentBiome)) {
                            weightOcean += distanceWeight;
                        } else {
                            weightPlains += distanceWeight;
                        }
                        totalWeights += distanceWeight;
                    }
                }

                // Нормализация весов биомов
                weightEmpire /= totalWeights;
                weightPlains /= totalWeights;
                weightOcean /= totalWeights;

                // Генерация шумов
                double plainHills = (noise.getAdvancedPerlin(worldX, worldZ, 4, perlinScale, 0.5, 2.0) + 1.0) / 2.0;
                double sharpMountains = noise.getRidgedMountainNoise(worldX, worldZ, 5, mountainScale);
                double localVoronoi = noise.getVoronoiDistance(worldX, worldZ, voronoiScale);

                // Расчет высот для биома Empire
                double empireMountainWeight = (localVoronoi < 0.5) ? (1.0 - (localVoronoi / 0.5)) : 0.0;
                double empireHillsWeight = (localVoronoi < 0.5) ? (1.0 - empireMountainWeight)
                        : (1.0 - ((localVoronoi - 0.5) / 0.5));
                double empireHeightResult = (sharpMountains * empireMountainWeight * 95.0)
                        + (plainHills * empireHillsWeight * 30.0);

                // Расчет высот для биома Равнин (Plains)
                double plainsHillsWeight = (localVoronoi < 0.7) ? 1.0 : (1.0 - ((localVoronoi - 0.7) / 0.3));
                double plainsHeightResult = plainHills * plainsHillsWeight * 30.0;

                // Сильное подавление шумов рельефа при приближении к океану
                if (weightOcean > 0.0) {
                    double suppression = net.minecraft.util.Mth.clamp(1.0 - (weightOcean * 1.3), 0.0, 1.0);
                    empireHeightResult *= suppression;
                    plainsHeightResult *= suppression;
                }

                // Расчет высоты дна океана
                double oceanHeightResult = plainHills * localVoronoi * 0.4 * 15.0;

                // === ИСПРАВЛЕНИЕ ТУТ: Плавное снижение всей базовой суши к воде ===
                // Вместо линейного смешивания (64 -> 35), мы используем плавную интерполяцию
                // (Smoothstep).
                // Когда океан близко (weightOcean растет), базовая высота суши мягко опускается
                // до 62-63 блоков,
                // образуя пологий пляж, а затем уходит под воду (до 35 блоков дна).
                double landWeight = weightEmpire + weightPlains;
                double blendedBaseHeight;

                if (weightOcean > 0.0) {
                    // Используем функцию Smoothstep для идеального сглаживания базовой линии
                    double t = weightOcean;
                    double smoothT = t * t * (3.0 - 2.0 * t);

                    // Постепенно переходим от высоты суши (64) к высоте океанического дна (35)
                    blendedBaseHeight = net.minecraft.util.Mth.lerp(smoothT, 64.0, 35.0);
                } else {
                    blendedBaseHeight = 64.0;
                }

                double blendedHeightBonus = (empireHeightResult * weightEmpire) + (plainsHeightResult * weightPlains)
                        + (oceanHeightResult * weightOcean);

                int calculatedSurfaceY = (int) (blendedBaseHeight + blendedHeightBonus);
                int targetHeight = net.minecraft.util.Mth.clamp(calculatedSurfaceY, -63, 320);

                int seaLevel = getSeaLevel();

                // 1. Заполнение толщи земли камнем
                for (int y = -63; y < targetHeight - 2; y++) {
                    chunk.setBlockState(pos.set(x, y, z), Blocks.STONE.defaultBlockState(), false);
                }

                // 2. Формирование верхнего слоя (суша или океаническое дно)
                if (targetHeight >= -62) {
                    if (targetHeight < seaLevel) {
                        chunk.setBlockState(pos.set(x, targetHeight - 2, z), Blocks.DIRT.defaultBlockState(), false);
                        chunk.setBlockState(pos.set(x, targetHeight - 1, z), Blocks.GRAVEL.defaultBlockState(), false);
                        chunk.setBlockState(pos.set(x, targetHeight, z), Blocks.GRAVEL.defaultBlockState(), false);
                    } else {
                        chunk.setBlockState(pos.set(x, targetHeight - 2, z), Blocks.DIRT.defaultBlockState(), false);
                        chunk.setBlockState(pos.set(x, targetHeight - 1, z), Blocks.DIRT.defaultBlockState(), false);
                        chunk.setBlockState(pos.set(x, targetHeight, z), Blocks.GRASS_BLOCK.defaultBlockState(), false);
                    }
                }

                // 3. Заполнение водой до уровня моря
                for (int y = targetHeight + 1; y <= seaLevel; y++) {
                    chunk.setBlockState(pos.set(x, y, z), Blocks.WATER.defaultBlockState(), false);
                }
            }
        }

        return CompletableFuture.completedFuture(chunk);
    }

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
        return LAND_SURFACE_Y;
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
        return -64;
    }

    @Override
    public int getGenDepth() {
        return 384;
    }

    @Override
    public int getSeaLevel() {
        return 63;
    }
}