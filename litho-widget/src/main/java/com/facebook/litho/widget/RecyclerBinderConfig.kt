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

import com.facebook.kotlin.compilerplugins.dataclassgenerate.annotation.DataClassGenerate
import com.facebook.kotlin.compilerplugins.dataclassgenerate.annotation.Mode
import com.facebook.litho.config.ComponentsConfiguration
import com.facebook.litho.widget.collection.CrossAxisWrapMode

/**
 * This configuration is meant to be used in the context of [RecyclerBinder]. It allows you to
 * define to define specific behavior changes to the default behaviour of the RecyclerBinder.
 *
 * At this point, we are still in a transition phase where a lot of configs still live in the
 * [RecyclerBinder.Builder], but we aim to move all of them here.
 */
@DataClassGenerate(toString = Mode.OMIT, equalsHashCode = Mode.KEEP)
data class RecyclerBinderConfig(
    /**
     * Defines if an overriding [com.facebook.litho.config.ComponentsConfiguration] should be used
     * in the [com.facebook.litho.ComponentTree] used in the [RecyclerBinder] hierarchy.
     *
     * If [null], then it will attempt to use the one associated with the
     * [com.facebook.litho.ComponentContext] used to create the [RecyclerBinder].
     */
    @JvmField val componentsConfiguration: ComponentsConfiguration? = null,
    /**
     * Whether the underlying RecyclerBinder will have a circular behaviour. Defaults to false.
     *
     * Note: circular lists DO NOT support any operation that changes the size of items like insert,
     * remove, insert range, remove range.
     *
     * If this configuration is set to `true`, it will disable stable ids in the RecyclerView,
     * independently of the value set on [enableStableIds]
     */
    @JvmField val isCircular: Boolean = false,
    /**
     * The factory that will be used to create the nested [com.facebook.litho.LithoView] inside
     * Section/LazyCollection.
     */
    @JvmField val lithoViewFactory: LithoViewFactory? = null,
    /**
     * Experimental. Configuration to change the behavior of HScroll's when they are nested within a
     * vertical scroll. With this mode, the hscroll will attempt to compute all layouts in the
     * background before mounting so that no layouts are computed on the main thread. All subsequent
     * insertions will be treated with LAYOUT_BEFORE_INSERT policy to ensure those layouts also do
     * not happen on the main thread.
     */
    @JvmField val hScrollAsyncMode: Boolean = false,
    /**
     * Enable pre-mounting for pre-fetched items, which requires to turn on RecyclerView's item
     * prefetching first.
     *
     * @see recyclerViewItemPrefetch
     */
    @JvmField val requestMountForPrefetchedItems: Boolean = false,
    /**
     * Set whether item prefetch should be enabled on the underlying RecyclerView.LayoutManager.
     * Defaults to false.
     *
     * <p>ItemPrefetching feature of RecyclerView clashes with RecyclerBinder's compute range
     * optimization and in certain scenarios (like sticky header) it might reset ComponentTree of
     * LithoView while it is still on screen making it render blank or zero height.
     *
     * <p>As ItemPrefetching is built on top of item view cache, please do remember to set a proper
     * cache size if you want to enable this feature. Otherwise, prefetched item will be thrown into
     * the recycler pool immediately. See [RecyclerBinder.Builder#setItemViewCacheSize]¬
     */
    @JvmField val recyclerViewItemPrefetch: Boolean = false,
    /**
     * Set the number of offscreen views to retain before adding them to the potentially shared
     * recycled view pool.
     *
     * <p>The offscreen view cache stays aware of changes in the attached adapter, allowing a
     * LayoutManager to reuse those views unmodified without needing to return to the adapter to
     * rebind them.
     */
    @JvmField val itemViewCacheSize: Int = 0,
    /**
     * Set pool for pre-computing and storing [ComponentTree], which can be used to pre-compute and
     * store ComponentTrees before they are inserted in a [RecyclerBinder].
     *
     * @see [ComponentWarmer]
     */
    @JvmField val componentWarmer: ComponentWarmer? = null,
    /**
     * This is used in very specific cases on critical performance paths where measuring the first
     * item cannot be relied on to estimate the viewport count. It should not be used in the common
     * case, use with caution.
     */
    @Deprecated("This is only used for migration")
    @JvmField
    val estimatedViewportCount: Int? = null,
    /**
     * The [RecyclerBinder] will use this [LayoutHandlerFactory] when creating
     * [com.facebook.litho.ComponentTree] in order to specify on which thread layout calculation
     * should happen. Setting it to [null] means that the computation will be done in the background
     * thread.
     */
    @JvmField val layoutHandlerFactory: LayoutHandlerFactory? = null,
    /**
     * Ratio to determine the number of components before and after the
     * [androidx.recyclerview.widget.RecyclerView]'s total number of currently visible items to have
     * their [com.facebook.litho.Component] layout computed ahead of time.
     *
     * <p>e.g total number of visible items = 5 rangeRatio = 10 total number of items before the 1st
     * visible item to be computed = 5 * 10 = 50 total number of items after the last visible item
     * to be computed = 5 * 10 = 50
     */
    @JvmField val rangeRatio: Float = DEFAULT_RANGE_RATIO,
    /**
     * If set, the RecyclerView adapter will have stableId support turned on. Please note that this
     * configuration will be disregarded in case [isCircular] is set to `true`. If not set, it will
     * follow the default value set for the [ComponentsConfiguration]
     */
    @JvmField val enableStableIds: Boolean? = null,
    /**
     * If true, the [RecyclerBinder] will measure the parent height by the height of children if the
     * orientation is vertical, or measure the parent width by the width of children if the
     * orientation is horizontal.
     */
    @JvmField val wrapContent: Boolean = false,
    /** Specifies how a [Collection] will wrap its contents across the cross axis. */
    @JvmField val crossAxisWrapMode: CrossAxisWrapMode = CrossAxisWrapMode.NoWrap,
    /**
     * The strategy to make Recycler stick to the last unseen item while pagination, which is only
     * supposed to be used in full screen size surfaces like Reels.
     */
    @JvmField val paginationStrategy: Int = RecyclerBinder.PaginationStrategy.DEFAULT,
    /** The strategy to recycle layouts in the RecyclerBinder. */
    @JvmField
    @RecyclerBinder.RecyclingStrategy
    val recyclingStrategy: Int = RecyclerBinder.RecyclingStrategy.DEFAULT,
) {

  init {
    if (estimatedViewportCount != null) {
      require(estimatedViewportCount > 0) {
        "Estimated viewport count must be > 0: $estimatedViewportCount"
      }
    }

    require(rangeRatio >= 0) { "range ratio has to be bigger or equal to 0: $rangeRatio" }
  }

  companion object {

    const val DEFAULT_RANGE_RATIO: Float = 2f

    private val default: RecyclerBinderConfig = RecyclerBinderConfig()

    @JvmStatic
    fun create(configuration: RecyclerBinderConfig): RecyclerBinderConfigBuilder {
      return RecyclerBinderConfigBuilder(configuration)
    }

    @JvmStatic
    fun create(): RecyclerBinderConfigBuilder {
      return RecyclerBinderConfigBuilder(default)
    }
  }
}

