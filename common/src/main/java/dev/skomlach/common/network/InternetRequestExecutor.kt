/*
 *  Copyright (c) 2026 Sergey Komlach aka Salat-Cx65; Original project https://github.com/Salat-Cx65/AdvancedBiometricPromptCompat
 *  All rights reserved.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package dev.skomlach.common.network

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Every configured endpoint can run even when earlier endpoints occupy their full deadline. */
internal fun internetRequestExecutor(threadFactory: ThreadFactory): ThreadPoolExecutor = ThreadPoolExecutor(
    InternetProbe.MAX_ENDPOINTS,
    InternetProbe.MAX_ENDPOINTS,
    30,
    TimeUnit.SECONDS,
    ArrayBlockingQueue(InternetProbe.MAX_ENDPOINTS),
    threadFactory,
    ThreadPoolExecutor.AbortPolicy()
).apply { allowCoreThreadTimeOut(true) }
