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

package com.facebook.litho.sections.common;

import com.facebook.infer.annotation.Nullsafe;
import com.facebook.litho.annotations.Event;
import com.facebook.litho.annotations.EventHandlerRebindMode;

/**
 * This event is triggered by {@link DataDiffSectionSpec} when it needs to verify whether two model
 * objects that represent the same item also have the same content.
 *
 * <p>todo(t16485443): The generic type declaration(OnCheckIsSameContentEvent<TEdgeModel>) is
 * temporarily removed until the bug in the attached task is fixed.
 */
@Nullsafe(Nullsafe.Mode.LOCAL)
@Event(returnType = Boolean.class, mode = EventHandlerRebindMode.NONE)
public class OnCheckIsSameContentEvent<T> {
  // NULLSAFE_FIXME[Field Not Initialized]
  public T previousItem;
  // NULLSAFE_FIXME[Field Not Initialized]
  public T nextItem;
}
