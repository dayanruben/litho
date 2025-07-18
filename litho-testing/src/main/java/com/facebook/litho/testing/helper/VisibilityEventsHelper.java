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

package com.facebook.litho.testing.helper;

import com.facebook.infer.annotation.Nullsafe;
import com.facebook.litho.ComponentTree;
import com.facebook.litho.FocusedVisibleEvent;
import com.facebook.litho.FullImpressionVisibleEvent;
import com.facebook.litho.InvisibleEvent;
import com.facebook.litho.UnfocusedVisibleEvent;
import com.facebook.litho.VisibleEvent;
import com.facebook.litho.testing.Whitebox;
import com.facebook.rendercore.visibility.VisibilityEventCallbackData;
import com.facebook.rendercore.visibility.VisibilityUtils;

/**
 * Allows calling visibility events manually which is useful in automated tests
 *
 * <p>Since this requires a bunch of private APIs, and we haven't reached a conclusion of whether
 * they should be public we are making aggressive use of reflection through Whitebox to call them
 */
@Nullsafe(Nullsafe.Mode.LOCAL)
public class VisibilityEventsHelper {

  /**
   * Tries to trigger the requested visibility event on the given component tree on the first
   * matching visibility output
   *
   * @param componentTree the component tree to search
   * @param visibilityEventType the event to trigger
   * @return true if a matching object to trigger the event on was found
   */
  public static boolean triggerVisibilityEvent(
      ComponentTree componentTree, Class<?> visibilityEventType) {

    final Object layoutState = getGetMainThreadLayoutState(componentTree);
    for (int i = 0, size = getVisibilityOutputCount(layoutState); i < size; i++) {
      final Object visibilityOutput = getVisibilityOutputAt(layoutState, i);
      if (visibilityEventType == VisibleEvent.class
          && getEventHandler(visibilityOutput, "Visible") != null) {
        VisibilityUtils.dispatchOnVisible(
            getEventHandler(visibilityOutput, "Visible").getCallback(), null);
        return true;
      } else if (visibilityEventType == InvisibleEvent.class
          && getEventHandler(visibilityOutput, "Invisible") != null) {
        VisibilityUtils.dispatchOnInvisible(
            getEventHandler(visibilityOutput, "Invisible").getCallback());
        return true;
      } else if (visibilityEventType == FocusedVisibleEvent.class
          && getEventHandler(visibilityOutput, "FocusedVisible") != null) {
        VisibilityUtils.dispatchOnFocused(
            getEventHandler(visibilityOutput, "FocusedVisible").getCallback());
        return true;
      } else if (visibilityEventType == UnfocusedVisibleEvent.class
          && getEventHandler(visibilityOutput, "UnfocusedVisible") != null) {
        VisibilityUtils.dispatchOnUnfocused(
            getEventHandler(visibilityOutput, "UnfocusedVisible").getCallback());
        return true;
      } else if (visibilityEventType == FullImpressionVisibleEvent.class
          && getEventHandler(visibilityOutput, "FullImpression") != null) {
        VisibilityUtils.dispatchOnFullImpression(
            getEventHandler(visibilityOutput, "FullImpression").getCallback());
        return true;
      }
    }
    return false;
  }

  private static Object getGetMainThreadLayoutState(ComponentTree componentTree) {
    return Whitebox.invokeMethod(componentTree, "getMainThreadLayoutState");
  }

  private static int getVisibilityOutputCount(Object layoutState) {
    return (int) Whitebox.invokeMethod(layoutState, "getVisibilityOutputCount");
  }

  private static Object getVisibilityOutputAt(Object layoutState, int i) {
    return Whitebox.invokeMethod(layoutState, "getVisibilityOutputAt", i);
  }

  private static VisibilityEventCallbackData getEventHandler(Object layoutState, String name) {
    return Whitebox.invokeMethod(layoutState, "getOn" + name);
  }

  private static void dispatch(Object eventHandler, String name) throws ClassNotFoundException {
    Whitebox.invokeMethod(
        Class.forName("com.facebook.rendercore.visibility.VisibilityUtils"),
        "dispatchOn" + name,
        eventHandler);
  }
}
