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

package com.facebook.samples.lithoktbarebones

import com.facebook.litho.Column
import com.facebook.litho.Component
import com.facebook.litho.ComponentContext
import com.facebook.litho.annotations.LayoutSpec
import com.facebook.litho.annotations.OnCreateLayout
import com.facebook.litho.annotations.Prop
import com.facebook.litho.widget.Text
import com.facebook.yoga.YogaEdge.ALL

@LayoutSpec
object ListItemSpec {

  @OnCreateLayout
  fun onCreateLayout(
      c: ComponentContext,
      @Prop color: Int,
      @Prop title: String,
      @Prop subtitle: String
  ): Component =
      Column.create(c)
          .paddingDip(ALL, 16f)
          .backgroundColor(color)
          .child(Text.create(c).text(title).textSizeSp(40f))
          .child(Text.create(c).text(subtitle).textSizeSp(20f))
          .build()
}
