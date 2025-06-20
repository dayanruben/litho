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

package com.facebook.litho.testing.viewtree;

import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.TextView;
import com.facebook.litho.ComponentHost;
import com.facebook.rendercore.text.RCTextView;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowCanvas;

/** A collection of useful predicates over Android views for tests */
public final class ViewPredicates {

  private ViewPredicates() {}

  /**
   * Returns a predicate that returns true if the applied on view's text is equal to the given text.
   * substring.
   *
   * @param predicate the predicate with which to test the text
   * @return the predicate
   */
  public static Predicate<View> hasTextMatchingPredicate(final Predicate<String> predicate) {
    return new Predicate<View>() {
      @Override
      public boolean apply(final View input) {
        if (predicate.apply(extractString(input))) {
          return true;
        }

        if (input instanceof ComponentHost) {
          return ComponentQueries.hasTextMatchingPredicate((ComponentHost) input, predicate);
        }

        return false;
      }
    };
  }

  /**
   * Returns a predicate that returns true if the applied on view's text is equal to the given text.
   * substring.
   *
   * @param text the text to check
   * @return the predicate
   */
  public static Predicate<View> hasText(final String text) {
    return hasTextMatchingPredicate(Predicates.equalTo(text));
  }

  public static Predicate<View> hasTag(final int tagId, final Object tagValue) {
    return new Predicate<View>() {
      @Override
      public boolean apply(final View input) {
        final Object tag = input.getTag(tagId);
        return tag != null && tag.equals(tagValue);
      }
    };
  }

  public static Predicate<View> hasContentDescription(final String contentDescription) {
    return new Predicate<View>() {
      @Override
      public boolean apply(final View input) {
        if (input instanceof ComponentHost) {
          final List<CharSequence> contentDescriptions =
              ((ComponentHost) input).getContentDescriptions();
          return contentDescriptions.contains(contentDescription);
        }

        return contentDescription.equals(input.getContentDescription());
      }
    };
  }

  public static Predicate<View> hasVisibleText(final String text) {
    return Predicates.and(isVisible(), hasText(text));
  }

  public static Predicate<View> hasVisibleTextWithTag(
      final String text, final int tagId, final Object tagValue) {
    return Predicates.and(hasVisibleText(text), hasTag(tagId, tagValue));
  }

  public static Predicate<View> matchesText(final String text) {
    final Pattern pattern = Pattern.compile(text);

    return new Predicate<View>() {
      @Override
      public boolean apply(final View input) {
        if (pattern.matcher(extractString(input)).find()) {
          return true;
        }

        if (input instanceof ComponentHost) {
          return ComponentQueries.matchesPattern((ComponentHost) input, pattern);
        }

        return false;
      }
    };
  }

  public static Predicate<View> hasVisibleMatchingText(final String text) {
    return Predicates.and(isVisible(), matchesText(text));
  }

  public static Predicate<View> isVisible() {
    return new Predicate<View>() {
      @Override
      public boolean apply(final View input) {
        return input.getVisibility() == View.VISIBLE;
      }
    };
  }

  @SuppressWarnings("unchecked")
  public static Predicate<View> isClass(final Class<? extends View> clazz) {
    return (Predicate<View>) (Predicate<?>) Predicates.instanceOf(clazz);
  }

  /** Tries to extract the description of a drawn drawable from a canvas */
  static String getDrawnDrawableDescription(final Drawable drawable) {
    final Canvas canvas = new Canvas();
    drawable.draw(canvas);
    final ShadowCanvas shadowCanvas = Shadows.shadowOf(canvas);
    return shadowCanvas.getDescription();
  }

  private static String extractString(final View view) {
    CharSequence text = null;
    if (view instanceof TextView) {
      text = ((TextView) view).getText();
    } else if (view instanceof RCTextView) {
      text = ((RCTextView) view).getText();
    }
    return text != null ? text.toString() : "";
  }

  public static Predicate<View> hasDrawable(final Drawable drawable) {
    return new Predicate<View>() {
      @Override
      public boolean apply(@Nullable final View input) {
        if (input == null) {
          return false;
        }
        if (input instanceof ComponentHost) {
          return ComponentQueries.hasDrawable((ComponentHost) input, drawable);
        }

        String drawnDrawableDescription =
            extractResourceIdFromDrawnDescription(getDrawnDrawableDescription(drawable));
        String drawnViewDescription =
            extractResourceIdFromDrawnDescription(getDrawnViewDescription(input));
        return !drawnDrawableDescription.isEmpty()
            && drawnViewDescription.contains(drawnDrawableDescription);
      }
    };
  }

  private static String extractResourceIdFromDrawnDescription(String description) {
    int resourceIdIndex = description.indexOf("resource:");
    if (resourceIdIndex == -1) {
      return description;
    }
    return description.substring(resourceIdIndex);
  }

  public static Predicate<View> hasVisibleDrawable(final Drawable drawable) {
    return Predicates.and(isVisible(), hasDrawable(drawable));
  }

  /**
   * @return A Predicate which is true if the view is visible and has the given id.
   */
  public static Predicate<View> hasVisibleId(final int viewId) {
    return Predicates.and(isVisible(), hasId(viewId));
  }

  /**
   * Tries to extract the description of a drawn view from a canvas
   *
   * <p>Since Robolectric can screw up {@link View#draw}, this uses reflection to call {@link
   * View#onDraw} and give you a canvas that has all the information drawn into it. This is useful
   * for asserting some view draws something specific to a canvas.
   *
   * @param view the view to draw
   */
  private static String getDrawnViewDescription(View view) {
    final Canvas canvas = new Canvas();
    view.draw(canvas);
    final ShadowCanvas shadowCanvas = Shadows.shadowOf(canvas);
    if (!shadowCanvas.getDescription().isEmpty()) {
      return shadowCanvas.getDescription();
    }

    try {
      Method onDraw = getOnDrawMethod(view.getClass());

      if (onDraw == null) {
        throw new RuntimeException(
            view.getClass().getCanonicalName()
                + " has no implementation of View.onDraw(), which should be impossible");
      }

      onDraw.invoke(view, canvas);
      final ShadowCanvas shadowCanvas2 = Shadows.shadowOf(canvas);
      return shadowCanvas2.getDescription();
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * {@link View#onDraw(Canvas)} is used by Robolectric to get the view description after rendering
   * the view. This method is protected, but we need a reference to it. We walk up the class
   * definition hierarchy to find where it's declared and return it.
   *
   * <p>{@link Class#getMethod()} does work recursively, but only on public methods and so doesn't
   * fit here.
   *
   * <p>see {@link #getDrawnViewDescription(View)}
   *
   * @param viewClass
   * @return the method instance, or null if no definition can be found
   */
  @Nullable
  private static Method getOnDrawMethod(Class<? extends View> viewClass) {
    if (viewClass == null) {
      return null;
    }

    try {
      Method onDraw = viewClass.getDeclaredMethod("onDraw", Canvas.class);
      onDraw.setAccessible(true);
      return onDraw;
    } catch (NoSuchMethodException e) {
      // swallow exception and recur
    }

    Class<?> superclass = viewClass.getSuperclass();
    if (superclass == null || !View.class.isAssignableFrom(superclass)) {
      return null;
    }
    return getOnDrawMethod((Class<? extends View>) superclass);
  }

  public static Predicate<View> hasId(final int id) {
    return new Predicate<View>() {
      @Override
      public boolean apply(final View input) {
        return input.getId() == id;
      }
    };
  }
}
