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

package com.wire.kalium.persistence.backup

import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.db.TestDatabaseFile.Companion.SQLITE_HEADER
import com.wire.kalium.persistence.db.startsWith
import com.wire.kalium.persistence.utils.stubs.newConversationEntity
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfFile
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The backup export and import against an encrypted user database, as the SDK uses them on Apple. */
class EncryptedBackupTest : BaseDatabaseTest() {

    private val selfUserId = UserIDEntity("selfValue", "selfDomain")
    private val backupUserId = UserIDEntity("backup-selfValue", "selfDomain")

    @BeforeTest
    fun setUp() {
        deleteDatabase(selfUserId)
        deleteDatabase(backupUserId)
    }

    @Test
    fun givenAnEncryptedUserDatabase_whenExportingABackup_thenTheBackupIsUnencryptedAndTheUserDatabaseIsKept() =
        runTest(dispatcher) {
            val userDatabase = createDatabase(selfUserId, encryptedDBSecret, enableWAL = true)
            userDatabase.conversationDAO.insertConversation(CONVERSATION)

            val backupPath = userDatabase.databaseExporter.exportToPlainDB(encryptedDBSecret)

            assertEquals(databasePath(backupUserId), backupPath)
            assertTrue(fileBytes(databasePath(backupUserId)).startsWith(SQLITE_HEADER))
            val backup = createDatabase(backupUserId, passphrase = null, enableWAL = false)
            assertEquals(CONVERSATION.id, backup.conversationDAO.getConversationById(CONVERSATION.id)?.id)
            assertEquals(CONVERSATION.id, userDatabase.conversationDAO.getConversationById(CONVERSATION.id)?.id)
        }

    @Test
    fun givenAnUnencryptedBackup_whenImportingItIntoAnEncryptedUserDatabase_thenItsDataIsThere() = runTest(dispatcher) {
        val backup = createDatabase(backupUserId, passphrase = null, enableWAL = false)
        backup.conversationDAO.insertConversation(CONVERSATION)
        val userDatabase = createDatabase(selfUserId, encryptedDBSecret, enableWAL = true)

        userDatabase.databaseImporter.importFromFile(databasePath(backupUserId), fromOtherClient = false)

        assertEquals(CONVERSATION.id, userDatabase.conversationDAO.getConversationById(CONVERSATION.id)?.id)
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun fileBytes(path: String): ByteArray {
        val data = NSData.dataWithContentsOfFile(path) ?: return ByteArray(0)
        return data.bytes?.reinterpret<ByteVar>()?.readBytes(data.length.toInt()) ?: ByteArray(0)
    }

    private companion object {
        val CONVERSATION = newConversationEntity("encryptedBackupTest")
    }
}