/**
 * This builder is just a helper class for Java clients.
 *
 * It allows the configuration of a builder in a fluent way:
 * ```
 * val recyclerBinderConfig = RecyclerBinderConfig.create()
 *    .isCircular(true)
 *    .build();
 * ```
 */
class RecyclerBinderConfigBuilder internal constructor(configuration: RecyclerBinderConfig) {

  private var isCircular = configuration.isCircular
  private var hScrollAsyncMode = configuration.hScrollAsyncMode
  private var lithoViewFactory = configuration.lithoViewFactory
  private var componentWarmer = configuration.componentWarmer
  private var estimatedViewportCount = configuration.estimatedViewportCount
  private var requestMountForPrefetchedItems = configuration.requestMountForPrefetchedItems
  private var recyclerViewItemPrefetch = configuration.recyclerViewItemPrefetch
  private var itemViewCacheSize = configuration.itemViewCacheSize
  private var componentsConfiguration = configuration.componentsConfiguration
  private var rangeRatio = configuration.rangeRatio
  private var layoutHandlerFactory = configuration.layoutHandlerFactory
  private var enableStableIds = configuration.enableStableIds
  private var wrapContent = configuration.wrapContent
  private var crossAxisWrapMode = configuration.crossAxisWrapMode
  private var paginationStrategy = configuration.paginationStrategy
  private var recyclingStrategy = configuration.recyclingStrategy

