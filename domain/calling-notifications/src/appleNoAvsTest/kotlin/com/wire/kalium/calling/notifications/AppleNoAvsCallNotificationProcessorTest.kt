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

package com.wire.kalium.calling.notifications

import kotlin.test.Test
import kotlin.test.assertEquals

class AppleNoAvsCallNotificationProcessorTest {
    @Test
    fun givenAnAppleBuildWithoutAvs_whenProcessingCallNotifications_thenThePlatformIsUnsupported() {
        val processor = createPlatformAvsCallNotificationProcessor(
            selfUserId = "user@domain",
            selfClientId = "client",
            callbacks = object : AvsCallNotificationCallbacks {
                override fun onIncomingCall(incomingCall: AvsIncomingCallNotification) = Unit
                override fun onMissedCall(missedCall: AvsMissedCallNotification) = Unit
                override fun onClosedCall(closedCall: AvsClosedCallNotification) = Unit
            }
        )

        assertEquals(
            AvsCallNotificationProcessingResult.Failure(AvsCallNotificationProcessingFailure.UnsupportedPlatform),
            processor.process(emptyList())
        )
    }
}
