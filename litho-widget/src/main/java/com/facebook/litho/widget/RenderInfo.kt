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

import com.facebook.litho.Component
import com.facebook.litho.ComponentTreeDebugEventListener
import com.facebook.litho.ComponentsLogger
import com.facebook.litho.EventHandler
import com.facebook.litho.RenderCompleteEvent
import com.facebook.litho.viewcompat.ViewBinder
import com.facebook.litho.viewcompat.ViewCreator

interface RenderInfo {

  val isSticky: Boolean

  val spanSize: Int

  val isFullSpan: Boolean

  val parentWidthPercent: Float

  val parentHeightPercent: Float

  fun getCustomAttribute(key: String): Any?

  fun addCustomAttribute(key: String, value: Any?)

  fun rendersComponent(): Boolean

  val component: Component

  val componentsLogger: ComponentsLogger?

  val logTag: String?

  /**
   * @return the [ComponentTreeDebugEventListener] that should be used on the [ComponentTree]
   *   responsible for building this [RenderInfo]
   */
  val debugEventListener: ComponentTreeDebugEventListener?

  val renderCompleteEventHandler: EventHandler<RenderCompleteEvent>?

  fun rendersView(): Boolean

  val viewBinder: ViewBinder<*>

  val viewCreator: ViewCreator<*>

  fun hasCustomViewType(): Boolean

  /**
   * Set viewType of current [RenderInfo] if it was created through [ViewRenderInfo.create] and a
   * custom viewType was not set, or otherwise it will throw [UnsupportedOperationException].
   */
  var viewType: Int

  fun addDebugInfo(key: String, value: Any?)

  fun getDebugInfo(key: String): Any?

  val name: String
}
