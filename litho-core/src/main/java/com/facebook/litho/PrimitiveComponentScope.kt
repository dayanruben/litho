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
import android.view.ViewGroup
import com.facebook.kotlin.compilerplugins.dataclassgenerate.annotation.DataClassGenerate
import com.facebook.kotlin.compilerplugins.dataclassgenerate.annotation.Mode
import com.facebook.litho.ComponentContextUtils.buildDefaultLithoConfiguration
import com.facebook.litho.NestedLithoTree.commit
import com.facebook.litho.NestedLithoTree.enqueue
import com.facebook.litho.NestedLithoTree.runEffects
import com.facebook.litho.annotations.Hook
import com.facebook.litho.config.ComponentsConfiguration
import com.facebook.rendercore.ContentAllocator
import com.facebook.rendercore.primitives.MountBehavior as PrimitiveMountBehavior
import com.facebook.rendercore.primitives.MountConfigurationScope
import com.facebook.rendercore.primitives.Primitive
import java.util.concurrent.atomic.AtomicReference
import kotlin.reflect.KFunction2
import kotlin.reflect.KMutableProperty1

/** The implicit receiver for [PrimitiveComponent.render] call. */
class PrimitiveComponentScope internal constructor(context: ComponentContext) :
    ComponentScope(context) {

  /**
   * Indicates whether the component skips Incremental Mount. If this is true then the Component
   * will not be involved in Incremental Mount.
   */
  var shouldExcludeFromIncrementalMount: Boolean = false

  /**
   * Generates an unique id and creates a [com.facebook.rendercore.primitives.MountBehavior] with
   * it.
   *
   * @param contentAllocator Provides a [View]/[Drawable] content.
   * @param mountConfigurationCall A function that allows for applying properties to the content.
   */
  @Suppress("FunctionName", "NOTHING_TO_INLINE")
  inline fun <ContentType : Any> MountBehavior(
      contentAllocator: ContentAllocator<ContentType>,
      noinline mountConfigurationCall: MountConfigurationScope<ContentType>.() -> Unit
  ): PrimitiveMountBehavior<ContentType> {
    return PrimitiveMountBehavior(
        id = createPrimitiveId(), contentAllocator, mountConfigurationCall)
  }

  /**
   * Generates an unique id and creates a [com.facebook.rendercore.primitives.MountBehavior] with
   * it.
   *
   * @param description A lambda that returns the description of the underlying [RenderUnit]. Mainly
   *   for debugging purposes such as tracing and logs. Maximum description length is 127
   *   characters. Everything above that will be truncated.
   * @param contentAllocator Provides a [View]/[Drawable] content.
   * @param mountConfigurationCall A function that allows for applying properties to the content.
   */
  @Suppress("FunctionName", "NOTHING_TO_INLINE")
  inline fun <ContentType : Any> MountBehavior(
      noinline description: () -> String,
      contentAllocator: ContentAllocator<ContentType>,
      noinline mountConfigurationCall: MountConfigurationScope<ContentType>.() -> Unit
  ): PrimitiveMountBehavior<ContentType> {
    return PrimitiveMountBehavior(
        id = createPrimitiveId(), description, contentAllocator, mountConfigurationCall)
  }

  /**
   * Creates a binding between the dynamic value, and the content’s property.
   *
   * @param dynamicValue value that will be set on the Content
   * @param bindCall function or function reference that will set the dynamic value on the content
   */
  fun <ContentType : Any, T> MountConfigurationScope<ContentType>.bindDynamic(
      dynamicValue: DynamicValue<T>?,
      bindCall: BindDynamicScope.(ContentType, T) -> UnbindDynamicFunc
  ) {
    val bindDynamicScope = if (dynamicValue != null) BindDynamicScope() else null
    addBinder(
        dynamicValue,
        { content, value -> bindDynamicScope?.bindCall(content, value) },
        { bindDynamicScope?.unbindDynamicFunc?.onUnbindDynamic() })
  }

  /**
   * Creates a binding between the dynamic value, and the content’s property.
   *
   * @param dynamicValue value that will be set on the Content
   * @param setter function reference that will set the dynamic value on the content
   */
  fun <ContentType : Any, T> MountConfigurationScope<ContentType>.bindDynamic(
      dynamicValue: DynamicValue<T>?,
      setter: KFunction2<ContentType, T, Any?>,
      default: T
  ) {
    addBinder(
        dynamicValue,
        { content, value -> setter(content, value) },
        { content -> setter(content, default) })
  }

  /**
   * Creates a binding between the dynamic value, and the content’s property.
   *
   * @param dynamicValue value that will be set on the Content
   * @param setter property reference that will set the dynamic value on the content
   */
  fun <ContentType : Any, T> MountConfigurationScope<ContentType>.bindDynamic(
      dynamicValue: DynamicValue<T>?,
      setter: KMutableProperty1<ContentType, T>,
      default: T
  ) {
    addBinder(
        dynamicValue,
        { content, value -> setter.set(content, value) },
        { content -> setter.set(content, default) })
  }

  /**
   * Creates a binding between the dynamic value, and the content’s property. The default value of
   * the property is assumed to be null, so after unbind, null value will be set to the Content.
   *
   * @param setter function reference that will set the dynamic value on the content
   */
  inline fun <ContentType : Any, T> MountConfigurationScope<ContentType>.bindDynamic(
      dynamicValue: DynamicValue<T?>?,
      setter: KFunction2<ContentType, T?, Any?>,
  ) = bindDynamic(dynamicValue, setter, null)

  /**
   * Creates a binding between the dynamic value, and the content’s property. The default value of
   * the property is assumed to be null, so after unbind, null value will be set to the Content.
   *
   * @param setter property reference that will set the dynamic value on the content
   */
  inline fun <ContentType : Any, T> MountConfigurationScope<ContentType>.bindDynamic(
      dynamicValue: DynamicValue<T?>?,
      setter: KMutableProperty1<ContentType, T?>,
  ) = bindDynamic(dynamicValue, setter, null)

  /**
   * Adds a binder for a [DynamicValue] using [MountConfigurationScope.bind] and passing
   * [dynamicValue] as deps in order to make sure that the binder will update only when
   * [dynamicValue] has changed.
   */
  private inline fun <ContentType : Any, T> MountConfigurationScope<ContentType>.addBinder(
      dynamicValue: DynamicValue<T>?,
      crossinline bindCall: (ContentType, T) -> Unit,
      crossinline unbindCall: (ContentType) -> Unit
  ) {
    var listener: DynamicValue.OnValueChangeListener<T>? = null

    bind(dynamicValue) { content ->
      if (dynamicValue != null) {
        if (listener == null) {
          listener =
              DynamicValue.OnValueChangeListener {
                ThreadUtils.assertMainThread()
                bindCall(content, dynamicValue.get())
              }
        }
        listener?.let { dynamicValue.attachListener(it) }
        bindCall(content, dynamicValue.get())
      }
      onUnbind {
        if (dynamicValue != null) {
          unbindCall(content)
          listener?.let { dynamicValue.detach(it) }
          listener = null
        }
      }
    }
  }

  /** Creates an unique ID for a given component. */
  fun createPrimitiveId(): Long {
    // TODO(zielinskim): calculateLayoutOutputId is mutated during resolve/layout and it may race.
    // Ideally, we'd like to replace this hacky solution with something else.
    return context.renderUnitIdGenerator?.calculateLayoutOutputId(
        context.globalKey, OutputUnitType.CONTENT)
        ?: throw IllegalStateException("Attempt to use a released RenderStateContext")
  }
}

