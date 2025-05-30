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

import android.annotation.TargetApi
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.util.SparseArray
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.facebook.litho.animated.alpha
import com.facebook.litho.config.ComponentsConfiguration
import com.facebook.litho.core.height
import com.facebook.litho.core.width
import com.facebook.litho.testing.LithoTestRule
import com.facebook.litho.testing.helper.ComponentTestHelper
import com.facebook.litho.testing.testrunner.LithoTestRunner
import com.facebook.litho.view.backgroundColor
import com.facebook.litho.widget.DynamicPropsResetValueTester
import com.facebook.litho.widget.DynamicPropsResetValueTesterSpec
import com.facebook.rendercore.MountItem
import com.facebook.rendercore.primitives.ExactSizeConstraintsLayoutBehavior
import com.facebook.rendercore.primitives.ViewAllocator
import com.facebook.rendercore.px
import com.facebook.yoga.YogaAlign
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@LooperMode(LooperMode.Mode.LEGACY)
@RunWith(LithoTestRunner::class)
class DynamicPropsTest {

  private lateinit var context: ComponentContext

  val config =
      ComponentsConfiguration.defaultInstance.copy(shouldAddHostViewForRootComponent = true)

  @JvmField @Rule val lithoTestRule = LithoTestRule(config)

  @Before
  fun setup() {
    context = ComponentContext(ApplicationProvider.getApplicationContext<Context>())
  }

  @Test
  fun testDynamicAlphaApplied() {
    val startValue = 0.8f
    val alphaDV = DynamicValue(startValue)
    val testLithoView =
        lithoTestRule.render {
          Column.create(context).widthPx(80).heightPx(80).alpha(alphaDV).build()
        }
    assertThat(testLithoView.lithoView.childCount).isEqualTo(1)
    val hostView = testLithoView.lithoView.getChildAt(0)
    assertThat(hostView.alpha).isEqualTo(startValue)
    alphaDV.set(0.5f)
    assertThat(hostView.alpha).isEqualTo(0.5f)
    alphaDV.set(0f)
    assertThat(hostView.alpha).isEqualTo(0f)
    alphaDV.set(1f)
    assertThat(hostView.alpha).isEqualTo(1f)
  }

  @Test
  fun testAttributesAndDynamicPropDuringUpdate() {
    val startValue = 0.8f
    val alphaDV = DynamicValue(startValue)
    val component1 =
        Column.create(context)
            .widthPx(80)
            .heightPx(80)
            .backgroundColor(Color.GREEN)
            .alpha(alphaDV)
            .build()
    val component2 =
        Column.create(context)
            .widthPx(80)
            .heightPx(80)
            .backgroundColor(Color.MAGENTA)
            .alpha(alphaDV)
            .build()
    val testLithoView = lithoTestRule.render(widthPx = 80, heightPx = 80) { component1 }
    val lithoView = testLithoView.lithoView

    // Ensure we have one view.
    assertThat(lithoView.childCount).isEqualTo(1)
    var hostView = lithoView.getChildAt(0)

    // Ensure alpha DV is correct
    assertThat(hostView.alpha).isEqualTo(startValue)

    // Ensure background attribute is present and has the correct value.
    assertThat(hostView.background).isNotNull
    assertThat((hostView.background as ColorDrawable).color).isEqualTo(Color.GREEN)

    // Mount component2, which is identical to component1, except with a different bg, invoking
    // an update sequence.
    testLithoView.setRoot(component2)

    // Grab the host again
    hostView = lithoView.getChildAt(0)

    // Alter the alpha DV
    alphaDV.set(0.5f)

    // Ensure the DV is properly applied on the view
    assertThat(hostView.alpha).isEqualTo(0.5f)

    // Ensure background attribute is present and has the correct value.
    assertThat(hostView.background).isNotNull
    assertThat((hostView.background as ColorDrawable).color).isEqualTo(Color.MAGENTA)
  }

