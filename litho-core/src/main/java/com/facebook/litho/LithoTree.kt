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

import com.facebook.infer.annotation.ThreadConfined
import com.facebook.litho.config.ComponentsConfiguration
import com.facebook.litho.state.StateProviderImpl
import com.facebook.litho.state.TreeStateProvider
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

internal typealias StateProvider = TreeStateProvider

internal typealias RootHostProvider = MountedViewReference

internal typealias LithoRendererLifecycleProvider = LithoTreeLifecycleProvider

/** Represents a pointer to the Tree that a ComponentContext is attached to */
class LithoTree
internal constructor(
    val id: Int,
    stateProvider: StateProvider,
    @field:ThreadConfined(ThreadConfined.ANY) val stateUpdater: StateUpdater,
    @field:ThreadConfined(ThreadConfined.UI) val rootHost: RootHostProvider,
    val errorComponentReceiver: ErrorComponentReceiver,
    val treeLifecycle: LithoRendererLifecycleProvider
) {

  val isReadTrackingEnabled: Boolean =
      ComponentsConfiguration.defaultInstance.enableStateReadTracking

  @field:ThreadConfined(ThreadConfined.ANY)
  val stateProvider: com.facebook.litho.state.StateProvider =
      StateProviderImpl(id, isReadTrackingEnabled, stateProvider)

  // Used to lazily store a CoroutineScope, if coroutine helper methods are used.
  @JvmField val internalScopeRef: AtomicReference<Any> = AtomicReference<Any>()

  companion object {

    private val IdGenerator = AtomicInteger(0)

    fun create(componentTree: ComponentTree, stateUpdater: StateUpdater): LithoTree =
        LithoTree(
            id = componentTree.id,
            stateProvider = componentTree,
            stateUpdater = stateUpdater,
            rootHost = componentTree,
            errorComponentReceiver = componentTree,
            treeLifecycle = componentTree)

    @JvmStatic
    fun generateComponentTreeId(): Int {
      return IdGenerator.getAndIncrement()
    }
  }
}
