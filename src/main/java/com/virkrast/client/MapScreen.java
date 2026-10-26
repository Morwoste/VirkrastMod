package com.virkrast.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

public class MapScreen extends Screen {

    private static final ResourceLocation MAP_TEXTURE = new ResourceLocation("virkrast", "textures/gui/world_map.png");
    private static final ResourceLocation MARKER_TEXTURE = new ResourceLocation("virkrast", "textures/gui/player_marker.png");

    private static final int MAP_WIDTH = 512;
    private static final int MAP_HEIGHT = 512;

    // Переменные для трансформации карты (смещение теперь считается ОТНОСИТЕЛЬНО ЦЕНТРА РАМКИ)
    private float zoom = 1.0f;
    private float offsetX = 0.0f;
    private float offsetY = 0.0f;

    private boolean isDragging = false;
    private double lastMouseX;
    private double lastMouseY;

    // Размеры и координаты рамки (вычисляются динамически)
    private int frameX, frameY, frameWidth, frameHeight;

    public MapScreen() {
        super(Component.literal("Карта мира"));
    }

    @Override
    protected void init() {
        super.init();
        
        // Вычисляем размер рамки: 40% от наименьшей стороны экрана, чтобы рамка всегда была квадратной и аккуратной
        int minSide = Math.min(this.width, this.height);
        this.frameWidth = (int) (minSide * 0.4f);
        this.frameHeight = this.frameWidth;

        // Центрируем рамку на экране
        this.frameX = (this.width - this.frameWidth) / 2;
        this.frameY = (this.height - this.frameHeight) / 2;

        // Изначально сдвигаем карту так, чтобы её центр (256, 256) был ровно по центру рамки
        this.offsetX = this.frameWidth / 2f - (MAP_WIDTH / 2f) * this.zoom;
        this.offsetY = this.frameHeight / 2f - (MAP_HEIGHT / 2f) * this.zoom;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 1. Стандартный фон игры (затемнение)
        this.renderBackground(guiGraphics);

        // 2. Рисуем рамку-контейнер
        guiGraphics.fill(this.frameX - 2, this.frameY - 2, this.frameX + this.frameWidth + 2, this.frameY + this.frameHeight + 2, 0xFF555555); // Обводка
        guiGraphics.fill(this.frameX, this.frameY, this.frameX + this.frameWidth, this.frameY + this.frameHeight, 0xFF000000); // Внутренний фон

        // Ограничиваем перетаскивание карты
        clampMargins();

        // 3. Активируем обрезку по рамке
        double scale = Minecraft.getInstance().getWindow().getGuiScale();
        int scissorX = (int) (this.frameX * scale);
        int scissorY = (int) ((this.height - (this.frameY + this.frameHeight)) * scale);
        int scissorW = (int) (this.frameWidth * scale);
        int scissorH = (int) (this.frameHeight * scale);
        
        RenderSystem.enableScissor(scissorX, scissorY, scissorW, scissorH);

        // Заполняем фон синим океаном
        guiGraphics.fill(this.frameX, this.frameY, this.frameX + this.frameWidth, this.frameY + this.frameHeight, 0xFF002244);

        // Сдвигаем матрицу к рамке и применяем зум/перетаскивание
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(this.frameX + offsetX, this.frameY + offsetY, 0);
        guiGraphics.pose().scale(zoom, zoom, 1.0f);

        // Рисуем текстуру самой карты
        RenderSystem.setShaderTexture(0, MAP_TEXTURE);
        guiGraphics.blit(MAP_TEXTURE, 0, 0, 0, 0, MAP_WIDTH, MAP_HEIGHT, MAP_WIDTH, MAP_HEIGHT);

         // ЧЕСТНЫЙ РЕНДЕР МАРКЕРА ИГРОКА
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            // 1 пиксель карты = 1 чанк (16 блоков)
            // Добавляем +256.0f, чтобы координаты мира (0, 0) стали центром картинки (256, 256)
            float playerMapX = 256.0f + (float) (player.getX() / 16.0);
            float playerMapY = 256.0f + (float) (player.getZ() / 16.0);

            guiGraphics.pose().pushPose();
            
            // Сдвигаем холст в точку, где стоит игрок относительно карты
            guiGraphics.pose().translate(playerMapX, playerMapY, 0);
            
            // Поворачиваем стрелочку по направлению взгляда (YRot)
            guiGraphics.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(player.getYRot() + 180f));
            
            // Центрируем сам маркер (размер 16х16)
            int markerSize = 10; 
            guiGraphics.pose().translate(-markerSize / 2f, -markerSize / 2f, 0);

            // ВАЖНО: Принудительно перезапускаем шейдер текстуры перед рендером маркера
            RenderSystem.setShaderTexture(0, MARKER_TEXTURE);
            guiGraphics.blit(MARKER_TEXTURE, 0, 0, 0, 0, markerSize, markerSize, markerSize, markerSize);

            guiGraphics.pose().popPose();
        }

        guiGraphics.pose().popPose();

        // Отключаем ножницы
        RenderSystem.disableScissor();

        // Выводим подсказки
        guiGraphics.drawCenteredString(this.font, "Карта Virkrast (1 пиксель = 1 чанк)", this.width / 2, this.frameY - 15, 0xFFFFFF);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    // ВАЖНО: Меняем возвращаемое значение на false!
    // Теперь, когда карта открыта, мир вокруг продолжает жить, а игрок может ходить.
    

    // Метод ограничивает перемещение карты, чтобы игрок не утянул её полностью за экран
    private void clampMargins() {
        // Позволяем оттянуть карту максимум на половину размера рамки в любую сторону
        float maxOffsetH = this.frameWidth / 2f;
        float maxOffsetV = this.frameHeight / 2f;

        float minX = maxOffsetH - (MAP_WIDTH * zoom);
        float maxX = this.frameWidth - maxOffsetH;
        float minY = maxOffsetV - (MAP_HEIGHT * zoom);
        float maxY = this.frameHeight - maxOffsetV;

        this.offsetX = Mth.clamp(this.offsetX, minX, maxX);
        this.offsetY = Mth.clamp(this.offsetY, minY, maxY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Мы можем зажать карту только если кликнули ВНУТРИ рамки
        if (button == 0 && mouseX >= frameX && mouseX <= frameX + frameWidth && mouseY >= frameY && mouseY <= frameY + frameHeight) {
            this.isDragging = true;
            this.lastMouseX = mouseX;
            this.lastMouseY = mouseY;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            this.isDragging = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.isDragging) {
            this.offsetX += (mouseX - this.lastMouseX);
            this.offsetY += (mouseY - this.lastMouseY);
            this.lastMouseX = mouseX;
            this.lastMouseY = mouseY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        // Зумим только если курсор находится над рамкой
        if (mouseX >= frameX && mouseX <= frameX + frameWidth && mouseY >= frameY && mouseY <= frameY + frameHeight) {
            float zoomFactor = (delta > 0) ? 1.1f : 0.9f;
            float oldZoom = this.zoom;
            this.zoom = Mth.clamp(this.zoom * zoomFactor, 0.3f, 8.0f); // Ограничение зума от 0.3x до 8.0x

            // Масштабирование относительно курсора мыши
            this.offsetX = (float) (mouseX - frameX - (mouseX - frameX - this.offsetX) * (this.zoom / oldZoom));
            this.offsetY = (float) (mouseY - frameY - (mouseY - frameY - this.offsetY) * (this.zoom / oldZoom));
            return true;
        }
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