  fun isCircular(isCircular: Boolean): RecyclerBinderConfigBuilder = also {
    this.isCircular = isCircular
  }

  fun lithoViewFactory(lithoViewFactory: LithoViewFactory?): RecyclerBinderConfigBuilder = also {
    this.lithoViewFactory = lithoViewFactory
  }

  fun hScrollAsyncMode(hScrollAsyncMode: Boolean): RecyclerBinderConfigBuilder = also {
    this.hScrollAsyncMode = hScrollAsyncMode
  }

  fun requestMountForPrefetchedItems(
      requestMountForPrefetchedItems: Boolean
  ): RecyclerBinderConfigBuilder = also {
    this.requestMountForPrefetchedItems = requestMountForPrefetchedItems
  }

  fun itemViewPrefetch(enabled: Boolean): RecyclerBinderConfigBuilder = also {
    recyclerViewItemPrefetch = enabled
  }

  fun itemViewCacheSize(size: Int): RecyclerBinderConfigBuilder = also { itemViewCacheSize = size }

  fun estimatedViewportCount(estimatedViewportCount: Int?): RecyclerBinderConfigBuilder = also {
    this.estimatedViewportCount = estimatedViewportCount
  }

  fun componentWarmer(componentWarmer: ComponentWarmer?): RecyclerBinderConfigBuilder = also {
    this.componentWarmer = componentWarmer
  }

  fun crossAxisWrapMode(crossAxisWrapMode: CrossAxisWrapMode): RecyclerBinderConfigBuilder = also {
    this.crossAxisWrapMode = crossAxisWrapMode
  }

  fun componentsConfiguration(
      componentsConfiguration: ComponentsConfiguration?
  ): RecyclerBinderConfigBuilder = also { this.componentsConfiguration = componentsConfiguration }

  fun rangeRatio(rangeRatio: Float): RecyclerBinderConfigBuilder = also {
    this.rangeRatio = rangeRatio
  }

  fun layoutHandlerFactory(
      layoutHandlerFactory: LayoutHandlerFactory?
  ): RecyclerBinderConfigBuilder = also { this.layoutHandlerFactory = layoutHandlerFactory }

  fun enableStableIds(enabled: Boolean): RecyclerBinderConfigBuilder = also {
    this.enableStableIds = enabled
  }

  fun wrapContent(wrapContent: Boolean): RecyclerBinderConfigBuilder = also {
    this.wrapContent = wrapContent
  }

  fun paginationStrategy(strategy: Int): RecyclerBinderConfigBuilder = also {
    this.paginationStrategy = strategy
  }

  fun recyclingStrategy(
      @RecyclerBinder.RecyclingStrategy strategy: Int
  ): RecyclerBinderConfigBuilder = also { this.recyclingStrategy = strategy }

  fun build(): RecyclerBinderConfig {
    return RecyclerBinderConfig(
        componentsConfiguration = componentsConfiguration,
        isCircular = isCircular,
        lithoViewFactory = lithoViewFactory,
        hScrollAsyncMode = hScrollAsyncMode,
        requestMountForPrefetchedItems = requestMountForPrefetchedItems,
        recyclerViewItemPrefetch = recyclerViewItemPrefetch,
        itemViewCacheSize = itemViewCacheSize,
        componentWarmer = componentWarmer,
        estimatedViewportCount = estimatedViewportCount,
        rangeRatio = rangeRatio,
        layoutHandlerFactory = layoutHandlerFactory,
        enableStableIds = enableStableIds,
        wrapContent = wrapContent,
        crossAxisWrapMode = crossAxisWrapMode,
        paginationStrategy = paginationStrategy,
        recyclingStrategy = recyclingStrategy,
    )
  }
}
