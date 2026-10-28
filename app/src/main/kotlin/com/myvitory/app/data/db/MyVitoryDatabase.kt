package com.myvitory.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ExerciseEntity::class,
        TemplateEntity::class,
        TemplateItemEntity::class,
        PlanDayEntity::class,
        SessionEntity::class,
        SetLogEntity::class,
        CachedVideoEntity::class,
        PinnedVideoEntity::class,
        UnavailableDayEntity::class,
    ],
    version = 5,
)
@TypeConverters(Converters::class)
abstract class MyVitoryDatabase : RoomDatabase() {
    abstract fun exercises(): ExerciseDao
    abstract fun templates(): TemplateDao
    abstract fun plan(): PlanDao
    abstract fun sessions(): SessionDao
    abstract fun setLogs(): SetLogDao
    abstract fun videos(): VideoDao
    abstract fun availability(): AvailabilityDao

    companion object {
        /** Fase 2: recálculo da semana (treino movido e decisão tomada). */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN movedTo INTEGER")
                db.execSQL("ALTER TABLE sessions ADD COLUMN resolved INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Fase 3: vídeos (cache de buscas e vídeo fixado por exercício). */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `video_cache` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`query` TEXT NOT NULL, `position` INTEGER NOT NULL, `videoId` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                        "`channel` TEXT NOT NULL, `thumbnailUrl` TEXT, `durationSec` INTEGER, `fetchedAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_video_cache_query` ON `video_cache` (`query`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `pinned_videos` (`exerciseId` TEXT NOT NULL, `videoId` TEXT NOT NULL, " +
                        "`title` TEXT NOT NULL, `channel` TEXT NOT NULL, PRIMARY KEY(`exerciseId`))"
                )
            }
        }

        /** Fase 5: dias indisponíveis da semana. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `unavailable_days` (`date` INTEGER NOT NULL, PRIMARY KEY(`date`))")
            }
        }

        /** Fase 6: treino fundido pelo recálculo. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN mergedTemplateId INTEGER")
            }
        }

        fun create(context: Context): MyVitoryDatabase =
            Room.databaseBuilder(context, MyVitoryDatabase::class.java, "myvitory.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
    }
}
