# Мобільна версія (Android)

Спрощена мобільна версія додатку для перевірки швидкості читання.

**Стек:** Kotlin, Jetpack Compose, Material 3
**Мінімальна версія:** Android 8.0 (API 26)

## Документація

- Специфікація: [`../docs/SPEC_ANDROID.md`](../docs/SPEC_ANDROID.md)
- Задачі для реалізації: [`../docs/TASKS_ANDROID.md`](../docs/TASKS_ANDROID.md)

## Складання

Відкрити цю папку в Android Studio → Sync Gradle → Run. Файл `local.properties` створюється локально й у git не потрапляє.

## Спільні дані

Норми WPM і тексти-зразки беруться з [`../shared/`](../shared/) — копіюйте їх в `assets/` на етапі збірки (Gradle-таск), а не вручну, щоб не розсинхронізувати з десктопом.

> Проєкт ще не створено — почніть із Задачі 1 у `docs/TASKS_ANDROID.md`.
