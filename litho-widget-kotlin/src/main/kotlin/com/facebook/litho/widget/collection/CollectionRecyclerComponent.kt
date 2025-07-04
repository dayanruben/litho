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

package com.facebook.litho.widget.collection

import android.content.Context
import android.graphics.Color
import android.os.SystemClock
import android.view.View
import androidx.annotation.UiThread
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SnapHelper
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.facebook.kotlin.compilerplugins.dataclassgenerate.annotation.DataClassGenerate
import com.facebook.kotlin.compilerplugins.dataclassgenerate.annotation.Mode
import com.facebook.litho.Component
import com.facebook.litho.ComponentContext
import com.facebook.litho.ComponentScope
import com.facebook.litho.ComponentsSystrace
import com.facebook.litho.KComponent
import com.facebook.litho.LithoRenderTreeView
import com.facebook.litho.LithoStartupLogger
import com.facebook.litho.PrimitiveComponentScope
import com.facebook.litho.Style
import com.facebook.litho.annotations.ExperimentalLithoApi
import com.facebook.litho.annotations.Hook
import com.facebook.litho.config.PrimitiveRecyclerBinderStrategy
import com.facebook.litho.effects.useEffect as useLayoutEffect
import com.facebook.litho.onCleanup
import com.facebook.litho.sections.widget.GridRecyclerConfiguration
import com.facebook.litho.sections.widget.NoUpdateItemAnimator
import com.facebook.litho.sections.widget.RecyclerConfiguration
import com.facebook.litho.sections.widget.StaggeredGridRecyclerConfiguration
import com.facebook.litho.useCallback
import com.facebook.litho.useEffect
import com.facebook.litho.useState
import com.facebook.litho.useStateWithDeps
import com.facebook.litho.widget.ChangeSetCompleteCallback
import com.facebook.litho.widget.CollectionItem
import com.facebook.litho.widget.CollectionItemRootHostHolder
import com.facebook.litho.widget.CollectionLayoutData
import com.facebook.litho.widget.CollectionLayoutScope
import com.facebook.litho.widget.CollectionOrientation
import com.facebook.litho.widget.CollectionPreparationManager
import com.facebook.litho.widget.CollectionPrimitiveViewAdapter
import com.facebook.litho.widget.CollectionPrimitiveViewScroller
import com.facebook.litho.widget.ComponentRenderInfo
import com.facebook.litho.widget.ItemDecorationWithMeasureFunction
import com.facebook.litho.widget.LayoutInfo
import com.facebook.litho.widget.LithoCollectionItem
import com.facebook.litho.widget.LithoRecyclerView.OnAfterLayoutListener
import com.facebook.litho.widget.LithoRecyclerView.OnBeforeLayoutListener
import com.facebook.litho.widget.LithoRecyclerView.TouchInterceptor
import com.facebook.litho.widget.Recycler
import com.facebook.litho.widget.Recycler.Companion.createSectionsRecyclerView
import com.facebook.litho.widget.RecyclerBinder
import com.facebook.litho.widget.RecyclerEventsController
import com.facebook.litho.widget.RenderInfo
import com.facebook.litho.widget.SectionsRecyclerView
import com.facebook.litho.widget.SectionsRecyclerView.SectionsRecyclerViewLogger
import com.facebook.litho.widget.SnapUtil
import com.facebook.litho.widget.SnapUtil.SnapMode
import com.facebook.litho.widget.ViewportInfo.ViewportChanged
import com.facebook.litho.widget.bindLegacyAttachBinder
import com.facebook.litho.widget.bindLegacyMountBinder
import com.facebook.litho.widget.calculateLayout
import com.facebook.litho.widget.getChildSizeConstraints
import com.facebook.litho.widget.requireLithoRecyclerView
import com.facebook.litho.widget.unbindLegacyAttachBinder
import com.facebook.litho.widget.unbindLegacyMountBinder
import com.facebook.rendercore.PoolScope
import com.facebook.rendercore.Size
import com.facebook.rendercore.SizeConstraints
import com.facebook.rendercore.primitives.LayoutBehavior
import com.facebook.rendercore.primitives.LayoutScope
import com.facebook.rendercore.primitives.MountBehavior
import com.facebook.rendercore.primitives.PrimitiveLayoutResult
import com.facebook.rendercore.primitives.ViewAllocator
import com.facebook.rendercore.toHeightSpec
import com.facebook.rendercore.toWidthSpec
import kotlin.math.max

