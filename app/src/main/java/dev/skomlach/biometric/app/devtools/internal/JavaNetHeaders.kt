/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.skomlach.biometric.app.devtools.internal

import okhttp3.Headers
import java.util.Collections
import java.util.TreeMap

object JavaNetHeaders {
    private val FIELD_NAME_COMPARATOR = java.util.Comparator<String?> { a, b ->

        // @FindBugsSuppressWarnings("ES_COMPARING_PARAMETER_STRING_WITH_EQ")
        if (a === b) {
            0
        } else if (a == null) {
            -1
        } else if (b == null) {
            1
        } else {
            java.lang.String.CASE_INSENSITIVE_ORDER.compare(a, b)
        }
    }

    /**
     * Returns an immutable map containing each field to its list of values.
     *
     * @param valueForNullKey the request line for requests, or the status line for responses. If
     * non-null, this value is mapped to the null key.
     */
    @JvmStatic
    fun toMultimap(headers: Headers, valueForNullKey: String?): Map<String?, List<String>> {
        val result: MutableMap<String?, List<String>> = TreeMap(FIELD_NAME_COMPARATOR)
        var i = 0
        val size = headers.size
        while (i < size) {
            val fieldName = headers.name(i)
            val value = headers.value(i)
            val allValues: MutableList<String> = ArrayList()
            val otherValues = result[fieldName]
            if (otherValues != null) {
                allValues.addAll(otherValues)
            }
            allValues.add(value)
            result[fieldName] = Collections.unmodifiableList(allValues)
            i++
        }
        if (valueForNullKey != null) {
            result[null] =
                Collections.unmodifiableList(listOf(valueForNullKey))
        }
        return Collections.unmodifiableMap(result)
    }
}