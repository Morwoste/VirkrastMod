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
    
    public static final Codec<VirkrastChunkGenerator> CODEC = RecordCodecBuilder.create(instance -> 
        instance.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource)
        ).apply(instance, VirkrastChunkGenerator::new)
    );

    public VirkrastChunkGenerator(BiomeSource biomeSource) {
        super(biomeSource);
    }

    @Override
    protected Codec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Executor executor, Blender blender, RandomState randomState, StructureManager structureManager, ChunkAccess chunk) {
        ChunkPos chunkPos = chunk.getPos();
        int chunkX = chunkPos.x;
        int chunkZ = chunkPos.z;

        int pixelX = chunkX + 256;
        int pixelY = chunkZ + 256;

        boolean isLand = false;

        if (VirkrastBiomeSource.INSTANCE != null) {
            BufferedImage img = VirkrastBiomeSource.INSTANCE.getMapImage();
            if (img != null && pixelX >= 0 && pixelX < 512 && pixelY >= 0 && pixelY < 512) {
                int rgb = img.getRGB(pixelX, pixelY) & 0xFFFFFF;
                if (rgb == 0x06DD06 || rgb == 0x0B770B) {
                    isLand = true;
                }
            }
        }

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                chunk.setBlockState(pos.set(x, -64, z), Blocks.BEDROCK.defaultBlockState(), false);

                for (int y = -63; y <= 60; y++) {
                    chunk.setBlockState(pos.set(x, y, z), Blocks.STONE.defaultBlockState(), false);
                }

                if (isLand) {
                    chunk.setBlockState(pos.set(x, 61, z), Blocks.DIRT.defaultBlockState(), false);
                    chunk.setBlockState(pos.set(x, 62, z), Blocks.DIRT.defaultBlockState(), false);
                    chunk.setBlockState(pos.set(x, 63, z), Blocks.GRASS_BLOCK.defaultBlockState(), false);
                } else {
                    for (int y = 61; y <= 63; y++) {
                        chunk.setBlockState(pos.set(x, y, z), Blocks.WATER.defaultBlockState(), false);
                    }
                }
            }
        }
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structureManager, RandomState randomState, ChunkAccess chunk) {}

    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState randomState, BiomeManager biomeManager, StructureManager structureManager, ChunkAccess chunk, GenerationStep.Carving carving) {}

    @Override
    public void createStructures(RegistryAccess registryAccess, ChunkGeneratorStructureState structureState, StructureManager structureManager, ChunkAccess chunk, StructureTemplateManager templateManager) {}

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {}

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState randomState) {
        return 63;
    }

    @Override
    public net.minecraft.world.level.NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState randomState) {
        return new net.minecraft.world.level.NoiseColumn(-64, new net.minecraft.world.level.block.state.BlockState[0]);
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState randomState, BlockPos pos) {}

    @Override
    public int getMinY() { return -64; }

    @Override
    public int getGenDepth() { return 384; }

    @Override
    public int getSeaLevel() { return 63; }
}