/** A component that renders a list of items using a [RecyclerBinder]. */
@OptIn(ExperimentalLithoApi::class)
class CollectionRecyclerComponent(
    private val children: List<CollectionChild>,
    private val componentRenderer: (index: Int, model: CollectionChild) -> RenderInfo,
    private val contentComparator:
        (previousItem: CollectionChild, nextItem: CollectionChild) -> Boolean,
    private val idComparator: (previousItem: CollectionChild, nextItem: CollectionChild) -> Boolean,
    private val recyclerConfiguration: RecyclerConfiguration,
    private val clipChildren: Boolean = true,
    private val clipToPadding: Boolean = true,
    private val bottomPadding: Int = 0,
    private val endPadding: Int = 0,
    private val fadingEdgeLength: Int = 0,
    private val horizontalFadingEdgeEnabled: Boolean = false,
    private val isBottomFadingEnabled: Boolean = true,
    private val isLeftFadingEnabled: Boolean = true,
    private val isRightFadingEnabled: Boolean = true,
    private val isTopFadingEnabled: Boolean = true,
    private val itemAnimator: RecyclerView.ItemAnimator? = null,
    private val itemDecoration: RecyclerView.ItemDecoration? = null,
    private val itemTouchListener: RecyclerView.OnItemTouchListener? = null,
    private val lazyCollectionController: LazyCollectionController? = null,
    private val nestedScrollingEnabled: Boolean = true,
    private val onAfterLayoutListener: OnAfterLayoutListener? = null,
    private val onBeforeLayoutListener: OnBeforeLayoutListener? = null,
    private val onDataBound: OnDataBound? = null,
    private val onDataRendered: OnDataRendered? = null,
    private val onPullToRefresh: (() -> Unit)? = null,
    private val onScrollListeners: List<RecyclerView.OnScrollListener?>? = null,
    private val onViewportChanged: OnViewportChanged? = null,
    private val overScrollMode: Int = View.OVER_SCROLL_ALWAYS,
    private val pullToRefreshEnabled: Boolean = false,
    private val recyclerViewId: Int = View.NO_ID,
    private val refreshProgressBarBackgroundColor: Int? = null,
    private val refreshProgressBarColor: Int = Color.BLACK,
    private val scrollBarStyle: Int = View.SCROLLBARS_INSIDE_OVERLAY,
    private val sectionsViewLogger: SectionsRecyclerViewLogger? = null,
    private val shouldExcludeFromIncrementalMount: Boolean = false,
    private val startPadding: Int = 0,
    private val startupLogger: LithoStartupLogger? = null,
    private val style: Style? = null,
    private val topPadding: Int = 0,
    private val touchInterceptor: TouchInterceptor? = null,
    private val verticalFadingEdgeEnabled: Boolean = false,
) : KComponent() {

  override fun ComponentScope.render(): Component {
    val layoutConfig: CollectionLayoutConfig = useConfig(recyclerConfiguration)
    val latestCommittedData = useState { LatestCommittedData() }.value
    val poolScope = useState { PoolScope.ManuallyManaged() }.value
    val viewportChangedCallback =
        useCallback {
            firstVisibleIndex: Int,
            lastVisibleIndex: Int,
            itemCount: Int,
            firstFullyVisibleIndex: Int,
            lastFullyVisibleIndex: Int ->
          onViewportChanged?.invoke(
              firstVisibleIndex,
              lastVisibleIndex,
              itemCount,
              firstFullyVisibleIndex,
              lastFullyVisibleIndex)
        }

    val layoutInfo =
        useStateWithDeps(
                layoutConfig.orientation,
                layoutConfig.reverseLayout,
                layoutConfig.stackFromEnd,
                layoutConfig.spanCount,
                layoutConfig.gapStrategy) {
                  recyclerConfiguration.getLayoutInfo(context)
                }
            .value
    val binder =
        useStateWithDeps(layoutInfo) {
              RecyclerBinder.Builder()
                  .layoutInfo(layoutInfo)
                  .startupLogger(startupLogger)
                  .recyclerBinderConfig(
                      recyclerConfiguration.recyclerBinderConfiguration.recyclerBinderConfig)
                  .poolScope(poolScope)
                  .build(context)
            }
            .value
    val adapter = useState { CollectionPrimitiveViewAdapter() }.value
    val collectionPreparationManager =
        useStateWithDeps(layoutInfo) { CollectionPreparationManager(layoutInfo) }.value

    val recyclerEventsController = useState { RecyclerEventsController() }.value
    val collectionPrimitiveViewScroller =
        useState { CollectionPrimitiveViewScroller(context.androidContext) }.value

    // This calculates the diff between the previous and new data to determine what changes need to
    // be made to the RecyclerView. It's performed as a best-effort calculation on the background
    // thread without synchronization locks since this data might be discarded if new updates come
    // in before it's applied.
    val updateOperation =
        calculateDiff(
            previousData = latestCommittedData.data,
            nextData = children,
            sameItemComparator = idComparator,
            sameContentComparator = contentComparator)
    // We're going to measure the list with a best effort calculation of the changeset.
    val collectionItems = buildCollectionItems(context, adapter, componentRenderer, updateOperation)

    useEffect(adapter, collectionPreparationManager) {
      val viewportChangedListener =
          ViewportChanged {
              firstVisibleIndex,
              lastVisibleIndex,
              firstFullyVisibleIndex,
              lastFullyVisibleIndex,
              state ->
            viewportChangedCallback(
                firstVisibleIndex,
                lastVisibleIndex,
                adapter.getItemCount(),
                firstFullyVisibleIndex,
                lastFullyVisibleIndex)
          }
      collectionPreparationManager.addViewportChangedListener(viewportChangedListener)
      onCleanup {
        collectionPreparationManager.removeViewportChangedListener(viewportChangedListener)
      }
    }

    useEffect(Any()) {
      lazyCollectionController?.recyclerEventsController = recyclerEventsController
      lazyCollectionController?.scrollerDelegate = RecyclerScroller(collectionPrimitiveViewScroller)

      var resolvedItems: List<CollectionItem<*>> = collectionItems
      var resolvedUpdateOperation: CollectionUpdateOperation<CollectionChild> = updateOperation

      if (updateOperation.prevData !== latestCommittedData.data) {
        // If the data has changed since the last diff calculation, we need to re-calculate the
        // result to make sure we're always applying the latest changeset.
        val operation =
            calculateDiff(
                previousData = latestCommittedData.data,
                nextData = children,
                sameItemComparator = idComparator,
                sameContentComparator = contentComparator)
        val items = buildCollectionItems(context, adapter, componentRenderer, updateOperation)
        // todo remeasure the list and trigger re-layout if the size doesn't match
        resolvedItems = items
        resolvedUpdateOperation = operation
      }

      latestCommittedData.data = resolvedUpdateOperation.nextData
      applyUpdateOperations(
          updateOperation = resolvedUpdateOperation,
          adapter = adapter,
          items = resolvedItems,
          onDataRendered = onDataRendered,
          onDataBound = onDataBound)

      onCleanup {
        lazyCollectionController?.recyclerEventsController = null
        lazyCollectionController?.scrollerDelegate = null
      }
    }
    useEffect(Unit) { onCleanup { poolScope.releaseScope() } }

    val internalPullToRefreshEnabled = (layoutConfig.orientation.isVertical && pullToRefreshEnabled)
    val componentsConfiguration = context.lithoConfiguration.componentsConfig
    val primitiveRecyclerBinderStrategy =
        recyclerConfiguration.recyclerBinderConfiguration.primitiveRecyclerBinderStrategy
            ?: componentsConfiguration.primitiveRecyclerBinderStrategy

    /*
     * This is a temporary solution while we experiment with offering the same behavior regarding
     * the default item animators as in
     * [com.facebook.litho.sections.widget.RecyclerCollectionComponent].
     *
     * This is needed because we will have a crash if we re-use the same animator instance across
     * different RV instances. In this approach we identify if the client opted by using the
     * "default" animator, and if so, it will pass on a new instance of the same type, to avoid a
     * crash that happens due to re-using the same instances in different RVs.
     */
    val itemAnimatorToUse =
        when (itemAnimator) {
          CollectionRecyclerSpec.itemAnimator -> {
            if (context.lithoConfiguration.componentsConfig
                .useDefaultItemAnimatorInLazyCollections &&
                context.lithoConfiguration.componentsConfig.primitiveRecyclerBinderStrategy ==
                    PrimitiveRecyclerBinderStrategy.SPLIT_BINDERS) {
              NoUpdateItemAnimator()
            } else {
              null
            }
          }
          else -> itemAnimator
        }

    return Recycler(
        binderStrategy = primitiveRecyclerBinderStrategy,
        binder = binder,
        bottomPadding = bottomPadding,
        excludeFromIncrementalMount = shouldExcludeFromIncrementalMount,
        fadingEdgeLength = fadingEdgeLength,
        isBottomFadingEnabled = isBottomFadingEnabled,
        isClipChildrenEnabled = clipChildren,
        isClipToPaddingEnabled = clipToPadding,
        isHorizontalFadingEdgeEnabled = horizontalFadingEdgeEnabled,
        isLeftFadingEnabled = isLeftFadingEnabled,
        isNestedScrollingEnabled = nestedScrollingEnabled,
        isPullToRefreshEnabled = internalPullToRefreshEnabled,
        isRightFadingEnabled = isRightFadingEnabled,
        isTopFadingEnabled = isTopFadingEnabled,
        isVerticalFadingEdgeEnabled = verticalFadingEdgeEnabled,
        itemAnimator = itemAnimatorToUse,
        itemDecorations = itemDecoration?.let { listOf(it) },
        leftPadding = startPadding,
        onAfterLayoutListener = onAfterLayoutListener,
        onBeforeLayoutListener = onBeforeLayoutListener,
        onItemTouchListener = itemTouchListener,
        onRefresh =
            if (internalPullToRefreshEnabled && onPullToRefresh != null) {
              onPullToRefresh
            } else {
              null
            },
        onScrollListeners = onScrollListeners?.filterNotNull(),
        overScrollMode = overScrollMode,
        recyclerEventsController = recyclerEventsController,
        recyclerViewId = recyclerViewId,
        refreshProgressBarBackgroundColor = refreshProgressBarBackgroundColor,
        refreshProgressBarColor = refreshProgressBarColor,
        rightPadding = endPadding,
        scrollBarStyle = scrollBarStyle,
        sectionsViewLogger = sectionsViewLogger,
        snapHelper = recyclerConfiguration.snapHelper,
        topPadding = topPadding,
        touchInterceptor = touchInterceptor,
        style = style)
  }

  companion object {

    /**
     * Reads configuration values from a RecyclerConfiguration and creates a new
     * CollectionLayoutConfig if there's any change. This function extracts various layout and
     * behavior settings from the provided configuration and wraps them in a state-managed
     * CollectionLayoutConfig object for use in the component.
     */
    @OptIn(ExperimentalLithoApi::class)
    @Hook
    private fun ComponentScope.useConfig(config: RecyclerConfiguration): CollectionLayoutConfig {
      val mainAxisWrapContent = config.recyclerBinderConfiguration.recyclerBinderConfig.wrapContent
      val crossAxisWrapMode =
          config.recyclerBinderConfiguration.recyclerBinderConfig.crossAxisWrapMode
      val snapHelper = config.snapHelper
      val snapMode = config.snapMode
      val rangeRatio = config.recyclerBinderConfiguration.recyclerBinderConfig.rangeRatio
      val orientation = CollectionOrientation.fromInt(config.orientation)
      val reverseLayout = config.reverseLayout
      val stackFromEnd = config.stackFromEnd
      val spanCount =
          when (config) {
            is GridRecyclerConfiguration -> config.numColumns
            is StaggeredGridRecyclerConfiguration -> config.numSpans
            else -> GridLayoutManager.DEFAULT_SPAN_COUNT
          }
      val gapStrategy =
          if (config is StaggeredGridRecyclerConfiguration) {
            config.gapStrategy
          } else {
            StaggeredGridLayoutManager.GAP_HANDLING_NONE
          }

      return useStateWithDeps(
              mainAxisWrapContent,
              crossAxisWrapMode,
              snapHelper,
              snapMode,
              rangeRatio,
              orientation,
              reverseLayout,
              stackFromEnd,
              spanCount,
              gapStrategy) {
                CollectionLayoutConfig(
                    mainAxisWrapContent = mainAxisWrapContent,
                    crossAxisWrapMode = crossAxisWrapMode,
                    snapHelper = snapHelper,
                    snapMode = snapMode,
                    rangeRatio = rangeRatio,
                    orientation = orientation,
                    reverseLayout = reverseLayout,
                    stackFromEnd = stackFromEnd,
                    spanCount = spanCount,
                    gapStrategy = gapStrategy)
              }
          .value
    }

    /**
     * Calculates the difference between two lists and returns a CollectionUpdateOperation
     * containing the operations needed to transform the previous list into the next list.
     *
     * @param T The type of items in the lists
     * @param previousData The original list of items (can be null)
     * @param nextData The new list of items to compare against (can be null)
     * @param sameItemComparator Optional comparator to determine if two items represent the same
     *   entity
     * @param sameContentComparator Optional comparator to determine if two items have the same
     *   content
     * @return CollectionUpdateOperation containing the diff operations and data references
     */
    private fun <T> calculateDiff(
        previousData: List<T>?,
        nextData: List<T>?,
        sameItemComparator: ((previousItem: T, nextItem: T) -> Boolean)? = null,
        sameContentComparator: ((previousItem: T, nextItem: T) -> Boolean)? = null,
    ): CollectionUpdateOperation<T> {
      ComponentsSystrace.trace("diffing") {
        val updateOperation =
            CollectionUpdateOperation(prevData = previousData, nextData = nextData)
        val diffCallback =
            CollectionDiffCallback(
                previousData, nextData, sameItemComparator, sameContentComparator)
        val result = DiffUtil.calculateDiff(diffCallback)
        result.dispatchUpdatesTo(updateOperation)

        return updateOperation
      }
    }

    /**
     * Builds a list of CollectionItem objects by applying update operations to an existing
     * adapter's items. This method processes insert, delete, move, and update operations to
     * transform the current collection into the target state defined by the update callback.
     *
     * @param context The ComponentContext used for creating new collection items
     * @param adapter The CollectionPrimitiveViewAdapter containing the current items
     * @param renderer Function that converts a model at a given index into RenderInfo
     * @param updateOperation Contains the operations and target data for the collection update
     * @return List of CollectionItem objects representing the updated collection state
     */
    private fun buildCollectionItems(
        context: ComponentContext,
        adapter: CollectionPrimitiveViewAdapter,
        renderer: (index: Int, model: CollectionChild) -> RenderInfo,
        updateOperation: CollectionUpdateOperation<CollectionChild>,
    ): List<CollectionItem<*>> {

      if (updateOperation.operations.isEmpty()) {
        // Returns a read only list of items if there are no operations to apply
        return adapter.getItems()
      }

      // We're creating a speculative changeset for measurement without side effects,
      // which may be discarded if the dataset changes before committing the result,
      // so we need to duplicate the items and apply modifications to the copy.
      val updatedItems = adapter.getItems().toMutableList()
      val itemsNeedToRefreshRenderInfo = mutableSetOf<Int>()
      for (operation in updateOperation.operations) {
        when (operation.type) {
          CollectionOperation.Type.INSERT -> {
            for (index in 0 until operation.count) {
              val item =
                  LithoCollectionItem(
                      componentContext = context, renderInfo = ComponentRenderInfo.createEmpty())
              updatedItems.add(operation.index + index, item)
              itemsNeedToRefreshRenderInfo.add(item.id)
            }
          }
          CollectionOperation.Type.DELETE -> {
            repeat(operation.count) { updatedItems.removeAt(operation.index) }
          }
          CollectionOperation.Type.MOVE -> {
            updatedItems.add(operation.toIndex, updatedItems.removeAt(operation.index))
          }
          CollectionOperation.Type.UPDATE -> {
            for (index in 0 until operation.count) {
              val oldItem = updatedItems[operation.index + index]
              itemsNeedToRefreshRenderInfo.add(oldItem.id)
            }
          }
        }
      }

      if (updateOperation.nextData != null && updateOperation.nextData.size != updatedItems.size) {
        // We may encounter a scenario where the data size doesn't match the result after applying
        // the changeset operations. In such cases, we need to clear all existing items and
        // repopulate the list with the new data.
        updateOperation.operations.clear()
        updatedItems.clear()
        updateOperation.operations.add(
            CollectionOperation(
                type = CollectionOperation.Type.DELETE, index = 0, count = updatedItems.size))

        // Refill the list with new data
        for (index in updateOperation.nextData.indices) {
          val model = updateOperation.nextData[index]
          val renderInfo = renderer(index, model)
          val item = LithoCollectionItem(componentContext = context, renderInfo = renderInfo)
          updatedItems.add(item)
        }
        updateOperation.operations.add(
            CollectionOperation(
                type = CollectionOperation.Type.INSERT,
                index = 0,
                count = updateOperation.nextData.size))
      } else {
        for (index in 0 until updatedItems.size) {
          val item = updatedItems[index]
          // Generate render info for all changed items
          if (itemsNeedToRefreshRenderInfo.contains(item.id)) {
            item.renderInfo =
                (updateOperation.nextData?.get(index)?.let { model -> renderer(index, model) }
                    ?: ComponentRenderInfo.Companion.createEmpty())
          }
        }
      }
      return updatedItems
    }

    /**
     * Applies update operations to the RecyclerView adapter and notifies it of data changes. This
     * method processes a collection of operations (insert, delete, move, update) and dispatches the
     * appropriate notifications to the adapter to trigger UI updates.
     *
     * @param updateOperation The collection update operation containing the list of changes to
     *   apply
     * @param adapter The adapter that manages the RecyclerView items
     * @param items The updated list of collection items to set on the adapter
     * @param onDataBound Optional callback invoked when data binding is complete
     * @param onDataRendered Optional callback invoked when data rendering is complete
     */
    @UiThread
    private fun applyUpdateOperations(
        updateOperation: CollectionUpdateOperation<*>,
        adapter: CollectionPrimitiveViewAdapter,
        items: List<CollectionItem<*>>,
        onDataBound: OnDataBound? = null,
        onDataRendered: OnDataRendered? = null,
    ) {
      ComponentsSystrace.trace("applyUpdateOperations") {
        val isDataChanged = updateOperation.operations.isNotEmpty()
        if (isDataChanged) {
          adapter.setItems(items)
          for (operation in updateOperation.operations) {
            when (operation.type) {
              CollectionOperation.Type.INSERT -> {
                if (operation.count > 1) {
                  adapter.notifyItemRangeInserted(operation.index, operation.count)
                } else {
                  adapter.notifyItemInserted(operation.index)
                }
              }
              CollectionOperation.Type.DELETE -> {
                if (operation.count > 1) {
                  adapter.notifyItemRangeRemoved(operation.index, operation.count)
                } else {
                  adapter.notifyItemRemoved(operation.index)
                }
              }
              CollectionOperation.Type.MOVE -> {
                adapter.notifyItemMoved(operation.index, operation.toIndex)
              }
              CollectionOperation.Type.UPDATE -> {
                if (operation.count > 1) {
                  adapter.notifyItemRangeChanged(operation.index, operation.count)
                } else {
                  adapter.notifyItemChanged(operation.index)
                }
              }
            }
          }
        }
        val changeSetCompleteCallback =
            object : ChangeSetCompleteCallback {
              override fun onDataBound() {
                if (!isDataChanged) {
                  return
                }
                onDataBound?.invoke()
              }

              override fun onDataRendered(isMounted: Boolean, uptimeMillis: Long) {
                onDataRendered?.invoke(
                    isDataChanged,
                    isMounted,
                    uptimeMillis,
                    -1, // todo: read firstVisiblePosition from layout manager
                    -1, // todo: read lastVisiblePosition from layout manager
                )
              }
            }
        // todo use a controller to dispatch data change events
        changeSetCompleteCallback.onDataBound()
        changeSetCompleteCallback.onDataRendered(true, SystemClock.uptimeMillis())
      }
    }
  }

  private class LatestCommittedData(@Volatile var data: List<CollectionChild>? = null)
}

