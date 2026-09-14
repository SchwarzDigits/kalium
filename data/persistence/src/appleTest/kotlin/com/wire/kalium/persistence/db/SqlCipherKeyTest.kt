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
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SqlCipherKeyTest {

    private val directory = (NSTemporaryDirectory() + "sqlcipher-key-test-" + NSUUID.UUID().UUIDString)
        .also { NSFileManager.defaultManager.createDirectoryAtPath(it, true, null, null) }

    @AfterTest
    fun tearDown() {
        NSFileManager.defaultManager.removeItemAtPath(directory, null)
    }

    @Test
    fun givenARawKey_whenWritingTheDatabase_thenTheFileIsEncryptedAndOpensWithTheKey() {
        write(RAW_KEY)

        assertFalse(fileBytes().startsWith(SQLITE_HEADER))
        assertFalse(fileContains(MARKER))
        assertEquals(MARKER, read(RAW_KEY))
    }

    @Test
    fun givenAPassphrase_whenWritingTheDatabase_thenTheFileIsEncryptedAndOpensWithIt() {
        write(PASSPHRASE)

        assertFalse(fileBytes().startsWith(SQLITE_HEADER))
        assertFalse(fileContains(MARKER))
        assertEquals(MARKER, read(PASSPHRASE))
    }

    @Test
    fun givenAnEncryptedDatabase_whenOpeningItWithAnotherKeyOrNone_thenItFails() {
        write(RAW_KEY)

        assertFails { read(OTHER_RAW_KEY) }
        assertFails { read(null) }
    }

    @Test
    fun givenAnEmptySecret_whenWritingTheDatabase_thenItStaysUnencrypted() {
        write(ByteArray(0))

        assertTrue(fileBytes().startsWith(SQLITE_HEADER))
        assertTrue(fileContains(MARKER))
    }

    @Test
    fun givenAWalDatabase_whenReadingAfterAWrite_thenTheReaderConnectionIsKeyedToo() {
        val driver = driver(RAW_KEY, isWALEnabled = true)
        try {
            driver.execute(null, "INSERT INTO t VALUES ('$MARKER')", 0)

            assertEquals(MARKER, driver.firstValue())
        } finally {
            driver.close()
        }
    }

    private fun driver(secret: ByteArray?, isWALEnabled: Boolean = false): SqlDriver =
        databaseDriver(directory, NAME, TestSchema, secret) {
            this.isWALEnabled = isWALEnabled
        }

    private fun write(secret: ByteArray) {
        val driver = driver(secret)
        try {
            driver.execute(null, "INSERT INTO t VALUES ('$MARKER')", 0)
        } finally {
            driver.close()
        }
    }

    private fun read(secret: ByteArray?): String? {
        val driver = driver(secret)
        return try {
            driver.firstValue()
        } finally {
            driver.close()
        }
    }

    private fun SqlDriver.firstValue(): String? = executeQuery(
        identifier = null,
        sql = "SELECT x FROM t",
        mapper = { cursor -> QueryResult.Value(if (cursor.next().value) cursor.getString(0) else null) },
        parameters = 0
    ).value

    @OptIn(ExperimentalForeignApi::class)
    private fun fileBytes(): ByteArray {
        val data = NSData.dataWithContentsOfFile("$directory/$NAME") ?: return ByteArray(0)
        return data.bytes?.reinterpret<ByteVar>()?.readBytes(data.length.toInt()) ?: ByteArray(0)
    }

    private fun fileContains(text: String): Boolean = fileBytes().decodeToString().contains(text)

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && copyOf(prefix.size).contentEquals(prefix)

    private object TestSchema : SqlSchema<QueryResult.Value<Unit>> {
        override val version: Long = 1

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

    private companion object {
        const val NAME = "test.db"
        const val MARKER = "plaintext-marker"
        val SQLITE_HEADER = "SQLite format 3\u0000".encodeToByteArray()
        val RAW_KEY = "x'${"42".repeat(32)}'".encodeToByteArray()
        val OTHER_RAW_KEY = "x'${"24".repeat(32)}'".encodeToByteArray()
        val PASSPHRASE = "a passphrase".encodeToByteArray()
    }
}