  @Test
  fun testDynamicTranslationApplied() {
    val startValueX = 100f
    val startValueY = -100f
    val translationXDV = DynamicValue(startValueX)
    val translationYDV = DynamicValue(startValueY)
    val testLithoView =
        lithoTestRule.render {
          Column.create(context)
              .widthPx(80)
              .heightPx(80)
              .translationX(translationXDV)
              .translationY(translationYDV)
              .build()
        }
    assertThat(testLithoView.lithoView.childCount).isEqualTo(1)
    val hostView = testLithoView.lithoView.getChildAt(0)
    assertThat(hostView.translationX).isEqualTo(startValueX)
    assertThat(hostView.translationY).isEqualTo(startValueY)
    translationXDV.set(50f)
    translationYDV.set(20f)
    assertThat(hostView.translationX).isEqualTo(50f)
    assertThat(hostView.translationY).isEqualTo(20f)
    translationXDV.set(-50f)
    translationYDV.set(-20f)
    assertThat(hostView.translationX).isEqualTo(-50f)
    assertThat(hostView.translationY).isEqualTo(-20f)
    translationXDV.set(0f)
    translationYDV.set(0f)
    assertThat(hostView.translationX).isEqualTo(0f)
    assertThat(hostView.translationY).isEqualTo(0f)
  }

  @Test
  fun testDynamicScaleApplied() {
    val startValueX = 1.5f
    val startValueY = -1.5f
    val scaleXDV = DynamicValue(startValueX)
    val scaleYDV = DynamicValue(startValueY)
    val testLithoView =
        lithoTestRule.render {
          Column.create(context).widthPx(80).heightPx(80).scaleX(scaleXDV).scaleY(scaleYDV).build()
        }
    assertThat(testLithoView.lithoView.childCount).isEqualTo(1)
    val hostView = testLithoView.lithoView.getChildAt(0)
    assertThat(hostView.scaleX).isEqualTo(startValueX)
    assertThat(hostView.scaleY).isEqualTo(startValueY)
    scaleXDV.set(0.5f)
    scaleYDV.set(2f)
    assertThat(hostView.scaleX).isEqualTo(0.5f)
    assertThat(hostView.scaleY).isEqualTo(2f)
    scaleXDV.set(2f)
    scaleYDV.set(0.5f)
    assertThat(hostView.scaleX).isEqualTo(2f)
    assertThat(hostView.scaleY).isEqualTo(.5f)
    scaleXDV.set(0f)
    scaleYDV.set(0f)
    assertThat(hostView.scaleX).isEqualTo(0f)
    assertThat(hostView.scaleY).isEqualTo(0f)
  }

  @Test
  fun testDynamicBackgroundColorApplied() {
    val startValue = Color.RED
    val backgroundColorDV = DynamicValue(startValue)
    val testLithoView =
        lithoTestRule.render {
          Column.create(context).widthPx(80).heightPx(80).backgroundColor(backgroundColorDV).build()
        }
    assertThat(testLithoView.lithoView.childCount).isEqualTo(1)
    val hostView = testLithoView.lithoView.getChildAt(0)
    assertThat(hostView.background).isInstanceOf(ColorDrawable::class.java)
    assertThat((hostView.background as ColorDrawable).color).isEqualTo(startValue)
    backgroundColorDV.set(Color.BLUE)
    assertThat((hostView.background as ColorDrawable).color).isEqualTo(Color.BLUE)
    backgroundColorDV.set(Color.GRAY)
    assertThat((hostView.background as ColorDrawable).color).isEqualTo(Color.GRAY)
    backgroundColorDV.set(Color.TRANSPARENT)
    assertThat((hostView.background as ColorDrawable).color).isEqualTo(Color.TRANSPARENT)
  }

