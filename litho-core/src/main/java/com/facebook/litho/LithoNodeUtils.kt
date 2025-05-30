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

import android.graphics.drawable.Drawable
import android.util.SparseArray
import androidx.core.util.isNotEmpty
import androidx.core.view.ViewCompat
import com.facebook.litho.Component.MountType
import com.facebook.litho.DynamicPropsManager.Companion.hasCustomDynamicProps
import com.facebook.litho.MountSpecLithoRenderUnit.UpdateState
import com.facebook.litho.annotations.ImportantForAccessibility
import com.facebook.litho.config.LithoDebugConfigurations
import com.facebook.litho.drawable.BorderColorDrawable
import com.facebook.litho.host.HostViewAttributesCleanupBinder
import com.facebook.rendercore.BinderKey
import com.facebook.rendercore.MountState
import com.facebook.rendercore.RenderUnit
import com.facebook.rendercore.RenderUnit.DelegateBinder
import com.facebook.rendercore.primitives.Primitive
import com.facebook.rendercore.primitives.PrimitiveRenderUnit
import com.facebook.rendercore.transitions.TransitionUtils

object LithoNodeUtils {

  /** Creates a [LithoRenderUnit] for the content output iff the result mounts content. */
  @JvmStatic
  fun createContentRenderUnit(
      node: LithoNode,
      areCachedMeasuresValid: Boolean,
      diffNode: DiffNode? = null,
  ): LithoRenderUnit? {
    val component: Component = node.tailComponent

    // We need to merge dynamic props from all scoped component infos in order to cover cases where
    // a non-SpecGeneratedComponent such as Primitive is wrapped in a Wrapper. If we don't merge
    // then DynamicCommonProps added through the Wrapper will be lost.
    val commonDynamicProps: SparseArray<DynamicValue<*>>? =
        mergeCommonDynamicProps(node.scopedComponentInfos, component.hasCustomDynamicProps())

    if (component.mountType == MountType.NONE) {
      return null
    }

    val componentKey: String = node.tailComponentKey
    val context: ComponentContext = node.tailComponentContext
    var previousId: Long = -1

    diffNode?.contentOutput?.let { contentOutput -> previousId = contentOutput.id }

    val id: Long = context.calculateLayoutOutputId(componentKey, OutputUnitType.CONTENT)

    val additionalBinders: MutableMap<BinderKey, DelegateBinder<Any, Any, Any>> = HashMap()
    if (node.primitive == null) {
      node.customBindersForMountSpec?.let { additionalBinders.putAll(it) }
    }
    if (node.primitive == null && component.mountType == MountType.VIEW) {
      node.customViewBindersForMountSpec?.let { additionalBinders.putAll(it) }
    }

    return createRenderUnit(
        id = id,
        component = component,
        commonDynamicProps = commonDynamicProps,
        context = context,
        node = node,
        importantForAccessibility = node.importantForAccessibility,
        updateState =
            if (previousId != id) {
              MountSpecLithoRenderUnit.STATE_UNKNOWN
            } else if (areCachedMeasuresValid) {
              MountSpecLithoRenderUnit.STATE_UPDATED
            } else {
              MountSpecLithoRenderUnit.STATE_DIRTY
            },
        duplicateParentState = node.isDuplicateParentStateEnabled,
        hasHostView = node.needsHostView(),
        isMountViewSpec = node.willMountView,
        customDelegateBindersForMountSpec = additionalBinders,
        debugKey = getLithoNodeDebugKey(node, OutputUnitType.CONTENT),
        viewAttributes =
            if (node.needsHostView() || node.primitive != null || !node.willMountView) {
              null
            } else {
              createViewAttributesForBinder(
                  context = context,
                  lithoNode = node,
                  component = component,
                  willMountView = true,
                  importantForAccessibility = node.importantForAccessibility,
              )
            })
  }

