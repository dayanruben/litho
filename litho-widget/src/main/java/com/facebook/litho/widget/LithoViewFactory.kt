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

import com.facebook.litho.ComponentContext
import com.facebook.litho.LithoView

/** A factory used to create [LithoView]s in [RecyclerBinder]. */
fun interface LithoViewFactory {

  /** @return a new [LithoView] that will be used to host children of the [Recycler]. */
  fun createLithoView(context: ComponentContext): LithoView
}
