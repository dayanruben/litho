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

import android.view.View
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/**
 * Holds a mapping of Views inside a Litho hierarchy to related debugging information. This is
 * needed as a way of passing data to Views inside a Section hierarchy without relying on the
 * specific type of view that is used. For example, while a ComponentRenderInfo will be rendered
 * inside a LithoView always, a ViewRenderInfo could be using any type of View. This is to avoid
 * passing debugging data through structures that could be used by developers, such as the view tag.
 */
object RenderInfoDebugInfoRegistry {

  const val SONAR_SECTIONS_DEBUG_INFO_TAG: String = "SONAR_SECTIONS_DEBUG_INFO"
  const val SONAR_SINGLE_COMPONENT_SECTION_DATA_PREV: String = "SCS_DATA_INFO_PREV"
  const val SONAR_SINGLE_COMPONENT_SECTION_DATA_NEXT: String = "SCS_DATA_INFO_NEXT"

  private var ViewToRenderInfo: MutableMap<View, WeakReference<Any?>>? = null

  @JvmStatic
  fun getRenderInfoSectionDebugInfo(view: View): Any? {
    val viewToRenderInfo = ViewToRenderInfo
    if (viewToRenderInfo == null || !viewToRenderInfo.containsKey(view)) {
      return null
    }

    val weakRenderInfo: WeakReference<*>? = viewToRenderInfo[view]
    val renderInfo = weakRenderInfo?.get()

    if (renderInfo == null) {
      viewToRenderInfo.remove(view)
      return null
    }

    return renderInfo
  }

  @JvmStatic
  fun setRenderInfoToViewMapping(view: View, renderInfoSectionDebugInfo: Any?) {
    if (ViewToRenderInfo == null) {
      ViewToRenderInfo = WeakHashMap()
    }

    ViewToRenderInfo?.put(view, WeakReference(renderInfoSectionDebugInfo))
  }
}
