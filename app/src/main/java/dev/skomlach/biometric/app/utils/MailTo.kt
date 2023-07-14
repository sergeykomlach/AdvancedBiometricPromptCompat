/*
 *  Copyright (c) 2021 Sergey Komlach aka Salat-Cx65; Original project https://github.com/Salat-Cx65/AdvancedBiometricPromptCompat
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

package dev.skomlach.biometric.app.utils

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.core.app.ShareCompat
import androidx.core.content.ContextCompat

object MailTo {
    fun startMailClient(
        ctx: Activity,
        to: String,
        subject: String,
        extraText: String?
    ): Boolean {

        try {
            // or
            val builder: ShareCompat.IntentBuilder = ShareCompat.IntentBuilder(ctx)
            builder.setType("message/rfc822")
            builder.addEmailTo(to)
            builder.setText(extraText)
            builder.setSubject(subject)

            builder.setChooserTitle("Send e-mail")

            if (ctx.packageManager.queryIntentActivities(builder.intent, 0).isNotEmpty()) {
                builder.startChooser()
                return true
            }
        } catch (e: Throwable){

        }
        return false
    }
}