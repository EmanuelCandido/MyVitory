package com.myvitory.app.data.update

import java.io.File
import java.net.URL
import java.security.MessageDigest
import org.json.JSONObject

/**
 * Versão publicada no Firebase Hosting do painel (app/version.json), gerada por web/scripts/publicar-app.mjs.
 * O app é distribuído fora da Play Store: ele mesmo procura, baixa e instala a versão nova.
 */
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    /** Endereço completo do APK. */
    val apkUrl: String,
    /** SHA-256 do APK em hexadecimal: o download só é instalado se bater. */
    val sha256: String,
    val sizeBytes: Long,
    val notes: String,
) {
    fun isNewerThan(currentVersionCode: Int) = versionCode > currentVersionCode

    companion object {
        /** Lê o version.json; o "apk" pode ser relativo ao próprio arquivo. */
        fun parse(json: String, sourceUrl: String): UpdateInfo {
            val o = JSONObject(json)
            return UpdateInfo(
                versionCode = o.getInt("versionCode"),
                versionName = o.getString("versionName"),
                apkUrl = URL(URL(sourceUrl), o.getString("apk")).toString(),
                sha256 = o.getString("sha256").lowercase(),
                sizeBytes = o.optLong("sizeBytes", 0),
                notes = o.optString("notes", ""),
            )
        }

        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
