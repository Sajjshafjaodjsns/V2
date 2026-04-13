# AutoLoader Scania 4×4

Android-приложение для расчёта погрузки 8 автомобилей на автовоз  
**Scania P-series 4×4 (2008)** с установкой **Uçsuoğlu**.

---

## Статус сборки

[![Build Debug APK](https://github.com/ВАШ_ЛОГИН/ВАШ_РЕПО/actions/workflows/build_debug.yml/badge.svg)](https://github.com/ВАШ_ЛОГИН/ВАШ_РЕПО/actions/workflows/build_debug.yml)
[![Run Unit Tests](https://github.com/ВАШ_ЛОГИН/ВАШ_РЕПО/actions/workflows/run_tests.yml/badge.svg)](https://github.com/ВАШ_ЛОГИН/ВАШ_РЕПО/actions/workflows/run_tests.yml)

> Замените `ВАШ_ЛОГИН/ВАШ_РЕПО` на реальный путь к репозиторию.

---

## Скачать APK

**Debug APK** (для тестирования):  
Перейдите: `Actions → Build Debug APK → последний run → Artifacts`

**Release APK** (стабильный):  
Перейдите: `Releases → последний релиз → Assets`

---

## Структура проекта

```
AutoLoaderApp/
├── .github/
│   └── workflows/
│       ├── build_debug.yml    ← сборка debug APK при каждом push
│       ├── build_release.yml  ← подписанный release APK по тегу v*
│       └── run_tests.yml      ← unit-тесты при pull request
│
├── app/src/main/java/com/autoloader/scania/
│   ├── model/          ← данные: CarSpecs, LoadPlan, UiState
│   ├── domain/         ← бизнес-логика: алгоритм, валидация
│   ├── repository/     ← получение данных: парсинг + fallback база
│   ├── viewmodel/      ← LoadPlanViewModel
│   └── ui/             ← MainActivity, ResultRenderer
│
├── app/src/test/       ← unit-тесты (18 тестов)
├── gradle/wrapper/     ← gradle-wrapper.jar + .properties
├── gradlew             ← скрипт запуска Gradle (Unix)
└── gradlew.bat         ← скрипт запуска Gradle (Windows)
```

---

## Физическая модель автовоза

| Слот | Позиция | Платформа | Макс. авто |
|------|---------|-----------|------------|
| 1 | Тягач — нижний, перед | 1 200 мм | 2 800 мм |
| 2 | Тягач — нижний, зад | 1 200 мм | 2 800 мм |
| **3** | **Тягач — над кабиной ⚠** | **2 550 мм** | **1 450 мм** |
| 4 | Прицеп — нижний, 1-я | 1 050 мм | 2 950 мм |
| 5 | Прицеп — нижний, 2-я | 1 050 мм | 2 950 мм |
| 6 | Прицеп — нижний, 3-я | 1 050 мм | 2 950 мм |
| 7 | Прицеп — верхний, 1-я | 2 150 мм | 1 850 мм |
| 8 | Прицеп — верхний, 2-я | 2 150 мм | 1 850 мм |

Рама 4×4 выше стандарта на 250 мм → слот 3 ограничен 1 450 мм  
`4 000 − 2 550 = 1 450` (максимальный габарит автопоезда 4 000 мм)

---

## Как загрузить в GitHub и собрать APK

### Шаг 1 — Подготовка gradle-wrapper.jar

Этот файл не включён в архив (бинарный). Нужен ОДИН из способов:

**Способ А — через Android Studio:**
1. Откройте проект в Android Studio
2. `Tools → Gradle → Generate Wrapper`

**Способ Б — через командную строку:**
```bash
# Если Gradle установлен глобально:
gradle wrapper --gradle-version 8.4

# ИЛИ скачать файл напрямую:
curl -L "https://github.com/gradle/gradle/raw/v8.4.0/gradle/wrapper/gradle-wrapper.jar" \
     -o gradle/wrapper/gradle-wrapper.jar
```

### Шаг 2 — Создать репозиторий на GitHub

1. Зайдите на [github.com](https://github.com) → **New repository**
2. Имя: `AutoLoaderScania` (или любое)
3. **НЕ** инициализируйте с README (мы сами загрузим)
4. Нажмите **Create repository**

### Шаг 3 — Загрузить код

```bash
cd AutoLoaderApp

# Инициализация git
git init
git add .
git commit -m "Initial commit: AutoLoader Scania 4x4 v3.0"

# Подключение к GitHub (замените URL на свой)
git remote add origin https://github.com/ВАШ_ЛОГИН/AutoLoaderScania.git
git branch -M main
git push -u origin main
```

### Шаг 4 — Смотреть результат сборки

1. Откройте репозиторий на GitHub
2. Перейдите на вкладку **Actions**
3. Workflow **Build Debug APK** запустится автоматически
4. После завершения (~3-5 минут) нажмите на run → **Artifacts** → скачайте APK

### Шаг 5 — Создать Release (подписанный APK)

```bash
# Создаём тег — это запустит build_release.yml
git tag v1.0.0
git push origin v1.0.0
```

Подписанный APK появится в разделе **Releases** репозитория.

---

## Настройка секретов для подписанного APK

Нужно один раз в настройках репозитория:  
`Settings → Secrets and variables → Actions`

| Секрет | Значение |
|--------|----------|
| `KEYSTORE_BASE64` | Keystore файл в base64: `base64 -w 0 my-key.jks` |
| `KEY_STORE_PASSWORD` | Пароль от keystore |
| `KEY_ALIAS` | Alias ключа |
| `KEY_PASSWORD` | Пароль ключа |

**Создать keystore** (один раз, локально):
```bash
keytool -genkey -v \
  -keystore my-release-key.jks \
  -alias autoloader \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```

---

## Требования

- Android 7.0+ (minSdk 24)
- Интернет — для парсинга характеристик авто (есть offline-режим через встроенную базу)
