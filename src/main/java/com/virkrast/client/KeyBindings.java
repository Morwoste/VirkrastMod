package com.virkrast.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public class KeyBindings {
    // Создаем категорию в настройках управления игры
    public static final String KEY_CATEGORY_VIRKRAST = "key.category.virkrast.virkrast";
    // Создаем название самой кнопки в настройках
    public static final String KEY_OPEN_MAP = "key.virkrast.open_map";

    // Объявляем объект нашей кнопки
    public static KeyMapping openMapKey;

    public static void init() {
        openMapKey = new KeyMapping(
            KEY_OPEN_MAP, // Название кнопки
            KeyConflictContext.IN_GAME, // Кнопка работает только когда игрок внутри мира (в игре)
            InputConstants.Type.KEYSYM, // Тип ввода (клавиатура)
            GLFW.GLFW_KEY_M, // Сама клавиша по умолчанию (M)
            KEY_CATEGORY_VIRKRAST // Категория управления
        );
    }
}
