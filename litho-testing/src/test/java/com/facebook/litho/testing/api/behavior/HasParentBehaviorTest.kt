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

package com.facebook.litho.testing.api.behavior

import com.facebook.litho.Column
import com.facebook.litho.Component
import com.facebook.litho.ComponentScope
import com.facebook.litho.KComponent
import com.facebook.litho.kotlin.widget.Text
import com.facebook.litho.testing.api.LithoRule
import com.facebook.litho.testing.api.hasParent
import com.facebook.litho.testing.api.hasText
import com.facebook.litho.testing.api.hasType
import com.facebook.litho.testing.testrunner.LithoTestRunner
import com.facebook.litho.widget.Image
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.LooperMode

@LooperMode(LooperMode.Mode.LEGACY)
@RunWith(LithoTestRunner::class)
class HasParentBehaviorTest {

  @get:Rule val rule: LithoRule = LithoRule()

  @Test
  fun `hasParent will succeed when there is a parent component that respects the condition`() {
    rule
        .render { TopLevelComponent() }
        .selectNode(hasParent(hasType<BottomLevelComponent>()))
        .selectChildren()
        .selectFirst()
        .assert(hasText("bottom-level"))
  }

  @Test
  fun `hasParent will throw an error when there is no parent component that respects the condition`() {
    assertThatThrownBy {
          rule.render { TopLevelComponent() }.selectNode(hasParent(hasType<Image>())).assertExists()
        }
        .isInstanceOf(AssertionError::class.java)
  }

  private class TopLevelComponent : KComponent() {

    override fun ComponentScope.render(): Component {
      return Column {
        child(Text("top-level"))
        child(MidLevelComponent())
      }
    }
  }

  private class MidLevelComponent : KComponent() {

    override fun ComponentScope.render(): Component {
      return Column {
        child(Text("mid-level"))
        child(BottomLevelComponent())
      }
    }
  }

  private class BottomLevelComponent : KComponent() {

    override fun ComponentScope.render(): Component {
      return Text("bottom-level")
    }
  }
}
