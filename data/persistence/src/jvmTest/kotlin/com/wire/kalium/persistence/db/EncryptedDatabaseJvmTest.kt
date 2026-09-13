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

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.utils.stubs.newUserDetailsEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.sqlite.mc.SQLiteMCChacha20Config
import org.sqlite.mc.SQLiteMCConfig
import org.sqlite.mc.SQLiteMCSqlCipherConfig
import java.io.File
import java.nio.file.Files
import java.sql.DriverManager
import java.sql.SQLException
import java.util.Properties
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EncryptedDatabaseJvmTest {

    private val directory = Files.createTempDirectory("encrypted-db").toFile()
    private val userId = UserIDEntity("user", "domain")
    private val databaseFile = userDatabaseFile(directory, userId)

    @AfterTest
    fun cleanUp() {
        directory.deleteRecursively()
    }

    @Test
    fun givenRawKey_whenDatabaseIsWritten_thenTheFileIsEncryptedAndOnlyOpensWithTheKey() {
        openDatabase(RAW_KEY).useDriver { it.putMarker() }

        assertFalse(databaseFile.hasSqliteHeader())
        assertFalse(databaseFile.containsMarker())
        assertFailsWith<SQLException> { countTablesWithoutKey(databaseFile) }
        openDatabase(RAW_KEY).useDriver { assertEquals(MARKER, it.readMarker()) }
    }

    @Test
    fun givenRawKey_whenDatabaseIsWritten_thenItUsesSqlCipherV4AndNotTheDefaultSchemeOfTheDriver() {
        openDatabase(RAW_KEY).useDriver { it.putMarker() }

        countTablesWithRawKey(databaseFile, SQLiteMCSqlCipherConfig.getV4Defaults())
        assertFailsWith<SQLException> { countTablesWithRawKey(databaseFile, SQLiteMCChacha20Config.getDefault()) }
    }

    @Test
    fun givenBinaryPassphrase_whenDatabaseIsReopened_thenItOpensWithTheSamePassphrase() {
        val binaryPassphrase = UserDBSecret(ByteArray(32) { (it * 37).toByte() })

        openDatabase(binaryPassphrase).useDriver { it.putMarker() }
        openDatabase(binaryPassphrase).useDriver { assertEquals(MARKER, it.readMarker()) }

        assertFalse(databaseFile.hasSqliteHeader())
    }

    // The fixture was written by SQLCipher 4.5.6 (Ubuntu 24.04's sqlcipher) with PRAGMA key = "<RAW_KEY>",
    // CREATE TABLE fixture(v TEXT NOT NULL) and INSERT INTO fixture VALUES ('written by SQLCipher').
    // Reading it pins the JVM driver's settings and its raw-key handling to SQLCipher's.
    @Test
    fun givenDatabaseWrittenBySqlCipher_whenOpenedWithTheRawKey_thenItsRowsArrive() {
        val file = directory.resolve("sqlcipher.db")
        val fixture = assertNotNull(javaClass.getResourceAsStream("/$SQLCIPHER_FIXTURE"))
        fixture.use { input -> file.outputStream().use { input.copyTo(it) } }
        assertFalse(file.hasSqliteHeader())

        val driver = databaseDriver(uri = jdbcUrl(file), passphrase = RAW_KEY.value)
        try {
            val value = driver.executeQuery(
                identifier = null,
                sql = "SELECT v FROM fixture",
                mapper = { cursor -> QueryResult.Value(if (cursor.next().value) cursor.getString(0) else null) },
                parameters = 0
            ).value
            assertEquals("written by SQLCipher", value)
        } finally {
            driver.close()
        }
    }

    @Test
    fun givenEncryptedDatabase_whenBackupIsExportedAndDeleted_thenTheUserDatabaseIsUntouched() = runTest {
        val database = openDatabase(RAW_KEY)
        database.sqlDriver.putMarker()

        val backupPath = database.databaseExporter.exportToPlainDB(RAW_KEY)

        assertNotNull(backupPath)
        assertTrue(File(backupPath).hasSqliteHeader())
        assertTrue(database.databaseExporter.deleteBackupDBFile())
        assertTrue(databaseFile.exists())
        assertEquals(MARKER, database.sqlDriver.readMarker())
        database.sqlDriver.close()
    }

    @Test
    fun givenPlainBackup_whenImportedIntoEncryptedDatabase_thenItsRowsArrive() = runTest {
        val source = openDatabase(RAW_KEY)
        val user = newUserDetailsEntity("restored-user")
        source.userDAO.upsertUser(user.toSimpleEntity())
        val backupPath = assertNotNull(source.databaseExporter.exportToPlainDB(RAW_KEY))

        val target = userDatabaseBuilder(
            platformDatabaseData = PlatformDatabaseData(StorageData.FileBacked(directory.resolve("target"))),
            userId = UserIDEntity("other", "domain"),
            passphrase = OTHER_RAW_KEY,
            dispatcher = Dispatchers.IO,
            enableWAL = true
        )
        target.databaseImporter.importFromFile(backupPath, fromOtherClient = false)

        assertEquals(user.id, target.userDAO.getUserDetailsByQualifiedID(user.id)?.id)
        source.sqlDriver.close()
        target.sqlDriver.close()
    }

    private fun openDatabase(passphrase: UserDBSecret?): UserDatabaseBuilder = userDatabaseBuilder(
        platformDatabaseData = PlatformDatabaseData(StorageData.FileBacked(directory)),
        userId = userId,
        passphrase = passphrase,
        dispatcher = Dispatchers.IO,
        enableWAL = true
    )

    private fun UserDatabaseBuilder.useDriver(block: (SqlDriver) -> Unit) {
        try {
            block(sqlDriver)
        } finally {
            sqlDriver.close()
        }
    }

    private fun SqlDriver.putMarker() {
        execute(null, "INSERT INTO Metadata(key, stringValue) VALUES ('marker', '$MARKER')", 0)
    }

    private fun SqlDriver.readMarker(): String? = executeQuery(
        identifier = null,
        sql = "SELECT stringValue FROM Metadata WHERE key = 'marker'",
        mapper = { cursor -> QueryResult.Value(if (cursor.next().value) cursor.getString(0) else null) },
        parameters = 0
    ).value

    private fun countTablesWithoutKey(file: File) = countTables(file, Properties())

    private fun countTablesWithRawKey(file: File, cipher: SQLiteMCConfig.Builder) =
        countTables(file, cipher.withKey(RAW_KEY.value.decodeToString()).build().toProperties())

    private fun countTables(file: File, properties: Properties) {
        DriverManager.getConnection(jdbcUrl(file), properties).use { connection ->
            connection.createStatement().use { it.executeQuery("SELECT count(*) FROM sqlite_master").next() }
        }
    }

    private fun File.hasSqliteHeader(): Boolean =
        readBytes().copyOf(SQLITE_HEADER.size).contentEquals(SQLITE_HEADER)

    private fun File.containsMarker(): Boolean =
        String(readBytes(), Charsets.ISO_8859_1).contains(MARKER)

    private companion object {
        const val MARKER = "plaintext-marker-4711"
        const val SQLCIPHER_FIXTURE = "sqlcipher-v4-raw-key.db"
        val RAW_KEY = UserDBSecret("x'${"ab".repeat(32)}'".toByteArray())
        val OTHER_RAW_KEY = UserDBSecret("x'${"cd".repeat(32)}'".toByteArray())
        val SQLITE_HEADER = "SQLite format 3\u0000".encodeToByteArray()
    }
}
