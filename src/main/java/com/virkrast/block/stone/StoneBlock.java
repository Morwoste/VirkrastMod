package com.virkrast.block.stone;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public class StoneBlock extends Block {
    public StoneBlock() {
        // blockBehaviour.Properties.of() — это конструктор свойств.
        // Мы говорим игре, что наш блок:
        // 1. Имеет цвет травы на карте (MapColor.GRASS)
        // 2. Ломается с такой же скоростью, как земля (прочность 0.6f)
        // 3. Издает звуки травы при ходьбе и ломании (SoundType.GRASS)
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.STONE)
                .strength(0.9f)
                .sound(SoundType.STONE));
    }
}
