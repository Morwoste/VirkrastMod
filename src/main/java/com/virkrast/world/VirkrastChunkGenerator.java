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
import net.minecraft.world.level.block.state.BlockState;
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

/**
 * Главный класс генератора чанков для мода Vekrast.
 * Отвечает за создание рельефа, заполнение блоков земли, камня и воды.
 */
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

        int blendRadius = 20;

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunkPos.getMinBlockX() + x;
                int worldZ = chunkPos.getMinBlockZ() + z;

                chunk.setBlockState(pos.set(x, -64, z), Blocks.BEDROCK.defaultBlockState(), false);

                double weightEmpire = 0.0;
                double weightPlains = 0.0;
                double weightOcean = 0.0;
                double totalWeights = 0.0;

                // 1. Сбор весов биомов вокруг блока
                for (int bx = -blendRadius; bx <= blendRadius; bx++) {
                    for (int bz = -blendRadius; bz <= blendRadius; bz++) {
                        int sampleX = worldX + bx;
                        int sampleZ = worldZ + bz;

                        int chunkCoordX = Math.floorDiv(sampleX, 16);
                        int chunkCoordZ = Math.floorDiv(sampleZ, 16);
                        int pixelX = chunkCoordX + 256;
                        int pixelY = chunkCoordZ + 256;

                        String currentBiome = "plains";

                        if (VirkrastBiomeSource.INSTANCE != null) {
                            BufferedImage img = VirkrastBiomeSource.INSTANCE.getMapImage();
                            if (img != null && pixelX >= 0 && pixelX < 512 && pixelY >= 0 && pixelY < 512) {
                                int rgb = img.getRGB(pixelX, pixelY) & 0xFFFFFF;
                                if (rgb == 0x06DD06) {
                                    currentBiome = "plains";
                                } else if (rgb == 0x0B770B) {
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

                        switch (currentBiome) {
                            case "empire_biome" -> weightEmpire += distanceWeight;
                            case "ocean_biome" -> weightOcean += distanceWeight;
                            default -> weightPlains += distanceWeight;
                        }

                        totalWeights += distanceWeight;
                    }
                }

                weightEmpire /= totalWeights;
                weightPlains /= totalWeights;
                weightOcean /= totalWeights;

                // 2. Генерируем текстурный шум для блоков
                double blockNoise = (noise.getAdvancedPerlin(worldX, worldZ, 2, 4.0, 0.6, 2.0) + 1.0) / 2.0;

                VirkrastBlockRegistry.BlockRule empireRule = VirkrastBlockRegistry
                        .selectRule(VirkrastBlockRegistry.EMPIRE_BLOCKS, blockNoise);
                VirkrastBlockRegistry.BlockRule empireWater = VirkrastBlockRegistry
                        .selectRule(VirkrastBlockRegistry.EMPIRE_UNDERWATER, blockNoise);
                VirkrastBlockRegistry.BlockRule plainsRule = VirkrastBlockRegistry
                        .selectRule(VirkrastBlockRegistry.PLAINS_BLOCKS, blockNoise);
                VirkrastBlockRegistry.BlockRule plainsWater = VirkrastBlockRegistry
                        .selectRule(VirkrastBlockRegistry.PLAINS_UNDERWATER, blockNoise);
                VirkrastBlockRegistry.BlockRule oceanRule = VirkrastBlockRegistry
                        .selectRule(VirkrastBlockRegistry.OCEAN_UNDERWATER, blockNoise);

                BlockState surfaceBlock;
                BlockState subSurfaceBlock;
                BlockState underwaterBlock;

                double noiseInbound = (blockNoise - 0.5) * 0.4;
                double modifiedEmpireWeight = weightEmpire + noiseInbound;

                if (modifiedEmpireWeight > 0.45) {
                    surfaceBlock = empireRule.surface;
                    subSurfaceBlock = empireRule.subSurface;
                    underwaterBlock = empireWater.surface;
                } else if (weightOcean >= weightPlains && weightOcean >= weightEmpire) {
                    surfaceBlock = oceanRule.surface;
                    subSurfaceBlock = oceanRule.subSurface;
                    underwaterBlock = oceanRule.surface;
                } else {
                    surfaceBlock = plainsRule.surface;
                    subSurfaceBlock = plainsRule.subSurface;
                    underwaterBlock = plainsWater.surface;
                }

                // === ИСПРАВЛЕНИЕ: ГЕНЕРАЦИЯ ШУМОВ НА ФИКСИРОВАННЫХ МАСШТАБАХ БИОМОВ ===

                // Расчет высот чисто для EMPIRE (используем константные масштабы: 300.0, 140.0,
                // 220.0)
                double empirePlainHills = (noise.getAdvancedPerlin(worldX, worldZ, 4, 140.0, 0.5, 2.0) + 1.0) / 2.0;
                double empireSharpMountains = noise.getRidgedMountainNoise(worldX, worldZ, 5, 220.0);
                double empireVoronoi = noise.getVoronoiDistance(worldX, worldZ, 300.0);

                double empireMountainWeight = (empireVoronoi < 0.5) ? (1.0 - (empireVoronoi / 0.5)) : 0.0;
                double empireHillsWeight = (empireVoronoi < 0.5) ? (1.0 - empireMountainWeight)
                        : (1.0 - ((empireVoronoi - 0.5) / 0.5));
                double empireHeightResult = (empireSharpMountains * empireMountainWeight * 95.0)
                        + (empirePlainHills * empireHillsWeight * 30.0);

                // Расчет высот чисто для PLAINS (используем константные масштабы: 200.0, 100.0,
                // 50.0)
                double plainsPlainHills = (noise.getAdvancedPerlin(worldX, worldZ, 4, 100.0, 0.5, 2.0) + 1.0) / 2.0;
                double plainsVoronoi = noise.getVoronoiDistance(worldX, worldZ, 200.0);

                double plainsHillsWeight = (plainsVoronoi < 0.7) ? 1.0 : (1.0 - ((plainsVoronoi - 0.7) / 0.3));
                double plainsHeightResult = plainsPlainHills * plainsHillsWeight * 30.0;

                // Расчет высот чисто для OCEAN (используем константные масштабы: 500.0, 200.0,
                // 80.0)
                double oceanPlainHills = (noise.getAdvancedPerlin(worldX, worldZ, 4, 200.0, 0.5, 2.0) + 1.0) / 2.0;
                double oceanVoronoi = noise.getVoronoiDistance(worldX, worldZ, 500.0);
                double oceanHeightResult = oceanPlainHills * oceanVoronoi * 0.4 * 15.0;

                // Подавление шумов рельефа при приближении к океану
                if (weightOcean > 0.0) {
                    double suppression = net.minecraft.util.Mth.clamp(1.0 - (weightOcean * 1.3), 0.0, 1.0);
                    empireHeightResult *= suppression;
                    plainsHeightResult *= suppression;
                }

                // Плавное снижение базовой суши к воде через Smoothstep
                double blendedBaseHeight;
                if (weightOcean > 0.0) {
                    double t = weightOcean;
                    double smoothT = t * t * (3.0 - 2.0 * t);
                    blendedBaseHeight = net.minecraft.util.Mth.lerp(smoothT, 64.0, 35.0);
                } else {
                    blendedBaseHeight = 64.0;
                }

                // Смешиваем уже ИТОГОВЫЕ высоты биомов, перемножая их на веса
                double blendedHeightBonus = (empireHeightResult * weightEmpire) + (plainsHeightResult * weightPlains)
                        + (oceanHeightResult * weightOcean);
                int calculatedSurfaceY = (int) (blendedBaseHeight + blendedHeightBonus);
                int targetHeight = net.minecraft.util.Mth.clamp(calculatedSurfaceY, -63, 320);
                int seaLevel = getSeaLevel();

                // 3. Заполнение блоков по вертикали
                for (int y = -63; y <= targetHeight; y++) {
                    int depth = targetHeight - y;

                    if (y < targetHeight - 4) {
                        chunk.setBlockState(pos.set(x, y, z), Blocks.STONE.defaultBlockState(), false);
                    } else {
                        if (targetHeight < seaLevel) {
                            if (depth <= 1) {
                                chunk.setBlockState(pos.set(x, y, z), underwaterBlock, false);
                            } else {
                                chunk.setBlockState(pos.set(x, y, z), subSurfaceBlock, false);
                            }
                        } else {
                            if (depth == 0) {
                                chunk.setBlockState(pos.set(x, y, z), surfaceBlock, false);
                            } else {
                                chunk.setBlockState(pos.set(x, y, z), subSurfaceBlock, false);
                            }
                        }
                    }
                }

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