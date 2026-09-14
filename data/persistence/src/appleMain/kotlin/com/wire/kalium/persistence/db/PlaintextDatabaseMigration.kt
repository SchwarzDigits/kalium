/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.persistence.db

import co.touchlab.sqliter.DatabaseConfiguration
import co.touchlab.sqliter.DatabaseManager
import co.touchlab.sqliter.JournalMode
import co.touchlab.sqliter.NO_VERSION_CHECK
import co.touchlab.sqliter.createDatabaseManager
import co.touchlab.sqliter.getVersion
import co.touchlab.sqliter.interop.Logger
import co.touchlab.sqliter.interop.SQLiteException
import co.touchlab.sqliter.longForQuery
import co.touchlab.sqliter.withConnection
import com.wire.kalium.persistence.kaliumLogger
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.posix.rename

/** The encrypted copy is written next to the database under this suffix, then renamed over it. */
internal const val ENCRYPTED_COPY_SUFFIX = ".encrypting"

/**
 * Encrypts an Apple database that an earlier version of Kalium stored unencrypted, before a driver opens it with [key].
 *
 * As with the raw-key migration of the global database on Android, the state comes from the file itself rather than
 * from a flag: a file that opens with [key] is left as it is, and one that opens without a key is exported into an
 * encrypted copy, which then replaces it. A file that opens with neither is left alone, and opening it fails as it did
 * before.
 *
 * The export runs with a rollback journal. Leaving WAL checkpoints the unencrypted file, so the copy has all of it and
 * no `-wal` file is left over to be applied to the encrypted one. The unencrypted file stays intact until the rename,
 * and a copy left behind by an interrupted run is deleted before the next one.
 *
 * Callers must make sure nothing else has the database open.
 *
 * @param journalMode the mode the driver opens the database in, so that checking the file doesn't switch it.
 */
internal fun encryptPlaintextDatabase(
    directory: String,
    name: String,
    key: SqlCipherKey,
    journalMode: JournalMode
) {
    if (!isNonEmptyFile("$directory/$name")) return
    // Trying the key first also makes sure that the SQLite is SQLCipher before anything gets exported.
    if (canOpen(directory, name, key, journalMode) || !canOpen(directory, name, null, journalMode)) return

    kaliumLogger.i("Encrypting a database that an earlier version stored unencrypted")
    val copyName = "$name$ENCRYPTED_COPY_SUFFIX"
    deleteDatabaseFiles(directory, copyName)
    exportEncrypted(directory, name, copyName, key)
    check(canOpen(directory, copyName, key, JournalMode.DELETE)) {
        "The encrypted copy of an unencrypted database doesn't open with its key"
    }
    deleteJournalFiles(directory, name)
    check(rename("$directory/$copyName", "$directory/$name") == 0) {
        "Could not replace an unencrypted database with its encrypted copy"
    }
}

private fun exportEncrypted(directory: String, name: String, copyName: String, key: SqlCipherKey) {
    databaseManager(directory, name, key = null, JournalMode.DELETE).withConnection { connection ->
        val copyPath = "$directory/$copyName".toSqlLiteral()
        connection.rawExecSql("ATTACH DATABASE $copyPath AS $COPY_ALIAS KEY ${key.attachLiteral()}")
        try {
            connection.rawExecSql("SELECT sqlcipher_export('$COPY_ALIAS')")
            connection.rawExecSql("PRAGMA $COPY_ALIAS.user_version = ${connection.getVersion()}")
        } finally {
            connection.rawExecSql("DETACH DATABASE $COPY_ALIAS")
        }
    }
}

/** SQLCipher can't return `sqlite_schema` rows for a file it failed to decrypt; it throws instead. */
private fun canOpen(directory: String, name: String, key: SqlCipherKey?, journalMode: JournalMode): Boolean = try {
    databaseManager(directory, name, key, journalMode).withConnection { connection ->
        connection.longForQuery("SELECT count(*) FROM sqlite_schema")
    }
    true
} catch (_: SQLiteException) {
    false
}

private fun databaseManager(
    directory: String,
    name: String,
    key: SqlCipherKey?,
    journalMode: JournalMode
): DatabaseManager = createDatabaseManager(
    DatabaseConfiguration(
        name = name,
        version = NO_VERSION_CHECK,
        create = {},
        journalMode = journalMode,
        extendedConfig = DatabaseConfiguration.Extended(basePath = directory),
        // A file that doesn't open with the key being tried is an expected outcome here, not an error to log.
        loggingConfig = DatabaseConfiguration.Logging(logger = SilentLogger),
        lifecycleConfig = DatabaseConfiguration.Lifecycle(
            onCreateConnection = { connection -> key?.applyTo(connection) }
        )
    )
)

/** An interrupted first run can leave a zero-length file behind, which is not a database to migrate. */
private fun isNonEmptyFile(path: String): Boolean {
    val size = NSFileManager.defaultManager.attributesOfItemAtPath(path, null)?.get(NSFileSize) as? NSNumber
    return (size?.longLongValue ?: 0L) > 0L
}

private fun String.toSqlLiteral(): String = "'${replace("'", "''")}'"

private object SilentLogger : Logger {
    override fun trace(message: String) = Unit
    override val vActive: Boolean = false
    override fun vWrite(message: String) = Unit
    override val eActive: Boolean = false
    override fun eWrite(message: String, exception: Throwable?) = Unit
}

private const val COPY_ALIAS = "encrypted_copy"
