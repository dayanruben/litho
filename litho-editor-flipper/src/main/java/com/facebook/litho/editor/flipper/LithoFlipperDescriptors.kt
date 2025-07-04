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

package com.facebook.litho.editor.flipper

import com.facebook.flipper.plugins.inspector.DescriptorMapping
import com.facebook.litho.BaseMountingView
import com.facebook.litho.DebugComponent
import com.facebook.litho.sections.debug.DebugSection
import com.facebook.litho.widget.LithoRecyclerView

object LithoFlipperDescriptors {

  @JvmStatic
  fun add(descriptorMapping: DescriptorMapping) {
    descriptorMapping.register(BaseMountingView::class.java, LithoViewDescriptor())
    descriptorMapping.register(DebugComponent::class.java, DebugComponentDescriptor())
  }

  @JvmStatic
  fun addWithSections(descriptorMapping: DescriptorMapping) {
    add(descriptorMapping)
    descriptorMapping.register(LithoRecyclerView::class.java, LithoRecyclerViewDescriptor())
    descriptorMapping.register(DebugSection::class.java, DebugSectionDescriptor())
  }
}