  @Test
  fun testDynamicRotationApplied() {
    val startValue = 1f
    val rotation = DynamicValue(startValue)
    val rotationX = DynamicValue(startValue)
    val rotationY = DynamicValue(startValue)
    val testLithoView =
        lithoTestRule.render {
          Column.create(context)
              .widthPx(80)
              .heightPx(80)
              .rotation(rotation)
              .rotationX(rotationX)
              .rotationY(rotationY)
              .build()
        }
    assertThat(testLithoView.lithoView.childCount).isEqualTo(1)
    val hostView = testLithoView.lithoView.getChildAt(0)
    assertThat(hostView.rotation).isEqualTo(startValue)
    assertThat(hostView.rotationX).isEqualTo(startValue)
    assertThat(hostView.rotationY).isEqualTo(startValue)
    rotation.set(364f)
    rotationX.set(10f)
    rotationY.set(100f)
    assertThat(hostView.rotation).isEqualTo(364f)
    assertThat(hostView.rotationX).isEqualTo(10f)
    assertThat(hostView.rotationY).isEqualTo(100f)
    rotation.set(-1f)
    rotationX.set(-10f)
    rotationY.set(-100f)
    assertThat(hostView.rotation).isEqualTo(-1f)
    assertThat(hostView.rotationX).isEqualTo(-10f)
    assertThat(hostView.rotationY).isEqualTo(-100f)

    testLithoView.lithoView.unmountAllItems()
    assertThat(hostView.rotation).isEqualTo(0f)
    assertThat(hostView.rotationX).isEqualTo(0f)
    assertThat(hostView.rotationY).isEqualTo(0f)
  }

  @Test
  fun testNullDynamicValue() {
    val nullIntegerValue: DynamicValue<Int>? = null
    val nullFloatValue: DynamicValue<Float>? = null
    val lithoView =
        ComponentTestHelper.mountComponent(
            context,
            Column.create(context)
                .widthPx(80)
                .heightPx(80)
                .backgroundColor(nullIntegerValue)
                .rotation(nullFloatValue)
                .build())
    assertThat(lithoView.background).isEqualTo(null)
    assertThat(lithoView.rotation).isEqualTo(0.0f)
  }

  private class DynamicElevationBuilder(
      c: ComponentContext,
      defStyleAttr: Int,
      defStyleRes: Int,
      private var _component: SpecGeneratedComponent
  ) : Component.Builder<DynamicElevationBuilder?>(c, defStyleAttr, defStyleRes, _component) {
    override fun build(): SpecGeneratedComponent = _component

    override fun getThis(): DynamicElevationBuilder = this

    override fun setComponent(component: Component) {
      this._component = component as SpecGeneratedComponent
    }
  }

  @Test
  @Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  fun testDynamicElevationApplied() {
    val lithoView = LithoView(context)
    val startValue = 1f
    val elevationDV = DynamicValue(startValue)
    val component =
        DynamicElevationBuilder(
                context,
                -1,
                -1,
                object : SpecGeneratedComponent("DynamicElevationTestComponent") {
                  override fun getMountType(): MountType = MountType.VIEW
                })
            .shadowElevation(elevationDV)
            .build()
    val dynamicPropsManager = DynamicPropsManager()
    dynamicPropsManager.onBindComponentToContent(
        component,
        context,
        component.commonDynamicProps as SparseArray<out DynamicValue<Any?>>,
        lithoView)
    assertThat(lithoView.elevation).isEqualTo(startValue)
    elevationDV.set(50f)
    assertThat(lithoView.elevation).isEqualTo(50f)
    elevationDV.set(-50f)
    assertThat(lithoView.elevation).isEqualTo(-50f)
  }

  @Test
  fun testDynamicTranslationZApplied() {
    val startValue = 100f
    val translationZDV = DynamicValue(startValue)
    val testLithoView =
        lithoTestRule.render {
          Column.create(context).widthPx(80).heightPx(80).translationZ(translationZDV).build()
        }
    assertThat(testLithoView.lithoView.childCount).isEqualTo(1)
    val hostView = testLithoView.lithoView.getChildAt(0)
    assertThat(hostView.translationZ).isEqualTo(startValue)
    translationZDV.set(25f)
    assertThat(hostView.translationZ).isEqualTo(25f)

    testLithoView.lithoView.unmountAllItems()
    assertThat(hostView.translationZ).isEqualTo(0f)
  }