  /** Creates a [LithoRenderUnit] for the host output iff the result needs a host view. */
  @JvmStatic
  fun createHostRenderUnit(node: LithoNode): LithoRenderUnit? {
    if (!node.needsHostView()) {
      return null
    }

    val context = node.tailComponentContext
    val hostComponent: HostComponent = HostComponent.create(context)

    // We need to pass common dynamic props to the host component, as they only could be applied to
    // views, so we'll need to set them up, when binding HostComponent to ComponentHost. At the same
    // time, we don't remove them from the current component, as we may calculate multiple
    // LayoutStates using same Components
    val commonDynamicProps: SparseArray<DynamicValue<*>>? =
        mergeCommonDynamicProps(node.scopedComponentInfos)
    hostComponent.commonDynamicProps = commonDynamicProps

    val id: Long = context.calculateLayoutOutputId(node.tailComponentKey, OutputUnitType.HOST)

    val additionalBinders =
        node.customHostViewBinders +
            if (node.needsHostView()) {
              node.customViewBindersForMountSpec
            } else {
              null
            }

    return createRenderUnit(
        id = id,
        component = hostComponent,
        commonDynamicProps = commonDynamicProps,
        context = context,
        node = node,
        importantForAccessibility = node.importantForAccessibility,
        updateState = MountSpecLithoRenderUnit.STATE_UNKNOWN,
        duplicateParentState = node.isHostDuplicateParentStateEnabled,
        duplicateChildrenStates = node.isDuplicateChildrenStatesEnabled,
        isMountViewSpec = true,
        customDelegateBindersForMountSpec = additionalBinders,
        debugKey = getLithoNodeDebugKey(node, OutputUnitType.HOST),
        viewAttributes =
            createViewAttributesForBinder(
                context = context,
                lithoNode = node,
                component = hostComponent,
                willMountView = true,
                importantForAccessibility = node.importantForAccessibility,
            ))
  }

  /** Creates a [LithoRenderUnit] for the root host */
  @JvmStatic
  fun createRootHostRenderUnit(node: LithoNode): LithoRenderUnit {
    val context = node.tailComponentContext
    val hostComponent: HostComponent = HostComponent.create(context)

    // We need to pass common dynamic props to the host component, as they only could be applied to
    // views, so we'll need to set them up, when binding HostComponent to ComponentHost. At the same
    // time, we don't remove them from the current component, as we may calculate multiple
    // LayoutStates using same Components
    val commonDynamicProps: SparseArray<DynamicValue<*>>? =
        mergeCommonDynamicProps(node.scopedComponentInfos)
    hostComponent.commonDynamicProps = commonDynamicProps

    return createRenderUnit(
        id = MountState.ROOT_HOST_ID, // The root host (LithoView) always has ID 0
        component = hostComponent,
        commonDynamicProps = commonDynamicProps,
        context = context,
        node = node,
        importantForAccessibility = node.importantForAccessibility,
        updateState =
            MountSpecLithoRenderUnit.STATE_DIRTY, // set as dirty to skip shouldComponentUpdate()
        duplicateParentState = node.isHostDuplicateParentStateEnabled,
        duplicateChildrenStates = node.isDuplicateChildrenStatesEnabled,
        isMountViewSpec = true,
        customDelegateBindersForMountSpec =
            if (node.willMountView) null else node.customViewBindersForMountSpec,
        debugKey = getLithoNodeDebugKey(node, OutputUnitType.HOST),
        viewAttributes =
            createViewAttributesForBinder(
                context = context,
                lithoNode = node,
                component = hostComponent,
                willMountView = true,
                importantForAccessibility = node.importantForAccessibility,
            ))
  }

  /** Creates a [LithoRenderUnit] for the background output iff the result has a background. */
  @JvmStatic
  fun createBackgroundRenderUnit(
      node: LithoNode,
      width: Int,
      height: Int,
      diffNode: DiffNode? = null,
  ): LithoRenderUnit? {
    val background: Drawable? = node.background

    // Only create a background output when the component does not mount a View because
    // the background will get set in the output of the component.
    return if (background != null && !node.willMountView) {
      createDrawableRenderUnit(node, background, width, height, OutputUnitType.BACKGROUND, diffNode)
    } else {
      null
    }
  }

