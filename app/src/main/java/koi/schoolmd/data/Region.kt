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
    ),
    TATARSTAN(
        title = "Татарстан",
        subtitle = "ms-edu.tatar.ru (Моя Школа РТ)",
        loginPortalUrl = "https://ms-edu.tatar.ru/",
        tokenUrl = "https://ms-edu.tatar.ru/v2/token/refresh?roleId=1&subsystem=2",
        tokenRefreshUrl = "https://ms-edu.tatar.ru/v2/token/refresh?roleId=1&subsystem=2",
        apiHost = "ms-edu.tatar.ru"
    ),
    TYUMEN(
        title = "Тюменская область",
        subtitle = "myschool.72to.ru (Моя Школа ТО)",
        loginPortalUrl = "https://myschool.72to.ru/",
        tokenUrl = "https://myschool.72to.ru/v2/token/refresh?roleId=1&subsystem=2",
        tokenRefreshUrl = "https://myschool.72to.ru/v2/token/refresh?roleId=1&subsystem=2",
        apiHost = "myschool.72to.ru"
    ),
    KALUGA(
        title = "Калужская область",
        subtitle = "education.admoblkaluga.ru (Моя Школа КО)",
        loginPortalUrl = "https://education.admoblkaluga.ru/",
        tokenUrl = "https://education.admoblkaluga.ru/v2/token/refresh?roleId=1&subsystem=2",
        tokenRefreshUrl = "https://education.admoblkaluga.ru/v2/token/refresh?roleId=1&subsystem=2",
        apiHost = "education.admoblkaluga.ru"
    ),
    DAGESTAN(
        title = "Дагестан",
        subtitle = "myschool.05edu.ru (Моя Школа РД)",
        loginPortalUrl = "https://myschool.05edu.ru/",
        tokenUrl = "https://myschool.05edu.ru/v2/token/refresh?roleId=1&subsystem=2",
        tokenRefreshUrl = "https://myschool.05edu.ru/v2/token/refresh?roleId=1&subsystem=2",
        apiHost = "myschool.05edu.ru"
    );

    companion object {
        fun fromName(name: String?): Region =
            entries.firstOrNull {
                it.name.equals(name, ignoreCase = true) ||
                it.title.equals(name, ignoreCase = true) ||
                it.apiHost.equals(name, ignoreCase = true)
            } ?: MOSCOW_REGION
    }
}
