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

package com.facebook.litho.sections.treeprops;

import com.facebook.infer.annotation.Nullsafe;
import com.facebook.litho.annotations.OnCreateTreeProp;
import com.facebook.litho.annotations.TreeProp;
import com.facebook.litho.sections.Children;
import com.facebook.litho.sections.SectionContext;
import com.facebook.litho.sections.annotations.GroupSectionSpec;
import com.facebook.litho.sections.annotations.OnCreateChildren;
import com.facebook.litho.sections.common.SingleComponentSection;

@Nullsafe(Nullsafe.Mode.LOCAL)
@GroupSectionSpec
public class TopGroupSectionSpec {

  @OnCreateChildren
  protected static Children onCreateChildren(SectionContext c) {
    return Children.create()
        .child(BottomGroupSection.create(c).build())
        .child(SingleComponentSection.create(c).component(LeafComponent.create(c)))
        .build();
  }

  @OnCreateTreeProp
  static LogContext onCreateTestTreeProp(SectionContext c, @TreeProp LogContext t) {
    return LogContext.append(t, "top");
  }
}