  /** Creates a [LithoRenderUnit] for the foreground output iff the result has a foreground. */
  @JvmStatic
  fun createForegroundRenderUnit(
      node: LithoNode,
      width: Int,
      height: Int,
      diffNode: DiffNode? = null,
  ): LithoRenderUnit? {
    val foreground: Drawable? = node.foreground

    /// Only create a foreground output when the component does not mount a View because
    // the foreground has already been set in the output of the component.
    return if (foreground != null && (!node.willMountView)) {
      createDrawableRenderUnit(node, foreground, width, height, OutputUnitType.FOREGROUND, diffNode)
    } else {
      null
    }
  }

  /** Creates a [LithoRenderUnit] for the border output iff the result has borders. */
  @JvmStatic
  fun createBorderRenderUnit(
      node: LithoNode,
      border: BorderColorDrawable,
      width: Int,
      height: Int,
      diffNode: DiffNode? = null,
  ): LithoRenderUnit =
      createDrawableRenderUnit(node, border, width, height, OutputUnitType.BORDER, diffNode)

  /**
   * Common method to create the [LithoRenderUnit] for backgrounds, foregrounds, and border. The
   * method uses the {@param outputType} to decide between the options. This method will call the
   * shouldUpdate, and [SpecGeneratedComponent.onBoundsDefined] for the [DrawableComponent].
   */
  @JvmStatic
  fun createDrawableRenderUnit(
      node: LithoNode,
      drawable: Drawable,
      width: Int,
      height: Int,
      @OutputUnitType outputType: Int,
      diffNode: DiffNode? = null,
  ): LithoRenderUnit {
    val component: DrawableComponent = DrawableComponent.create(drawable, width, height)
    val context: ComponentContext = node.tailComponentContext
    val componentKey: String = node.tailComponentKey
    val recycle: LithoRenderUnit? =
        if (diffNode != null) {
          when (outputType) {
            OutputUnitType.BACKGROUND -> diffNode.backgroundOutput
            OutputUnitType.FOREGROUND -> diffNode.foregroundOutput
            OutputUnitType.BORDER -> diffNode.borderOutput
            OutputUnitType.CONTENT,
            OutputUnitType.HOST ->
                throw IllegalArgumentException("OutputUnitType $outputType not supported")
            else -> throw IllegalArgumentException("OutputUnitType $outputType not supported")
          }
        } else {
          null
        }
    val isCachedOutputUpdated: Boolean =
        if (recycle != null) {
          try {
            !component.shouldComponentUpdate(null, recycle.component, null, component)
          } catch (e: Exception) {
            ComponentUtils.handleWithHierarchy(context, component, e)
            false
          }
        } else {
          false
        }
    val previousId: Long = recycle?.id ?: -1
    val id: Long = context.calculateLayoutOutputId(componentKey, outputType)

    return createRenderUnit(
        id = id,
        component = component,
        commonDynamicProps = null, /* Drawables don't bind dynamic props */
        context = context,
        node = node,
        importantForAccessibility = ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO,
        updateState =
            if (previousId != id) {
              MountSpecLithoRenderUnit.STATE_UNKNOWN
            } else if (isCachedOutputUpdated) {
              MountSpecLithoRenderUnit.STATE_UPDATED
            } else {
              MountSpecLithoRenderUnit.STATE_DIRTY
            },
        duplicateParentState = node.isDuplicateParentStateEnabled,
        hasHostView = node.needsHostView(),
        debugKey = getLithoNodeDebugKey(node, outputType),
        viewAttributes = null)
  }

