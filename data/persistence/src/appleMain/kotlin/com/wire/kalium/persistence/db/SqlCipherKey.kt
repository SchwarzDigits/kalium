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

import co.touchlab.sqliter.DatabaseConnection
import co.touchlab.sqliter.setCipherKey
import co.touchlab.sqliter.withStatement

/**
 * Keys the connections of an Apple database with SQLCipher.
 *
 * Kalium doesn't link SQLCipher on Apple. CoreCrypto's library contains it and exports the SQLite API, and every app
 * using Kalium links CoreCrypto, so the SQLite that SQLiter calls is SQLCipher. Plain SQLite would accept `PRAGMA key`
 * and write the database unencrypted, so [applyTo] refuses a connection whose SQLite isn't SQLCipher.
 *
 * @param secret the database secret. New databases get SQLCipher's raw-key form, `x'<64 hex digits>'`, which is used as
 * the key as it is; anything else is a passphrase and goes through SQLCipher's key derivation. An empty secret leaves the
 * database unencrypted, which the backup export relies on to attach an encrypted database to a plain one.
 */
internal class SqlCipherKey(private val secret: ByteArray) {

    /** False for the empty secret, which leaves the database unencrypted. */
    val encrypts: Boolean get() = secret.isNotEmpty()

    /**
     * SQLiter calls this for every new connection, the readers of a WAL database included, before it reads from the
     * file. That is when SQLCipher needs the key.
     */
    fun applyTo(connection: DatabaseConnection) {
        val cipherVersion = connection.withStatement("PRAGMA cipher_version") {
            val cursor = query()
            if (cursor.next()) cursor.getString(0) else null
        }
        checkNotNull(cipherVersion) {
            "The SQLite in this process is not SQLCipher, so the database can't be encrypted. " +
                "On Apple, SQLCipher comes with CoreCrypto's library."
        }
        when {
            secret.isEmpty() -> Unit
            secret.isRawKey() -> connection.setCipherKey(secret.decodeToString())
            else -> connection.rawExecSql("PRAGMA hexkey = '${secret.toHex()}'")
        }
    }

    /** The key as `ATTACH ... KEY` takes it: a raw key as its text, a passphrase as its bytes, as `PRAGMA hexkey` does. */
    fun attachLiteral(): String =
        if (secret.isRawKey()) "'${secret.decodeToString().replace("'", "''")}'" else "X'${secret.toHex()}'"

    private fun ByteArray.isRawKey(): Boolean =
        size == RAW_KEY_LENGTH &&
            this[0] == RAW_KEY_MARKER &&
            this[1] == QUOTE &&
            last() == QUOTE &&
            (2 until size - 1).all { index -> this[index].toInt().toChar().lowercaseChar() in HEX_DIGITS }

    private fun ByteArray.toHex(): String = joinToString("") { byte ->
        (byte.toInt() and BYTE_MASK).toString(HEX_RADIX).padStart(HEX_DIGITS_PER_BYTE, '0')
    }

    private companion object {
        // `x'`, 64 hex digits and `'`: SQLCipher takes a key as raw only in exactly this form.
        const val RAW_KEY_LENGTH = 67
        const val RAW_KEY_MARKER: Byte = 0x78
        const val QUOTE: Byte = 0x27
        const val HEX_DIGITS = "0123456789abcdef"
        const val HEX_RADIX = 16
        const val HEX_DIGITS_PER_BYTE = 2
        const val BYTE_MASK = 0xFF
    }
}
