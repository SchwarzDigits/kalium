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

import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.db.TestDatabaseFile.Companion.MARKER
import com.wire.kalium.persistence.db.TestDatabaseFile.Companion.NAME
import com.wire.kalium.persistence.db.TestDatabaseFile.Companion.OTHER_RAW_KEY
import com.wire.kalium.persistence.db.TestDatabaseFile.Companion.RAW_KEY
import com.wire.kalium.persistence.db.TestDatabaseFile.Companion.SCHEMA_VERSION
import com.wire.kalium.persistence.db.TestDatabaseFile.Companion.SQLITE_HEADER
import com.wire.kalium.persistence.util.FileNameUtil
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse

class PlaintextDatabaseMigrationTest {

    private val database = TestDatabaseFile()

    @AfterTest
    fun tearDown() {
        database.delete()
    }

    @Test
    fun givenAnUnencryptedWalDatabase_whenOpenedWithAKey_thenItIsEncryptedAndKeepsItsContent() {
        database.write(null, isWALEnabled = true)

        val driver = database.driver(RAW_KEY, isWALEnabled = true)
        try {
            assertEquals(MARKER, driver.firstValue())
            assertEquals(SCHEMA_VERSION, driver.userVersion())
        } finally {
            driver.close()
        }

        assertFalse(database.bytes().startsWith(SQLITE_HEADER))
        assertFalse(database.contains(MARKER))
        assertFalse(database.exists("$NAME$ENCRYPTED_COPY_SUFFIX"))
    }

    @Test
    fun givenAnEncryptedDatabase_whenOpenedAgain_thenTheFileIsLeftAsItIs() {
        database.write(RAW_KEY)
        val before = database.bytes()

        assertEquals(MARKER, database.read(RAW_KEY))

        assertContentEquals(before, database.bytes())
    }

    @Test
    fun givenADatabaseEncryptedWithAnotherKey_whenOpened_thenItFailsAndTheFileIsLeftAsItIs() {
        database.write(OTHER_RAW_KEY)
        val before = database.bytes()

        assertFails { database.read(RAW_KEY) }

        assertContentEquals(before, database.bytes())
    }

    @Test
    fun givenACopyLeftByAnInterruptedMigration_whenOpened_thenTheMigrationStillSucceeds() {
        database.write(null)
        database.writeFile("$NAME$ENCRYPTED_COPY_SUFFIX", "not a database")

        assertEquals(MARKER, database.read(RAW_KEY))

        assertFalse(database.contains(MARKER))
    }

    @Test
    fun givenAnUnencryptedUserDatabase_whenOpenedWithASecret_thenItsDataIsStillThere() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val platformDatabaseData = PlatformDatabaseData(StorageData.FileBacked(database.directory))
        val userId = UserIDEntity("user", "domain")
        val unencrypted = userDatabaseBuilder(platformDatabaseData, userId, null, dispatcher)
        unencrypted.metadataDAO.insertValue(MARKER, METADATA_KEY)
        unencrypted.sqlDriver.close()

        val encrypted = userDatabaseBuilder(platformDatabaseData, userId, UserDBSecret(RAW_KEY), dispatcher)
        try {
            assertEquals(MARKER, encrypted.metadataDAO.valueByKey(METADATA_KEY))
        } finally {
            encrypted.sqlDriver.close()
        }

        assertFalse(database.bytes(FileNameUtil.userDBName(userId)).startsWith(SQLITE_HEADER))
    }

    private companion object {
        const val METADATA_KEY = "migration-test"
    }
}