  /** Generic method to create a [LithoRenderUnit]. */
  @JvmStatic
  fun createRenderUnit(
      id: Long,
      component: Component,
      commonDynamicProps: SparseArray<DynamicValue<*>>? = null,
      context: ComponentContext,
      node: LithoNode,
      importantForAccessibility: Int,
      @UpdateState updateState: Int,
      duplicateParentState: Boolean = false,
      duplicateChildrenStates: Boolean = false,
      hasHostView: Boolean = false,
      isMountViewSpec: Boolean = false,
      customDelegateBindersForMountSpec: Map<BinderKey, RenderUnit.DelegateBinder<Any, Any, Any>>? =
          null,
      debugKey: String? = null,
      viewAttributes: ViewAttributes?
  ): LithoRenderUnit {
    var flags = 0
    val nodeInfo: NodeInfo? = node.nodeInfo

    // View mount specs are able to set their own attributes when they're mounted.
    // Non-view specs (drawable and layout) always transfer their view attributes
    // to their respective hosts.
    // Moreover, if the component mounts a view, then we apply padding to the view itself later on.
    // Otherwise, apply the padding to the bounds of the layout output.
    @Suppress("KotlinConstantConditions")
    val layoutOutputNodeInfo: NodeInfo? =
        if (isMountViewSpec) {
          nodeInfo
        } else {
          if (nodeInfo?.enabledState == NodeInfo.ENABLED_SET_FALSE) {
            flags = flags or LithoRenderUnit.LAYOUT_FLAG_DISABLE_TOUCHABLE
          }
          null
        }
    if (duplicateParentState) {
      flags = flags or LithoRenderUnit.LAYOUT_FLAG_DUPLICATE_PARENT_STATE
    }
    if (duplicateChildrenStates) {
      flags = flags or LithoRenderUnit.LAYOUT_FLAG_DUPLICATE_CHILDREN_STATES
    }
    if (hasHostView) {
      flags = flags or LithoRenderUnit.LAYOUT_FLAG_MATCH_HOST_BOUNDS
    }
    if (ComponentContext.getComponentsConfig(node.headComponentContext)
        .shouldAddRootHostViewOrDisableBgFgOutputs) {
      flags = flags or LithoRenderUnit.LAYOUT_FLAG_DRAWABLE_OUTPUTS_DISABLED
    }
    if (nodeInfo?.hasTouchEventHandlers() == true) {
      flags = flags or LithoRenderUnit.LAYOUT_FLAG_HAS_TOUCH_EVENT_HANDLERS
    }

    val primitive: Primitive? = node.primitive
    if (primitive != null && Component.isPrimitive(component)) {
      @Suppress("UNCHECKED_CAST")
      return PrimitiveLithoRenderUnit.create(
          component,
          commonDynamicProps as SparseArray<DynamicValue<Any?>>?,
          context,
          layoutOutputNodeInfo,
          flags,
          importantForAccessibility,
          primitive.renderUnit as PrimitiveRenderUnit<Any>,
          debugKey)
    }
    @Suppress("UNCHECKED_CAST")
    val renderUnit: LithoRenderUnit =
        MountSpecLithoRenderUnit.create(
            id,
            component,
            commonDynamicProps as SparseArray<DynamicValue<Any?>>?,
            context,
            layoutOutputNodeInfo,
            flags,
            importantForAccessibility,
            updateState,
            debugKey)

    if (customDelegateBindersForMountSpec != null) {
      for (binder in customDelegateBindersForMountSpec.values) {
        renderUnit.addOptionalMountBinder(binder)
      }
    }

    val config = node.headComponentContext.lithoConfiguration.componentsConfig

    if (viewAttributes != null) {
      renderUnit.addOptionalMountBinder(
          ViewAttributesViewBinder.create(
              ViewAttributesViewBinder.Model(
                  renderUnit = renderUnit,
                  viewAttributes = viewAttributes,
                  cloneStateListAnimators = config.cloneStateListAnimators,
                  isRootHost = id == MountState.ROOT_HOST_ID,
                  isEventHandlerRedesignEnabled = config.useNonRebindingEventHandlers,
              )))
    }

    if (config.isHostViewAttributesCleanUpEnabled && component is HostComponent) {
      renderUnit.addOptionalMountBinder(
          RenderUnit.DelegateBinder.createDelegateBinder(
              model = null,
              binder = HostViewAttributesCleanupBinder(),
          ) as RenderUnit.DelegateBinder<Any?, Any, Any>,
      )
    }

    return renderUnit
  }

