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

import platform.Foundation.NSFileManager

/**
 * Deletes a database file and its journal files. Returns true when no database file is left, so deleting a
 * database that doesn't exist succeeds too.
 */
internal fun deleteDatabaseFiles(directory: String, name: String): Boolean {
    val path = "$directory/$name"
    deleteJournalFiles(directory, name)
    NSFileManager.defaultManager.removeItemAtPath(path, null)
    return !NSFileManager.defaultManager.fileExistsAtPath(path)
}

/** Deletes what SQLite keeps next to a database: the WAL file and its index, or the rollback journal. */
internal fun deleteJournalFiles(directory: String, name: String) {
    JOURNAL_FILE_SUFFIXES.forEach { suffix ->
        NSFileManager.defaultManager.removeItemAtPath("$directory/$name$suffix", null)
    }
}

private val JOURNAL_FILE_SUFFIXES = listOf("-wal", "-shm", "-journal")
