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
package com.wire.kalium.logic.feature.call

import com.wire.kalium.logic.util.PlatformContext
import com.wire.kalium.network.CurrentNetwork
import com.wire.kalium.network.NetworkState
import com.wire.kalium.network.NetworkStateObserver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class GlobalCallManagerTest {

    @Test
    fun givenNoCallManagerWithCallingEnabled_whenNetworkChanges_thenAvsIsNotLoaded() = runTest {
        // given: the JVM test runtime has no libavs, so touching AVS would throw UnsatisfiedLinkError
        val globalCallManager = GlobalCallManager(
            scope = backgroundScope,
            networkStateObserver = networkStateObserver,
            platformContext = PlatformContext()
        )
        // when / then: no exception
        globalCallManager.networkChanged()
    }

    private val networkStateObserver = object : NetworkStateObserver {
        override fun observeNetworkState(): StateFlow<NetworkState> = MutableStateFlow(NetworkState.ConnectedWithInternet)
        override fun observeCurrentNetwork(): StateFlow<CurrentNetwork?> = MutableStateFlow(null)
    }
}
