package com.virkrast.world;

import com.mojang.serialization.Codec;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.stream.Stream;

public class VirkrastBiomeSource extends BiomeSource {
    public static final Codec<VirkrastBiomeSource> CODEC = com.mojang.serialization.codecs.RecordCodecBuilder.create(instance -> 
        instance.group(
            net.minecraft.world.level.biome.Biome.CODEC.fieldOf("plains_biome").forGetter(src -> src.plains),
            net.minecraft.world.level.biome.Biome.CODEC.fieldOf("forest_biome").forGetter(src -> src.forest),
            net.minecraft.world.level.biome.Biome.CODEC.fieldOf("ocean_biome").forGetter(src -> src.ocean)
        ).apply(instance, VirkrastBiomeSource::new)
    );

    public static VirkrastBiomeSource INSTANCE;

    private BufferedImage mapImage;
    private final Holder<Biome> plains;
    private final Holder<Biome> forest;
    private final Holder<Biome> ocean;

    public VirkrastBiomeSource(Holder<Biome> plains, Holder<Biome> forest, Holder<Biome> ocean) {
        this.plains = plains;
        this.forest = forest;
        this.ocean = ocean;
        INSTANCE = this;
        loadImage();
    }

    private void loadImage() {
        try {
            InputStream stream = getClass().getResourceAsStream("/data/virkrast/world_map.png");
            if (stream != null) {
                this.mapImage = ImageIO.read(stream);
                stream.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public BufferedImage getMapImage() {
        return this.mapImage;
    }

    @Override
    protected Codec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return Stream.of(plains, forest, ocean);
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, net.minecraft.world.level.biome.Climate.Sampler sampler) {
        int chunkX = Math.floorDiv(x, 4);
        int chunkZ = Math.floorDiv(z, 4);

        int pixelX = chunkX + 256;
        int pixelY = chunkZ + 256;

        if (mapImage == null || pixelX < 0 || pixelX >= 512 || pixelY < 0 || pixelY >= 512) {
            return ocean;
        }

        int rgb = mapImage.getRGB(pixelX, pixelY) & 0xFFFFFF;

        if (rgb == 0x06DD06) {
            return plains;
        }
        if (rgb == 0x0B770B) {
            return forest;
        }
        if (rgb == 0x00CCFF) {
            return ocean;
        }

        return ocean;
    }
}
