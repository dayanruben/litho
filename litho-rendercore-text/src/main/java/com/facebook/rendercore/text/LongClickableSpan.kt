/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.facebook.rendercore.text

import android.text.style.ClickableSpan
import android.view.View

/** Extension of [ClickableSpan] that provides longclick capability in addition to click. */
abstract class LongClickableSpan : ClickableSpan() {
  /**
   * Callback for longclick of this span.
   *
   * @return true if the callback consumed the longclick, false otherwise.
   */
  abstract fun onLongClick(view: View): Boolean
}
