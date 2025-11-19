# Garmin Activity Streaming - Project Overview

## Основная идея

**Проблема**: Garmin watches имеют множество датчиков (HR, GPS, барометр, акселерометр, power meter), но нет встроенного решения для трансляции этих данных в реальном времени на телефон.

**Решение**: Приложение для стриминга активности с часов на Android в режиме реального времени через Bluetooth/WiFi с отображением метрик, записью сессий и экспортом данных.

---

## Архитектура

```
┌─────────────────┐                        ┌─────────────────┐
│  Garmin Watch   │    Bluetooth/WiFi      │  Android Phone  │
│  (Connect IQ)   │  ──────────────────►   │   (Companion)   │
│                 │    JSON каждые 3с      │                 │
│  Monkey C       │                        │  Kotlin/Compose │
└─────────────────┘                        └─────────────────┘
```

---

## Реализованная функциональность

### Watch App (Connect IQ)
- **Сбор данных**: HR, GPS, скорость, высота, дистанция, cadence, power
- **Передача**: JSON по Communications API каждые N секунд
- **Типы активности**: Running, Cycling, Walking, Hiking, Swimming
- **Настраиваемый интервал**: 1с, 3с, 5с, 10с
- **Управление**: Start/Stop кнопками и свайпами

### Android App (Kotlin + Jetpack Compose)

#### Главный экран
- Real-time метрики: HR (с цветом зоны), Speed, Distance, Pace, Cadence, Power
- Индикатор текущей зоны (Zone 1-5 с описанием)
- HR график с min/avg/max
- Карта с треком (OSMDroid)
- Кнопка записи Start/Stop

#### История сессий
- Список всех записанных активностей
- Карточки с основными метриками
- Бейдж типа активности
- Удаление сессий

#### Детали сессии
- Полная статистика (дистанция, время, темп, скорость)
- HR статистика (min/avg/max)
- HR график за сессию
- Распределение по зонам (визуальные бары с процентами и временем)
- Карта маршрута
- Экспорт (GPX, TCX)
- Upload в Strava

#### Экспорт
- **GPX 1.1**: С Garmin TrackPointExtension (HR, cadence, power)
- **TCX**: Training Center XML, совместим с Garmin Connect

#### Strava интеграция
- OAuth 2.0 авторизация
- Upload активностей
- Автоматический refresh токенов

#### Training Zones (5 зон по % от max HR 190)
- Zone 1 (50-60%): Recovery - голубой
- Zone 2 (60-70%): Endurance - зелёный
- Zone 3 (70-80%): Tempo - жёлтый
- Zone 4 (80-90%): Threshold - оранжевый
- Zone 5 (90-100%): VO2 Max - красный

#### Data Storage
- Room Database (версия 3)
- JSON сериализация track points и HR history
- Статистика по зонам

---

## Технический стек

### Watch
- Connect IQ SDK 6.x
- Monkey C
- Sensor API, Position API, Communications API

### Android
- Kotlin 1.9+
- Jetpack Compose + Material 3
- Navigation Compose
- Room Database + KSP
- Coroutines/Flow
- OSMDroid для карт
- Gson для JSON

---

## Поддерживаемые устройства

### Часы
- Fenix 5/5S/5X (Plus)
- Fenix 6/6S/6X Pro
- Fenix 7/7S/7X (Pro)
- Forerunner 245/255/265
- Forerunner 745/945/955/965
- Venu/Venu 2/Venu 3

### Android
- Android 8.0+ (API 26)
- Bluetooth Low Energy support

---

## Статус разработки

### Завершённые фазы

#### Phase 1 (MVP)
- [x] HR + GPS streaming
- [x] Real-time display
- [x] Map tracking

#### Phase 2
- [x] HR graph с зонами
- [x] Session history (Room DB)
- [x] Cadence, Power
- [x] Configurable interval

#### Phase 3
- [x] Multiple activity types (5 видов)
- [x] GPX Export
- [x] TCX Export
- [x] Training zones (5 зон)
- [x] Strava integration

### Phase 4 (Planned)
- [ ] Home screen widgets
- [ ] Statistics dashboard
- [ ] HR zone alerts
- [ ] Auto-pause
- [ ] Settings screen

---

## Статистика проекта

- **9 PRs** merged
- **3 фазы** завершены
- Watch app: ~250 строк Monkey C
- Android app: ~3000+ строк Kotlin
