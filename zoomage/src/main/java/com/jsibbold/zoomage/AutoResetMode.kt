/**
 * Copyright 2016 Jeffrey Sibbold
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jsibbold.zoomage

import androidx.annotation.IntDef


/**
 * Describes how the [ZoomageView] will reset to its original size
 * once interaction with it stops. [.UNDER] will reset when the image is smaller
 * than or equal to its starting size, [.OVER] when it's larger than or equal to its starting size,
 * [.ALWAYS] in both situations,
 * and [.NEVER] causes no reset. Note that when using [.NEVER], the image will still animate
 * to within the screen bounds in certain situations.
 */
@Retention(AnnotationRetention.SOURCE)
@IntDef(value = [AutoResetMode.Companion.NEVER, AutoResetMode.Companion.UNDER, AutoResetMode.Companion.OVER, AutoResetMode.Companion.ALWAYS])
annotation class AutoResetMode {
    object Parser {
        @JvmStatic
        @AutoResetMode
        fun fromInt(value: Int): Int = when (value) {
            OVER -> OVER
            ALWAYS -> ALWAYS
            NEVER -> NEVER
            else -> UNDER
        }
    }

    companion object {
        const val UNDER: Int = 0
        const val OVER: Int = 1
        const val ALWAYS: Int = 2
        const val NEVER: Int = 3
    }
}
