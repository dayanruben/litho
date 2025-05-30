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

package com.facebook.litho

import android.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.facebook.litho.it.R
import com.facebook.litho.it.R.attr.testAttrDimen
import com.facebook.litho.it.R.attr.testAttrDrawable
import com.facebook.litho.it.R.attr.undefinedAttrDimen
import com.facebook.litho.it.R.attr.undefinedAttrDrawable
import com.facebook.litho.it.R.dimen.default_dimen
import com.facebook.litho.it.R.dimen.test_dimen
import com.facebook.litho.it.R.dimen.test_dimen_float
import com.facebook.litho.it.R.drawable.test_bg
import com.facebook.litho.it.R.style.TestTheme
import com.facebook.litho.testing.LithoTestRule
import com.facebook.litho.testing.testrunner.LithoTestRunner
import com.facebook.litho.testing.unspecified
import com.facebook.yoga.YogaEdge
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows

@RunWith(LithoTestRunner::class)
class ResolveAttributeTest {

  @JvmField @Rule val lithoTestRule: LithoTestRule = LithoTestRule()

  @Before
  fun setup() {
    lithoTestRule.useContext(
        ComponentContext(
            ContextThemeWrapper(ApplicationProvider.getApplicationContext(), TestTheme)))
  }

  @Test
  fun testResolveDrawableAttribute() {
    val c = lithoTestRule.context
    val column = Column.create(c).backgroundAttr(testAttrDrawable, 0).build()
    val testLithoView =
        lithoTestRule.render(widthSpec = unspecified(), heightSpec = unspecified()) { column }
    val d = c.resources.getDrawable(test_bg)
    val drawable = testLithoView.currentRootNode?.node?.background
    assertThat(Shadows.shadowOf(drawable).createdFromResId)
        .isEqualTo(Shadows.shadowOf(d).createdFromResId)
  }

  @Test
  fun testResolveDimenAttribute() {
    val c = lithoTestRule.context
    val column = Column.create(c).widthAttr(testAttrDimen, default_dimen).build()
    val testLithoView =
        lithoTestRule.render(widthSpec = unspecified(), heightSpec = unspecified()) { column }
    val dimen = c.resources.getDimensionPixelSize(R.dimen.test_dimen)
    assertThat(testLithoView.currentRootNode?.width).isEqualTo(dimen)
  }

  @Test
  fun testDefaultDrawableAttribute() {
    val c = lithoTestRule.context
    val column = Column.create(c).backgroundAttr(undefinedAttrDrawable, test_bg).build()
    val testLithoView =
        lithoTestRule.render(widthSpec = unspecified(), heightSpec = unspecified()) { column }
    val d = c.resources.getDrawable(test_bg)
    val drawable = testLithoView.currentRootNode?.node?.background
    assertThat(Shadows.shadowOf(drawable).createdFromResId)
        .isEqualTo(Shadows.shadowOf(d).createdFromResId)
  }

  @Test
  fun testDefaultDimenAttribute() {
    val c = lithoTestRule.context
    val column = Column.create(c).widthAttr(undefinedAttrDimen, test_dimen).build()
    val testLithoView =
        lithoTestRule.render(widthSpec = unspecified(), heightSpec = unspecified()) { column }
    val dimen = c.resources.getDimensionPixelSize(R.dimen.test_dimen)
    assertThat(testLithoView.currentRootNode?.width).isEqualTo(dimen)
  }

  @Test
  fun testFloatDimenWidthAttribute() {
    val c = lithoTestRule.context
    val column = Column.create(c).widthAttr(undefinedAttrDimen, test_dimen_float).build()
    val testLithoView =
        lithoTestRule.render(widthSpec = unspecified(), heightSpec = unspecified()) { column }
    val dimen = c.resources.getDimensionPixelSize(test_dimen_float)
    assertThat(testLithoView.currentRootNode?.width).isEqualTo(dimen)
  }

  @Test
  fun testFloatDimenPaddingAttribute() {
    val c = lithoTestRule.context
    val column =
        Column.create(c).paddingAttr(YogaEdge.LEFT, undefinedAttrDimen, test_dimen_float).build()
    val testLithoView =
        lithoTestRule.render(widthSpec = unspecified(), heightSpec = unspecified()) { column }
    val dimen = c.resources.getDimensionPixelSize(test_dimen_float)
    assertThat(testLithoView.currentRootNode?.paddingLeft).isEqualTo(dimen)
  }
}
