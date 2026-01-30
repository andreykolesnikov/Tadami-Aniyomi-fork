package tachiyomi.data.achievement.database

import app.cash.sqldelight.db.SqlDriver
import tachiyomi.data.achievement.AchievementsDatabase as SqlDelightAchievementsDatabase

class AchievementsDatabase(
    private val driver: SqlDriver,
) {

    val achievementsQueries
        get() = database.achievementsQueries

    val achievementProgressQueries
        get() = database.achievement_progressQueries

    companion object {
        const val NAME = "achievements.db"
        const val VERSION = 4L
    }

    private val database = SqlDelightAchievementsDatabase(driver)
}
