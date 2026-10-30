import math
import random
from PIL import Image

# === НАСТРОЙКИ (Меняй их здесь!) ===
MODE = "perlin"       # "perlin" или "voronoi"
WIDTH, HEIGHT = 512, 512  # Размер получаемой картинки в пикселях

# Настройки для Перлина
SCALE_PERLIN = 220.0
OCTAVES = 4

# Настройки для Вороного
SCALE_VORONOI = 450.0
# ==================================

# Инициализация таблицы перестановок для Перлина (как в Java)
p = list(range(256))
random.seed(1337)
random.shuffle(p)
p = p + p

def fade(t): return t * t * t * (t * (t * 6 - 15) + 10)
def lerp(t, a, b): return a + t * (b - a)
def grad(hash_val, x, z):
    h = hash_val & 7
    u = x if h < 4 else z
    v = z if h < 4 else x
    return ((u if (h & 1) == 0 else -u) + (2.0 * v if (h & 2) == 0 else -2.0 * v))

def perlin2d(x, z):
    X = int(math.floor(x)) & 255
    Z = int(math.floor(z)) & 255
    x -= math.floor(x)
    z -= math.floor(z)
    u, v = fade(x), fade(z)
    a, b = p[X] + Z, p[X + 1] + Z
    return lerp(v, lerp(u, grad(p[a], x, z), grad(p[b], x - 1, z)),
                   lerp(u, grad(p[a + 1], x, z - 1), grad(p[b + 1], x - 1, z - 1)))

def get_advanced_perlin(x, z, octaves, scale):
    total, frequency, amplitude, max_val = 0, 1.0 / scale, 1.0, 0
    for _ in range(octaves):
        total += perlin2d(x * frequency, z * frequency) * amplitude
        max_val += amplitude
        amplitude *= 0.5
        frequency *= 2.0
    return total / max_val

def get_pseudo_random(cx, cz):
    hash_val = (cx * 3432918353 + cz * 461845907) & 0xFFFFFFFF
    hash_val = ((hash_val << 15) | (hash_val >> 17)) * 5 + 0xe6546b64
    return (abs(hash_val) % 1000) / 1000.0

def get_voronoi_distance(x, z, scale):
    nx, nz = x / scale, z / scale
    cell_X, cell_Z = int(math.floor(nx)), int(math.floor(nz))
    min_dist = float('inf')
    for i in range(-1, 2):
        for j in range(-1, 2):
            tX, tZ = cell_X + i, cell_Z + j
            pX = tX + get_pseudo_random(tX, tZ)
            pZ = tZ + get_pseudo_random(tZ, tX) # небольшой оффсет для Z
            dx, dz = pX - nx, pZ - nz
            dist = math.sqrt(dx*dx + dz*dz)
            if dist < min_dist: min_dist = dist
    return min_dist
# Генерация изображения
img = Image.new("RGB", (WIDTH, HEIGHT))
pixels = img.load()

for x in range(WIDTH):
    for z in range(HEIGHT):
        if MODE == "perlin":
            val = get_advanced_perlin(x, z, OCTAVES, SCALE_PERLIN)
            # Переводим из диапазона [-1, 1] в [0, 255] для цвета
            color = int((val + 1) / 2 * 255)
            color = max(0, min(255, color))
            pixels[x, z] = (color, color, color) # Численное отображение высоты (серый цвет)
        else:
            dist = get_voronoi_distance(x, z, SCALE_VORONOI)
            if dist < 0.15:
                pixels[x, z] = (255, 0, 0) # Красный центр ячейки
            elif dist > 0.85:
                pixels[x, z] = (0, 0, 0)   # Черная граница
            else:
                pixels[x, z] = (100, 200, 100) # Зеленое тело биома

img.save("noise_result.png")
print("Картинка noise_result.png успешно сохранена!")
# # Генерация каноничного шума Вороного (Диаграммы Вороного)
# img = Image.new("RGB", (WIDTH, HEIGHT))
# pixels = img.load()

# for x in range(WIDTH):
#     for z in range(HEIGHT):
#         # Получаем чистое расстояние от 0.0 до ~1.0+
#         dist = get_voronoi_distance(x, z, SCALE_VORONOI)
        
#         # Ограничиваем значение до 1.0, чтобы не выйти за пределы цветов
#         dist = min(1.0, dist)
        
#         # Переводим в оттенки серого (0 — черный центр, 255 — белые стыки/границы)
#         color = int(dist * 255)
        
#         pixels[x, z] = (color, color, color)

# img.save("noise_result.png")
# print("Картинка с чистым шумом Вороного успешно сохранена!")
