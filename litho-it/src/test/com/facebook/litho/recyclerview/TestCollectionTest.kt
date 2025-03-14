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

package com.facebook.litho.recyclerview

import androidx.recyclerview.widget.RecyclerView
import com.facebook.litho.Column
import com.facebook.litho.Component
import com.facebook.litho.ComponentScope
import com.facebook.litho.KComponent
import com.facebook.litho.Style
import com.facebook.litho.core.height
import com.facebook.litho.kotlin.widget.Text
import com.facebook.litho.sections.SectionContext
import com.facebook.litho.sections.common.SingleComponentSection
import com.facebook.litho.sections.widget.RecyclerCollectionComponent
import com.facebook.litho.testing.LithoTestRule
import com.facebook.litho.testing.assertj.LithoAssertions
import com.facebook.litho.testing.atMost
import com.facebook.litho.widget.collection.CrossAxisWrapMode
import com.facebook.litho.widget.collection.LazyList
import com.facebook.rendercore.dp
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.LooperMode

/**
 * Tests which ensure that helpful errors are displayed to the developer when operating with the
 * TestCollection API. This utilizes the [RobolectricTestRunner] to better emulate the end user's
 * test setup.
 */
@LooperMode(LooperMode.Mode.LEGACY)
@RunWith(RobolectricTestRunner::class)
class TestCollectionTest {

  @Rule @JvmField val lithoTestRule = LithoTestRule()

  @Test
  fun `respect CrossAxisWrapMode MatchFirstChild when set on LazyList`() {
    val lithoView =
        lithoTestRule.render(heightSpec = atMost(500)) {
          LazyList(
              orientation = RecyclerView.HORIZONTAL,
              crossAxisWrapMode = CrossAxisWrapMode.MatchFirstChild) {
                children(items = (0..5), id = { it }) {
                  if (it % 2 == 0) {
                    return@children Text("$it", style = Style.height(50.dp))
                  } else {
                    return@children Text("$it", style = Style.height(100.dp))
                  }
                }
              }
        }
    assertThat(lithoView.lithoView.measuredHeight).isEqualTo(50)
  }

  @Test
  @Ignore(
      "We haven't exposed re-measure listener to lazy collection yet, so we're not able to get the expected measured size, will fix it later. ")
  fun `respect CrossAxisWrapMode Dynamic when set on LazyList`() {
    val lithoView =
        lithoTestRule.render(heightSpec = atMost(500)) {
          LazyList(
              orientation = RecyclerView.HORIZONTAL,
              crossAxisWrapMode = CrossAxisWrapMode.Dynamic) {
                children(items = (0..5), id = { it }) {
                  if (it % 2 == 0) {
                    return@children Text("$it", style = Style.height(50.dp))
                  } else {
                    return@children Text("$it", style = Style.height(100.dp))
                  }
                }
              }
        }
    assertThat(lithoView.lithoView.measuredHeight).isEqualTo(100)
  }

  @Test
  fun `respect CrossAxisWrapMode NoWrap when set on LazyList`() {
    val lithoView =
        lithoTestRule.render(heightSpec = atMost(500)) {
          LazyList(
              orientation = RecyclerView.HORIZONTAL, crossAxisWrapMode = CrossAxisWrapMode.NoWrap) {
                children(items = (0..5), id = { it }) {
                  if (it % 2 == 0) {
                    return@children Text("$it", style = Style.height(50.dp))
                  } else {
                    return@children Text("$it", style = Style.height(100.dp))
                  }
                }
              }
        }
    assertThat(lithoView.lithoView.measuredHeight).isEqualTo(500)
  }

  @Test
  fun `test helpful error when recycler collection components recycler view is not mounted`() {

    val testView =
        lithoTestRule.render {
          TestComponent(
              RecyclerCollectionComponent(
                  section =
                      SingleComponentSection.create(SectionContext(context))
                          .component(Text("0"))
                          .build()))
        }

    val listComponent = testView.findCollectionComponent()
    assertThat(listComponent).isNotNull

    assertThatThrownBy { LithoAssertions.assertThat(listComponent).hasVisibleText("0") }
        .hasMessageContaining("could not find a mounted recycler view")
        .hasMessageContaining("add a width and height")
  }

  @Test
  fun `test helpful error when lazy list recycler view is unable to properly measure`() {
    assertThatThrownBy {
          lithoTestRule.render {
            TestComponent(LazyList { child(id = 0, component = Text("child0")) })
          }
        }
        .hasMessageContaining(
            "Height mode has to be EXACTLY OR AT MOST for a vertical scrolling RecyclerView")
  }

  private class TestComponent(private val childComponent: Component) : KComponent() {
    override fun ComponentScope.render(): Component = Column { child(childComponent) }
  }
}