  private fun mergeCommonDynamicProps(
      infos: List<ScopedComponentInfo>,
      hasCustomDynamicProps: Boolean = false
  ): SparseArray<DynamicValue<*>>? {
    val mergedDynamicProps = SparseArray<DynamicValue<*>>()
    for (info in infos) {
      val commonProps: CommonProps? = info.commonProps
      val commonDynamicProps: SparseArray<DynamicValue<*>> =
          commonProps?.commonDynamicProps ?: continue
      for (i in 0 until commonDynamicProps.size()) {
        val key: Int = commonDynamicProps.keyAt(i)
        commonDynamicProps[key]?.let { commonDynamicProp ->
          mergedDynamicProps.append(key, commonDynamicProp)
        }
      }
    }
    return if (mergedDynamicProps.isNotEmpty() || hasCustomDynamicProps) {
      // In order to bind custom dynamic value we have to provide an empty container to do
      // the mappings for DynamicPropsExtension
      mergedDynamicProps
    } else {
      // Return null to avoid allocating an empty set of dynamic values
      null
    }
  }

  private fun getLithoNodeDebugKey(node: LithoNode, @OutputUnitType outputUnitType: Int): String? =
      getDebugKey(node.tailComponentKey, outputUnitType)

  @JvmStatic
  fun getDebugKey(componentKey: String?, @OutputUnitType outputUnitType: Int): String? =
      if (!LithoDebugConfigurations.isDebugModeEnabled) {
        null
      } else {
        when (outputUnitType) {
          OutputUnitType.BACKGROUND -> "$componentKey\$background"
          OutputUnitType.BORDER -> "$componentKey\$border"
          OutputUnitType.FOREGROUND -> "$componentKey\$foreground"
          OutputUnitType.CONTENT -> componentKey
          OutputUnitType.HOST -> "$componentKey\$host"
          else -> null
        }
      }

  @JvmStatic
  fun createTransitionId(node: LithoNode?): TransitionId? =
      if (node == null) {
        null
      } else {
        TransitionUtils.createTransitionId(
            node.transitionKey,
            node.transitionKeyType,
            node.transitionOwnerKey,
            node.transitionGlobalKey)
      }

  /**
   * This is used to determine the set of [ViewAttributes] that are to be used in the scope of the
   * ViewAttributesViewBinder.
   */
  @JvmStatic
  internal fun createViewAttributesForBinder(
      context: ComponentContext,
      lithoNode: LithoNode,
      component: Component,
      willMountView: Boolean,
      @ImportantForAccessibility importantForAccessibility: Int,
  ): ViewAttributes? {
    val nodeInfo = lithoNode.nodeInfo
    val disableBgFgOutputs =
        context.lithoConfiguration.componentsConfig.shouldAddRootHostViewOrDisableBgFgOutputs
    if (nodeInfo == null && !willMountView) {
      return null
    }

    val attrs = ViewAttributes()
    attrs.isHostSpec = Component.isHostSpec(component)
    attrs.componentName = component.simpleName
    attrs.importantForAccessibility = importantForAccessibility
    attrs.disableDrawableOutputs = disableBgFgOutputs
    nodeInfo?.copyInto(attrs)

    // The following only applies if bg/fg outputs are NOT disabled:
    // backgrounds and foregrounds should not be set for HostComponents
    // because those will either be set on the content output or explicit outputs
    // will be created for backgrounds and foreground.
    if (disableBgFgOutputs || !attrs.isHostSpec) {
      attrs.background = lithoNode.background
      attrs.foreground = lithoNode.foreground
    }
    attrs.layoutDirection = lithoNode.layoutDirection
    attrs.layerType = lithoNode.layerType
    attrs.layoutPaint = lithoNode.layerPaint
    if (lithoNode.hasStateListAnimatorResSet()) {
      attrs.stateListAnimatorRes = lithoNode.stateListAnimatorRes
    } else {
      attrs.stateListAnimator = lithoNode.stateListAnimator
    }

    attrs.systemGestureExclusionZones = lithoNode.systemGestureExclusionZones

    return attrs
  }
}