/**
 * Interface for the [onUnbindDynamic] function: use [onUnbindDynamic] to define the cleanup
 * function for your dynamic values.
 */
fun interface UnbindDynamicFunc {
  fun onUnbindDynamic()
}

fun <ContentType : ViewGroup> MountConfigurationScope<ContentType>.bindToRenderTreeView(
    state: NestedLithoTreeState,
    getRenderTreeView: ContentType.() -> LithoRenderTreeView,
) {

  // mounts a Render Tree View in the Litho Scroll View
  doesMountRenderTreeHosts = true

  withDescription("root-host-reference") {
    bind(state.mountedViewReference) { content ->
      state.mountedViewReference.mountedView = content.getRenderTreeView()
      onUnbind { state.mountedViewReference.mountedView = null }
    }
  }

  withDescription("litho-tree") {
    bindWithLayoutData<LayoutState>(state) { content, layoutState ->
      state.commit(layoutState = layoutState)
      layoutState.runEffects()
      content.getRenderTreeView().setLayoutState(layoutState, layoutState.treeState)
      onUnbind {}
    }
  }

  withDescription("final-unmount") {
    bind(Unit) { content ->
      onUnbind {
        state.cleanup()
        content.getRenderTreeView().clean()
      }
    }
  }
}

@Hook
fun PrimitiveComponentScope.useNestedTree(
    androidContext: Context = context.androidContext,
    config: ComponentsConfiguration = context.lithoConfiguration.componentsConfig,
    root: Component,
    treeProps: TreePropContainer? = context.treePropContainer,
): Pair<NestedLithoTreeState, ResolveResult> {

  // Any() is used ensure state updates are always
  // requested, and not skipped due to duplicate checks.
  val stateForSync = useState { Any() }
  val errorComponentRef = useState { AtomicReference<Component?>(null) }

  val nestedTreeState = useState { NestedLithoTreeState(treeState = TreeState()) }.value
  val lithoConfig =
      useCached(config) {
        buildDefaultLithoConfiguration(
            context = androidContext,
            componentsConfig = config,
            renderUnitIdGenerator = RenderUnitIdGenerator(nestedTreeState.id),
        )
      }
  val lithoTree =
      useCached(nestedTreeState.id) {
        val stateUpdater =
            NestedStateUpdater(nestedTreeState::treeState) { update: PendingStateUpdate ->
              nestedTreeState.enqueue(update)
              when {
                update.isLazy -> {} // No-Op
                !update.isAsync -> stateForSync.updateSync(Any())
                else -> stateForSync.update(Any())
              }
            }
        LithoTree(
            id = nestedTreeState.id,
            stateProvider = stateUpdater,
            stateUpdater = stateUpdater,
            rootHost = nestedTreeState.mountedViewReference,
            errorComponentReceiver = { errorComponentRef.update(AtomicReference(it)) },
            treeLifecycle = nestedTreeState.treeLifecycleProvider)
      }
  val componentContext =
      ComponentContext(
          androidContext,
          treeProps,
          lithoConfig,
          lithoTree,
          "nested-tree-root",
          context.lithoVisibilityEventsController,
          null,
          null,
      )

  val errorComponent = errorComponentRef.value.getAndSet(null)
  val currentResolveResult = nestedTreeState.currentResolveResult

  val result =
      NestedLithoTree.resolve(
          nestedTreeState.id,
          componentContext,
          errorComponent ?: root,
          treeProps,
          nestedTreeState.getUpdatedState(),
          currentResolveResult,
      )

  return Pair(nestedTreeState, result)
}

