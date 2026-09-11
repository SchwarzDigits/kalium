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

package com.wire.kalium.persistence.kmmSettings

import com.sun.jna.platform.win32.Crypt32Util
import com.sun.jna.platform.win32.Win32Exception
import com.sun.jna.platform.win32.WinCrypt
import java.util.Base64

/**
 * Protects the settings master key with DPAPI for the current Windows user.
 *
 * DPAPI ties the key to the user account, not to the machine. It follows roaming profiles and
 * Citrix profile management, and survives an admin password reset of a domain account. It does not
 * survive such a reset of a local account. There is no key store entry: the protected key itself is
 * the reference kept in the key file.
 */
internal class WindowsDpapiMasterKeyStore : MasterKeyStore {

    override val name: String = "windows-dpapi"

    override fun store(key: ByteArray): String {
        val protectedKey = try {
            Crypt32Util.cryptProtectData(key, ENTROPY, WinCrypt.CRYPTPROTECT_UI_FORBIDDEN, DESCRIPTION, null)
        } catch (exception: Win32Exception) {
            throw SettingsEncryptionException("Can't protect the settings master key with DPAPI: ${exception.message}", exception)
        }
        return Base64.getEncoder().encodeToString(protectedKey)
    }

    override fun load(reference: String): ByteArray {
        val protectedKey = try {
            Base64.getDecoder().decode(reference)
        } catch (exception: IllegalArgumentException) {
            throw SettingsEncryptionException("The settings master key file is damaged", exception)
        }
        return try {
            Crypt32Util.cryptUnprotectData(protectedKey, ENTROPY, WinCrypt.CRYPTPROTECT_UI_FORBIDDEN, null)
        } catch (exception: Win32Exception) {
            throw SettingsEncryptionException(
                "Can't unprotect the settings master key with DPAPI, for example after a password reset: ${exception.message}",
                exception
            )
        }
    }

    private companion object {
        const val DESCRIPTION = "Kalium settings master key"

        // Not a secret: it keeps other DPAPI callers of the same user from unprotecting the key by accident.
        val ENTROPY = "com.wire.kalium.settings".encodeToByteArray()
    }
}
