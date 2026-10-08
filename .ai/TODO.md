# TODO

## Completed
- [x] Initialize Git repository (`main` branch)
- [x] Setup Android Gradle project with Kotlin, Jetpack Compose, Material 3 Expressive
- [x] Implement native JWT parser (`JwtDecoder`) and unit tests
- [x] Implement Secure Auth storage (`AuthRepository`) with EncryptedSharedPreferences
- [x] Implement Moscow & Moscow Region authentication flow (external browser SSO + direct token paste)
- [x] Implement Material 3 Expressive Schedule screen matching mockup (`style-example/главная-расписание.png`)
- [x] Implement Material 3 Expressive Profile screen:
  - Display First & Last name from token (with custom name override option)
  - Custom avatar upload via Android Photo Picker (`PickVisualMedia`) and initials fallback
  - Account info, region details, and token refresh/validation
- [x] Implement MD3 Bottom Navigation Bar with 4 tabs (Расписание, Оценки, Задания, Профиль)
- [x] Assemble and install APK on device

- [x] Connect real diary & schedule API endpoints for Mosreg / Moscow:
  - Student Profile API (`/api/family/web/v1/profile` with `X-Mes-Subsystem: familyweb`) fetching real First & Last name, school, and class
  - Real schedule calendar API (`/api/eventcalendar/v1/api/events`) fetching real lessons, times, rooms, subjects, and badges
  - Week navigation (previous/next week switcher, "Сегодня" reset button, week date range)
  - Full MD3 Expressive styling with animated pill selectors, status badges (live, exam, replacement), and cards
  - Multi-level caching (memory cache & persistent storage)
- [x] Implement Material 3 Expressive Homework screen:
  - Dynamic API fetching from `/api/family/web/v1/homeworks`
  - Week switcher, progress indicator, search bar, and status filtering (Все, К сдаче, Выполненные)
  - Teacher resolution dynamically saved from `/lesson_schedule_items/{id}` into local private cache
- [x] Implement Material 3 Expressive Marks screen:
  - Header with week switcher and average score banner
  - Date-feed mode and Subject-grouped mode
  - Detailed bottom sheet dialog for marks breakdown
- [x] Support regional endpoints (Moscow, Mosreg, Kaluga, Tyumen, Tatarstan, Dagestan)
- [x] UI/UX polish across Schedule, Marks, and Profile screens (spacing, ribbon days, developer footer)
- [x] Comprehensive repository privacy audit: sanitized test mocks, purged reflog, ensured zero personal data
- [x] Implement date section headers on Homework screen matching Marks screen design (На сегодня / На завтра / На ...)
- [x] Implement Material 3 Expressive sliding modal bottom sheets (ModalBottomSheet) matching mockup:
  - Tapping lesson in Schedule screen opens Lesson Detail sheet (subject, time, teacher, classroom, homework, copy action)
  - Tapping mark in Marks screen (by date or by subject) opens Mark Detail sheet (score, control form, weight, exam/point status, teacher comment)
  - Tapping subject card in Marks screen opens Subject Summary sheet with period averages and interactive mark breakdown
  - Tapping card in Homework screen opens Homework Detail sheet (subject, due date, full description, materials, toggle completion action)
- [x] Official Release v1.0.0 (full MD3 Expressive redesign, modal sheets, regional endpoints, performance audit)
- [x] Setup reproducible NixOS development environment (`shell.nix` with JDK 17, Android SDK 35, platform-tools, adb)
- [x] Full build verification (`assembleDebug` producing APK and `testDebugUnitTest` all passing in nix-shell)
- [x] Fix fast day/week navigation in Schedule screen: show MD3 loading indicator instead of empty state flash during fetch and prefetch adjacent weeks
- [x] Implement official Material 3 Expressive ContainedLoadingIndicator (shape morphing inside rounded container) across Schedule, Homework, and Marks screens
- [x] Unify loading screens: centered vertical alignment and consistent status labels ("Загрузка расписания...", "Загрузка заданий...", "Загрузка оценок...")

## In Progress
- [ ] Background sync & notifications architecture (WorkManager + NotificationCompat)

## Backlog / Future
- [ ] Periodic background check & notifications for new marks and homework
- [ ] Persistent offline caching sync (Room / SQLite)
- [ ] Field-testing regional endpoints (Kaluga, Tyumen, Tatarstan, Dagestan)
- [ ] Academic performance analytics & grade trends