  @Test
  fun commonDynamicProps_unbindAndRebindContent_resetValues() {
    val stateUpdateCaller = DynamicPropsResetValueTesterSpec.Caller()
    val component = DynamicPropsResetValueTester.create(context).caller(stateUpdateCaller).build()
    val testLithoView = lithoTestRule.render { component }
    val mountDelegateTarget = testLithoView.lithoView.mountDelegateTarget
    var text1HostId: Long = -1
    var text2HostId: Long = -1
    for (i in 0 until mountDelegateTarget.getMountItemCount()) {
      val mountItem = mountDelegateTarget.getMountItemAt(i)
      if (mountItem != null) {
        val unit = LithoRenderUnit.getRenderUnit(mountItem)
        if (unit.component.simpleName == "TextComponent") {
          val hostMarker = if (i != 0) mountItem.renderTreeNode.parent?.renderUnit?.id ?: -1 else -1
          if (text1HostId == -1L) {
            text1HostId = hostMarker
          } else if (text2HostId == -1L) {
            text2HostId = hostMarker
          }
        }
      }
    }
    lateinit var text1HostComponent: HostComponent
    lateinit var text2HostComponent: HostComponent
    lateinit var text1Host: ComponentHost
    lateinit var text2Host: ComponentHost
    for (i in 0 until mountDelegateTarget.getMountItemCount()) {
      val mountItem = mountDelegateTarget.getMountItemAt(i)
      if (mountItem != null) {
        val unit = LithoRenderUnit.getRenderUnit(mountItem)
        if (text1HostId == MountItem.getId(mountItem)) {
          text1HostComponent = unit.component as HostComponent
          text1Host = mountItem.content as ComponentHost
        }
        if (text2HostId == MountItem.getId(mountItem)) {
          text2HostComponent = unit.component as HostComponent
          text2Host = mountItem.content as ComponentHost
        }
      }
    }
    assertThat(text1HostComponent.hasCommonDynamicProps()).isTrue
    assertThat(text1Host.alpha).isEqualTo(DynamicPropsResetValueTesterSpec.ALPHA_TRANSPARENT)
    assertThat(text2HostComponent.hasCommonDynamicProps()).isFalse
    assertThat(text2Host.alpha).isEqualTo(DynamicPropsResetValueTesterSpec.ALPHA_OPAQUE)
    stateUpdateCaller.toggleShowChild()
    lateinit var stateUpdateText1HostComponent: HostComponent
    lateinit var stateUpdateText2HostComponent: HostComponent
    lateinit var stateUpdateText1Host: ComponentHost
    lateinit var stateUpdateText2Host: ComponentHost
    for (i in 0 until mountDelegateTarget.getMountItemCount()) {
      val mountItem = mountDelegateTarget.getMountItemAt(i)
      if (mountItem != null) {
        val unit = LithoRenderUnit.getRenderUnit(mountItem)
        if (text1HostId == MountItem.getId(mountItem)) {
          stateUpdateText1HostComponent = unit.component as HostComponent
          stateUpdateText1Host = mountItem.content as ComponentHost
        }
        if (text2HostId == MountItem.getId(mountItem)) {
          stateUpdateText2HostComponent = unit.component as HostComponent
          stateUpdateText2Host = mountItem.content as ComponentHost
        }
      }
    }
    assertThat(stateUpdateText1Host).isEqualTo(text1Host)
    assertThat(stateUpdateText2Host).isEqualTo(text2Host)
    assertThat(stateUpdateText1HostComponent.hasCommonDynamicProps()).isFalse
    assertThat(stateUpdateText1Host.alpha).isEqualTo(DynamicPropsResetValueTesterSpec.ALPHA_OPAQUE)
    assertThat(stateUpdateText2HostComponent.hasCommonDynamicProps()).isFalse
    assertThat(stateUpdateText2Host.alpha).isEqualTo(DynamicPropsResetValueTesterSpec.ALPHA_OPAQUE)
  }

