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

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.facebook.litho.SizeSpec.EXACTLY
import com.facebook.litho.SizeSpec.makeSizeSpec
import com.facebook.litho.config.ComponentsConfiguration
import com.facebook.litho.testing.ThreadTestingUtils
import com.facebook.litho.testing.Whitebox
import com.facebook.litho.testing.testrunner.LithoTestRunner
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.robolectric.Shadows
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowLooper

@LooperMode(LooperMode.Mode.LEGACY)
@RunWith(LithoTestRunner::class)
class LayoutStateFutureReleaseTest {

  private var widthSpec = 0
  private var heightSpec = 0
  private lateinit var context: ComponentContext
  private lateinit var layoutThreadShadowLooper: ShadowLooper

  private val defaultConfig = ComponentsConfiguration.defaultInstance

  @Before
  fun setup() {
    context = ComponentContext(ApplicationProvider.getApplicationContext<Context>())
    widthSpec = makeSizeSpec(40, EXACTLY)
    heightSpec = makeSizeSpec(40, EXACTLY)
    layoutThreadShadowLooper =
        Shadows.shadowOf(
            Whitebox.invokeMethod<Any>(ComponentTree::class.java, "getDefaultLayoutThreadLooper")
                as Looper)
  }

  private fun runToEndOfTasks() {
    layoutThreadShadowLooper.runToEndOfTasks()
  }

  @After
  fun tearDown() {
    ComponentsConfiguration.defaultInstance = defaultConfig
  }

  @Test
  fun testStopResolvingRowChildrenIfLsfReleased() {
    val layoutStateFutureMock: TreeFuture<*> = mock { on { isReleased } doReturn false }
    val c = ComponentContext(context)
    val resolveContext = c.setRenderStateContextForTests()
    resolveContext.setLayoutStateFutureForTest(layoutStateFutureMock)
    val wait = CountDownLatch(1)
    val child1 =
        TestChildComponent(
            wait,
            null,
            object : WaitActions {
              override fun unblock(layoutStateFuture: TreeFuture<*>?) {
                doReturn(true).`when`(layoutStateFutureMock).isReleased
              }
            })
    val child2 = TestChildComponent()
    val row = Row.create(context).child(child1).child(child2).build()
    val result = row.resolve(resolveContext, ScopedComponentInfo(row, c, null), 0, 0).lithoNode
    Assert.assertTrue(child1.hasRunLayout)
    Assert.assertFalse(child2.hasRunLayout)
    Assert.assertNull(result)
  }

  @Test
  fun testStopResolvingColumnChildrenIfLsfReleased() {
    val layoutStateFutureMock: TreeFuture<*> = mock()
    val c = ComponentContext(context)
    val resolveContext = c.setRenderStateContextForTests()
    resolveContext.setLayoutStateFutureForTest(layoutStateFutureMock)
    val wait = CountDownLatch(1)
    val child1 =
        TestChildComponent(
            wait,
            null,
            object : WaitActions {
              override fun unblock(layoutStateFuture: TreeFuture<*>?) {
                doReturn(true).`when`(layoutStateFutureMock).isReleased
              }
            })
    val child2 = TestChildComponent()
    val column = Column.create(context).child(child1).child(child2).build()
    val result =
        column.resolve(resolveContext, ScopedComponentInfo(column, c, null), 0, 0).lithoNode
    Assert.assertTrue(child1.hasRunLayout)
    Assert.assertFalse(child2.hasRunLayout)
    Assert.assertNull(result)
  }

  private interface WaitActions {
    fun unblock(layoutStateFuture: TreeFuture<*>?)
  }

  private class TestChildComponent
  @JvmOverloads
  constructor(
      private val wait: CountDownLatch? = null,
      private val unlockFinishedLayout: CountDownLatch? = null,
      val waitActions: WaitActions? = null
  ) : SpecGeneratedComponent("TestChildComponent") {
    private val layoutStateFutureList: MutableList<TreeFuture<*>?>
    var hasRunLayout = false

    override fun render(
        resolveContext: ResolveContext,
        c: ComponentContext,
        widthSpec: Int,
        heightSpec: Int
    ): RenderResult<Component> {
      waitActions?.unblock(c.layoutStateFuture)
      if (wait != null) {
        ThreadTestingUtils.failSilentlyIfInterrupted { wait.await(5000, TimeUnit.MILLISECONDS) }
      }
      hasRunLayout = true
      layoutStateFutureList.add(c.layoutStateFuture)
      unlockFinishedLayout?.countDown()
      return RenderResult(Column.create(c).build())
    }

    init {
      layoutStateFutureList = ArrayList()
    }
  }
}