@DataClassGenerate(toString = Mode.OMIT, equalsHashCode = Mode.KEEP)
data class NestedLithoTreeState(
    val id: Int = LithoTree.generateComponentTreeId(),
    @Volatile var treeState: TreeState?,
    @Volatile var currentResolveResult: ResolveResult? = null,
    @Volatile var currentLayoutState: LayoutState? = null,
) {

  val mountedViewReference: NestedMountedViewReference = NestedMountedViewReference()
  val treeLifecycleProvider: NestedLithoTreeLifecycleProvider = NestedLithoTreeLifecycleProvider()

  fun enqueue(update: PendingStateUpdate) {
    synchronized(this) { treeState?.enqueue(update) }
  }

  fun getUpdatedState(): TreeState {
    return synchronized(this) { TreeState(treeState) }
  }

  fun commit(layoutState: LayoutState) {
    synchronized(this) {
      treeState?.apply {
        commitResolveState(layoutState.resolveResult.treeState)
        commitLayoutState(layoutState.treeState)
      }
      currentResolveResult = layoutState.resolveResult
      currentLayoutState = layoutState

      // commit the layout state
      layoutState.commit()
    }
  }

  fun cleanup() {
    synchronized(this) {
      treeState?.effectsHandler?.onDetached()
      treeLifecycleProvider.release()
      treeState = null
      currentResolveResult = null
      currentLayoutState = null
    }
  }
}

class BindDynamicScope {

  // Cache the [UnbindDynamicFunc] to avoid creating a new instance on each [DynamicValue] update
  internal var unbindDynamicFunc: UnbindDynamicFunc? = null

  /**
   * Defines an unbind function to be invoked when the content needs to be updated or a [Primitive]
   * is detached.
   */
  fun onUnbindDynamic(unbindDynamic: () -> Unit): UnbindDynamicFunc {
    val unbindDynamicFunc = this.unbindDynamicFunc ?: UnbindDynamicFunc { unbindDynamic() }
    if (this.unbindDynamicFunc == null) {
      this.unbindDynamicFunc = unbindDynamicFunc
    }
    return unbindDynamicFunc
  }
}
