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

import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SnapHelper
import com.facebook.litho.Component
import com.facebook.litho.Handle
import com.facebook.litho.LithoStartupLogger
import com.facebook.litho.ResourcesScope
import com.facebook.litho.Style
import com.facebook.litho.config.PreAllocationHandler
import com.facebook.litho.sections.widget.LinearLayoutInfoFactory
import com.facebook.litho.widget.LithoRecyclerView
import com.facebook.litho.widget.LithoRecyclerView.OnAfterLayoutListener
import com.facebook.litho.widget.LithoRecyclerView.OnBeforeLayoutListener
import com.facebook.litho.widget.SnapUtil
import com.facebook.rendercore.Dimen
import com.facebook.rendercore.dp

/** A scrollable collection of components arranged linearly */
@Suppress("FunctionName")
inline fun ResourcesScope.LazyList(
    @RecyclerView.Orientation orientation: Int = RecyclerView.VERTICAL,
    @SnapUtil.SnapMode snapMode: Int = SnapUtil.SNAP_NONE,
    snapHelper: SnapHelper? = null,
    snapToStartOffset: Dimen = 0.dp,
    reverse: Boolean = false,
    crossAxisWrapMode: CrossAxisWrapMode = CrossAxisWrapMode.NoWrap,
    mainAxisWrapContent: Boolean = false,
    itemAnimator: RecyclerView.ItemAnimator? = CollectionRecyclerSpec.itemAnimator,
    itemDecoration: RecyclerView.ItemDecoration? = null,
    clipToPadding: Boolean? = null,
    clipChildren: Boolean? = null,
    startPadding: Dimen? = null,
    endPadding: Dimen? = null,
    topPadding: Dimen? = null,
    bottomPadding: Dimen? = null,
    nestedScrollingEnabled: Boolean? = null,
    scrollBarStyle: Int? = null,
    recyclerViewId: Int? = null,
    overScrollMode: Int? = null,
    refreshProgressBarBackgroundColor: Int? = null,
    refreshProgressBarColor: Int? = null,
    touchInterceptor: LithoRecyclerView.TouchInterceptor? = null,
    itemTouchListener: RecyclerView.OnItemTouchListener? = null,
    sectionTreeTag: String? = null,
    startupLogger: LithoStartupLogger? = null,
    style: Style? = null,
    noinline onViewportChanged: OnViewportChanged? = null,
    noinline onDataBound: OnDataBound? = null,
    handle: Handle? = null,
    noinline onPullToRefresh: (() -> Unit)? = null,
    onNearEnd: OnNearCallback? = null,
    onScrollListener: RecyclerView.OnScrollListener? = null,
    onScrollListeners: List<RecyclerView.OnScrollListener?>? = null,
    lazyCollectionController: LazyCollectionController? = null,
    noinline onDataRendered: OnDataRendered? = null,
    rangeRatio: Float? = null,
    useBackgroundChangeSets: Boolean = false,
    preAllocationHandler: PreAllocationHandler? =
        context.lithoConfiguration.componentsConfig.preAllocationHandler,
    childEquivalenceIncludesCommonProps: Boolean = true,
    alwaysDetectDuplicates: Boolean = false,
    isLeftFadingEnabled: Boolean = true,
    isRightFadingEnabled: Boolean = true,
    isTopFadingEnabled: Boolean = true,
    isBottomFadingEnabled: Boolean = true,
    fadingEdgeLength: Dimen? = null,
    shouldExcludeFromIncrementalMount: Boolean = false,
    isCircular: Boolean = false,
    enableStableIds: Boolean =
        context.lithoConfiguration.componentsConfig.useStableIdsInRecyclerBinder,
    enableNewCollection: Boolean = false,
    linearLayoutInfoFactory: LinearLayoutInfoFactory? = null,
    onBeforeLayout: OnBeforeLayoutListener? = null,
    onAfterLayout: OnAfterLayoutListener? = null,
    crossinline init: LazyListScope.() -> Unit
): Component {
  val lazyListScope = LazyListScope(context).apply { init() }

  return LazyCollection(
      layout =
          CollectionLayouts.Linear(
              componentContext = context,
              orientation = orientation,
              snapMode = snapMode,
              snapHelper = snapHelper,
              snapToStartOffset = snapToStartOffset.toPixels(resourceResolver),
              reverse = reverse,
              rangeRatio = rangeRatio,
              useBackgroundChangeSets = useBackgroundChangeSets,
              preAllocationHandler = preAllocationHandler,
              crossAxisWrapMode = crossAxisWrapMode,
              mainAxisWrapContent = mainAxisWrapContent,
              isCircular = isCircular,
              enableStableIds = enableStableIds,
              linearLayoutInfoFactory = linearLayoutInfoFactory,
          ),
      itemAnimator = itemAnimator,
      itemDecoration = itemDecoration,
      clipToPadding = clipToPadding,
      clipChildren = clipChildren,
      startPadding = startPadding,
      endPadding = endPadding,
      topPadding = topPadding,
      bottomPadding = bottomPadding,
      nestedScrollingEnabled = nestedScrollingEnabled,
      scrollBarStyle = scrollBarStyle,
      recyclerViewId = recyclerViewId,
      overScrollMode = overScrollMode,
      refreshProgressBarBackgroundColor = refreshProgressBarBackgroundColor,
      refreshProgressBarColor = refreshProgressBarColor,
      touchInterceptor = touchInterceptor,
      itemTouchListener = itemTouchListener,
      sectionTreeTag = sectionTreeTag,
      startupLogger = startupLogger,
      style = style,
      onViewportChanged = onViewportChanged,
      handle = handle,
      onPullToRefresh = onPullToRefresh,
      onNearEnd = onNearEnd,
      onScrollListener = onScrollListener,
      onScrollListeners = onScrollListeners,
      lazyCollectionController = lazyCollectionController,
      onDataRendered = onDataRendered,
      onDataBound = onDataBound,
      childEquivalenceIncludesCommonProps = childEquivalenceIncludesCommonProps,
      alwaysDetectDuplicates = alwaysDetectDuplicates,
      isLeftFadingEnabled = isLeftFadingEnabled,
      isRightFadingEnabled = isRightFadingEnabled,
      isTopFadingEnabled = isTopFadingEnabled,
      isBottomFadingEnabled = isBottomFadingEnabled,
      fadingEdgeLength = fadingEdgeLength,
      shouldExcludeFromIncrementalMount = shouldExcludeFromIncrementalMount,
      onBeforeLayout = onBeforeLayout,
      onAfterLayout = onAfterLayout,
      enableNewCollection = enableNewCollection,
      lazyCollectionChildren = lazyListScope.children)
}
