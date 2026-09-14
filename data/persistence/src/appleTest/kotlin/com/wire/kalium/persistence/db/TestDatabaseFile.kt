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

import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import platform.Foundation.dataWithContentsOfFile
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fputs

/** A small database in a directory of its own, for the SQLCipher tests. */
internal class TestDatabaseFile {

    val directory: String = (NSTemporaryDirectory() + "kalium-sqlcipher-test-" + NSUUID.UUID().UUIDString)
        .also { NSFileManager.defaultManager.createDirectoryAtPath(it, true, null, null) }

    fun driver(secret: ByteArray?, isWALEnabled: Boolean = false): SqlDriver =
        databaseDriver(directory, NAME, TestSchema, secret) {
            this.isWALEnabled = isWALEnabled
        }

    fun write(secret: ByteArray?, isWALEnabled: Boolean = false) {
        val driver = driver(secret, isWALEnabled)
        try {
            driver.execute(null, "INSERT INTO t VALUES ('$MARKER')", 0)
        } finally {
            driver.close()
        }
    }

    fun read(secret: ByteArray?): String? {
        val driver = driver(secret)
        return try {
            driver.firstValue()
        } finally {
            driver.close()
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    fun bytes(name: String = NAME): ByteArray {
        val data = NSData.dataWithContentsOfFile("$directory/$name") ?: return ByteArray(0)
        return data.bytes?.reinterpret<ByteVar>()?.readBytes(data.length.toInt()) ?: ByteArray(0)
    }

    fun contains(text: String, name: String = NAME): Boolean = bytes(name).decodeToString().contains(text)

    fun exists(name: String): Boolean = NSFileManager.defaultManager.fileExistsAtPath("$directory/$name")

    @OptIn(ExperimentalForeignApi::class)
    fun writeFile(name: String, text: String) {
        val file = fopen("$directory/$name", "w") ?: error("Could not create $name")
        try {
            fputs(text, file)
        } finally {
            fclose(file)
        }
    }

    fun delete() {
        NSFileManager.defaultManager.removeItemAtPath(directory, null)
    }

    private object TestSchema : SqlSchema<QueryResult.Value<Unit>> {
        override val version: Long = SCHEMA_VERSION

        override fun create(driver: SqlDriver): QueryResult.Value<Unit> {
            driver.execute(null, "CREATE TABLE t(x TEXT)", 0)
            return QueryResult.Unit
        }

        override fun migrate(
            driver: SqlDriver,
            oldVersion: Long,
            newVersion: Long,
            vararg callbacks: AfterVersion
        ): QueryResult.Value<Unit> = QueryResult.Unit
    }

    companion object {
        const val NAME = "test.db"
        const val MARKER = "plaintext-marker"
        const val SCHEMA_VERSION = 3L
        val SQLITE_HEADER = "SQLite format 3".encodeToByteArray() + byteArrayOf(0)
        val RAW_KEY = "x'${"42".repeat(32)}'".encodeToByteArray()
        val OTHER_RAW_KEY = "x'${"24".repeat(32)}'".encodeToByteArray()
        val PASSPHRASE = "a passphrase".encodeToByteArray()
    }
}

internal fun SqlDriver.firstValue(): String? = executeQuery(
    identifier = null,
    sql = "SELECT x FROM t",
    mapper = { cursor -> QueryResult.Value(if (cursor.next().value) cursor.getString(0) else null) },
    parameters = 0
).value

internal fun SqlDriver.userVersion(): Long? = executeQuery(
    identifier = null,
    sql = "PRAGMA user_version",
    mapper = { cursor -> QueryResult.Value(if (cursor.next().value) cursor.getLong(0) else null) },
    parameters = 0
).value

internal fun ByteArray.startsWith(prefix: ByteArray): Boolean =
    size >= prefix.size && copyOf(prefix.size).contentEquals(prefix)
