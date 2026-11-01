package com.myvitory.app.data.sync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Estado da ligação com o painel do treinador, guardado no celular. */
data class SyncState(
    /** Id da atleta na nuvem (o uid da conta anônima). Null: não conectado. */
    val athleteId: String? = null,
    val name: String = "",
    val shareWeight: Boolean = false,
    val lastSyncAt: Long? = null,
    val lastError: String? = null,
    val pairCode: String? = null,
    val pairCodeExpiresAt: Long? = null,
) {
    val connected get() = athleteId != null
}

private val Context.syncStore: DataStore<Preferences> by preferencesDataStore(name = "sync")

class SyncPrefs(private val context: Context) {
    private object Keys {
        val athleteId = stringPreferencesKey("athlete_id")
        val name = stringPreferencesKey("name")
        val shareWeight = booleanPreferencesKey("share_weight")
        val lastSyncAt = longPreferencesKey("last_sync_at")
        val lastError = stringPreferencesKey("last_error")
        val pairCode = stringPreferencesKey("pair_code")
        val pairCodeExpiresAt = longPreferencesKey("pair_code_expires_at")
        val weightsHash = intPreferencesKey("weights_hash")
    }

    val state: Flow<SyncState> = context.syncStore.data.map { p ->
        SyncState(
            athleteId = p[Keys.athleteId],
            name = p[Keys.name].orEmpty(),
            shareWeight = p[Keys.shareWeight] ?: false,
            lastSyncAt = p[Keys.lastSyncAt],
            lastError = p[Keys.lastError],
            pairCode = p[Keys.pairCode],
            pairCodeExpiresAt = p[Keys.pairCodeExpiresAt],
        )
    }

    suspend fun current(): SyncState = state.first()

    suspend fun connected(athleteId: String, name: String) = context.syncStore.edit {
        it[Keys.athleteId] = athleteId
        it[Keys.name] = name
    }

    suspend fun setShareWeight(on: Boolean) = context.syncStore.edit { it[Keys.shareWeight] = on }

    suspend fun synced(at: Long) = context.syncStore.edit {
        it[Keys.lastSyncAt] = at
        it.remove(Keys.lastError)
    }

    suspend fun failed(message: String) = context.syncStore.edit { it[Keys.lastError] = message }

    suspend fun pairCode(code: String, expiresAt: Long) = context.syncStore.edit {
        it[Keys.pairCode] = code
        it[Keys.pairCodeExpiresAt] = expiresAt
    }

    suspend fun weightsHash(): Int? = context.syncStore.data.first()[Keys.weightsHash]

    suspend fun setWeightsHash(hash: Int?) = context.syncStore.edit { if (hash == null) it.remove(Keys.weightsHash) else it[Keys.weightsHash] = hash }

    suspend fun clear() = context.syncStore.edit { it.clear() }
}
