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

import com.wire.kalium.persistence.db.TestDatabaseFile.Companion.MARKER
import com.wire.kalium.persistence.db.TestDatabaseFile.Companion.OTHER_RAW_KEY
import com.wire.kalium.persistence.db.TestDatabaseFile.Companion.PASSPHRASE
import com.wire.kalium.persistence.db.TestDatabaseFile.Companion.RAW_KEY
import com.wire.kalium.persistence.db.TestDatabaseFile.Companion.SQLITE_HEADER
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SqlCipherKeyTest {

    private val database = TestDatabaseFile()

    @AfterTest
    fun tearDown() {
        database.delete()
    }

    @Test
    fun givenARawKey_whenWritingTheDatabase_thenTheFileIsEncryptedAndOpensWithTheKey() {
        database.write(RAW_KEY)

        assertFalse(database.bytes().startsWith(SQLITE_HEADER))
        assertFalse(database.contains(MARKER))
        assertEquals(MARKER, database.read(RAW_KEY))
    }

    @Test
    fun givenAPassphrase_whenWritingTheDatabase_thenTheFileIsEncryptedAndOpensWithIt() {
        database.write(PASSPHRASE)

        assertFalse(database.bytes().startsWith(SQLITE_HEADER))
        assertFalse(database.contains(MARKER))
        assertEquals(MARKER, database.read(PASSPHRASE))
    }

    @Test
    fun givenAnEncryptedDatabase_whenOpeningItWithAnotherKeyOrNone_thenItFails() {
        database.write(RAW_KEY)

        assertFails { database.read(OTHER_RAW_KEY) }
        assertFails { database.read(null) }
    }

    @Test
    fun givenAnEmptySecret_whenWritingTheDatabase_thenItStaysUnencrypted() {
        database.write(ByteArray(0))

        assertTrue(database.bytes().startsWith(SQLITE_HEADER))
        assertTrue(database.contains(MARKER))
    }

    @Test
    fun givenAWalDatabase_whenReadingAfterAWrite_thenTheReaderConnectionIsKeyedToo() {
        val driver = database.driver(RAW_KEY, isWALEnabled = true)
        try {
            driver.execute(null, "INSERT INTO t VALUES ('$MARKER')", 0)

            assertEquals(MARKER, driver.firstValue())
        } finally {
            driver.close()
        }
    }
}
