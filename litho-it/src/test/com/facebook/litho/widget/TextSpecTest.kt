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

package com.facebook.litho.widget

import android.R
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.text.Layout
import android.text.Spannable
import android.text.TextUtils
import android.text.style.ClickableSpan
import android.view.MotionEvent
import android.view.View
import androidx.core.text.TextDirectionHeuristicCompat
import androidx.core.text.TextDirectionHeuristicsCompat
import androidx.test.core.app.ApplicationProvider
import com.facebook.litho.ComponentContext
import com.facebook.litho.DynamicValue
import com.facebook.litho.LithoView
import com.facebook.litho.config.ComponentsConfiguration
import com.facebook.litho.testing.LithoTestRule
import com.facebook.litho.testing.eventhandler.EventHandlerTestHelper
import com.facebook.litho.testing.helper.ComponentTestHelper
import com.facebook.litho.testing.testrunner.LithoTestRunner
import com.facebook.litho.testing.unspecified
import com.facebook.litho.widget.TextComponentSpec.resolveWidth
import com.facebook.rendercore.text.TouchableSpanListener
import com.facebook.yoga.YogaDirection
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/** Tests [Text] component. */
@RunWith(LithoTestRunner::class)
class TextSpecTest {
  private var context: ComponentContext? = null

  @JvmField @Rule var lithoTestRule = LithoTestRule()

  @Before
  fun setup() {
    context = ComponentContext(ApplicationProvider.getApplicationContext<Context>())
  }

  private class TestMountableCharSequence : MountableCharSequence {
    var mountDrawable: Drawable? = null

    override fun onMount(parent: Drawable) {
      mountDrawable = parent
    }

    override fun onUnmount(parent: Drawable) = Unit

    override val length: Int
      get() = 0

    override fun get(index: Int): Char {
      return 0.toChar()
    }

    override fun subSequence(start: Int, end: Int): CharSequence = ""
  }

  @Test
  fun testTextWithoutClickableSpans() {
    val drawable = getMountedDrawableForText("Some text.")
    assertThat(drawable.clickableSpans).isNull()
  }

  @Test
  fun testSpannableWithoutClickableSpans() {
    val nonClickableText = Spannable.Factory.getInstance().newSpannable("Some text.")

    val drawable = getMountedDrawableForText(nonClickableText)
    assertThat(drawable.clickableSpans).isNotNull().hasSize(0)
  }

  @Test
  fun testSpannableWithClickableSpans() {
    val clickableText = Spannable.Factory.getInstance().newSpannable("Some text.")
    clickableText.setSpan(
        object : ClickableSpan() {
          override fun onClick(widget: View) = Unit
        },
        0,
        1,
        0)

    val drawable = getMountedDrawableForText(clickableText)
    assertThat(drawable.clickableSpans).isNotNull().hasSize(1)
  }

  @Test
  fun testSpannableWithClickableSpansGettingCancelEvent() {
    ComponentsConfiguration.enableNewHandleTouchForSpansMethod = true
    testCancelEventHandling(0f, 0f, false, false, true)
  }

  @Test
  fun testSpannableWithClickableSpansGettingCancelEventOutsideOfBoundsWithNullSpan() {
    ComponentsConfiguration.enableNewHandleTouchForSpansMethod = true
    testCancelEventHandling(90000f, 900000f, false, false, true)
  }

  @Test
  fun testSpannableWithClickableSpansGettingCancelEventWithinBoundsWithNullSpan() {
    ComponentsConfiguration.enableNewHandleTouchForSpansMethod = true
    testCancelEventHandling(50f, 50f, false, false, true)
  }

  @Test
  fun testSpannableWithClickableSpansGettingCancelEventWithinBoundsWithoutPreviouslyTouchedSpanWillReturnNullSpan() {
    ComponentsConfiguration.enableNewHandleTouchForSpansMethod = true
    testCancelEventHandling(0f, 0f, true, true, true)
  }

  private fun setupClickableSpanTest(
      text: String,
      eventsFired: ArrayList<Int>,
      touchableSpanListener: TouchableSpanListener
  ): LithoView {
    val clickableText = Spannable.Factory.getInstance().newSpannable(text)
    val clickableSpan: ClickableSpan =
        object : ClickableSpan() {
          override fun onClick(widget: View) {
            // No action
          }
        }
    clickableText.setSpan(clickableSpan, 0, 1, 0)

    val lithoView =
        ComponentTestHelper.mountComponent(
            context,
            Text.create(context)
                .text(clickableText)
                .touchableSpanListener(touchableSpanListener)
                .build())

    return lithoView
  }