  @Test
  fun testDynamicPropsAddedToSpecGeneratedComponentUsingWrapper() {
    val alphaDV = DynamicValue(0.5f)
    val testLithoview =
        lithoTestRule.render {
          Wrapper.create(context)
              .delegate(Row.create(context).widthPx(80).heightPx(80).build())
              .kotlinStyle(Style.alpha(alphaDV))
              .build()
        }
    assertThat(testLithoview.lithoView.childCount).isEqualTo(1)
    val hostView = testLithoview.lithoView.getChildAt(0)
    assertThat(hostView.alpha).isEqualTo(0.5f)
    alphaDV.set(1f)
    assertThat(hostView.alpha).isEqualTo(1f)
  }

  @Test
  fun testDynamicPropsAddedToPrimitiveComponentUsingWrapper() {
    val alphaDV = DynamicValue(0.5f)
    val testLithoview =
        lithoTestRule.render {
          Wrapper.create(context)
              .delegate(SimpleTestPrimitiveComponent(style = Style.width(80.px).height(80.px)))
              .kotlinStyle(Style.alpha(alphaDV))
              .build()
        }
    assertThat(testLithoview.lithoView.childCount).isEqualTo(1)
    val hostView = testLithoview.lithoView.getChildAt(0)
    assertThat(hostView.alpha).isEqualTo(0.5f)
    alphaDV.set(1f)
    assertThat(hostView.alpha).isEqualTo(1f)
  }

  @Test
  fun `verify that dynamic value outputs is empty when there's no dynamic values specified`() {
    val component1 =
        Wrapper.create(context)
            .delegate(
                Row.create(context)
                    .alignContent(YogaAlign.CENTER)
                    .widthPx(80)
                    .heightPx(80)
                    .backgroundColor(Color.RED)
                    .build())
            .build()
    val component2 =
        Column.create(context)
            .child(
                Wrapper.create(context)
                    .delegate(
                        Row.create(context)
                            .alignContent(YogaAlign.STRETCH)
                            .widthPx(80)
                            .heightPx(80)
                            .backgroundColor(Color.RED)
                            .build()))
            .build()

    val testLithoView =
        lithoTestRule.render(widthPx = 80, heightPx = 80) {
          Column.create(context).child(component1).child(component2).build()
        }
    val lithoView = testLithoView.lithoView

    assertThat(lithoView.componentTree!!.committedLayoutState!!.dynamicValueOutputs)
        .describedAs("We should not collect empty dynamic values output for each component")
        .isEmpty()
  }

  @Test
  fun testCommonPropsDontDisappearAfterUpdateFromSpecGeneratedComponentUsingWrapperWithDynamicProps() {
    val alphaDV = DynamicValue(0.5f)
    val component1 =
        Wrapper.create(context)
            .delegate(
                Row.create(context)
                    .alignContent(YogaAlign.CENTER)
                    .widthPx(80)
                    .heightPx(80)
                    .backgroundColor(Color.RED)
                    .build())
            .kotlinStyle(Style.alpha(alphaDV))
            .build()
    val component2 =
        Wrapper.create(context)
            .delegate(
                Row.create(context)
                    .alignContent(YogaAlign.STRETCH)
                    .widthPx(80)
                    .heightPx(80)
                    .backgroundColor(Color.RED)
                    .build())
            .kotlinStyle(Style.alpha(alphaDV))
            .build()
    val testLithoView = lithoTestRule.render(widthPx = 80, heightPx = 80) { component1 }
    val lithoView = testLithoView.lithoView

    // Ensure we have one view.
    assertThat(lithoView.childCount).isEqualTo(1)
    var hostView = lithoView.getChildAt(0)

    // Ensure alpha DV is correct
    assertThat(hostView.alpha).isEqualTo(0.5f)

    // Ensure background attribute is present and has the correct value.
    assertThat(hostView.background).isNotNull
    assertThat((hostView.background as ColorDrawable).color).isEqualTo(Color.RED)

    // Mount component2, which is identical to component1, except with a different yoga align,
    // invoking an update sequence.
    testLithoView.setRoot(component2)

    // Grab the host again
    hostView = lithoView.getChildAt(0)

    // Ensure the DV is properly applied on the view
    assertThat(hostView.alpha).isEqualTo(0.5f)

    // Ensure background attribute is present and has the correct value.
    assertThat(hostView.background).isNotNull
    assertThat((hostView.background as ColorDrawable).color).isEqualTo(Color.RED)
  }

