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

package com.facebook.samples.litho.kotlin.accessibility

import com.facebook.litho.Column
import com.facebook.litho.Component
import com.facebook.litho.ComponentScope
import com.facebook.litho.KComponent
import com.facebook.litho.Style
import com.facebook.litho.accessibility.stateDescription
import com.facebook.litho.kotlin.widget.Text
import com.facebook.litho.useState
import com.facebook.litho.view.onClick

internal class StateDescriptionComponent : KComponent() {

  override fun ComponentScope.render(): Component? {
    val moodOfTheDay = useState { false }
    return Column {
      child(
          Text(
              "Mood: ${if(moodOfTheDay.value) "BE" else "Compose"} - Click to change",
              style =
                  Style.onClick { moodOfTheDay.update { !it } }
                      .stateDescription(
                          if (moodOfTheDay.value) "anna is the queen of BE"
                          else "michal is the king of Compose")))
    }
  }
}
