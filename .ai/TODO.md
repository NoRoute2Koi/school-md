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

## In Progress
- [ ] Connect real diary & schedule API endpoints for Mosreg / Moscow

## Backlog / Future
- [ ] Diary schedule & marks API integration (MOS / Mosreg)
- [ ] Material 3 Expressive Marks & Homework screens
- [ ] Support additional regions from `PROJECT.md` (Kaluga, Tyumen, Tatarstan, Dagestan)
