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

import android.graphics.Color
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.facebook.litho.Component
import com.facebook.litho.ComponentScope
import com.facebook.litho.ComponentUtils
import com.facebook.litho.Handle
import com.facebook.litho.KComponent
import com.facebook.litho.LithoStartupLogger
import com.facebook.litho.Style
import com.facebook.litho.eventHandlerWithReturn
import com.facebook.litho.handle
import com.facebook.litho.kotlinStyle
import com.facebook.litho.sections.Children
import com.facebook.litho.sections.Section
import com.facebook.litho.sections.SectionContext
import com.facebook.litho.sections.common.DataDiffSection
import com.facebook.litho.sections.common.OnCheckIsSameContentEvent
import com.facebook.litho.sections.common.OnCheckIsSameItemEvent
import com.facebook.litho.useState
import com.facebook.litho.widget.ComponentRenderInfo
import com.facebook.litho.widget.LithoRecyclerView
import com.facebook.litho.widget.LithoRecyclerView.OnAfterLayoutListener
import com.facebook.litho.widget.LithoRecyclerView.OnBeforeLayoutListener
import com.facebook.litho.widget.RecyclerBinder
import com.facebook.rendercore.Dimen

class LazyCollection(
    private val layout: CollectionLayout,
    private val itemAnimator: RecyclerView.ItemAnimator?,
    private val itemDecoration: RecyclerView.ItemDecoration? = null,
    private val clipToPadding: Boolean? = null,
    private val clipChildren: Boolean? = null,
    private val startPadding: Dimen? = null,
    private val endPadding: Dimen? = null,
    private val topPadding: Dimen? = null,
    private val bottomPadding: Dimen? = null,
    private val nestedScrollingEnabled: Boolean? = null,
    private val scrollBarStyle: Int? = null,
    private val recyclerViewId: Int? = null,
    private val overScrollMode: Int? = null,
    private val refreshProgressBarBackgroundColor: Int? = null,
    private val refreshProgressBarColor: Int? = null,
    private val touchInterceptor: LithoRecyclerView.TouchInterceptor? = null,
    private val itemTouchListener: RecyclerView.OnItemTouchListener? = null,
    private val sectionTreeTag: String? = null,
    private val startupLogger: LithoStartupLogger? = null,
    private val style: Style? = null,
    private val onViewportChanged: OnViewportChanged? = null,
    handle: Handle? = null,
    private val onPullToRefresh: (() -> Unit)? = null,
    private val onNearEnd: OnNearCallback? = null,
    private val onScrollListener: RecyclerView.OnScrollListener? = null,
    private val onScrollListeners: List<RecyclerView.OnScrollListener?>? = null,
    private val lazyCollectionController: LazyCollectionController? = null,
    private val onDataRendered: OnDataRendered? = null,
    private val onDataBound: OnDataBound? = null,
    private val childEquivalenceIncludesCommonProps: Boolean = true,
    private val alwaysDetectDuplicates: Boolean = false,
    private val isLeftFadingEnabled: Boolean = true,
    private val isRightFadingEnabled: Boolean = true,
    private val isTopFadingEnabled: Boolean = true,
    private val isBottomFadingEnabled: Boolean = true,
    private val fadingEdgeLength: Dimen? = null,
    private val shouldExcludeFromIncrementalMount: Boolean = false,
    private val onBeforeLayout: OnBeforeLayoutListener? = null,
    private val onAfterLayout: OnAfterLayoutListener? = null,
    private val enableNewCollection: Boolean = false,
    private val lazyCollectionChildren: LazyCollectionChildren
) : KComponent() {

  // There's a conflict with Component.handle, so use a different name
  private val recyclerHandle: Handle? = handle

  override fun ComponentScope.render(): Component {
    val sectionContext = SectionContext(context)
    val childTracker = useState { ChildVisibilityTracker() }.value

    val combinedOnViewportChanged: OnViewportChanged =
        {
            firstVisibleIndex,
            lastVisibleIndex,
            totalCount,
            firstFullyVisibleIndex,
            lastFullyVisibleIndex ->
          childTracker.onScrollOrUpdated(
              lazyCollectionChildren.effectiveIndexToId,
              lazyCollectionChildren.idToChild,
              firstVisibleIndex,
              lastVisibleIndex)
          onNearEnd?.let {
            if (lastVisibleIndex >= totalCount - 1 - it.offset) {
              it.callback()
            }
          }
          onViewportChanged?.invoke(
              firstVisibleIndex,
              lastVisibleIndex,
              totalCount,
              firstFullyVisibleIndex,
              lastFullyVisibleIndex)
        }

    val combinedOnDataRendered: OnDataRendered =
        {
            isDataChanged: Boolean,
            isMounted: Boolean,
            monoTimestampMs: Long,
            firstVisibleIndex: Int,
            lastVisibleIndex: Int ->
          childTracker.onScrollOrUpdated(
              lazyCollectionChildren.effectiveIndexToId,
              lazyCollectionChildren.idToChild,
              firstVisibleIndex,
              lastVisibleIndex)
          onDataRendered?.invoke(
              isDataChanged, isMounted, monoTimestampMs, firstVisibleIndex, lastVisibleIndex)
        }

    if (enableNewCollection) {
      val combinedScrollListeners =
          if (onScrollListeners == null && onScrollListener == null) {
            null
          } else {
            val listeners = mutableListOf<RecyclerView.OnScrollListener>()
            onScrollListeners?.filterNotNullTo(listeners)
            onScrollListener?.let { listeners.add(it) }
            listeners
          }
      val fadingEdgeLengthPx = fadingEdgeLength?.toPixels() ?: 0
      val component =
          CollectionRecyclerComponent(
              children = lazyCollectionChildren.collectionChildren,
              recyclerConfiguration = layout.recyclerConfiguration,
              idComparator = { previousItem, nextItem -> previousItem.id == nextItem.id },
              contentComparator = { previousItem, nextItem ->
                isChildEquivalent(previousItem, nextItem)
              },
              itemAnimator = itemAnimator,
              itemDecoration = itemDecoration,
              clipToPadding = clipToPadding ?: true,
              clipChildren = clipToPadding ?: true,
              startPadding = startPadding?.toPixels(resourceResolver) ?: 0,
              endPadding = endPadding?.toPixels(resourceResolver) ?: 0,
              topPadding = topPadding?.toPixels(resourceResolver) ?: 0,
              bottomPadding = bottomPadding?.toPixels(resourceResolver) ?: 0,
              pullToRefreshEnabled = onPullToRefresh != null,
              onPullToRefresh = onPullToRefresh,
              nestedScrollingEnabled = nestedScrollingEnabled ?: false,
              scrollBarStyle = scrollBarStyle ?: View.SCROLLBARS_INSIDE_OVERLAY,
              recyclerViewId = recyclerViewId ?: View.NO_ID,
              overScrollMode = overScrollMode ?: View.OVER_SCROLL_ALWAYS,
              horizontalFadingEdgeEnabled = fadingEdgeLengthPx > 0 && !layout.isVertical,
              verticalFadingEdgeEnabled = fadingEdgeLengthPx > 0 && layout.isVertical,
              isLeftFadingEnabled = if (fadingEdgeLengthPx > 0) isLeftFadingEnabled else false,
              isRightFadingEnabled = if (fadingEdgeLengthPx > 0) isRightFadingEnabled else false,
              isTopFadingEnabled = if (fadingEdgeLengthPx > 0) isTopFadingEnabled else false,
              isBottomFadingEnabled = if (fadingEdgeLengthPx > 0) isBottomFadingEnabled else false,
              refreshProgressBarColor = refreshProgressBarColor ?: Color.BLACK,
              touchInterceptor = touchInterceptor,
              itemTouchListener = itemTouchListener,
              startupLogger = startupLogger,
              onDataBound = onDataBound,
              onDataRendered = combinedOnDataRendered,
              onScrollListeners = combinedScrollListeners,
              onViewportChanged = combinedOnViewportChanged,
              lazyCollectionController = lazyCollectionController,
              shouldExcludeFromIncrementalMount = shouldExcludeFromIncrementalMount,
              onBeforeLayoutListener = onBeforeLayout,
              onAfterLayoutListener = onAfterLayout,
              componentRenderer = { _: Int, model: CollectionChild ->
                val component: Component? = model.component ?: model.componentFunction?.invoke()
                if (component != null) {
                  ComponentRenderInfo.create()
                      .apply {
                        isSticky(model.isSticky)
                        isFullSpan(model.isFullSpan)
                        model.spanSize?.let { spanSize(it) }
                        customAttribute(RecyclerBinder.ID_CUSTOM_ATTR_KEY, model.id)
                        model.customAttributes?.forEach { entry ->
                          customAttribute(entry.key, entry.value)
                        }
                        if (model.parentWidthPercent in 0.0f..100.0f) {
                          parentWidthPercent(model.parentWidthPercent)
                        }
                        if (model.parentHeightPercent in 0.0f..100.0f) {
                          parentHeightPercent(model.parentHeightPercent)
                        }
                      }
                      .component(component)
                      .build()
                } else {
                  ComponentRenderInfo.createEmpty()
                }
              },
              style = style)

      return if (recyclerHandle != null) {
        handle(recyclerHandle, componentLambda = { component })
      } else {
        component
      }
    } else {

      val section =
          CollectionGroupSection.create(sectionContext)
              .childrenBuilder { context ->
                Children.create()
                    .child(
                        createDataDiffSection(
                            context,
                            lazyCollectionChildren.collectionChildren,
                            alwaysDetectDuplicates))
              }
              .apply { onDataBound?.let { onDataBound(it) } }
              .onViewportChanged(combinedOnViewportChanged)
              .onPullToRefresh(onPullToRefresh)
              .onDataRendered(combinedOnDataRendered)
              .build()

      return CollectionRecycler.create(context)
          .section(section)
          .recyclerConfiguration(layout.recyclerConfiguration)
          .itemAnimator(itemAnimator)
          .itemDecoration(itemDecoration)
          .clipToPadding(clipToPadding)
          .clipChildren(clipChildren)
          .startPaddingPx(startPadding?.toPixels(resourceResolver) ?: 0)
          .endPaddingPx(endPadding?.toPixels(resourceResolver) ?: 0)
          .topPaddingPx(topPadding?.toPixels(resourceResolver) ?: 0)
          .bottomPaddingPx(bottomPadding?.toPixels(resourceResolver) ?: 0)
          .pullToRefreshEnabled(onPullToRefresh != null)
          .nestedScrollingEnabled(nestedScrollingEnabled)
          .scrollBarStyle(scrollBarStyle)
          .recyclerViewId(recyclerViewId)
          .overScrollMode(overScrollMode)
          .refreshProgressBarBackgroundColor(refreshProgressBarBackgroundColor)
          .refreshProgressBarColor(refreshProgressBarColor)
          .touchInterceptor(touchInterceptor)
          .itemTouchListener(itemTouchListener)
          .sectionTreeTag(sectionTreeTag)
          .startupLogger(startupLogger)
          .handle(recyclerHandle)
          .onScrollListener(onScrollListener)
          .onScrollListeners(onScrollListeners)
          .lazyCollectionController(lazyCollectionController)
          .shouldExcludeFromIncrementalMount(shouldExcludeFromIncrementalMount)
          .onBeforeLayoutListener(onBeforeLayout)
          .onAfterLayoutListener(onAfterLayout)
          .apply {
            val fadingEdgeLengthPx = fadingEdgeLength?.toPixels()
            if (fadingEdgeLengthPx != null && fadingEdgeLengthPx > 0) {
              fadingEdgeLengthPx(fadingEdgeLengthPx)
              if (layout.isVertical) {
                verticalFadingEdgeEnabled(true)
              } else {
                horizontalFadingEdgeEnabled(true)
              }
              isLeftFadingEnabled(isLeftFadingEnabled)
              isRightFadingEnabled(isRightFadingEnabled)
              isTopFadingEnabled(isTopFadingEnabled)
              isBottomFadingEnabled(isBottomFadingEnabled)
            }
          }
          .kotlinStyle(style)
          .build()
    }
  }

  private fun createDataDiffSection(
      sectionContext: SectionContext,
      children: List<CollectionChild>,
      alwaysDetectDuplicates: Boolean,
  ): Section {
    return DataDiffSection.create<CollectionChild>(sectionContext)
        .alwaysDetectDuplicates(alwaysDetectDuplicates)
        .data(children)
        .renderEventHandler(
            eventHandlerWithReturn { renderEvent ->
              val item = renderEvent.model
              val component =
                  item.component
                      ?: item.componentFunction?.invoke()
                      ?: return@eventHandlerWithReturn null
              ComponentRenderInfo.create()
                  .apply {
                    if (item.isSticky) {
                      isSticky(item.isSticky)
                    }
                    if (item.isFullSpan) {
                      isFullSpan(item.isFullSpan)
                    }
                    item.spanSize?.let { spanSize(it) }
                    customAttribute(RecyclerBinder.ID_CUSTOM_ATTR_KEY, item.id)
                    item.customAttributes?.forEach { entry ->
                      customAttribute(entry.key, entry.value)
                    }
                    if (item.parentWidthPercent in 0.0f..100.0f) {
                      parentWidthPercent(item.parentWidthPercent)
                    }
                    if (item.parentHeightPercent in 0.0f..100.0f) {
                      parentHeightPercent(item.parentHeightPercent)
                    }
                  }
                  .component(component)
                  .build()
            })
        .onCheckIsSameItemEventHandler(eventHandlerWithReturn(::isSameID))
        .onCheckIsSameContentEventHandler(eventHandlerWithReturn(::isChildEquivalent))
        .build()
  }

  private fun isSameID(event: OnCheckIsSameItemEvent<CollectionChild>): Boolean {
    return event.previousItem.id == event.nextItem.id
  }

  private fun componentsEquivalent(first: Component?, second: Component?): Boolean {
    if (first == null && second == null) return true
    return ComponentUtils.isEquivalent(first, second, childEquivalenceIncludesCommonProps)
  }

  private fun isChildEquivalent(event: OnCheckIsSameContentEvent<CollectionChild>): Boolean =
      isChildEquivalent(event.previousItem, event.nextItem)

  fun isChildEquivalent(previous: CollectionChild, next: CollectionChild): Boolean {
    if (previous.deps != null || next.deps != null) {
      return previous.deps?.contentDeepEquals(next.deps) == true
    }
    if (previous.isSticky != next.isSticky) {
      return false
    }

    return componentsEquivalent(previous.component, next.component)
  }
}

/**
 * Track which children are visible after an update or scroll, and trigger any necessary callbacks.
 */
private class ChildVisibilityTracker {

  private var previouslyVisibleIds: Set<Any> = setOf()

  fun onScrollOrUpdated(
      effectiveIndexToId: Map<Int, MutableSet<Any>>,
      idToChild: Map<Any, CollectionChild>,
      firstVisibleIndex: Int,
      lastVisibleIndex: Int
  ) {
    val visibleIds =
        mutableSetOf<Any>()
            .apply {
              (firstVisibleIndex..lastVisibleIndex).forEach { visibleIndex ->
                effectiveIndexToId[visibleIndex]?.let { ids -> addAll(ids) }
              }
            }
            .toSet()

    val enteredIds = visibleIds - previouslyVisibleIds
    enteredIds.forEach { id -> idToChild[id]?.onNearViewport?.callback?.invoke() }

    previouslyVisibleIds = visibleIds
  }
}
