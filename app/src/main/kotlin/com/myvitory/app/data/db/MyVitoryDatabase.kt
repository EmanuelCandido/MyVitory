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
        WeightEntity::class,
        TombstoneEntity::class,
        SentDayEntity::class,
        DatePlanEntity::class,
    ],
    version = 10,
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
    abstract fun weights(): WeightDao
    abstract fun sync(): SyncDao
    abstract fun datePlans(): DatePlanDao

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

        /** v0.7: exercícios criados pelo usuário. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE exercises ADD COLUMN custom INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v0.8: mudanças só para uma semana. */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE templates ADD COLUMN weekOnly INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sessions ADD COLUMN special INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v0.9: acompanhamento de peso. */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `weights` (`date` INTEGER NOT NULL, `kg` REAL NOT NULL, PRIMARY KEY(`date`))")
            }
        }

        /**
         * v0.10: painel do treinador. Id estável dos treinos (os que já existem ganham um aleatório), horário da
         * última mudança para o sync, observação por exercício, comentário do treino e as tabelas do sync.
         */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE templates ADD COLUMN remoteId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE templates ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE templates SET remoteId = lower(hex(randomblob(16)))")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_templates_remoteId` ON `templates` (`remoteId`)")
                db.execSQL("ALTER TABLE template_items ADD COLUMN note TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE plan_days ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE exercises ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sessions ADD COLUMN comment TEXT")
                db.execSQL("CREATE TABLE IF NOT EXISTS `sync_tombstones` (`key` TEXT NOT NULL, `deletedAt` INTEGER NOT NULL, PRIMARY KEY(`key`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `sync_sent_days` (`date` INTEGER NOT NULL, `hash` INTEGER NOT NULL, PRIMARY KEY(`date`))")
            }
        }

        /** v0.12: dias em que a academia fecha (da semana e datas) e treino escolhido por data no painel. */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE plan_days ADD COLUMN closed INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `date_plans` (`date` INTEGER NOT NULL, `closed` INTEGER NOT NULL, `label` TEXT NOT NULL, " +
                        "`override` INTEGER NOT NULL, `templateRemoteId` TEXT, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`date`))"
                )
            }
        }

        val ALL_MIGRATIONS = arrayOf(
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
            MIGRATION_9_10,
        )

        fun create(context: Context): MyVitoryDatabase =
            Room.databaseBuilder(context, MyVitoryDatabase::class.java, "myvitory.db")
                .addMigrations(*ALL_MIGRATIONS)
                .build()
    }
}
