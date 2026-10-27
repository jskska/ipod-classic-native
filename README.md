# iPod classic - нативное приложение (Kotlin + Jetpack Compose)

Android 10+ (minSdk 29). Музыка читается из памяти телефона (MediaStore), воспроизведение через MediaPlayer.

## Сборка APK
1. **Android Studio**: File -> Open -> папка проекта, дождаться синхронизации, Build -> Build APK(s).
   (Если нет wrapper: Studio предложит Gradle сама, либо `gradle wrapper` в папке.)
2. **Без установки SDK**: загрузите папку в репозиторий GitHub -> Actions -> Build APK -> скачайте `ipod-classic-apk`.
3. Консоль: `gradle assembleDebug` (JDK 17 + Android SDK 34) -> `app/build/outputs/apk/debug/app-debug.apk`.

## Управление
- Колесо: круговое движение пальцем = прокрутка; касание сторон = MENU / назад-вперёд / play-pause; центр = выбор
- Удержание центра на песне = добавить в On-The-Go
- Now Playing: колесо = громкость, центр = режим перемотки
- Settings: Shuffle, Repeat, Vibration (Off/Soft/Normal/Strong), Language (English/Русский)

Код: `app/src/main/java/com/ipod/classic/MainActivity.kt`.
