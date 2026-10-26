package com.virkrast.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

// Говорим Forge, что этот класс слушает события, но строго на КЛИЕНТЕ (Dist.CLIENT)
// "virkrast" — это MODID твоего мода (убедись, что он совпадает с тем, что в main-классе)
@Mod.EventBusSubscriber(modid = "virkrast", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ClientModEvents {

    // Это событие вызывается при запуске игры на этапе регистрации кнопок
    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        // Инициализируем нашу кнопку
        KeyBindings.init();
        // Регистрируем её в системе Forge
        event.register(KeyBindings.openMapKey);
    }

    // Внутренний подкласс для отслеживания нажатия каждый игровой такт (тик)
    // Этот класс слушает шину FORGE, а не MOD, поэтому регистрируется отдельно
    @Mod.EventBusSubscriber(modid = "virkrast", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class ForgeClientEvents {

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            // Проверяем, что это конец тика, чтобы не срабатывало дважды, и что игрок в мире
            if (event.phase == TickEvent.Phase.END && Minecraft.getInstance().player != null) {
                // Если наша кнопка была нажата
                while (KeyBindings.openMapKey.consumeClick()) {
                    // Открываем наш экран MapScreen
                    Minecraft.getInstance().setScreen(new MapScreen());
                }
            }
        }
    }
}
