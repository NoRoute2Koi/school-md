package ru.school.app.data

enum class Region(
    val title: String,
    val subtitle: String,
    val authUrl: String,
    val tokenRefreshUrl: String,
    val apiHost: String
) {
    MOSCOW(
        title = "Москва",
        subtitle = "school.mos.ru (МЭШ)",
        authUrl = "https://school.mos.ru/?backUrl=https://school.mos.ru/v2/token/refresh?roleId=1&subsystem=2",
        tokenRefreshUrl = "https://school.mos.ru/v2/token/refresh?roleId=1&subsystem=2",
        apiHost = "school.mos.ru"
    ),
    MOSCOW_REGION(
        title = "Московская область",
        subtitle = "authedu.mosreg.ru (Моя Школа МО)",
        authUrl = "https://authedu.mosreg.ru/v2/token/refresh?roleId=1&subsystem=2",
        tokenRefreshUrl = "https://authedu.mosreg.ru/v2/token/refresh?roleId=1&subsystem=2",
        apiHost = "authedu.mosreg.ru"
    );

    companion object {
        fun fromName(name: String?): Region =
            entries.firstOrNull { it.name == name } ?: MOSCOW
    }
}
