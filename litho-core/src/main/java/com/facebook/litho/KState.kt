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

import com.facebook.litho.annotations.Hook
import com.facebook.litho.state.ComponentState
import com.facebook.litho.state.StateId
import com.facebook.litho.state.StateProvider
import com.facebook.litho.state.StateReadRecorder
import java.util.Objects

/**
 * Declares a state variable within a Component. The initializer will provide the initial value if
 * it hasn't already been initialized in a previous render of the Component.
 *
 * Assignments to the state variables are allowed only in [updateState] block to batch updates and
 * trigger a UI layout only once per batch.
 */
@Hook
fun <T> ComponentScope.useState(initializer: () -> T): State<T> {
  val globalKey = context.globalKey
  val hookIndex = useStateIndex++
  val treeState: TreeState = resolveContext.treeState
  val lithoTree = context.lithoTree ?: error("LithoTree is null")

  val isNestedTreeContext = context.isNestedTreeContext
  val kState =
      treeState.getState(globalKey, isNestedTreeContext) as ComponentState<KStateContainer>?

  if (kState == null || kState.value.states.size <= hookIndex) {
    // The initial state was not computed yet. let's create it and put it in the state
    val state =
        treeState.createOrGetInitialHookState(
            globalKey,
            hookIndex,
            initializer,
            isNestedTreeContext,
            context.scopedComponentInfo.component.simpleName)
    treeState.addState(globalKey, state, isNestedTreeContext)

    context.scopedComponentInfo.state = state

    return State(
        lithoTree.stateProvider,
        lithoTree.stateUpdater,
        hookIndex,
        globalKey,
        isNestedTreeContext,
        context.componentScope,
        lithoTree.isReadTrackingEnabled,
        state.value.states[hookIndex] as T)
  } else {
    context.scopedComponentInfo.state = kState
  }

  // Only need to mark this global key as seen once
  if (hookIndex == 0) {
    treeState.markStateInUse(globalKey, isNestedTreeContext)
  }

  return State(
      lithoTree.stateProvider,
      lithoTree.stateUpdater,
      hookIndex,
      globalKey,
      isNestedTreeContext,
      context.componentScope,
      lithoTree.isReadTrackingEnabled,
      kState.value.states[hookIndex] as T)
}

/** Interface with which a component gets the value from a state or updates it. */
class State<T>
internal constructor(
    private val stateProvider: StateProvider,
    private val stateUpdater: StateUpdater,
    private val hookStateIndex: Int,
    private val globalKey: String,
    internal val isNestedTreeContext: Boolean,
    private val componentScope: Component?,
    private val isReadTrackingEnabled: Boolean,
    internal val fallback: T
) {

  internal val stateId: StateId = StateId(stateProvider.treeId, globalKey, hookStateIndex)

  val value: T
    get() {
      if (!isReadTrackingEnabled) return fallback
      StateReadRecorder.read(stateId)
      return stateProvider.getValue(this)
    }

  /**
   * Updates this state value and enqueues a new layout calculation reflecting it to execute in the
   * background.
   */
  fun update(newValue: T) {
    if (canSkip(newValue)) {
      return
    }

    stateUpdater?.updateHookStateAsync(
        globalKey,
        HookUpdaterValue(newValue),
        componentScope?.simpleName ?: "hook",
        isNestedTreeContext)
  }

  /**
   * Uses [newValueFunction] to update this state value using the previous state value, and enqueues
   * a new layout calculation reflecting it to execute in the background.
   *
   * [newValueFunction] receives the current state value and can use it to compute the update: this
   * is useful when there could be other enqueued updates that may not have been applied yet.
   *
   * For example, if your state update should increment a counter, using the function version of
   * [update] with `count -> count + 1` will allow you to account for updates that are in flight but
   * not yet applied (e.g. if the user has tapped a button triggering the update multiple times in
   * succession).
   */
  fun update(newValueFunction: (T) -> T) {
    if (canSkip(newValueFunction)) {
      return
    }

    stateUpdater.updateHookStateAsync(
        globalKey,
        HookUpdaterLambda(newValueFunction),
        componentScope?.simpleName ?: "hook",
        isNestedTreeContext)
  }

  /**
   * Updates this state value and enqueues a new layout calculation reflecting it to execute on the
   * current thread. If called on the main thread, this means that the UI will be updated for the
   * current frame.
   *
   * Note: If [updateSync] is used on the main thread, it can easily cause dropped frames and
   * degrade user experience. Therefore it should only be used in exceptional circumstances or when
   * it's known to be executed off the main thread.
   */
  fun updateSync(newValue: T) {
    if (canSkip(newValue)) {
      return
    }

    stateUpdater.updateHookStateSync(
        globalKey,
        HookUpdaterValue(newValue),
        componentScope?.simpleName ?: "hook",
        isNestedTreeContext)
  }

  /**
   * Uses [newValueFunction] to update this state value using the previous state value, and enqueues
   * a new layout calculation reflecting it to execute on the current thread.
   *
   * [newValueFunction] receives the current state value and can use it to compute the update: this
   * is useful when there could be other enqueued updates that may not have been applied yet.
   *
   * For example, if your state update should increment a counter, using the function version of
   * [update] with `count -> count + 1` will allow you to account for updates that are in flight but
   * not yet applied (e.g. if the user has tapped a button triggering the update multiple times in
   * succession).
   *
   * Note: If [updateSync] is used on the main thread, it can easily cause dropped frames and
   * degrade user experience. Therefore it should only be used in exceptional circumstances or when
   * it's known to be executed off the main thread.
   */
  fun updateSync(newValueFunction: (T) -> T) {
    if (canSkip(newValueFunction)) {
      return
    }

    stateUpdater.updateHookStateSync(
        globalKey,
        HookUpdaterLambda(newValueFunction),
        componentScope?.simpleName ?: "hook",
        isNestedTreeContext)
  }

  inner class HookUpdaterValue(val newValue: T) : HookUpdater {
    override fun getUpdatedStateContainer(currentState: KStateContainer): KStateContainer {
      return currentState.copyAndMutate(hookStateIndex, newValue)
    }
  }

  inner class HookUpdaterLambda(val newValueFunction: (T) -> T) : HookUpdater {
    override fun getUpdatedStateContainer(currentState: KStateContainer): KStateContainer {
      return currentState.copyAndMutate(
          hookStateIndex, newValueFunction(currentState.states[hookStateIndex] as T))
    }
  }

  private fun canSkip(newValue: T): Boolean {
    return stateUpdater.canSkipStateUpdate(globalKey, hookStateIndex, newValue, isNestedTreeContext)
  }

  private fun canSkip(newValueFunction: (T) -> T): Boolean {
    return stateUpdater.canSkipStateUpdate(
        newValueFunction, globalKey, hookStateIndex, isNestedTreeContext)
  }

  /**
   * We consider two state objects equal if they 1) belong to the same Tree, 2) have the same global
   * key and hook index, and 3) have the same value (according to its own .equals check)
   */
  override fun equals(other: Any?): Boolean {
    if (this === other) {
      return true
    }

    if (other !is State<*>) {
      return false
    }

    return globalKey == other.globalKey &&
        hookStateIndex == other.hookStateIndex &&
        if (isReadTrackingEnabled) stateId.treeId == other.stateId.treeId
        else fallback == other.fallback
  }

  override fun hashCode(): Int {
    return Objects.hash(
        globalKey, hookStateIndex, if (isReadTrackingEnabled) stateId.treeId else fallback)
  }
}