  @Test
  fun testCommonPropsDontDisappearAfterUpdateFromPrimitiveComponentUsingWrapperWithDynamicProps() {
    val alphaDV = DynamicValue(0.5f)
    val component1 =
        Wrapper.create(context)
            .delegate(
                SimpleTestPrimitiveComponent(
                    contentDescription = "A",
                    style = Style.width(80.px).height(80.px).backgroundColor(Color.RED)))
            .kotlinStyle(Style.alpha(alphaDV))
            .build()
    val component2 =
        Wrapper.create(context)
            .delegate(
                SimpleTestPrimitiveComponent(
                    contentDescription = "B",
                    style = Style.width(80.px).height(80.px).backgroundColor(Color.RED)))
            .kotlinStyle(Style.alpha(alphaDV))
            .build()
    val testLithoView = lithoTestRule.render(widthPx = 80, heightPx = 80) { component1 }
    val lithoView = testLithoView.lithoView

    // Ensure we have one view.
    assertThat(lithoView.childCount).isEqualTo(1)
    var hostView = lithoView.getChildAt(0)

    // Ensure alpha DV is correct
    assertThat(hostView.alpha).isEqualTo(0.5f)

    // Ensure background attribute is present and has the correct value.
    assertThat(hostView.background).isNotNull
    assertThat((hostView.background as ColorDrawable).color).isEqualTo(Color.RED)

    // Mount component2, which is identical to component1, except with a different content
    // description, invoking an update sequence.
    testLithoView.setRoot(component2)

    // Grab the host again
    hostView = lithoView.getChildAt(0)

    // Ensure the DV is properly applied on the view
    assertThat(hostView.alpha).isEqualTo(0.5f)

    // Ensure background attribute is present and has the correct value.
    assertThat(hostView.background).isNotNull
    assertThat((hostView.background as ColorDrawable).color).isEqualTo(Color.RED)
  }

  @Test
  fun testPrimitiveWithDynamicValueIsCorrectlyUnsubscribed() {
    val alphaDV1 = DynamicValue(1f)
    val testLithoView =
        lithoTestRule.render {
          SimpleTestPrimitiveComponent(style = Style.width(80.px).height(80.px).alpha(alphaDV1))
        }
    lithoTestRule.idle()

    assertThat(alphaDV1.numberOfListeners).isEqualTo(1)

    // simulate update
    val alphaDV2 = DynamicValue(1f)
    testLithoView
        .setRoot(
            SimpleTestPrimitiveComponent(style = Style.width(80.px).height(80.px).alpha(alphaDV2)))
        .measure()
        .layout()
    lithoTestRule.idle()

    // should unsubscribe from the old DV and subscribe to the new DV
    assertThat(alphaDV1.numberOfListeners).isEqualTo(0)
    assertThat(alphaDV2.numberOfListeners).isEqualTo(1)

    testLithoView.lithoView.unmountAllItems()
    lithoTestRule.idle()

    // should unsubscribe from all DVs
    assertThat(alphaDV1.numberOfListeners).isEqualTo(0)

    assertThat(alphaDV2.numberOfListeners).isEqualTo(0)
  }

