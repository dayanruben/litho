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

package com.facebook.litho.testing.subcomponents;

import static com.facebook.litho.testing.assertj.LegacyLithoAssertions.assertThat;
import static com.facebook.litho.testing.assertj.SubComponentExtractor.subComponentWith;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assume.assumeThat;

import com.facebook.litho.Column;
import com.facebook.litho.Component;
import com.facebook.litho.ComponentContext;
import com.facebook.litho.Row;
import com.facebook.litho.config.LithoDebugConfigurations;
import com.facebook.litho.testing.LithoTestRule;
import com.facebook.litho.testing.testrunner.LithoTestRunner;
import com.facebook.litho.widget.Card;
import com.facebook.litho.widget.TestCard;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.LooperMode;

@LooperMode(LooperMode.Mode.LEGACY)
@RunWith(LithoTestRunner.class)
public class CommonPropMatcherTest {
  @Rule public LithoTestRule lithoTestRule = new LithoTestRule();

  @Before
  public void setUp() {
    assumeThat(
        "These tests can only be run in debug mode.",
        LithoDebugConfigurations.isDebugModeEnabled,
        is(true));
  }

  @Test
  public void testTransitionKeyMatcher() {
    final ComponentContext c = lithoTestRule.getContext();
    final String key = "nocolusion";

    final Component component =
        Row.create(c).child(Card.create(c).transitionKey(key).content(Column.create(c))).build();

    assertThat(c, component)
        .has(subComponentWith(c, TestCard.matcher(c).transitionKey(key).build()));
  }
}
