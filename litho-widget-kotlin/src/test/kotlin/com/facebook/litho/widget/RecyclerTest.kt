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

package com.facebook.litho.kotlin.widget

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.OrientationHelper
import androidx.recyclerview.widget.RecyclerView
import com.facebook.litho.Style
import com.facebook.litho.core.height
import com.facebook.litho.core.width
import com.facebook.litho.testing.LithoTestRule
import com.facebook.litho.testing.testrunner.LithoTestRunner
import com.facebook.litho.widget.ComponentRenderInfo
import com.facebook.litho.widget.LinearLayoutInfo
import com.facebook.litho.widget.Recycler
import com.facebook.litho.widget.RecyclerBinder
import com.facebook.litho.widget.RecyclerEventsController
import com.facebook.litho.widget.SectionsRecyclerView
import com.facebook.litho.widget.Text
import com.facebook.rendercore.px
import junit.framework.Assert.assertNotNull
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.LooperMode

/** Tests for [Recycler] */
@LooperMode(LooperMode.Mode.LEGACY)
@RunWith(LithoTestRunner::class)
class RecyclerTest {

  @Rule @JvmField val mLithoTestRule: LithoTestRule = LithoTestRule()

  lateinit var recyclerBinder: RecyclerBinder

  @Before
  fun setup() {
    recyclerBinder =
        RecyclerBinder.Builder()
            .layoutInfo(LinearLayoutInfo(mLithoTestRule.context, OrientationHelper.VERTICAL, false))
            .build(mLithoTestRule.context)
    for (i in 0 until 10) {
      recyclerBinder.insertItemAt(
          i,
          ComponentRenderInfo.create()
              .component(Text.create(mLithoTestRule.context).text("test"))
              .build())
    }
  }

  @Test
  fun `ExperimentalRecycler should render with default values`() {
    val testLithoView =
        mLithoTestRule.render {
          Recycler(binder = recyclerBinder, style = Style.width(100.px).height(100.px))
        }

    // should find an ExperimentalRecycler in the tree
    assertNotNull(testLithoView.findComponent(Recycler::class))

    val sectionsRecyclerView =
        testLithoView.lithoView.getMountItemAt(0).content as SectionsRecyclerView

    // values should be set to default
    assertThat(sectionsRecyclerView.recyclerView.hasFixedSize()).isEqualTo(true)
    assertThat(sectionsRecyclerView.recyclerView.clipToPadding).isEqualTo(true)
    assertThat(sectionsRecyclerView.recyclerView.isNestedScrollingEnabled).isEqualTo(true)
    assertThat(sectionsRecyclerView.recyclerView.scrollBarStyle)
        .isEqualTo(View.SCROLLBARS_INSIDE_OVERLAY)
    assertThat(sectionsRecyclerView.recyclerView.id).isEqualTo(View.NO_ID)
    assertThat(sectionsRecyclerView.recyclerView.horizontalFadingEdgeLength).isEqualTo(0)
    assertThat(sectionsRecyclerView.recyclerView.isHorizontalFadingEdgeEnabled).isEqualTo(false)
    assertThat(sectionsRecyclerView.recyclerView.isVerticalFadingEdgeEnabled).isEqualTo(false)
  }

  @Test
  fun `ItemDecorator when set is respected`() {
    val itemDecoration: RecyclerView.ItemDecoration =
        object : RecyclerView.ItemDecoration() {
          override fun getItemOffsets(
              outRect: Rect,
              view: View,
              parent: RecyclerView,
              state: RecyclerView.State
          ) {
            outRect.left = 10
          }
        }

    val testLithoView =
        mLithoTestRule.render {
          Recycler(
              binder = recyclerBinder,
              itemDecorations = listOf(itemDecoration),
              style = Style.width(100.px).height(100.px))
        }

    val sectionsRecyclerView =
        testLithoView.lithoView.getMountItemAt(0).content as SectionsRecyclerView

    assertThat(sectionsRecyclerView.recyclerView.itemDecorationCount).isEqualTo(1)
    assertThat(sectionsRecyclerView.recyclerView.getItemDecorationAt(0)).isEqualTo(itemDecoration)
  }

  @Test
  fun `RecyclerEventController when set is respected`() {
    val recyclerEventsController = RecyclerEventsController()
    val testLithoView =
        mLithoTestRule.render {
          Recycler(
              binder = recyclerBinder,
              recyclerEventsController = recyclerEventsController,
              style = Style.width(100.px).height(100.px))
        }

    val sectionsRecyclerView =
        testLithoView.lithoView.getMountItemAt(0).content as SectionsRecyclerView

    assertThat(recyclerEventsController.recyclerView).isEqualTo(sectionsRecyclerView.recyclerView)
  }

  @Test
  fun `ItemAnimator when set is respected`() {
    val itemAnimator = DefaultItemAnimator()
    val testLithoView =
        mLithoTestRule.render {
          Recycler(
              binder = recyclerBinder,
              itemAnimator = itemAnimator,
              style = Style.width(100.px).height(100.px))
        }

    val sectionsRecyclerView =
        testLithoView.lithoView.getMountItemAt(0).content as SectionsRecyclerView

    assertThat(sectionsRecyclerView.recyclerView.itemAnimator).isEqualTo(itemAnimator)
  }
}
