package com.virkrast.world.noise;

import java.util.Random;

/**
 * Класс для генерации шума Перлина и шума Вороного.
 * Все вычисления происходят на стороне сервера/логики (Server-Side).
 */
public class NoiseGenerator {

    // Таблица перестановок для шума Перлина (генерация на основе сида)
    private final int[] p = new int[512];

    /**
     * Конструктор инициализирует генератор шума случайным зерном (сидом).
     */
    public NoiseGenerator(long seed) {
        Random random = new Random(seed);
        int[] permutation = new int[256];

        // Заполняем массив числами от 0 до 255
        for (int i = 0; i < 256; i++) {
            permutation[i] = i;
        }

        // Перемешиваем массив Фишером-Йетсом
        for (int i = 255; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int temp = permutation[i];
            permutation[i] = permutation[j];
            permutation[j] = temp;
        }

        // Дублируем массив для исключения выхода за границы при сложении
        for (int i = 0; i < 256; i++) {
            p[i] = permutation[i];
            p[256 + i] = permutation[i];
        }
    }

    // ========================================================================
    // 1. НАСТРАИВАЕМЫЙ ШУМ ПЕРЛИНА (Perlin Noise)
    // Используется для создания плавных холмов и рельефа.
    // ========================================================================

    /**
     * Генерирует базовый 2D шум Перлина в диапазоне от -1.0 до 1.0.
     */
    public double perlin2D(double x, double z) {
        int X = (int) Math.floor(x) & 255;
        int Z = (int) Math.floor(z) & 255;

        x -= Math.floor(x);
        z -= Math.floor(z);

        double u = fade(x);
        double v = fade(z);

        int a = p[X] + Z;
        int b = p[X + 1] + Z;

        return lerp(v, lerp(u, grad(p[a], x, z), grad(p[b], x - 1, z)),
                lerp(u, grad(p[a + 1], x, z - 1), grad(p[b + 1], x - 1, z - 1)));
    }

    /**
     * ФУНКЦИЯ ДЛЯ ТЕСТОВ И НАСТРОЙКИ: Многооктавный шум Перлина (FBM).
     * Складывает несколько слоев шума разной частоты для большей детализации.
     * 
     * @param x           Координата X блока.
     * @param z           Координата Z блока.
     * @param octaves     Количество слоев шума (чем больше, тем детальнее рельеф,
     *                    обычно 3-6).
     * @param scale       Масштаб (на сколько делить координаты. Больше число ->
     *                    плавнее холмы).
     * @param persistence Влияние каждой следующей октавы (обычно 0.5).
     * @param lacunarity  Увеличение частоты с каждой октавой (обычно 2.0).
     * @return Значение высоты (приблизительно от -1.0 до 1.0).
     */
    public double getAdvancedPerlin(double x, double z, int octaves, double scale, double persistence,
            double lacunarity) {
        double total = 0;
        double frequency = 1.0 / scale;
        double amplitude = 1.0;
        double maxValue = 0; // Используется для нормализации результата

        for (int i = 0; i < octaves; i++) {
            total += perlin2D(x * frequency, z * frequency) * amplitude;

            maxValue += amplitude;
            amplitude *= persistence;
            frequency *= lacunarity;
        }

        return total / maxValue;
    }

    // Вспомогательные математические методы для Перлина
    private double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    private double grad(int hash, double x, double z) {
        int h = hash & 7;
        double u = h < 4 ? x : z;
        double v = h < 4 ? z : x;
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? 2.0 * v : -2.0 * v);
    }

    // ========================================================================
    // 2. НАСТРАИВАЕМЫЙ ШУМ ВОРОНОГО (Voronoi / Cellular Noise)
    // Используется для разбиения мира на ячейки (биологические зоны, регионы).
    // ========================================================================

    /**
     * Генерирует расстояние до ближайшего центра ячейки Вороного.
     * 
     * @param x     Координата X блока.
     * @param z     Координата Z блока.
     * @param scale Размер ячеек (чем больше число, тем крупнее регионы).
     * @return Расстояние от 0.0 (центр ячейки) до ~1.0+ (граница ячейки).
     */
    public double getVoronoiDistance(double x, double z, double scale) {
        double nx = x / scale;
        double nz = z / scale;

        int cellX = (int) Math.floor(nx);
        int cellZ = (int) Math.floor(nz);

        double minDist = Double.MAX_VALUE;

        // Проверяем текущую ячейку сетки и все соседние (3х3)
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                int targetCellX = cellX + i;
                int targetCellZ = cellZ + j;

                // Получаем псевдослучайную точку внутри этой ячейки сетки
                double pointX = targetCellX + getPseudoRandom(targetCellX, targetCellZ, 0);
                double pointZ = targetCellZ + getPseudoRandom(targetCellX, targetCellZ, 1);

                // Считаем обычное Евклидово расстояние до точки
                double dx = pointX - nx;
                double dz = pointZ - nz;
                double dist = Math.sqrt(dx * dx + dz * dz);

                if (dist < minDist) {
                    minDist = dist;
                }
            }
        }
        return minDist;
    }

    /**
     * Генерирует складчатый шум (Rigid / Ridged Noise) для создания острых горных
     * хребтов.
     * 
     * @param x       Координата X блока.
     * @param z       Координата Z блока.
     * @param octaves Количество слоев (для гор лучше всего 4-5).
     * @param scale   Масштаб (чем больше, тем шире и массивнее горные цепи).
     * @return Значение высоты гор от 0.0 (долины) до 1.0 (острые пики).
     */
    public double getRidgedMountainNoise(double x, double z, int octaves, double scale) {
        double total = 0;
        double frequency = 1.0 / scale;
        double amplitude = 1.0;
        double maxValue = 0;

        // Коэффициент жесткости перелома рельефа
        double persistence = 0.5;
        double lacunarity = 2.0;

        for (int i = 0; i < octaves; i++) {
            // Получаем базовый Перлин от -1.0 до 1.0
            double n = perlin2D(x * frequency, z * frequency);

            // ⚠️ Главный секрет гор: берем модуль и переворачиваем!
            // Math.abs(n) делает все значения положительными (создает впадины на месте
            // волн)
            // 1.0 - Math.abs(n) превращает эти впадины в острые пики
            n = 1.0 - Math.abs(n);

            // Возводим в квадрат, чтобы сделать склоны у подножья более пологими,
            // а сами пики — более резкими и выразительными
            n = n * n;

            total += n * amplitude;
            maxValue += amplitude;

            amplitude *= persistence;
            frequency *= lacunarity;
        }

        return total / maxValue;
    }

    /**
     * Быстрая функция хэширования для получения случайного смещения точек Вороного.
     */
    private double getPseudoRandom(int cx, int cz, int seedOffset) {
        long hash = (long) cx * 3432918353L + (long) cz * 461845907L + seedOffset;
        hash = Long.rotateLeft(hash, 15) * 5L + 0xe6546b64L;
        return (double) (Math.abs(hash) % 1000) / 1000.0;
    }
}
