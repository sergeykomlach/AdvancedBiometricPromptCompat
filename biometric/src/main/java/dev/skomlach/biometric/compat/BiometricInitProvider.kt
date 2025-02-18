/*
 *  Copyright (c) 2023 Sergey Komlach aka Salat-Cx65; Original project https://github.com/Salat-Cx65/AdvancedBiometricPromptCompat
 *  All rights reserved.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package dev.skomlach.biometric.compat

import android.content.ContentProvider
import android.content.ContentValues
import android.net.Uri
import dev.skomlach.common.misc.ExecutorHelper

class BiometricInitProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        try {
            BiometricPromptCompat.init()
        } catch (e: Throwable) {
            ExecutorHelper.post{
                try {
                    BiometricPromptCompat.init()
                } catch (e: Throwable) {}
            }
        }

        return false
    }

    private fun unsupported(errorMessage: String? = null): Nothing =
        throw UnsupportedOperationException(errorMessage)

    override fun insert(uri: Uri, values: ContentValues?) = unsupported()


    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ) = unsupported()

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ) = unsupported()

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) =
        unsupported()

    override fun getType(uri: Uri) = unsupported()
}