/**
 * Layout behavior implementation for primitive collection views that handles the measurement and
 * layout of collection items within specified size constraints and padding.
 */
@OptIn(ExperimentalLithoApi::class)
private class CollectionPrimitiveViewLayoutBehavior(
    private val adapter: CollectionPrimitiveViewAdapter,
    private val layoutConfig: CollectionLayoutConfig,
    private val layoutInfo: LayoutInfo,
    private val items: List<CollectionItem<*>>,
    private val preparationManager: CollectionPreparationManager,
    private val startPadding: Int,
    private val endPadding: Int,
    private val topPadding: Int,
    private val bottomPadding: Int,
) : LayoutBehavior {

  override fun LayoutScope.layout(sizeConstraints: SizeConstraints): PrimitiveLayoutResult {
    val horizontalPadding = startPadding + endPadding
    val verticalPadding = topPadding + bottomPadding
    val constraintsWithoutPadding =
        sizeConstraintsWithoutPadding(sizeConstraints, horizontalPadding, verticalPadding)

    val scope =
        CollectionLayoutScope(
            layoutInfo,
            constraintsWithoutPadding,
            adapter.layoutData?.collectionSize,
            isVertical = layoutConfig.orientation.isVertical,
            wrapInMainAxis = layoutConfig.mainAxisWrapContent,
            crossAxisWrapMode = layoutConfig.crossAxisWrapMode)

    val size = scope.calculateLayout(items)

    if (!preparationManager.hasApproximateRangeSize) {
      // Measure the first child item with the calculated size constraints
      // todo: use different strategies to have a more precise estimation
      items.firstOrNull()?.let { firstChild ->
        val output = IntArray(2)
        firstChild.measure(scope.getChildSizeConstraints(firstChild), output)
        preparationManager.estimateItemsInViewPort(size, Size(output[0], output[1]))
      }
    }

    useLayoutEffect(adapter) {
      adapter.viewHolderCreator = ViewHolderCreator
      onCleanup { adapter.viewHolderCreator = null }
    }

    useLayoutEffect(layoutInfo, adapter) {
      layoutInfo.setRenderInfoCollection { position ->
        val item =
            adapter.findItemByPosition(position)
                ?: throw IllegalStateException("Trying to find a child item out of range!")
        item.renderInfo
      }
      onCleanup { layoutInfo.setRenderInfoCollection(null) }
    }

    useLayoutEffect(layoutInfo, layoutConfig, adapter, constraintsWithoutPadding, size) {
      val layoutData =
          CollectionLayoutData(
              layoutInfo = layoutInfo,
              collectionConstraints = constraintsWithoutPadding,
              collectionSize = size,
              items = adapter.getItems(),
              isVertical = layoutConfig.orientation.isVertical,
              isDynamicSize = layoutConfig.crossAxisWrapMode == CrossAxisWrapMode.Dynamic,
          )
      adapter.layoutData = layoutData
      onCleanup { adapter.layoutData = null }
    }

    return PrimitiveLayoutResult(width = size.width, height = size.height)
  }

  companion object {

    /**
     * Factory function that creates CollectionItemRootHostHolder instances for RecyclerView items.
     */
    private val ViewHolderCreator:
        (View, Int) -> CollectionItemRootHostHolder<out View, out CollectionItem<out View>> =
        { parent, viewType ->
          when (viewType) {
            LithoCollectionItem.DEFAULT_COMPONENT_VIEW_TYPE -> {
              LithoCollectionItemViewHolder(parent.context)
            }
            else -> {
              throw IllegalArgumentException("Unknown view type: $viewType")
            }
          }
        }

    /** Exclude paddings from the size constraints */
    private fun sizeConstraintsWithoutPadding(
        constraints: SizeConstraints,
        horizontalPadding: Int,
        verticalPadding: Int,
    ): SizeConstraints {

      var minWidth: Int = constraints.minWidth
      var maxWidth: Int = constraints.maxWidth
      var minHeight: Int = constraints.minHeight
      var maxHeight: Int = constraints.maxHeight
      if (constraints.hasBoundedWidth) {
        maxWidth = max(constraints.maxWidth - horizontalPadding, 0)
      } else if (constraints.hasExactWidth) {
        minWidth = max(constraints.minWidth - horizontalPadding, 0)
        maxWidth = max(constraints.maxWidth - horizontalPadding, 0)
      }
      if (constraints.hasBoundedHeight) {
        maxHeight = max(constraints.maxHeight - verticalPadding, 0)
      } else if (constraints.hasExactHeight) {
        minHeight = max(constraints.minHeight - verticalPadding, 0)
        maxHeight = max(constraints.maxHeight - verticalPadding, 0)
      }
      return SizeConstraints(
          minWidth = minWidth, maxWidth = maxWidth, minHeight = minHeight, maxHeight = maxHeight)
    }
  }
}

