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

package com.facebook.litho.widget

import android.content.Context
import android.graphics.drawable.Drawable
import android.widget.ProgressBar

class ProgressView(context: Context) : ProgressBar(context) {

  /**
   * ProgressBar is not setting the right bounds on the drawable passed to
   * [ProgressBar.setIndeterminateDrawable]. Overriding the method and setting the bounds before
   * passing the drawable in solves the issue.
   */
  override fun setIndeterminateDrawable(d: Drawable?) {
    d?.setBounds(0, 0, width, height)
    super.setIndeterminateDrawable(d)
  }
}