  @Test
  fun testSpannableWithClickableSpansGettingUpEventAfterClickingOnSpanAndMovingAway() {
    ComponentsConfiguration.enableNewHandleTouchForSpansMethod = true
    val eventsFired = ArrayList<Int>()

    val touchableSpanListener =
        TouchableSpanListener { span: ClickableSpan?, motionEvent: MotionEvent, view: View? ->
          eventsFired.add(motionEvent.action)
          assertThat(span).isNotNull()
          true
        }

    val lithoView = setupClickableSpanTest("Some text.", eventsFired, touchableSpanListener)

    val textDrawable = lithoView.drawables[0] as TextDrawable
    // click on the span
    val downEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0f, 0f, 0)
    assertThat(textDrawable.onTouchEvent(downEvent, lithoView)).isEqualTo(true)
    assertThat(eventsFired.size).isEqualTo(1)
    assertThat(eventsFired).contains(MotionEvent.ACTION_DOWN)
    eventsFired.clear()
    // action up outside of the bounds
    val upEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP, 90000f, 90000f, 0)
    assertThat(textDrawable.onTouchEvent(upEvent, lithoView)).isEqualTo(false)
    assertThat(eventsFired.size).isEqualTo(0)
  }

  @Test
  fun testSpannableWithClickableSpansGettingUpEventAfterClickingOnSpanAndMovingWithinBounds() {
    ComponentsConfiguration.enableNewHandleTouchForSpansMethod = true
    val eventsFired = ArrayList<Int>()

    val touchableSpanListener =
        TouchableSpanListener { span: ClickableSpan?, motionEvent: MotionEvent, view: View? ->
          eventsFired.add(motionEvent.action)
          assertThat(span).isNotNull()
          true
        }

    val lithoView =
        setupClickableSpanTest(
            "Some text that is really long and has two spans.", eventsFired, touchableSpanListener)

    val textDrawable = lithoView.drawables[0] as TextDrawable
    // click on the span
    val downEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0f, 0f, 0)
    assertThat(textDrawable.onTouchEvent(downEvent, lithoView)).isEqualTo(true)
    assertThat(eventsFired.size).isEqualTo(1)
    assertThat(eventsFired).contains(MotionEvent.ACTION_DOWN)
    eventsFired.clear()
    // action up within the bounds but on different span
    val upEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP, 0f, 50f, 0)
    assertThat(textDrawable.onTouchEvent(upEvent, lithoView)).isEqualTo(true)
    assertThat(eventsFired.size).isEqualTo(1)
    assertThat(eventsFired).contains(MotionEvent.ACTION_UP)
  }

  @Test
  fun testSpannableWithClickableSpansGettingUpEventAfterClickingOnSpanAndMovingToOtherSpan() {
    ComponentsConfiguration.enableNewHandleTouchForSpansMethod = true
    val eventsFired = ArrayList<Int>()

    val clickableText = Spannable.Factory.getInstance().newSpannable("Some text.")
    val clickableSpan: ClickableSpan =
        object : ClickableSpan() {
          override fun onClick(widget: View) {
            // No action
          }
        }
    val clickableSpan2: ClickableSpan =
        object : ClickableSpan() {
          override fun onClick(widget: View) {
            // No action
          }
        }

    clickableText.setSpan(clickableSpan, 0, 1, 0)
    clickableText.setSpan(clickableSpan2, 2, clickableText.length, 0)

    val touchableSpanListener =
        TouchableSpanListener { span: ClickableSpan?, motionEvent: MotionEvent, view: View? ->
          eventsFired.add(motionEvent.action)
          assertThat(span).isEqualTo(clickableSpan)
          true
        }

    val lithoView =
        ComponentTestHelper.mountComponent(
            context,
            Text.create(context)
                .text(clickableText)
                .touchableSpanListener(touchableSpanListener)
                .build())

    val textDrawable = lithoView.drawables[0] as TextDrawable
    // click on the span
    val downEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0f, 0f, 0)
    assertThat(textDrawable.onTouchEvent(downEvent, lithoView)).isEqualTo(true)
    assertThat(eventsFired.size).isEqualTo(1)
    assertThat(eventsFired).contains(MotionEvent.ACTION_DOWN)
    eventsFired.clear()
    // action up within of the bounds
    val upEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP, 0f, 0f, 0)
    assertThat(textDrawable.onTouchEvent(upEvent, lithoView)).isEqualTo(true)
    assertThat(eventsFired.size).isEqualTo(1)
    assertThat(eventsFired).contains(MotionEvent.ACTION_UP)
  }

  @Test
  fun testSpannableWithClickableSpansGettingCancelEventAfterClickingOnSpanAndMovingWithinBounds() {
    ComponentsConfiguration.enableNewHandleTouchForSpansMethod = true
    val eventsFired = ArrayList<Int>()

    val touchableSpanListener =
        TouchableSpanListener { span: ClickableSpan?, motionEvent: MotionEvent, view: View? ->
          eventsFired.add(motionEvent.action)
          assertThat(span).isNotNull()
          true
        }

    val lithoView =
        setupClickableSpanTest(
            "Some text that is really long and has two spans.", eventsFired, touchableSpanListener)

    val textDrawable = lithoView.drawables[0] as TextDrawable
    // click on the span
    val downEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0f, 0f, 0)
    assertThat(textDrawable.onTouchEvent(downEvent, lithoView)).isEqualTo(true)
    assertThat(eventsFired.size).isEqualTo(1)
    assertThat(eventsFired).contains(MotionEvent.ACTION_DOWN)
    eventsFired.clear()
    // action cancel within the bounds but on different span
    val cancelEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0f, 50f, 0)
    assertThat(textDrawable.onTouchEvent(cancelEvent, lithoView)).isEqualTo(true)
    assertThat(eventsFired.size).isEqualTo(1)
    assertThat(eventsFired).contains(MotionEvent.ACTION_CANCEL)
  }

  @Test
  fun testSpannableWithClickableSpansGettingCancelEventAfterClickingOnSpanAndMovingOutsideOfBounds() {
    ComponentsConfiguration.enableNewHandleTouchForSpansMethod = true
    val eventsFired = ArrayList<Int>()

    val touchableSpanListener =
        TouchableSpanListener { span: ClickableSpan?, motionEvent: MotionEvent, view: View? ->
          eventsFired.add(motionEvent.action)
          assertThat(span).isNotNull()
          true
        }

    val lithoView =
        setupClickableSpanTest(
            "Some text that is really long and has two spans.", eventsFired, touchableSpanListener)

    val textDrawable = lithoView.drawables[0] as TextDrawable
    // click on the span
    val downEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0f, 0f, 0)
    assertThat(textDrawable.onTouchEvent(downEvent, lithoView)).isEqualTo(true)
    assertThat(eventsFired.size).isEqualTo(1)
    assertThat(eventsFired).contains(MotionEvent.ACTION_DOWN)
    eventsFired.clear()
    // action cancel outside of bounds
    val cancelEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 90000f, 90000f, 0)
    assertThat(textDrawable.onTouchEvent(cancelEvent, lithoView)).isEqualTo(false)
    assertThat(eventsFired.size).isEqualTo(1)
    assertThat(eventsFired).contains(MotionEvent.ACTION_CANCEL)
  }

  private fun testCancelEventHandling(
      x: Float,
      y: Float,
      expectedHandled: Boolean,
      expectedReturnValue: Boolean,
      spanShouldBeNull: Boolean
  ) {
    val cancelEventFired = booleanArrayOf(false)

    val clickableText = Spannable.Factory.getInstance().newSpannable("Some text.")
    val clickableSpan: ClickableSpan =
        object : ClickableSpan() {
          override fun onClick(widget: View) {
            // No action
          }
        }
    clickableText.setSpan(clickableSpan, 0, 1, 0)

    val touchableSpanListener =
        TouchableSpanListener { span: ClickableSpan?, motionEvent: MotionEvent, view: View? ->
          if (motionEvent.action == MotionEvent.ACTION_CANCEL) {
            cancelEventFired[0] = true
            if (spanShouldBeNull) {
              assertThat(span).isNull()
            } else {
              assertThat(span).isNotNull()
            }
          }
          expectedHandled
        }

    val lithoView =
        ComponentTestHelper.mountComponent(
            context,
            Text.create(context)
                .text(clickableText)
                .touchableSpanListener(touchableSpanListener)
                .build())

    val textDrawable = lithoView.drawables[0] as TextDrawable
    val cancelEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, x, y, 0)
    assertThat(textDrawable.onTouchEvent(cancelEvent, lithoView)).isEqualTo(expectedReturnValue)
    assertThat(cancelEventFired[0]).isTrue()
  }

  @Test(expected = IllegalStateException::class)
  @Throws(Exception::class)
  fun testTextIsRequired() {
    Text.create(context).build()
  }

  @Test
  fun testMountableCharSequenceText() {
    val testMountableCharSequence = TestMountableCharSequence()
    assertThat<Drawable?>(testMountableCharSequence.mountDrawable).isNull()
    val drawable = getMountedDrawableForText(testMountableCharSequence)
    assertThat<Drawable?>(testMountableCharSequence.mountDrawable).isSameAs(drawable)
  }

  @Test
  fun testTouchOffsetChangeHandlerFired() {
    val eventFired = booleanArrayOf(false)
    val eventHandler =
        EventHandlerTestHelper.createMockEventHandler<TextOffsetOnTouchEvent, Void>(
            TextOffsetOnTouchEvent::class.java) {
              eventFired[0] = true
              null
            }

    val lithoView =
        ComponentTestHelper.mountComponent(
            context,
            Text.create(context).text("Some text").textOffsetOnTouchHandler(eventHandler).build())
    val textDrawable = lithoView.drawables[0] as TextDrawable
    val motionEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0f, 0f, 0)
    val handled = textDrawable.onTouchEvent(motionEvent, lithoView)
    // We don't consume touch events from TextTouchOffsetChange event
    assertThat(handled).isFalse()
    assertThat(eventFired[0]).isTrue()
  }

  @Test
  fun testTouchOffsetChangeHandlerNotFired() {
    val eventFired = booleanArrayOf(false)
    val eventHandler =
        EventHandlerTestHelper.createMockEventHandler<TextOffsetOnTouchEvent, Void>(
            TextOffsetOnTouchEvent::class.java) {
              eventFired[0] = true
              null
            }

    val lithoView =
        ComponentTestHelper.mountComponent(
            context,
            Text.create(context).text("Text2").textOffsetOnTouchHandler(eventHandler).build())

    val textDrawable = lithoView.drawables[0] as TextDrawable

    val actionUp = MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP, 0f, 0f, 0)
    val handledActionUp = textDrawable.onTouchEvent(actionUp, lithoView)
    assertThat(handledActionUp).isFalse()
    assertThat(eventFired[0]).isFalse()

    val actionDown = MotionEvent.obtain(0, 0, MotionEvent.ACTION_MOVE, 0f, 0f, 0)
    val handledActionMove = textDrawable.onTouchEvent(actionDown, lithoView)
    assertThat(handledActionMove).isFalse()
    assertThat(eventFired[0]).isFalse()
  }

  @Test
  fun testColorDefault() {
    val drawable = getMountedDrawableForText("Some text")
    assertThat(drawable.color).isEqualTo(Color.BLACK)
  }

  @Test
  fun testColorOverride() {
    val states = arrayOf(intArrayOf(0))
    val colors = intArrayOf(Color.GREEN)
    val colorStateList = ColorStateList(states, colors)
    val drawable = getMountedDrawableForTextWithColors("Some text", Color.RED, colorStateList)
    assertThat(drawable.color).isEqualTo(Color.RED)
  }

  @Test
  fun testColor() {
    val drawable = getMountedDrawableForTextWithColors("Some text", Color.RED, null)
    assertThat(drawable.color).isEqualTo(Color.RED)
  }

  @Test
  fun testColorStateList() {
    val states = arrayOf(intArrayOf(0))
    val colors = intArrayOf(Color.GREEN)
    val colorStateList = ColorStateList(states, colors)
    val drawable = getMountedDrawableForTextWithColors("Some text", 0, colorStateList)
    assertThat(drawable.color).isEqualTo(Color.GREEN)
  }

  @Test
  fun testColorStateListMultipleStates() {
    val colorStateList =
        ColorStateList(
            arrayOf(
                intArrayOf(-R.attr.state_enabled), // disabled state
                intArrayOf()),
            intArrayOf(Color.RED, Color.GREEN))
    val drawable = getMountedDrawableForTextWithColors("Some text", 0, colorStateList)

    // color should fallback to default state
    assertThat(drawable.color).isEqualTo(Color.GREEN)
  }

  private fun getMountedDrawableForText(text: CharSequence): TextDrawable {
    return ComponentTestHelper.mountComponent(context, Text.create(context).text(text).build())
        .drawables[0]
        as TextDrawable
  }

  private fun getMountedDrawableForTextWithColors(
      text: CharSequence,
      color: Int,
      colorStateList: ColorStateList?
  ): TextDrawable {
    val builder = Text.create(context).text(text)
    if (color != 0) {
      builder.textColor(color)
    }
    if (colorStateList != null) {
      builder.textColorStateList(colorStateList)
    }
    return ComponentTestHelper.mountComponent(context, builder.build()).drawables[0] as TextDrawable
  }

  @Test
  fun testFullWidthText() {
    val layout = setupWidthTestTextLayout()

    val resolvedWidth =
        resolveWidth(
            unspecified(), layout, false, /* minimallyWide */ 0 /* minimallyWideThreshold */)

    assertThat(resolvedWidth.toLong()).isEqualTo(FULL_TEXT_WIDTH.toLong())
  }

  @Test
  fun testMinimallyWideText() {
    val layout = setupWidthTestTextLayout()

    val resolvedWidth =
        resolveWidth(
            unspecified(),
            layout,
            true, /* minimallyWide */
            FULL_TEXT_WIDTH - MINIMAL_TEXT_WIDTH - 1 /* minimallyWideThreshold */)

    assertThat(resolvedWidth.toLong()).isEqualTo(MINIMAL_TEXT_WIDTH.toLong())
  }

  @Test
  fun testMinimallyWideThresholdText() {
    val layout = setupWidthTestTextLayout()

    val resolvedWidth =
        resolveWidth(
            unspecified(),
            layout,
            true, /* minimallyWide */
            FULL_TEXT_WIDTH - MINIMAL_TEXT_WIDTH /* minimallyWideThreshold */)

    assertThat(resolvedWidth.toLong()).isEqualTo(FULL_TEXT_WIDTH.toLong())
  }

  @Test
  fun testTextAlignment_textStart() {
    assertThat(
            getMountedDrawableLayoutAlignment(
                "asdf", YogaDirection.LTR, null, TextAlignment.TEXT_START))
        .isEqualTo(Layout.Alignment.ALIGN_NORMAL)

    assertThat(
            getMountedDrawableLayoutAlignment(
                "asdf", YogaDirection.RTL, null, TextAlignment.TEXT_START))
        .isEqualTo(Layout.Alignment.ALIGN_NORMAL)

    // Layout.Alignment.ALIGN_NORMAL is mapped to TextAlignment.TEXT_START
    assertThat(
            getMountedDrawableLayoutAlignment(
                "asdf", YogaDirection.LTR, Layout.Alignment.ALIGN_NORMAL, null))
        .isEqualTo(Layout.Alignment.ALIGN_NORMAL)

    assertThat(
            getMountedDrawableLayoutAlignment(
                "asdf", YogaDirection.RTL, Layout.Alignment.ALIGN_NORMAL, null))
        .isEqualTo(Layout.Alignment.ALIGN_NORMAL)
  }

  @Test
  fun testTextAlignment_textEnd() {
    assertThat(
            getMountedDrawableLayoutAlignment(
                "asdf", YogaDirection.LTR, null, TextAlignment.TEXT_END))
        .isEqualTo(Layout.Alignment.ALIGN_OPPOSITE)

    assertThat(
            getMountedDrawableLayoutAlignment(
                "asdf", YogaDirection.RTL, null, TextAlignment.TEXT_END))
        .isEqualTo(Layout.Alignment.ALIGN_OPPOSITE)

    // Layout.Alignment.ALIGN_OPPOSITE is mapped to TextAlignment.TEXT_END
    assertThat(
            getMountedDrawableLayoutAlignment(
                "asdf", YogaDirection.LTR, Layout.Alignment.ALIGN_OPPOSITE, null))
        .isEqualTo(Layout.Alignment.ALIGN_OPPOSITE)

    assertThat(
            getMountedDrawableLayoutAlignment(
                "asdf", YogaDirection.RTL, Layout.Alignment.ALIGN_OPPOSITE, null))
        .isEqualTo(Layout.Alignment.ALIGN_OPPOSITE)
  }

  @Test
  fun testTextAlignment_center() {
    assertThat(
            getMountedDrawableLayoutAlignment(
                "asdf", YogaDirection.LTR, null, TextAlignment.CENTER))
        .isEqualTo(Layout.Alignment.ALIGN_CENTER)

    assertThat(
            getMountedDrawableLayoutAlignment(
                "asdf", YogaDirection.RTL, null, TextAlignment.CENTER))
        .isEqualTo(Layout.Alignment.ALIGN_CENTER)

    // Layout.Alignment.ALIGN_CENTER is mapped to TextAlignment.CENTER
    assertThat(
            getMountedDrawableLayoutAlignment(
                "asdf", YogaDirection.LTR, Layout.Alignment.ALIGN_CENTER, null))
        .isEqualTo(Layout.Alignment.ALIGN_CENTER)

    assertThat(
            getMountedDrawableLayoutAlignment(
                "asdf", YogaDirection.RTL, Layout.Alignment.ALIGN_CENTER, null))
        .isEqualTo(Layout.Alignment.ALIGN_CENTER)
  }

  @Test
  fun testTextAlignment_layoutStart() {
    assertThat(
            getMountedDrawableLayoutAlignment(
                "asdf", YogaDirection.LTR, null, TextAlignment.LAYOUT_START))
        .isEqualTo(Layout.Alignment.ALIGN_NORMAL)

    assertThat(
            getMountedDrawableLayoutAlignment(
                "asdf", YogaDirection.RTL, null, TextAlignment.LAYOUT_START))
        .isEqualTo(Layout.Alignment.ALIGN_OPPOSITE)

    assertThat(
            getMountedDrawableLayoutAlignment(
                ARABIC_RTL_TEST_STRING, YogaDirection.LTR, null, TextAlignment.LAYOUT_START))
        .isEqualTo(Layout.Alignment.ALIGN_OPPOSITE)

    assertThat(
            getMountedDrawableLayoutAlignment(
                ARABIC_RTL_TEST_STRING, YogaDirection.RTL, null, TextAlignment.LAYOUT_START))
        .isEqualTo(Layout.Alignment.ALIGN_NORMAL)
  }

  @Test
  fun testTextAlignment_layoutEnd() {
    assertThat(
            getMountedDrawableLayoutAlignment(
                "asdf", YogaDirection.LTR, null, TextAlignment.LAYOUT_END))
        .isEqualTo(Layout.Alignment.ALIGN_OPPOSITE)

    assertThat(
            getMountedDrawableLayoutAlignment(
                "asdf", YogaDirection.RTL, null, TextAlignment.LAYOUT_END))
        .isEqualTo(Layout.Alignment.ALIGN_NORMAL)

    assertThat(
            getMountedDrawableLayoutAlignment(
                ARABIC_RTL_TEST_STRING, YogaDirection.LTR, null, TextAlignment.LAYOUT_END))
        .isEqualTo(Layout.Alignment.ALIGN_NORMAL)

    assertThat(
            getMountedDrawableLayoutAlignment(
                ARABIC_RTL_TEST_STRING, YogaDirection.RTL, null, TextAlignment.LAYOUT_END))
        .isEqualTo(Layout.Alignment.ALIGN_OPPOSITE)
  }

  @Test
  fun testTextAlignment_left() {
    assertThat(
            getMountedDrawableLayoutAlignment("asdf", YogaDirection.LTR, null, TextAlignment.LEFT))
        .isEqualTo(Layout.Alignment.ALIGN_NORMAL)

    assertThat(
            getMountedDrawableLayoutAlignment("asdf", YogaDirection.RTL, null, TextAlignment.LEFT))
        .isEqualTo(Layout.Alignment.ALIGN_NORMAL)

    assertThat(
            getMountedDrawableLayoutAlignment(
                ARABIC_RTL_TEST_STRING, YogaDirection.LTR, null, TextAlignment.LEFT))
        .isEqualTo(Layout.Alignment.ALIGN_OPPOSITE)

    assertThat(
            getMountedDrawableLayoutAlignment(
                ARABIC_RTL_TEST_STRING, YogaDirection.RTL, null, TextAlignment.LEFT))
        .isEqualTo(Layout.Alignment.ALIGN_OPPOSITE)
  }

  @Test
  fun testTextAlignment_right() {
    assertThat(
            getMountedDrawableLayoutAlignment("asdf", YogaDirection.LTR, null, TextAlignment.RIGHT))
        .isEqualTo(Layout.Alignment.ALIGN_OPPOSITE)

    assertThat(
            getMountedDrawableLayoutAlignment("asdf", YogaDirection.RTL, null, TextAlignment.RIGHT))
        .isEqualTo(Layout.Alignment.ALIGN_OPPOSITE)

    assertThat(
            getMountedDrawableLayoutAlignment(
                ARABIC_RTL_TEST_STRING, YogaDirection.LTR, null, TextAlignment.RIGHT))
        .isEqualTo(Layout.Alignment.ALIGN_NORMAL)

    assertThat(
            getMountedDrawableLayoutAlignment(
                ARABIC_RTL_TEST_STRING, YogaDirection.RTL, null, TextAlignment.RIGHT))
        .isEqualTo(Layout.Alignment.ALIGN_NORMAL)
  }

  /* Test for LTR text aligned left. */
  @Test
  @Ignore("T146174263")
  fun testCustomEllipsisTextForLtrShortText() {
    val textDrawable =
        getMountedDrawableForTextWithMaxLines(
            "Simple\nSome second line",
            1,
            "Truncate",
            TextAlignment.LEFT,
            TextDirectionHeuristicsCompat.FIRSTSTRONG_LTR)

    assertThat(textDrawable.text.toString()).isEqualTo("SimpleTruncate")
  }

  @Test
  @Ignore("T146174263")
  fun testCustomEllipsisTextForLtrLongText() {
    val textDrawable =
        getMountedDrawableForTextWithMaxLines(
            """
            Simple sentence that should be quite long quite long quite long quite long quite long quite long quite long
            Some second line
            """
                .trimIndent(),
            1,
            "Truncate",
            TextAlignment.LEFT,
            TextDirectionHeuristicsCompat.FIRSTSTRONG_LTR)

    assertThat(textDrawable.text.toString())
        .isEqualTo(
            "Simple sentence that should be quite long quite long quite long quite long quite long" +
                " quiteTruncate")
  }

  @Test
  @Ignore("T146174263")
  fun testCustomEllipsisTextForLtrShortTextWithShortEllipsis() {
    val textDrawable =
        getMountedDrawableForTextWithMaxLines(
            "Simple\nSome second line",
            1,
            ".",
            TextAlignment.LEFT,
            TextDirectionHeuristicsCompat.FIRSTSTRONG_LTR)

    assertThat(textDrawable.text.toString()).isEqualTo("Simple.")
  }

  @Test
  @Ignore("T146174263")
  fun testCustomEllipsisTextForLtrLongTextWithShortEllipsis() {
    val textDrawable =
        getMountedDrawableForTextWithMaxLines(
            """
            Simple sentence that should be quite long quite long quite long quite long quite long quite long quite long
            Some second line
            """
                .trimIndent(),
            1,
            ".",
            TextAlignment.LEFT,
            TextDirectionHeuristicsCompat.FIRSTSTRONG_LTR)

    assertThat(textDrawable.text.toString())
        .isEqualTo(
            "Simple sentence that should be quite long quite long quite long quite long quite long" +
                " quite long q.")
  }

  /* Test for LTR text aligned right. */
  @Test
  @Ignore("T146174263")
  fun testCustomEllipsisTextForLtrShortTextAlignedRight() {
    val textDrawable =
        getMountedDrawableForTextWithMaxLines(
            "Simple\nSome second line",
            1,
            "Truncate",
            TextAlignment.RIGHT,
            TextDirectionHeuristicsCompat.FIRSTSTRONG_LTR)

    assertThat(textDrawable.text.toString()).isEqualTo("SimpleTruncate")
  }

  @Test
  @Ignore("T146174263")
  fun testCustomEllipsisTextForLtrLongTextAlignedRight() {
    val textDrawable =
        getMountedDrawableForTextWithMaxLines(
            """
            Simple sentence that should be quite long quite long quite long quite long quite long quite long quite long
            Some second line
            """
                .trimIndent(),
            1,
            "Truncate",
            TextAlignment.RIGHT,
            TextDirectionHeuristicsCompat.FIRSTSTRONG_LTR)

    assertThat(textDrawable.text.toString())
        .isEqualTo(
            "Simple sentence that should be quite long quite long quite long quite long quite long" +
                " quiteTruncate")
  }

  @Test
  @Ignore("T146174263")
  fun testCustomEllipsisTextForLtrShortTextWithShortEllipsisAlignedRight() {
    val textDrawable =
        getMountedDrawableForTextWithMaxLines(
            "Simple\nSome second line",
            1,
            ".",
            TextAlignment.RIGHT,
            TextDirectionHeuristicsCompat.FIRSTSTRONG_LTR)

    assertThat(textDrawable.text.toString()).isEqualTo("Simple.")
  }

  @Test
  @Ignore("T146174263")
  fun testCustomEllipsisTextForLtrLongTextWithShortEllipsisAlignedRight() {
    val textDrawable =
        getMountedDrawableForTextWithMaxLines(
            """
            Simple sentence that should be quite long quite long quite long quite long quite long quite long quite long
            Some second line
            """
                .trimIndent(),
            1,
            ".",
            TextAlignment.RIGHT,
            TextDirectionHeuristicsCompat.FIRSTSTRONG_LTR)

    assertThat(textDrawable.text.toString())
        .isEqualTo(
            "Simple sentence that should be quite long quite long quite long quite long quite long" +
                " quite long q.")
  }

  @Test
  fun whenDynamicTextColorIsChanged_TextColorShouldUpdateWithoutReRendering() {
    val textColor = DynamicValue(Color.BLUE)

    val lithoView =
        lithoTestRule
            .render { Text.create(context).text("hello world").dynamicTextColor(textColor).build() }
            .lithoView

    val content = lithoView.getMountItemAt(0).content
    assertThat(content).isInstanceOf(TextDrawable::class.java)
    assertThat((content as TextDrawable).layout?.paint?.color).isEqualTo(Color.BLUE)

    textColor.set(Color.GREEN)
    assertThat(content.layout?.paint?.color).isEqualTo(Color.GREEN)

    lithoView.componentTree = null

    assertThat(content.layout).isNull()
  }

  private fun getMountedDrawableForTextWithMaxLines(
      text: CharSequence,
      maxLines: Int,
      customEllipsisText: String,
      alignment: TextAlignment,
      textDirection: TextDirectionHeuristicCompat
  ): TextDrawable {
    return ComponentTestHelper.mountComponent(
            context,
            Text.create(context)
                .ellipsize(TextUtils.TruncateAt.END)
                .textDirection(textDirection)
                .text(text)
                .alignment(alignment)
                .maxLines(maxLines)
                .customEllipsisText(customEllipsisText)
                .build())
        .drawables[0]
        as TextDrawable
  }

  private fun getMountedDrawableLayoutAlignment(
      text: String,
      layoutDirection: YogaDirection?,
      deprecatedTextAlignment: Layout.Alignment?,
      textAlignment: TextAlignment?
  ): Layout.Alignment? {
    val builder = Text.create(context).text(text)

    if (layoutDirection != null) {
      builder.layoutDirection(layoutDirection)
    }

    if (deprecatedTextAlignment != null) {
      builder.textAlignment(deprecatedTextAlignment)
    }

    if (textAlignment != null) {
      builder.alignment(textAlignment)
    }

    return (ComponentTestHelper.mountComponent(context, builder.build()).drawables[0]
            as TextDrawable)
        .layoutAlignment
  }

  companion object {
    private const val FULL_TEXT_WIDTH = 100
    private const val MINIMAL_TEXT_WIDTH = 95
    private const val ARABIC_RTL_TEST_STRING =
        ("\u0645\u0646 \u0627\u0644\u064A\u0645\u064A\u0646 \u0627\u0644\u0649" +
            " \u0627\u0644\u064A\u0633\u0627\u0631")

    private fun setupWidthTestTextLayout(): Layout {
      val layout: Layout = mock()

      whenever(layout.lineCount).thenReturn(2)
      whenever(layout.width).thenReturn(FULL_TEXT_WIDTH)
      whenever(layout.getLineRight(ArgumentMatchers.anyInt()))
          .thenReturn(MINIMAL_TEXT_WIDTH.toFloat())
      whenever(layout.getLineLeft(ArgumentMatchers.anyInt())).thenReturn(0.0f)

      return layout
    }
  }
}