/**
 * ViewHolder implementation for Litho collection items that wraps a LithoRenderTreeView. This class
 * serves as a bridge between RecyclerView's ViewHolder pattern and Litho's component rendering
 * system, providing the view container for Litho components within a RecyclerView.
 *
 * @param context The Android context used to create the LithoRenderTreeView
 */
internal class LithoCollectionItemViewHolder(context: Context) :
    CollectionItemRootHostHolder<LithoRenderTreeView, LithoCollectionItem>() {

  /**
   * The root view for this ViewHolder, which is a LithoRenderTreeView that can render Litho
   * components within the RecyclerView item.
   */
  override val view: LithoRenderTreeView = LithoRenderTreeView(context)
}

/**
 * Creates a MountBehavior for CollectionPrimitiveView that handles the mounting and configuration
 * of a SectionsRecyclerView with all necessary properties, listeners, and decorations.
 */
@OptIn(ExperimentalLithoApi::class)
private fun PrimitiveComponentScope.CollectionPrimitiveViewMountBehavior(
    layoutConfig: CollectionLayoutConfig,
    layoutInfo: LayoutInfo,
    adapter: CollectionPrimitiveViewAdapter,
    preparationManager: CollectionPreparationManager,
    scroller: CollectionPrimitiveViewScroller,
    bottomPadding: Int,
    clipChildren: Boolean,
    clipToPadding: Boolean,
    endPadding: Int,
    excludeFromIncrementalMount: Boolean,
    fadingEdgeLength: Int,
    horizontalFadingEdgeEnabled: Boolean,
    isBottomFadingEnabled: Boolean,
    isLeftFadingEnabled: Boolean,
    isRightFadingEnabled: Boolean,
    isTopFadingEnabled: Boolean,
    itemAnimator: RecyclerView.ItemAnimator?,
    itemDecorations: List<RecyclerView.ItemDecoration>?,
    itemTouchListener: RecyclerView.OnItemTouchListener?,
    nestedScrollingEnabled: Boolean,
    onAfterLayoutListener: OnAfterLayoutListener?,
    onBeforeLayoutListener: OnBeforeLayoutListener?,
    onRefresh: (() -> Unit)?,
    onScrollListeners: List<RecyclerView.OnScrollListener>?,
    overScrollMode: Int,
    pullToRefreshEnabled: Boolean,
    recyclerEventsController: RecyclerEventsController?,
    recyclerViewId: Int,
    refreshProgressBarBackgroundColor: Int,
    refreshProgressBarColor: Int,
    scrollBarStyle: Int,
    sectionsViewLogger: SectionsRecyclerViewLogger?,
    snapHelper: SnapHelper?,
    startPadding: Int,
    topPadding: Int,
    touchInterceptor: TouchInterceptor?,
    verticalFadingEdgeEnabled: Boolean
): MountBehavior<SectionsRecyclerView> {

  return MountBehavior(ViewAllocator { context -> createSectionsRecyclerView(context) }) {
    doesMountRenderTreeHosts = true
    shouldExcludeFromIncrementalMount = excludeFromIncrementalMount

    withDescription("recycler-decorations") {
      bind(itemDecorations, adapter) { sectionsRecyclerView ->
        val recyclerView = sectionsRecyclerView.requireLithoRecyclerView()

        val measureFunction: (View.() -> Unit) = {
          val position = (layoutParams as RecyclerView.LayoutParams).viewLayoutPosition
          adapter.layoutData?.let { scope ->
            val item = scope.items[position]
            val constraints = scope.getChildSizeConstraints(item)
            measure(constraints.toWidthSpec(), constraints.toHeightSpec())
          }
        }

        itemDecorations?.forEach { decoration ->
          if (decoration is ItemDecorationWithMeasureFunction) {
            decoration.measure = measureFunction
          }
          recyclerView.addItemDecoration(decoration)
        }

        onUnbind {
          itemDecorations?.forEach { decoration ->
            recyclerView.removeItemDecoration(decoration)
            if (decoration is ItemDecorationWithMeasureFunction) {
              decoration.measure = null
            }
          }
        }
      }
    }

    withDescription("recycler-equivalent-mount") {
      bind(
          clipToPadding,
          startPadding,
          topPadding,
          endPadding,
          bottomPadding,
          clipChildren,
          scrollBarStyle,
          horizontalFadingEdgeEnabled,
          verticalFadingEdgeEnabled,
          fadingEdgeLength,
          refreshProgressBarBackgroundColor,
          refreshProgressBarColor,
          itemAnimator?.javaClass) { sectionsRecyclerView ->
            bindLegacyMountBinder(
                sectionsRecyclerView = sectionsRecyclerView,
                contentDescription = "", // not supported yet, using default value instead
                hasFixedSize = true, // not supported yet, using default value instead
                isClipToPaddingEnabled = clipToPadding,
                paddingAdditionDisabled = false, // not supported yet, using default value instead
                leftPadding = startPadding,
                topPadding = topPadding,
                rightPadding = endPadding,
                bottomPadding = bottomPadding,
                isClipChildrenEnabled = clipChildren,
                isNestedScrollingEnabled = nestedScrollingEnabled,
                scrollBarStyle = scrollBarStyle,
                isHorizontalFadingEdgeEnabled = horizontalFadingEdgeEnabled,
                isVerticalFadingEdgeEnabled = verticalFadingEdgeEnabled,
                isLeftFadingEnabled = isLeftFadingEnabled,
                isRightFadingEnabled = isRightFadingEnabled,
                isTopFadingEnabled = isTopFadingEnabled,
                isBottomFadingEnabled = isBottomFadingEnabled,
                fadingEdgeLength = fadingEdgeLength,
                recyclerViewId = recyclerViewId,
                overScrollMode = overScrollMode,
                edgeFactory = null, // // not supported yet, using default value instead
                refreshProgressBarBackgroundColor = refreshProgressBarBackgroundColor,
                refreshProgressBarColor = refreshProgressBarColor,
                itemAnimator = itemAnimator)

            onUnbind {
              unbindLegacyMountBinder(
                  sectionsRecyclerView = sectionsRecyclerView,
                  refreshProgressBarBackgroundColor = refreshProgressBarBackgroundColor,
                  edgeFactory = null,
                  snapHelper = snapHelper)
            }
          }
    }

    withDescription("layout-manager") {
      bind(layoutInfo) { sectionsRecyclerView ->
        val recyclerView = sectionsRecyclerView.requireLithoRecyclerView()
        recyclerView.layoutManager = layoutInfo.getLayoutManager()
        onUnbind { recyclerView.layoutManager = null }
      }
    }

    withDescription("recycler-adapter") {
      bind(adapter) { sectionsRecyclerView ->
        val recyclerView = sectionsRecyclerView.requireLithoRecyclerView()
        recyclerView.adapter = adapter
        onUnbind { recyclerView.adapter = null }
      }
    }

    withDescription("preparation-manager") {
      bindWithLayoutData<CollectionLayoutData>(
          preparationManager, layoutConfig.rangeRatio, adapter) { sectionsRecyclerView, layoutData
            ->
            val recyclerView = sectionsRecyclerView.requireLithoRecyclerView()
            val onEnterRangeCallback: (Int) -> Unit = { position ->
              val item = layoutData.items[position]
              item.prepare(layoutData.getChildSizeConstraints(item))
            }
            val onExitRangeCallback: (Int) -> Unit = { position ->
              val item = layoutData.items[position]
              item.unprepare()
            }
            preparationManager.bind(
                recyclerView, layoutConfig.rangeRatio, onEnterRangeCallback, onExitRangeCallback)
            onUnbind { preparationManager.unbind(recyclerView) }
          }
    }

    withDescription("recycler-scroller") {
      bind(scroller, layoutInfo) { sectionsRecyclerView ->
        val recyclerView = sectionsRecyclerView.requireLithoRecyclerView()
        scroller.bind(layoutInfo, adapter)
        onUnbind {
          scroller.rememberScrollOffset(recyclerView)
          scroller.unbind()
        }
      }
    }

    withDescription("recycler-before-layout") {
      bind(onBeforeLayoutListener) { sectionsRecyclerView ->
        val recyclerView = sectionsRecyclerView.requireLithoRecyclerView()
        recyclerView.setOnBeforeLayoutListener(onBeforeLayoutListener)
        onUnbind { recyclerView.setOnBeforeLayoutListener(null) }
      }
    }

    withDescription("recycler-after-layout") {
      bind(onAfterLayoutListener) { sectionsRecyclerView ->
        val recyclerView = sectionsRecyclerView.requireLithoRecyclerView()
        recyclerView.setOnAfterLayoutListener(onAfterLayoutListener)
        onUnbind { recyclerView.setOnAfterLayoutListener(null) }
      }
    }

    withDescription("recycler-equivalent-bind") {
      bind(Any()) { sectionsRecyclerView ->
        bindLegacyAttachBinder(
            sectionsRecyclerView = sectionsRecyclerView,
            sectionsViewLogger = sectionsViewLogger,
            isPullToRefreshEnabled = pullToRefreshEnabled,
            onRefresh = onRefresh,
            onScrollListeners = onScrollListeners,
            touchInterceptor = touchInterceptor,
            onItemTouchListener = itemTouchListener,
            snapHelper = snapHelper,
            recyclerEventsController = recyclerEventsController)

        onUnbind {
          unbindLegacyAttachBinder(
              sectionsRecyclerView = sectionsRecyclerView,
              recyclerEventsController = recyclerEventsController,
              onScrollListeners = onScrollListeners,
              onItemTouchListener = itemTouchListener)
        }
      }
    }
  }
}

/** An internal model that helps us access these configs as dependency. */
@DataClassGenerate(toString = Mode.OMIT, equalsHashCode = Mode.KEEP)
private data class CollectionLayoutConfig(
    val mainAxisWrapContent: Boolean = false,
    val crossAxisWrapMode: CrossAxisWrapMode = CrossAxisWrapMode.NoWrap,
    val snapHelper: SnapHelper? = null,
    @SnapMode val snapMode: Int = SnapUtil.SNAP_NONE,
    val rangeRatio: Float = DEFAULT_RANGE_RATIO,

    // Configs that requires regenerating a new layout info
    val orientation: CollectionOrientation = CollectionOrientation.VERTICAL,
    val reverseLayout: Boolean = false,
    val stackFromEnd: Boolean = false,
    val spanCount: Int = GridLayoutManager.DEFAULT_SPAN_COUNT,
    val gapStrategy: Int = StaggeredGridLayoutManager.GAP_HANDLING_NONE
) {

  companion object {
    // Default range ratio for the collection
    private const val DEFAULT_RANGE_RATIO: Float = 2f
  }
}
