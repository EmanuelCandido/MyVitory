package com.myvitory.app.data.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Quem instalou a Fase 1 abre a Fase 2 sem perder dados (o Room valida o esquema ao abrir). */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "migration-test.db"

    @Test
    fun `migra da versão 1 até a atual mantendo as sessões`() = runBlocking {
        context.deleteDatabase(name)
        createVersion1()

        val db = Room.databaseBuilder(context, MyVitoryDatabase::class.java, name)
            .addMigrations(*MyVitoryDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        val session = db.sessions().get(1)!!
        assertEquals("Pernas", session.name)
        assertFalse(session.resolved)
        assertNull(session.movedTo)
        assertNull(session.mergedTemplateId)
        // Tabelas da Fase 3 existem e funcionam.
        db.videos().pin(PinnedVideoEntity("leg_press_45", "dQw4w9WgXcQ", "Vídeo", "Canal"))
        assertEquals(0, db.videos().cached("leg press").size)
        // Tabela da Fase 5.
        db.availability().add(UnavailableDayEntity(java.time.LocalDate.of(2026, 10, 31)))
        // v0.7: o que já existia vem como exercício do catálogo.
        assertFalse(db.exercises().getAll().any { it.custom })
        // v0.9: pesagens.
        db.weights().upsert(WeightEntity(java.time.LocalDate.of(2026, 10, 30), 80.5))
        assertEquals(80.5, db.weights().latest()!!.kg, 0.0)
        // v0.10: treinos que já existiam ganham um id estável e diferente para o painel; comentário começa vazio.
        val ids = db.templates().getBaseTemplates().map { it.remoteId }
        assertEquals(2, ids.size)
        assertTrue(ids.all { it.length == 32 })
        assertEquals(2, ids.toSet().size)
        assertNull(session.comment)
        assertEquals("", db.templates().getItems(10).single().note)
        db.close()
    }

    /** Cria o banco exatamente como a versão 1, a partir do esquema exportado pelo Room. */
    private fun createVersion1() {
        val schema = JSONObject(File("schemas/com.myvitory.app.data.db.MyVitoryDatabase/1.json").readText()).getJSONObject("database")
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    val entities = schema.getJSONArray("entities")
                    for (i in 0 until entities.length()) {
                        val entity = entities.getJSONObject(i)
                        val table = entity.getString("tableName")
                        db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                        entity.optJSONArray("indices")?.let { indices ->
                            for (j in 0 until indices.length()) {
                                db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                            }
                        }
                    }
                    val setup = schema.getJSONArray("setupQueries")
                    for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
                    db.execSQL("INSERT INTO sessions (id, date, templateId, name, status) VALUES (1, 20389, NULL, 'Pernas', 'PLANNED')")
                    db.execSQL("INSERT INTO exercises (id, name, equipment, `primary`, secondary) VALUES ('leg_press_45', 'Leg press', 'Máquina', 'QUADS', '')")
                    db.execSQL("INSERT INTO templates (id, code, name) VALUES (10, 'A', 'Pernas'), (11, 'B', 'Costas')")
                    db.execSQL("INSERT INTO template_items (templateId, exerciseId, position, sets, reps, restSec) VALUES (10, 'leg_press_45', 0, 3, 10, 90)")
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase.close()
    }
}
