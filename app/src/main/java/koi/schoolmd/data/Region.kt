package koi.schoolmd.data

enum class Region(
    val title: String,
    val subtitle: String,
    val loginPortalUrl: String,
    val tokenUrl: String,
    val tokenRefreshUrl: String,
    val apiHost: String
) {
    MOSCOW(
        title = "Москва",
        subtitle = "school.mos.ru (МЭШ)",
        loginPortalUrl = "https://school.mos.ru/",
        tokenUrl = "https://school.mos.ru/?backUrl=https://school.mos.ru/v2/token/refresh?roleId=1&subsystem=2",
        tokenRefreshUrl = "https://school.mos.ru/v2/token/refresh?roleId=1&subsystem=2",
        apiHost = "school.mos.ru"
    ),
    MOSCOW_REGION(
        title = "Московская область",
        subtitle = "authedu.mosreg.ru (Моя Школа МО)",
        loginPortalUrl = "https://authedu.mosreg.ru/",
        tokenUrl = "https://authedu.mosreg.ru/v2/token/refresh?roleId=1&subsystem=2",
        tokenRefreshUrl = "https://authedu.mosreg.ru/v2/token/refresh?roleId=1&subsystem=2",
        apiHost = "authedu.mosreg.ru"
    );

    companion object {
        fun fromName(name: String?): Region =
            entries.firstOrNull { it.name == name } ?: MOSCOW
    }
}