  @Test
  fun testWrappedPrimitiveWithDynamicValueIsCorrectlyUnsubscribed() {
    val alphaDV1 = DynamicValue(1f)
    val testLithoView =
        lithoTestRule.render {
          Wrapper.create(context)
              .delegate(SimpleTestPrimitiveComponent(style = Style.width(80.px).height(80.px)))
              .kotlinStyle(Style.alpha(alphaDV1))
              .build()
        }
    lithoTestRule.idle()

    assertThat(alphaDV1.numberOfListeners).isEqualTo(1)

    // simulate update
    val alphaDV2 = DynamicValue(1f)
    testLithoView
        .setRoot(
            Wrapper.create(context)
                .delegate(SimpleTestPrimitiveComponent(style = Style.width(80.px).height(80.px)))
                .kotlinStyle(Style.alpha(alphaDV2))
                .build())
        .measure()
        .layout()
    lithoTestRule.idle()

    // should unsubscribe from the old DV and subscribe to the new DV
    assertThat(alphaDV1.numberOfListeners).isEqualTo(0)
    assertThat(alphaDV2.numberOfListeners).isEqualTo(1)

    testLithoView.lithoView.unmountAllItems()
    lithoTestRule.idle()

    // should unsubscribe from all DVs
    assertThat(alphaDV1.numberOfListeners).isEqualTo(0)

    assertThat(alphaDV2.numberOfListeners).isEqualTo(0)
  }

  @Test
  fun testDynamicValueIsCorrectlyUnsubscribedForIncrementalMount() {
    val alphaDV1 = DynamicValue(1f)
    val alphaDV2 = DynamicValue(1f)
    val component =
        object : KComponent() {
          override fun ComponentScope.render(): Component? {
            return Column {
              child(
                  SimpleTestPrimitiveComponent(
                      style = Style.width(10.px).height(10.px).alpha(alphaDV1)))
              child(Row(style = Style.width(10.px).height(10.px).alpha(alphaDV2)) {})
            }
          }
        }
    val testLithoView = lithoTestRule.render(widthPx = 1000, heightPx = 1000) { component }
    val lithoView = testLithoView.lithoView

    // mount rect that is above both components so none of them is visible
    lithoView.mountComponent(Rect(0, -50, 10, -20), true)
    assertThat(alphaDV1.numberOfListeners).isEqualTo(0)
    assertThat(alphaDV2.numberOfListeners).isEqualTo(0)

    // mount that covers only the first of two components
    lithoView.mountComponent(Rect(0, 0, 10, 5), true)
    assertThat(alphaDV1.numberOfListeners).isEqualTo(1)
    assertThat(alphaDV2.numberOfListeners).isEqualTo(0)

    // mount rect that covers both components
    lithoView.mountComponent(Rect(0, 5, 10, 15), true)
    assertThat(alphaDV1.numberOfListeners).isEqualTo(1)
    assertThat(alphaDV2.numberOfListeners).isEqualTo(1)

    // mount rect that covers only the second of two components
    lithoView.mountComponent(Rect(0, 15, 10, 20), true)
    assertThat(alphaDV1.numberOfListeners).isEqualTo(0)
    assertThat(alphaDV2.numberOfListeners).isEqualTo(1)

    // mount rect that is below both components so none of them is visible
    lithoView.mountComponent(Rect(0, 40, 10, 50), true)
    assertThat(alphaDV1.numberOfListeners).isEqualTo(0)
    assertThat(alphaDV2.numberOfListeners).isEqualTo(0)
  }
}

private class SimpleTestPrimitiveComponent(
    private val contentDescription: String? = null,
    private val style: Style? = null,
) : PrimitiveComponent() {
  override fun PrimitiveComponentScope.render(): LithoPrimitive {
    return LithoPrimitive(
        layoutBehavior = ExactSizeConstraintsLayoutBehavior,
        mountBehavior =
            MountBehavior(ViewAllocator { context -> View(context) }) {
              bind(contentDescription) { content ->
                content.contentDescription = contentDescription
                onUnbind { content.contentDescription = null }
              }
            },
        style)
  }
}
