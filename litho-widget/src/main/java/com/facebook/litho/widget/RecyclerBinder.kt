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

import android.annotation.SuppressLint
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.View.OnAttachStateChangeListener
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.ViewParent
import android.view.ViewTreeObserver.OnPreDrawListener
import androidx.annotation.AnyThread
import androidx.annotation.IntDef
import androidx.annotation.UiThread
import androidx.annotation.VisibleForTesting
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.CollectionInfoCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.OrientationHelper.HORIZONTAL
import androidx.recyclerview.widget.OrientationHelper.VERTICAL
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerViewAccessibilityDelegate
import com.facebook.infer.annotation.ThreadConfined
import com.facebook.litho.Component
import com.facebook.litho.ComponentContext
import com.facebook.litho.ComponentTree
import com.facebook.litho.ComponentTree.MeasureListener
import com.facebook.litho.ComponentTree.NewLayoutStateReadyListener
import com.facebook.litho.ComponentUtils
import com.facebook.litho.ComponentsLogger
import com.facebook.litho.ComponentsReporter
import com.facebook.litho.ComponentsSystrace
import com.facebook.litho.ComponentsSystrace.isTracing
import com.facebook.litho.EventHandler
import com.facebook.litho.FrameworkLogEvents
import com.facebook.litho.LithoStartupLogger
import com.facebook.litho.LithoView
import com.facebook.litho.LithoVisibilityEventsController
import com.facebook.litho.LogTreePopulator
import com.facebook.litho.MeasureComparisonUtils
import com.facebook.litho.MountHelper
import com.facebook.litho.RenderCompleteEvent
import com.facebook.litho.Size
import com.facebook.litho.SizeSpec
import com.facebook.litho.SizeSpec.getMode
import com.facebook.litho.SizeSpec.getSize
import com.facebook.litho.SizeSpec.makeSizeSpec
import com.facebook.litho.ThreadUtils
import com.facebook.litho.annotations.ExperimentalLithoApi
import com.facebook.litho.choreographercompat.ChoreographerCompat
import com.facebook.litho.choreographercompat.ChoreographerCompatImpl
import com.facebook.litho.config.ComponentsConfiguration
import com.facebook.litho.config.LithoDebugConfigurations
import com.facebook.litho.viewcompat.ViewBinder
import com.facebook.litho.widget.ComponentTreeHolder.ComponentTreeMeasureListenerFactory
import com.facebook.litho.widget.ComponentWarmer.ComponentTreeHolderPreparer
import com.facebook.litho.widget.LayoutInfo.RenderInfoCollection
import com.facebook.litho.widget.RecyclerBinder.ComponentTreeHolderFactory
import com.facebook.litho.widget.ViewportInfo.ViewportChanged
import com.facebook.litho.widget.collection.CrossAxisWrapMode
import com.facebook.rendercore.FastMath
import com.facebook.rendercore.PoolScope
import com.facebook.rendercore.RunnableHandler
import com.facebook.rendercore.utils.MeasureSpecUtils
import java.util.ArrayDeque
import java.util.Deque
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.annotation.concurrent.GuardedBy
import javax.annotation.concurrent.ThreadSafe
import kotlin.concurrent.Volatile
import kotlin.math.max
import kotlin.math.min

/**
 * This binder class is used to asynchronously layout Components given a list of [Component] and
 * attaching them to a [Recycler].
 */
@SuppressLint("NotifyDataSetChanged")
@ThreadSafe
class RecyclerBinder private constructor(builder: Builder) :
    Binder<RecyclerView>, RenderInfoCollection, HasStickyHeader {

  @GuardedBy("this")
  private val _componentTreeHolders: MutableList<ComponentTreeHolder> = ArrayList()

  @GuardedBy("this")
  private val asyncComponentTreeHolders: MutableList<ComponentTreeHolder?> = ArrayList()

  private val layoutInfo: LayoutInfo?

  /**
   * @return the internal RecyclerView.Adapter that is used to communicate to the RecyclerView. This
   *   should generally only be useful when operating in sub-adapter mode.
   */
  val internalAdapter: RecyclerView.Adapter<RecyclerView.ViewHolder>
  private val componentContext: ComponentContext?

  private val recyclerBinderConfig: RecyclerBinderConfig? = builder.recyclerBinderConfig
  private val layoutHandlerFactory: LayoutHandlerFactory?
  private val componentTreeHolderFactory: ComponentTreeHolderFactory?
  private val mainThreadHandler = Handler(Looper.getMainLooper())
  private val rangeRatio: Float
  private val _isMeasured = AtomicBoolean(false)
  private val requiresRemeasure = AtomicBoolean(false)
  private val enableStableIds: Boolean
  private val asyncInsertHandler: RunnableHandler?
  private val lithoVisibilityEventsController: LithoVisibilityEventsController?
  private val rangeTraverser: RecyclerRangeTraverser
  private val hScrollAsyncMode: Boolean
  private val isSubAdapter: Boolean
  private val hasManualEstimatedViewportCount: Boolean
  private val recyclerViewItemPrefetch: Boolean
  private val itemViewCacheSize: Int
  private val requestMountForPrefetchedItems: Boolean

  @RecyclingStrategy private val recyclingStrategy: Int
  private val componentsConfiguration: ComponentsConfiguration

  private val currentChangeSetThreadId = AtomicLong(-1)

  @JvmField @VisibleForTesting val traverseLayoutBackwards: Boolean

  @GuardedBy("this") private val asyncBatches: Deque<AsyncBatch> = ArrayDeque()

  private val hasAsyncBatchesToCheck = AtomicBoolean(false)
  private val isInMeasure = AtomicBoolean(false)

  private val errorHandler: ((Exception) -> Unit)?

  private val poolScope: PoolScope

  @JvmField
  @ThreadConfined(ThreadConfined.UI)
  @VisibleForTesting
  val dataRenderedCallbacks: Deque<ChangeSetCompleteCallback?> = ArrayDeque()

  @JvmField
  @VisibleForTesting
  val remeasureRunnable: Runnable = Runnable {
    reMeasureEventEventHandler?.dispatchEvent(ReMeasureEvent())
  }

  /**
   * To avoid creating a new runnable for each ComponentTreeHolder, we maintain a task queue to
   * consume them in order.
   */
  @JvmField var componentTreeHoldersToRelease: Deque<ComponentTreeHolder> = ArrayDeque()

  private val releaseTreeRunnableLock = Any()
  private var hasPendingReleaseTreeRunnable = false

  @JvmField
  @ThreadConfined(ThreadConfined.UI)
  val releaseTreeRunnable: Runnable = Runnable {
    val iterator: Iterator<ComponentTreeHolder>
    synchronized(releaseTreeRunnableLock) {
      hasPendingReleaseTreeRunnable = false
      if (componentTreeHoldersToRelease.isEmpty()) {
        return@Runnable
      }

      iterator = componentTreeHoldersToRelease.iterator()
      componentTreeHoldersToRelease = ArrayDeque()
    }
    while (iterator.hasNext()) {
      val holder = iterator.next()
      maybeAcquireStateAndReleaseTree(holder)
    }
  }

  private val postDispatchDrawListener: PostDispatchDrawListener = PostDispatchDrawListener {
    maybeDispatchDataRendered()
  }

  private val onPreDrawListener: OnPreDrawListener = OnPreDrawListener {
    maybeDispatchDataRendered()
    true
  }

  private val onAttachStateChangeListener: OnAttachStateChangeListener =
      object : OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) = Unit

        override fun onViewDetachedFromWindow(v: View) {
          unregisterDrawListener(v as RecyclerView)
          v.removeOnAttachStateChangeListener(this)
        }
      }

  private val notifyDatasetChangedRunnable: Runnable

  private val componentTreeMeasureListenerFactory: ComponentTreeMeasureListenerFactory?

  @get:VisibleForTesting var componentWarmer: ComponentWarmer?

  private fun getMeasureListener(holder: ComponentTreeHolder): MeasureListener {
    return MeasureListener { _, width, height, _ ->
      if (holder.measuredHeight == height) {
        return@MeasureListener
      }
      holder.measuredHeight = height

      val sizeForMeasure = this@RecyclerBinder.sizeForMeasuring

      if (sizeForMeasure != UNSET && holder.measuredHeight <= sizeForMeasure) {
        return@MeasureListener
      }
      synchronized(this@RecyclerBinder) {
        resetMeasuredSize(width)
        requestRemeasure()
      }
    }
  }

  private val asyncLayoutReadyListener = NewLayoutStateReadyListener { componentTree ->
    applyReadyBatches()
    componentTree.newLayoutStateReadyListener = null
  }

  private val applyReadyBatchesCallback: ChoreographerCompat.FrameCallback =
      object : ChoreographerCompat.FrameCallback() {
        @UiThread
        override fun doFrame(frameTimeNanos: Long) {
          applyReadyBatches()
        }
      }

  private val isCircular: Boolean
  override val isMainAxisWrapContent: Boolean
  override val isCrossAxisWrapContent: Boolean
  private val hasDynamicItemHeight: Boolean

  private var lastWidthSpec = UNINITIALIZED
  private var lastHeightSpec = UNINITIALIZED
  private var measuredSize: Size? = null
  private var mountedView: RecyclerView? = null

  /**
   * Can be set for RecyclerBinder instances which do not have control over the RecyclerView which
   * the adapter sends operations to, and it does not mount or measure it. Only for subadapter mode.
   */
  private var subAdapterRecyclerView: RecyclerView? = null

  @JvmField @VisibleForTesting var currentFirstVisiblePosition: Int = RecyclerView.NO_POSITION

  @JvmField @VisibleForTesting var currentLastVisiblePosition: Int = RecyclerView.NO_POSITION

  @PaginationStrategy private val paginationStrategy: Int
  private var currentOffset = 0
  private var smoothScrollAlignmentType: SmoothScrollAlignmentType? = null

  // The estimated number of items needed to fill the viewport.
  @JvmField @VisibleForTesting var estimatedViewportCount: Int = UNSET

  // The size computed for the first Component to be used when we can't use the size specs passed to
  // measure.
  @JvmField @VisibleForTesting @Volatile var sizeForMeasure: Size? = null

  @GuardedBy("this") private var lowestRangeStartSinceDeletes = Int.MAX_VALUE

  @GuardedBy("this") private var highestRangeStartSinceDeletes = Int.MIN_VALUE

  private var stickyHeaderController: StickyHeaderController? = null
  private val stickyHeaderControllerFactory: StickyHeaderControllerFactory?
  private var reMeasureEventEventHandler: EventHandler<ReMeasureEvent>? = null

  @Volatile private var hasAsyncOperations = false
  private var isInitMounted = false // Set to true when the first mount() is called.

  @CommitPolicy private var commitPolicy = CommitPolicy.IMMEDIATE
  private var hasFilledViewport = false
  private val startupLogger: LithoStartupLogger?
  private var startupLoggerAttribution = ""
  private val firstMountLogged = BooleanArray(1)
  private val lastMountLogged = BooleanArray(1)
  private val recyclerBinderAdapterDelegate: RecyclerBinderAdapterDelegate<RecyclerBinderViewHolder>
  private val additionalPostDispatchDrawListeners: List<PostDispatchDrawListener>

  @GuardedBy("this") private var currentBatch: AsyncBatch? = null

  @JvmField @VisibleForTesting val viewportManager: ViewportManager
  private val viewportChangedListener: ViewportChanged =
      object : ViewportChanged {
        override fun viewportChanged(
            firstVisibleIndex: Int,
            lastVisibleIndex: Int,
            firstFullyVisibleIndex: Int,
            lastFullyVisibleIndex: Int,
            state: Int
        ) {
          onNewVisibleRange(firstVisibleIndex, lastVisibleIndex)
          onNewWorkingRange(
              firstVisibleIndex, lastVisibleIndex, firstFullyVisibleIndex, lastFullyVisibleIndex)
        }
      }
  private var postUpdateViewportAttempts = 0

  @JvmField @VisibleForTesting val renderInfoViewCreatorController: RenderInfoViewCreatorController

  private val updateViewportRunnable: Runnable

  internal class RenderCompleteRunnable(
      private val renderCompleteEventHandler: EventHandler<RenderCompleteEvent>,
      private val renderState: RenderCompleteEvent.RenderState,
      private val timestampMillis: Long
  ) : Runnable {
    override fun run() {
      dispatchRenderCompleteEvent(renderCompleteEventHandler, renderState, timestampMillis)
    }
  }

  fun interface ComponentTreeHolderFactory {
    fun create(
        renderInfo: RenderInfo,
        layoutHandler: RunnableHandler?,
        measureListenerFactory: ComponentTreeMeasureListenerFactory?,
        componentsConfiguration: ComponentsConfiguration,
        lifecycleProvider: LithoVisibilityEventsController?
    ): ComponentTreeHolder
  }

  class Builder {
    var recyclerBinderConfig: RecyclerBinderConfig? = null
    var layoutInfo: LayoutInfo? = null
    var componentTreeHolderFactory: ComponentTreeHolderFactory? = null
    var componentContext: ComponentContext? = null
    var componentViewType: Int = RenderInfoViewCreatorController.DEFAULT_COMPONENT_VIEW_TYPE
    var overrideInternalAdapter: RecyclerView.Adapter<RecyclerView.ViewHolder>? = null
    var recyclerRangeTraverser: RecyclerRangeTraverser? = null
    var stickyHeaderControllerFactory: StickyHeaderControllerFactory? = null
    var isSubAdapter: Boolean = false

    var startupLogger: LithoStartupLogger? = null
    var asyncInsertLayoutHandler: RunnableHandler? = null
    private var acquireStateHandlerOnRelease = true
    var lithoVisibilityEventsController: LithoVisibilityEventsController? = null
    private var _adapterDelegate: RecyclerBinderAdapterDelegate<RecyclerBinderViewHolder>? = null
    val adapterDelegate: RecyclerBinderAdapterDelegate<RecyclerBinderViewHolder>?
      get() = _adapterDelegate

    var additionalPostDispatchDrawListeners: List<PostDispatchDrawListener>? = null

    var errorHandler: ((Exception) -> Unit)? = null

    var poolScope: PoolScope = PoolScope.None

    /**
     * Associates a [RecyclerBinderConfig] to the [RecyclerBinder] created by this builder.
     *
     * If none is specified, it will use the default behaviors.
     */
    fun recyclerBinderConfig(config: RecyclerBinderConfig): Builder {
      recyclerBinderConfig = config
      return this
    }

    /**
     * Defaults to true. If false, when a ComponentTreeHolder is released because it exists the
     * prepared range, the StateHandler of the ComponentTree will not be cached and restored when
     * re-entering the range, so previous state will be lost.
     */
    fun acquireStateHandlerOnRelease(acquireStateHandlerOnRelease: Boolean): Builder {
      this.acquireStateHandlerOnRelease = acquireStateHandlerOnRelease
      return this
    }

    /**
     * @param layoutInfo an implementation of [LayoutInfo] that will expose information about the
     *   [RecyclerView.LayoutManager] this RecyclerBinder will use. If not set, it will default to a
     *   vertical list.
     */
    fun layoutInfo(layoutInfo: LayoutInfo): Builder {
      this.layoutInfo = layoutInfo
      return this
    }

    /** @param componentTreeHolderFactory Factory to acquire a new ComponentTreeHolder. */
    fun componentTreeHolderFactory(
        componentTreeHolderFactory: ComponentTreeHolderFactory
    ): Builder {
      this.componentTreeHolderFactory = componentTreeHolderFactory
      return this
    }

    /**
     * Enable setting custom viewTypes on [ViewRenderInfo]s.
     *
     * After this is set, all [ViewRenderInfo]s must be built with a custom viewType through
     * [ViewRenderInfo.Builder#customViewType(int)], otherwise exception will be thrown.
     *
     * @param componentViewType the viewType to be used for Component types, provided through
     *   [ComponentRenderInfo]. Set this to a value that won't conflict with your custom viewTypes.
     */
    fun enableCustomViewType(componentViewType: Int): Builder {
      this.componentViewType = componentViewType
      return this
    }

    /** Set a custom range traverser */
    fun recyclerRangeTraverser(traverser: RecyclerRangeTraverser): Builder {
      this.recyclerRangeTraverser = traverser
      return this
    }

    /**
     * Method for tests to allow mocking of the InternalAdapter to verify interaction with the
     * RecyclerView.
     */
    @VisibleForTesting
    fun overrideInternalAdapter(
        overrideInternalAdapter: RecyclerView.Adapter<RecyclerView.ViewHolder>
    ): Builder {
      this.overrideInternalAdapter = overrideInternalAdapter
      return this
    }

    /** Set a delegation to customize the adapter behaviour. */
    fun setAdapterDelegate(
        delegate: RecyclerBinderAdapterDelegate<RecyclerBinderViewHolder>
    ): Builder {
      this._adapterDelegate = delegate
      return this
    }

    /** Sets a factory to be used to create a custom controller for sticky section headers */
    fun stickyHeaderControllerFactory(
        stickyHeaderControllerFactory: StickyHeaderControllerFactory?
    ): Builder {
      this.stickyHeaderControllerFactory = stickyHeaderControllerFactory
      return this
    }

    /**
     * Note: this is an advanced usage of RecyclerBinder that requires much more manual hand-holding
     * of the RecyclerBinder than normal usage.
     *
     * In sub adapter mode, the RecyclerBinder doesn't control the entire RecyclerView, but instead
     * just a part of it. This means that the RecyclerBinder can't mount to a RecyclerView and set
     * its adapter, and it won't set a scroll listener on the RecyclerView.
     *
     * Instead, the internal adapter will need to be used/observed and plugged into some sort of
     * multi-adapter that can multiplex the RecyclerView's requests between the different sub
     * adapters.
     *
     * Additionally, since the RecyclerBinder will never mount to a RecyclerView, the owner of this
     * RecyclerBinder must manually dispatch [updateSubAdapterVisibleRange] and
     * [updateSubAdapterWorkingRange] events if this RecyclerBinder can contains more than a screens
     * worth of content.
     */
    fun isSubAdapter(isSubAdapter: Boolean): Builder {
      this.isSubAdapter = isSubAdapter
      return this
    }

    fun startupLogger(logger: LithoStartupLogger?): Builder {
      this.startupLogger = logger
      return this
    }

    fun asyncInsertLayoutHandler(handler: RunnableHandler): Builder {
      asyncInsertLayoutHandler = handler
      return this
    }

    fun lithoVisibilityEventsController(
        lithoVisibilityEventsController: LithoVisibilityEventsController
    ): Builder {
      this.lithoVisibilityEventsController = lithoVisibilityEventsController
      return this
    }

    fun addAdditionalPostDispatchDrawListeners(listeners: List<PostDispatchDrawListener>): Builder {
      this.additionalPostDispatchDrawListeners = listeners
      return this
    }

    fun errorHandler(handler: ((Exception) -> Unit)): Builder {
      this.errorHandler = handler
      return this
    }

    @ExperimentalLithoApi
    fun poolScope(poolScope: PoolScope): Builder {
      this.poolScope = poolScope
      return this
    }

    /** @param c The [ComponentContext] the RecyclerBinder will use. */
    fun build(c: ComponentContext): RecyclerBinder {
      if (recyclerBinderConfig == null) {
        recyclerBinderConfig = RecyclerBinderConfig()
      }

      this.componentContext = ComponentContext.makeCopyForNestedTree(c)
      if (lithoVisibilityEventsController == null) {
        this.lithoVisibilityEventsController = ComponentTree.getLithoVisibilityEventsController(c)
      }

      if (layoutInfo == null) {
        this.layoutInfo = LinearLayoutInfo(c.androidContext, VERTICAL, false)
      }

      if (componentTreeHolderFactory == null) {
        this.componentTreeHolderFactory =
            ComponentTreeHolderFactory {
                renderInfo: RenderInfo?,
                layoutHandler: RunnableHandler?,
                measureListenerFactory: ComponentTreeMeasureListenerFactory?,
                componentsConfiguration: ComponentsConfiguration,
                lifecycleProvider: LithoVisibilityEventsController? ->
              ComponentTreeHolder.create(componentsConfiguration)
                  .renderInfo(renderInfo)
                  .layoutHandler(layoutHandler)
                  .componentTreeMeasureListenerFactory(measureListenerFactory)
                  .lithoVisibilityEventsController(lifecycleProvider)
                  .acquireTreeStateOnRelease(acquireStateHandlerOnRelease)
                  .poolScope(poolScope)
                  .build()
            }
      }

      return RecyclerBinder(this)
    }
  }

  override fun detach() {
    if (lithoVisibilityEventsController != null) {
      return
    }

    // Since ComponentTree#release() can only be called on main thread, release the trees
    // immediately if we're on main thread, or post a runnable on main thread.
    if (ThreadUtils.isMainThread) {
      releaseComponentTreeHolders(_componentTreeHolders)
    } else {
      val toRelease: List<ComponentTreeHolder?>
      synchronized(this) { toRelease = ArrayList(_componentTreeHolders) }
      postReleaseComponentTreeHolders(toRelease)
    }
  }

  private fun releaseComponentTreeHoldersImmediatelyOrOnViewDetached(
      holders: List<ComponentTreeHolder?>
  ) {
    for (i in 0 until holders.size) {
      holders[i]?.releaseTreeImmediatelyOrOnViewDetached()
    }
  }

  private fun postReleaseComponentTreeHolders(holders: List<ComponentTreeHolder?>) {
    mainThreadHandler.post { releaseComponentTreeHolders(holders) }
  }

  @Suppress("DEPRECATION")
  @UiThread
  fun notifyItemRenderCompleteAt(position: Int, timestampMillis: Long) {
    val holder = _componentTreeHolders[position]
    val renderCompleteEventHandler = holder.renderInfo.renderCompleteEventHandler ?: return

    @ComponentTreeHolder.RenderState val state = holder.renderState
    if (state != ComponentTreeHolder.RENDER_UNINITIALIZED) {
      return
    }

    // Dispatch a RenderCompleteEvent asynchronously.
    ViewCompat.postOnAnimation(
        checkNotNull(mountedView),
        RenderCompleteRunnable(
            renderCompleteEventHandler,
            RenderCompleteEvent.RenderState.RENDER_DRAWN,
            timestampMillis))

    // Update the state to prevent dispatch an event again for the same holder.
    holder.renderState = ComponentTreeHolder.RENDER_DRAWN
  }

  /**
   * Update the item at index position. The [RecyclerView] will only be notified of the item being
   * updated after a layout calculation has been completed for the new [Component].
   */
  fun updateItemAtAsync(position: Int, renderInfo: RenderInfo?) {
    assertSingleThreadForChangeSet()

    if (SectionsDebug.ENABLED) {
      Log.d(SectionsDebug.TAG, "(${hashCode()}) updateItemAtAsync ${position}")
    }

    // TODO(t34154921): Experiment with applying new RenderInfo for updates immediately when in
    // immediate mode
    synchronized(this) {
      renderInfo?.let { addToCurrentBatch(AsyncUpdateOperation(position, renderInfo)) }
    }
  }

  /**
   * Update the items starting from the given index position. The [RecyclerView] will only be
   * notified of the item being updated after a layout calculation has been completed for the new
   * [Component].
   */
  fun updateRangeAtAsync(position: Int, renderInfos: List<@JvmSuppressWildcards RenderInfo>) {
    assertSingleThreadForChangeSet()

    if (SectionsDebug.ENABLED) {
      Log.d(
          SectionsDebug.TAG,
          "(${hashCode()}) updateRangeAtAsync ${position}, count: ${renderInfos.size}")
    }

    synchronized(this) { addToCurrentBatch(AsyncUpdateRangeOperation(position, renderInfos)) }
  }

  /**
   * Inserts an item at position. The [RecyclerView] will only be notified of the item being
   * inserted after a layout calculation has been completed for the new [Component].
   */
  fun insertItemAtAsync(position: Int, renderInfo: RenderInfo) {
    assertSingleThreadForChangeSet()

    assertNoInsertOperationIfCircular()

    if (SectionsDebug.ENABLED) {
      Log.d(
          SectionsDebug.TAG,
          "(${hashCode()}) insertItemAtAsync ${position}, name: ${renderInfo.name}")
    }

    val operation = createAsyncInsertOperation(position, renderInfo)

    synchronized(this) {
      hasAsyncOperations = true
      if (handleIndexOutOfBoundsException(
          "insertItemAtAsync",
          asyncComponentTreeHolders.size,
          position,
          true,
          renderInfo,
          errorHandler)) {
        return
      }
      asyncComponentTreeHolders.add(position, operation.holder)
      registerAsyncInsert(operation)
    }
  }

  /**
   * Inserts the new items starting from position. The [RecyclerView] will only be notified of the
   * items being inserted after a layout calculation has been completed for the new [Component]s.
   * There is not a guarantee that the [RecyclerView] will be notified about all the items in the
   * range at the same time.
   */
  fun insertRangeAtAsync(position: Int, renderInfos: List<RenderInfo?>) {
    assertSingleThreadForChangeSet()

    assertNoInsertOperationIfCircular()

    if (SectionsDebug.ENABLED) {
      val names = Array(renderInfos.size) { index -> renderInfos[index]?.name }
      Log.d(
          SectionsDebug.TAG,
          ("(${hashCode()}) insertRangeAtAsync ${position}, size: ${renderInfos.size}, names: ${names.contentToString()}"))
    }

    synchronized(this) {
      hasAsyncOperations = true
      for (i in 0 until renderInfos.size) {
        val renderInfo = renderInfos[i]
        if (handleIndexOutOfBoundsException(
            "insertRangeAtAsync",
            asyncComponentTreeHolders.size,
            position + i,
            true,
            renderInfo,
            errorHandler)) {
          return
        }
        requireNotNull(renderInfo)
        val operation = createAsyncInsertOperation(position + i, renderInfo)
        asyncComponentTreeHolders.add(position + i, operation.holder)
        registerAsyncInsert(operation)
      }
    }
  }

  private fun ensureApplyReadyBatches() {
    if (ThreadUtils.isMainThread) {
      applyReadyBatches()
    } else {
      ChoreographerCompatImpl.getInstance().postFrameCallback(applyReadyBatchesCallback)
    }
  }

  private val isRecyclerViewTargetComputingLayout: Boolean
    get() {
      val mountedView = this.mountedView
      if (mountedView != null) {
        return mountedView.isComputingLayout
      }

      val subAdapterRecyclerView = this.subAdapterRecyclerView
      if (subAdapterRecyclerView != null) {
        return subAdapterRecyclerView.isComputingLayout
      }

      return false
    }

  fun setSubAdapterModeRecyclerView(recyclerView: RecyclerView) {
    check(isSubAdapter) {
      "Cannot set a subadapter RecyclerView on a RecyclerBinder which is not in subadapter mode."
    }

    registerDrawListener(recyclerView)
    subAdapterRecyclerView = recyclerView
    isInitMounted = true
  }

  fun removeSubAdapterModeRecyclerView(recyclerView: RecyclerView) {
    check(isSubAdapter) {
      "Cannot remove a subadapter RecyclerView on a RecyclerBinder which is not in subadapter mode."
    }

    unregisterDrawListener(recyclerView)
    maybeDispatchDataRendered()
    subAdapterRecyclerView = null
  }

  @UiThread
  @VisibleForTesting
  fun applyReadyBatches() {
    applyReadyBatchesWithRetry(0)
  }

  private fun getState(recyclerView: RecyclerView): String {
    try {
      val field = RecyclerView::class.java.getDeclaredField("mState")
      field.isAccessible = true
      val state = field[recyclerView]
      return state.toString()
    } catch (e: Exception) {
      return "Exception getting state: ${e.message}"
    }
  }

  @UiThread
  private fun applyReadyBatchesWithRetry(retryCount: Int) {
    ThreadUtils.assertMainThread()

    ComponentsSystrace.trace({ "applyReadyBatches" }) {
      // Fast check that doesn't acquire lock -- measure() is locking and will post a call to
      // applyReadyBatches when it completes.
      if (!hasAsyncBatchesToCheck.get() || !_isMeasured.get() || isInMeasure.get()) {
        return
      }

      // If applyReadyBatches happens to be called from scroll of the RecyclerView (e.g. a scroll
      // event triggers a new sections root synchronously which adds a component and calls
      // applyReadyBatches), we need to postpone changing the adapter since RecyclerView asserts
      // that changes don't happen while it's in scroll/layout.
      if (isRecyclerViewTargetComputingLayout) {
        // Sanity check that we don't get stuck in an infinite loop
        if (retryCount > APPLY_READY_BATCHES_RETRY_LIMIT) {
          val mountedView = if (isSubAdapter) subAdapterRecyclerView else mountedView
          var exceptionMessage =
              "Too many retries -- RecyclerView is stuck in layout. Batch size: ${asyncBatches.size}, isSubAdapter: ${isSubAdapter}"
          exceptionMessage +=
              if (mountedView == null) {
                ", mountedView: null"
              } else {
                (", isAttachedToWindow: ${mountedView.isAttachedToWindow}, isAnimating: ${mountedView.isAnimating}, state: ${getState(mountedView)}, mountedView: ${mountedView}")
              }
          throw ComponentUtils.wrapWithMetadata(
              componentContext, RuntimeException(exceptionMessage))
        }

        // Making changes to the adapter here will crash us. Just post to the next frame boundary.
        ChoreographerCompatImpl.getInstance()
            .postFrameCallback(
                object : ChoreographerCompat.FrameCallback() {
                  override fun doFrame(frameTimeNanos: Long) {
                    applyReadyBatchesWithRetry(retryCount + 1)
                  }
                })
        return
      }

      var appliedBatch = false
      loop@ while (true) {
        var batch: AsyncBatch? = null
        var shouldBreak = false
        synchronized(this) {
          if (asyncBatches.isEmpty()) {
            hasAsyncBatchesToCheck.set(false)
            shouldBreak = true
            return@synchronized
          }
          val firstBatch = asyncBatches.peekFirst()
          batch = firstBatch
          if (firstBatch != null && !isBatchReady(firstBatch)) {
            shouldBreak = true
            return@synchronized
          }
          asyncBatches.pollFirst()
        }
        if (shouldBreak) {
          break
        }

        val computedBatch = checkNotNull(batch)
        applyBatch(computedBatch)
        appliedBatch = appliedBatch || computedBatch.isDataChanged
      }

      if (appliedBatch) {
        val logger = startupLogger
        if (logger != null && LithoStartupLogger.isEnabled(logger)) {
          startupLoggerAttribution = logger.latestDataAttribution
        }

        maybeUpdateRangeOrRemeasureForMutation()
      }
    }
  }

  @UiThread
  private fun applyBatch(batch: AsyncBatch) {
    synchronized(this) {
      for (i in 0 until batch.operations.size) {
        val operation = batch.operations[i]

        when (operation.operation) {
          Operation.INSERT -> applyAsyncInsert(operation as AsyncInsertOperation)
          Operation.UPDATE -> {
            val updateOperation = operation as AsyncUpdateOperation
            updateItemAt(updateOperation.position, updateOperation.renderInfo)
          }

          Operation.UPDATE_RANGE -> {
            val updateRangeOperation = operation as AsyncUpdateRangeOperation
            updateRangeAt(updateRangeOperation.position, updateRangeOperation.renderInfos)
          }

          Operation.REMOVE -> removeItemAt((operation as AsyncRemoveOperation).position)
          Operation.REMOVE_RANGE -> {
            val removeRangeOperation = operation as AsyncRemoveRangeOperation
            removeRangeAt(removeRangeOperation.position, removeRangeOperation.count)
          }

          Operation.MOVE -> {
            val moveOperation = operation as AsyncMoveOperation
            moveItem(moveOperation.fromPosition, moveOperation.toPosition)
          }

          else -> throw RuntimeException("Unhandled operation type: ${operation.operation}")
        }
      }
    }

    batch.changeSetCompleteCallback?.onDataBound()
    dataRenderedCallbacks.addLast(batch.changeSetCompleteCallback)
    maybeDispatchDataRendered()
  }

  @GuardedBy("this")
  @UiThread
  private fun applyAsyncInsert(operation: AsyncInsertOperation) {
    if (operation.holder.isInserted) {
      return
    }

    if (SectionsDebug.ENABLED) {
      Log.d(SectionsDebug.TAG, "(${hashCode()}) applyAsyncInsert ${operation.position}")
    }

    val renderInfo = operation.holder.renderInfo
    renderInfoViewCreatorController.maybeTrackViewCreator(renderInfo)
    if (handleIndexOutOfBoundsException(
        "applyAsyncInsert",
        _componentTreeHolders.size,
        operation.position,
        true,
        renderInfo,
        errorHandler)) {
      return
    }
    _componentTreeHolders.add(operation.position, operation.holder)
    operation.holder.isInserted = true
    internalAdapter.notifyItemInserted(operation.position)
    val shouldUpdate =
        viewportManager.insertAffectsVisibleRange(operation.position, 1, estimatedViewportCount)
    maybeScrollToTarget(operation.position, shouldUpdate)
    viewportManager.setShouldUpdate(shouldUpdate)
  }

  @GuardedBy("this")
  private fun registerAsyncInsert(operation: AsyncInsertOperation) {
    addToCurrentBatch(operation)

    val holder = operation.holder
    holder.setNewLayoutReadyListener(asyncLayoutReadyListener)
    // Otherwise, we'll kick off the layout at the end of measure
    if (isMeasured) {
      // Kicking off layout computation for all insert operations can be wasteful because some of
      // them may not in the working range. We can optimize this by respecting the working range and
      // postponing the layout computation to [maybeUpdateRangeOrRemeasureForMutation], which will
      // be invoked when we apply batch later on.
      if (ComponentsConfiguration.enableComputeLayoutAsyncAfterInsertion ||
          commitPolicy == CommitPolicy.LAYOUT_BEFORE_INSERT) {
        computeLayoutAsync(holder)
      }
    }
  }

  /**
   * Moves an item from fromPosition to toPosition. If there are other pending operations on this
   * binder this will only be executed when all the operations have been completed (to ensure index
   * consistency).
   */
  fun moveItemAsync(fromPosition: Int, toPosition: Int) {
    assertSingleThreadForChangeSet()

    if (SectionsDebug.ENABLED) {
      Log.d(SectionsDebug.TAG, "(${hashCode()}) moveItemAsync ${fromPosition} to ${toPosition}")
    }

    val operation = AsyncMoveOperation(fromPosition, toPosition)
    synchronized(this) {
      hasAsyncOperations = true
      asyncComponentTreeHolders.add(toPosition, asyncComponentTreeHolders.removeAt(fromPosition))

      // TODO(t28619782): When moving a CT into range, do an async prepare
      addToCurrentBatch(operation)
    }
  }

  /**
   * Removes an item from position. If there are other pending operations on this binder this will
   * only be executed when all the operations have been completed (to ensure index
   * consistency).Return true if the item was removed, false if the item was not removed.
   */
  fun removeItemAtAsync(position: Int) {
    assertSingleThreadForChangeSet()

    if (SectionsDebug.ENABLED) {
      Log.d(SectionsDebug.TAG, "(${hashCode()}) removeItemAtAsync ${position}")
    }

    val asyncRemoveOperation = AsyncRemoveOperation(position)
    synchronized(this) {
      hasAsyncOperations = true
      if (handleIndexOutOfBoundsException(
          "removeItemAtAsync",
          asyncComponentTreeHolders.size,
          position,
          false,
          null,
          errorHandler)) {
        return
      }
      asyncComponentTreeHolders.removeAt(position)
      addToCurrentBatch(asyncRemoveOperation)
    }
  }

  /**
   * Removes count items starting from position. If there are other pending operations on this
   * binder this will only be executed when all the operations have been completed (to ensure index
   * consistency). Return true if the range was removed, false if the range was not removed.
   */
  fun removeRangeAtAsync(position: Int, count: Int) {
    assertSingleThreadForChangeSet()

    assertNoRemoveOperationIfCircular(count)

    if (SectionsDebug.ENABLED) {
      Log.d(SectionsDebug.TAG, "(${hashCode()}) removeRangeAtAsync ${position}, size: ${count}")
    }

    val operation = AsyncRemoveRangeOperation(position, count)
    synchronized(this) {
      hasAsyncOperations = true
      for (i in 0..<count) {
        // TODO(t28712163): Cancel pending layouts for async inserts
        if (handleIndexOutOfBoundsException(
            "removeRangeAtAsync",
            asyncComponentTreeHolders.size,
            position,
            false,
            null,
            errorHandler)) {
          return
        }
        asyncComponentTreeHolders.removeAt(position)
      }
      addToCurrentBatch(operation)
    }
  }

  /** Removes all items in this binder async. */
  fun clearAsync() {
    assertSingleThreadForChangeSet()

    if (SectionsDebug.ENABLED) {
      Log.d(SectionsDebug.TAG, "(${hashCode()}) clear")
    }

    synchronized(this) {
      hasAsyncOperations = true
      val count = asyncComponentTreeHolders.size

      // TODO(t28712163): Cancel pending layouts for async inserts
      asyncComponentTreeHolders.clear()

      val operation = AsyncRemoveRangeOperation(0, count)
      addToCurrentBatch(operation)
    }
  }

  @GuardedBy("this")
  private fun addToCurrentBatch(operation: AsyncOperation) {
    if (currentBatch == null) {
      currentBatch = AsyncBatch(commitPolicy)
    }
    currentBatch?.operations?.add(operation)
  }

  /** Replaces all items in the [RecyclerBinder] with the provided [RenderInfo]s. */
  @SuppressLint("NotifyDataSetChanged")
  @UiThread
  fun replaceAll(renderInfos: List<RenderInfo>) {
    val toRelease: List<ComponentTreeHolder?>
    synchronized(this) {
      if (hasAsyncOperations) {
        throw RuntimeException("Trying to do a sync replaceAll when using asynchronous mutations!")
      }
      toRelease = ArrayList(_componentTreeHolders)
      _componentTreeHolders.clear()
      for (renderInfo in renderInfos) {
        _componentTreeHolders.add(createComponentTreeHolder(renderInfo))
      }
    }
    internalAdapter.notifyDataSetChanged()
    viewportManager.setShouldUpdate(true)

    if (ComponentsConfiguration.disableReleaseComponentTreeInRecyclerBinder) {
      // do nothing
    } else if (ComponentsConfiguration.enableFixForDisappearTransitionInRecyclerBinder) {
      // When items are removed, the corresponding views might want to disappear with animations,
      // but posting a runnable to release the ComponentTrees later may not work because the
      // animation is not started yet. Therefore, we may need to wait until the view is detached.
      releaseComponentTreeHoldersImmediatelyOrOnViewDetached(toRelease)
    } else {
      // When items are removed, the corresponding views might want to disappear with animations,
      // therefore we post a runnable to release the ComponentTrees later.
      postReleaseComponentTreeHolders(toRelease)
    }
  }

  /** See [RecyclerBinder#appendItem(RenderInfo)]. */
  @UiThread
  fun appendItem(component: Component?) {
    insertItemAt(getItemCount(), component)
  }

  /**
   * Inserts a new item at tail. The [RecyclerView] gets notified immediately about the new item
   * being inserted. If the item's position falls within the currently visible range, the layout is
   * immediately computed on the] UiThread. The RenderInfo contains the component that will be
   * inserted in the Binder and extra info like isSticky or spanCount.
   */
  @UiThread
  fun appendItem(renderInfo: RenderInfo) {
    insertItemAt(getItemCount(), renderInfo)
  }

  /** See [RecyclerBinder#insertItemAt(int, RenderInfo)]. */
  @UiThread
  fun insertItemAt(position: Int, component: Component?) {
    insertItemAt(position, ComponentRenderInfo.create().component(component).build())
  }

  /**
   * Inserts a new item at position. The [RecyclerView] gets notified immediately about the new item
   * being inserted. If the item's position falls within the currently visible range, the layout is
   * immediately computed on the] UiThread. The RenderInfo contains the component that will be
   * inserted in the Binder and extra info like isSticky or spanCount.
   */
  @UiThread
  fun insertItemAt(position: Int, renderInfo: RenderInfo?) {
    ThreadUtils.assertMainThread()

    assertNoInsertOperationIfCircular()

    if (SectionsDebug.ENABLED) {
      Log.d(
          SectionsDebug.TAG, "(${hashCode()}) insertItemAt ${position}, name: ${renderInfo?.name}")
    }

    requireNotNull(renderInfo)
    val holder = createComponentTreeHolder(renderInfo)
    synchronized(this) {
      if (hasAsyncOperations) {
        throw RuntimeException("Trying to do a sync insert when using asynchronous mutations!")
      }
      _componentTreeHolders.add(position, holder)
      renderInfoViewCreatorController.maybeTrackViewCreator(renderInfo)
    }

    internalAdapter.notifyItemInserted(position)

    val shouldAffectVisibleRange =
        viewportManager.insertAffectsVisibleRange(position, 1, estimatedViewportCount)
    maybeScrollToTarget(position, shouldAffectVisibleRange)
    viewportManager.setShouldUpdate(shouldAffectVisibleRange)
  }

  private fun getInitialMeasuredSize(
      parentWidthSpec: Int,
      parentHeightSpec: Int,
      canRemeasure: Boolean
  ): Size {
    val out = Size()
    val scrollDirection = checkNotNull(layoutInfo).getScrollDirection()

    val measuredWidth: Int
    val measuredHeight: Int

    val shouldMeasureItemForSize =
        shouldMeasureItemForSize(parentWidthSpec, parentHeightSpec, scrollDirection, canRemeasure)

    val sizeForMeasure = this.sizeForMeasure
    when (scrollDirection) {
      VERTICAL -> {
        measuredHeight = getSize(parentHeightSpec)

        measuredWidth =
            if (!shouldMeasureItemForSize) {
              getSize(parentWidthSpec)
            } else if (sizeForMeasure != null) {
              sizeForMeasure.width
            } else {
              0
            }
      }

      HORIZONTAL -> {
        measuredWidth = getSize(parentWidthSpec)

        measuredHeight =
            if (!shouldMeasureItemForSize) {
              getSize(parentHeightSpec)
            } else if (sizeForMeasure != null) {
              sizeForMeasure.height
            } else {
              0
            }
      }

      else -> {
        measuredWidth = getSize(parentWidthSpec)

        measuredHeight =
            if (!shouldMeasureItemForSize) {
              getSize(parentHeightSpec)
            } else if (sizeForMeasure != null) {
              sizeForMeasure.height
            } else {
              0
            }
      }
    }

    out.width = measuredWidth
    out.height = measuredHeight

    return out
  }

  private fun maybeRequestRemeasureIfBoundsChanged() {
    val measuredSize = checkNotNull(this.measuredSize)
    if (measuredSize.width == 0 || measuredSize.height == 0) {
      // It was measured before, but no data was bound in previous measurement,
      // therefore we need to remeasure.
      requestRemeasure()
      return
    }

    // Even after data change we may not require triggering remeasure event if bounds of
    // RecyclerView did not change.
    val initialSize = getInitialMeasuredSize(lastWidthSpec, lastHeightSpec, true)

    val wrapSize = Size()
    fillListViewport(initialSize.width, initialSize.height, wrapSize)

    if (wrapSize.width != measuredSize.width || wrapSize.height != measuredSize.height) {
      requestRemeasure()
    }
  }

  @Suppress("DEPRECATION")
  private fun requestRemeasure() {
    if (SectionsDebug.ENABLED) {
      Log.d(SectionsDebug.TAG, "(${hashCode()}) requestRemeasure")
    }

    val mountedView = mountedView
    mainThreadHandler.removeCallbacks(remeasureRunnable)
    if (mountedView != null) {
      mountedView.removeCallbacks(remeasureRunnable)
      ViewCompat.postOnAnimation(mountedView, remeasureRunnable)
    } else {
      // We are not mounted but we still need to post this. Just post on the main thread.
      mainThreadHandler.post(remeasureRunnable)
    }
  }

  /**
   * Inserts the new items starting from position. The [RecyclerView] gets notified immediately
   * about the new item being inserted. The RenderInfo contains the component that will be inserted
   * in the Binder and extra info like isSticky or spanCount.
   */
  @UiThread
  fun insertRangeAt(position: Int, renderInfos: List<RenderInfo?>) {
    ThreadUtils.assertMainThread()

    assertNoInsertOperationIfCircular()

    if (SectionsDebug.ENABLED) {
      val names = Array(renderInfos.size) { index -> renderInfos[index]?.name }
      Log.d(
          SectionsDebug.TAG,
          ("(${hashCode()}) insertRangeAt ${position}, size: ${renderInfos.size}, names: ${names.contentToString()}"))
    }

    synchronized(this) {
      for (i in 0 until renderInfos.size) {
        val renderInfo = renderInfos[i]
        requireNotNull(renderInfo)

        val holder = createComponentTreeHolder(renderInfo)
        if (hasAsyncOperations) {
          throw RuntimeException("Trying to do a sync insert when using asynchronous mutations!")
        }
        _componentTreeHolders.add(position + i, holder)
        renderInfoViewCreatorController.maybeTrackViewCreator(renderInfo)
      }
    }

    internalAdapter.notifyItemRangeInserted(position, renderInfos.size)

    val shouldAffectVisibleRange =
        viewportManager.insertAffectsVisibleRange(
            position, renderInfos.size, estimatedViewportCount)
    maybeScrollToTarget(position, shouldAffectVisibleRange)
    viewportManager.setShouldUpdate(shouldAffectVisibleRange)
  }

  private fun maybeScrollToTarget(position: Int, shouldAffectVisibleRange: Boolean) {
    if (paginationStrategy == PaginationStrategy.SCROLL_TO_LAST_VISIBLE &&
        shouldAffectVisibleRange) {
      scrollToPosition(currentLastVisiblePosition)
    } else if (paginationStrategy ==
        PaginationStrategy
            .SCROLL_TO_INSERT_POSITION // We want to only handle the situation where items are
    // inserted right above the last
    // loading item. This means that the start position of insertion should be exactly at the
    // currently visible position.
    &&
        (position ==
            currentLastVisiblePosition) // 2. We don't want to interrupt users' scrolling behavior,
        // more specifically when the
        // loading item is half visible.
        &&
        (currentFirstVisiblePosition == currentLastVisiblePosition)) {
      scrollToPosition(position)
    }
  }

  /** See [RecyclerBinder#updateItemAt(int, Component)]. */
  @UiThread
  fun updateItemAt(position: Int, component: Component?) {
    updateItemAt(position, ComponentRenderInfo.create().component(component).build())
  }

  /**
   * Updates the item at position. The [RecyclerView] gets notified immediately about the item being
   * updated. If the item's position falls within the currently visible range, the layout is
   * immediately computed on the UiThread.
   */
  @UiThread
  fun updateItemAt(position: Int, renderInfo: RenderInfo?) {
    ThreadUtils.assertMainThread()

    if (SectionsDebug.ENABLED) {
      Log.d(
          SectionsDebug.TAG, "(${hashCode()}) updateItemAt ${position}, name: ${renderInfo?.name}")
    }

    val holder: ComponentTreeHolder
    val renderInfoWasView: Boolean
    synchronized(this) {
      if (handleIndexOutOfBoundsException(
          "updateItemAt", _componentTreeHolders.size, position, false, renderInfo, errorHandler)) {
        return
      }
      holder = _componentTreeHolders[position]
      renderInfoWasView = holder.renderInfo.rendersView()

      requireNotNull(renderInfo)
      renderInfoViewCreatorController.maybeTrackViewCreator(renderInfo)
      updateHolder(holder, renderInfo)
    }

    // If this item is rendered with a view (or was rendered with a view before now) we need to
    // notify the RecyclerView's adapter that something changed.
    if (renderInfoWasView || (renderInfo?.rendersView() == true)) {
      internalAdapter.notifyItemChanged(position)
    }

    viewportManager.setShouldUpdate(viewportManager.updateAffectsVisibleRange(position, 1))
  }

  /**
   * Updates the range of items starting at position. The [RecyclerView] gets notified immediately
   * about the item being updated.
   */
  @UiThread
  fun updateRangeAt(position: Int, renderInfos: List<RenderInfo?>) {
    ThreadUtils.assertMainThread()

    if (SectionsDebug.ENABLED) {
      val names = arrayOfNulls<String>(renderInfos.size)
      for (i in renderInfos.indices) {
        names[i] = renderInfos[i]?.name
      }
      Log.d(
          SectionsDebug.TAG,
          ("(${hashCode()}) updateRangeAt ${position}, size: ${renderInfos.size}, names: ${names.contentToString()}"))
    }

    synchronized(this) {
      for (i in 0 until renderInfos.size) {
        val newRenderInfo = renderInfos[i]
        val targetPosition = position + i
        if (handleIndexOutOfBoundsException(
            "updateRangeAt",
            _componentTreeHolders.size,
            targetPosition,
            false,
            newRenderInfo,
            errorHandler)) {
          return
        }

        val holder = _componentTreeHolders[targetPosition]

        requireNotNull(newRenderInfo)

        // If this item is rendered with a view (or was rendered with a view before now) we still
        // need to notify the RecyclerView's adapter that something changed.
        if (newRenderInfo.rendersView() || holder.renderInfo.rendersView()) {
          internalAdapter.notifyItemChanged(position + i)
        }

        renderInfoViewCreatorController.maybeTrackViewCreator(newRenderInfo)
        updateHolder(holder, newRenderInfo)
      }
    }

    viewportManager.setShouldUpdate(
        viewportManager.updateAffectsVisibleRange(position, renderInfos.size))
  }

  /**
   * Moves an item from fromPosition to toPosition. If the new position of the item is within the
   * currently visible range, a layout is calculated immediately on the UI Thread.
   */
  @UiThread
  fun moveItem(fromPosition: Int, toPosition: Int) {
    ThreadUtils.assertMainThread()

    if (SectionsDebug.ENABLED) {
      Log.d(SectionsDebug.TAG, "(${hashCode()}) moveItem $fromPosition to $toPosition")
    }

    val holder: ComponentTreeHolder
    val isNewPositionInRange: Boolean
    synchronized(this) {
      if (handleIndexOutOfBoundsException(
          "moveItemFrom", _componentTreeHolders.size, fromPosition, false, null, errorHandler)) {
        return
      }
      holder = _componentTreeHolders.removeAt(fromPosition)
      if (handleIndexOutOfBoundsException(
          "moveItemTo", _componentTreeHolders.size, toPosition, true, null, errorHandler)) {
        return
      }
      _componentTreeHolders.add(toPosition, holder)
      isNewPositionInRange =
          estimatedViewportCount != UNSET &&
              (toPosition >= currentFirstVisiblePosition - (estimatedViewportCount * rangeRatio)) &&
              (toPosition <= currentLastVisiblePosition + (estimatedViewportCount * rangeRatio))
    }
    val isTreeValid = holder.isTreeValid

    if (isTreeValid && !isNewPositionInRange) {
      holder.acquireStateAndReleaseTree()
    }
    internalAdapter.notifyItemMoved(fromPosition, toPosition)

    viewportManager.setShouldUpdate(
        viewportManager.moveAffectsVisibleRange(
            fromPosition,
            toPosition,
            (currentLastVisiblePosition - currentFirstVisiblePosition + 1)))
  }

  /** Removes an item from index position. */
  @UiThread
  fun removeItemAt(position: Int) {
    ThreadUtils.assertMainThread()

    assertNoRemoveOperationIfCircular(1)

    if (SectionsDebug.ENABLED) {
      Log.d(SectionsDebug.TAG, "(${hashCode()}) removeItemAt $position")
    }

    val holder: ComponentTreeHolder?
    synchronized(this) {
      if (handleIndexOutOfBoundsException(
          "removeItemAt", _componentTreeHolders.size, position, false, null, errorHandler)) {
        return
      }
      holder = _componentTreeHolders.removeAt(position)
    }
    internalAdapter.notifyItemRemoved(position)

    viewportManager.setShouldUpdate(viewportManager.removeAffectsVisibleRange(position, 1))

    if (holder != null) {
      if (ComponentsConfiguration.disableReleaseComponentTreeInRecyclerBinder) {
        // do nothing
      } else if (ComponentsConfiguration.enableFixForDisappearTransitionInRecyclerBinder) {
        // When item is removed, the corresponding view might want to disappear with animations,
        // but posting a runnable to release the ComponentTrees later may not work because the
        // animation is not started yet. Therefore, we may need to wait until the view is
        // detached.
        holder.releaseTreeImmediatelyOrOnViewDetached()
      } else {
        // When item is removed, the corresponding view might want to disappear with an animation,
        // therefore we post a runnable to release the ComponentTree later.
        mainThreadHandler.post { holder.releaseTree() }
      }
    }
  }

  @get:Synchronized
  @get:VisibleForTesting
  val measuredHeight: Int
    get() = checkNotNull(measuredSize).height

  @get:Synchronized
  @get:VisibleForTesting
  val measuredWidth: Int
    get() = checkNotNull(measuredSize).width

  /** Removes count items starting from position. */
  @UiThread
  fun removeRangeAt(position: Int, count: Int) {
    ThreadUtils.assertMainThread()

    assertNoRemoveOperationIfCircular(count)

    if (SectionsDebug.ENABLED) {
      Log.d(SectionsDebug.TAG, "(${hashCode()}) removeRangeAt ${position}, size: $count")
    }

    val toRelease: MutableList<ComponentTreeHolder?> = ArrayList()
    synchronized(this) {
      for (i in 0..<count) {
        if (handleIndexOutOfBoundsException(
            "removeRangeAt", _componentTreeHolders.size, position, false, null, errorHandler)) {
          return
        }
        val holder = _componentTreeHolders.removeAt(position)
        toRelease.add(holder)
      }
    }
    internalAdapter.notifyItemRangeRemoved(position, count)

    viewportManager.setShouldUpdate(viewportManager.removeAffectsVisibleRange(position, count))

    if (ComponentsConfiguration.disableReleaseComponentTreeInRecyclerBinder) {
      // do nothing
    } else if (ComponentsConfiguration.enableFixForDisappearTransitionInRecyclerBinder) {
      // When items are removed, the corresponding views might want to disappear with animations,
      // but posting a runnable to release the ComponentTrees later may not work because the
      // animation is not started yet. Therefore, we may need to wait until the view is detached.
      releaseComponentTreeHoldersImmediatelyOrOnViewDetached(toRelease)
    } else {
      // When items are removed, the corresponding views might want to disappear with animations,
      // therefore we post a runnable to release the ComponentTrees later.
      postReleaseComponentTreeHolders(toRelease)
    }
  }

  /**
   * Called after all the change set operations (inserts, removes, etc.) in a batch have completed.
   * Async variant, may be called off the main thread.
   */
  fun notifyChangeSetCompleteAsync(
      isDataChanged: Boolean,
      changeSetCompleteCallback: ChangeSetCompleteCallback
  ) {
    val isTracing = isTracing
    if (isTracing) {
      ComponentsSystrace.beginSection("notifyChangeSetCompleteAsync")
    }
    try {
      if (SectionsDebug.ENABLED) {
        Log.d(SectionsDebug.TAG, "(${hashCode()}) notifyChangeSetCompleteAsync")
      }

      hasAsyncOperations = true

      assertSingleThreadForChangeSet()
      closeCurrentBatch(isDataChanged, changeSetCompleteCallback)
      if (ThreadUtils.isMainThread) {
        applyReadyBatches()
        if (isDataChanged) {
          val logger = startupLogger
          if (logger != null && LithoStartupLogger.isEnabled(logger)) {
            startupLoggerAttribution = logger.latestDataAttribution
          }

          maybeUpdateRangeOrRemeasureForMutation()
        }
      } else {
        // measure() will post this for us
        if (_isMeasured.get()) {
          ChoreographerCompatImpl.getInstance().postFrameCallback(applyReadyBatchesCallback)
        }
      }
      clearThreadForChangeSet()
    } finally {
      if (isTracing) {
        ComponentsSystrace.endSection()
      }
    }
  }

  /**
   * Called after all the change set operations (inserts, removes, etc.) in a batch have completed.
   */
  @UiThread
  fun notifyChangeSetComplete(
      isDataChanged: Boolean,
      changeSetCompleteCallback: ChangeSetCompleteCallback?
  ) {
    val isTracing = isTracing
    if (isTracing) {
      ComponentsSystrace.beginSection("notifyChangeSetComplete")
    }
    try {
      if (SectionsDebug.ENABLED) {
        Log.d(SectionsDebug.TAG, "(${hashCode()}) notifyChangeSetComplete")
      }

      ThreadUtils.assertMainThread()

      if (hasAsyncOperations) {
        throw RuntimeException(
            "Trying to do a sync notifyChangeSetComplete when using asynchronous mutations!")
      }

      if (changeSetCompleteCallback != null) {
        changeSetCompleteCallback.onDataBound()
        dataRenderedCallbacks.addLast(changeSetCompleteCallback)
      }
      maybeDispatchDataRendered()

      if (isDataChanged) {
        val logger = startupLogger
        if (logger != null && LithoStartupLogger.isEnabled(logger)) {
          startupLoggerAttribution = logger.latestDataAttribution
        }

        maybeUpdateRangeOrRemeasureForMutation()
      }
    } finally {
      if (isTracing) {
        ComponentsSystrace.endSection()
      }
    }
  }

  @GuardedBy("this")
  private fun maybeFillHScrollViewport() {
    if (!hScrollAsyncMode || hasFilledViewport) {
      return
    }

    // Now that we're filling, all new batches should be inserted async to not drop frames
    commitPolicy = CommitPolicy.LAYOUT_BEFORE_INSERT

    if (ThreadUtils.isMainThread) {
      applyReadyBatches()
    } else {
      val measuredSize = checkNotNull(this.measuredSize)
      if (_componentTreeHolders.isNotEmpty()) {
        fillListViewport(measuredSize.width, measuredSize.height, null)
      } else if (asyncBatches.isNotEmpty()) {
        val insertsInFirstBatch: MutableList<ComponentTreeHolder?> = ArrayList()
        for (operation in asyncBatches.first.operations) {
          if (operation is AsyncInsertOperation) {
            insertsInFirstBatch.add(operation.holder)
          }
        }
        computeLayoutsToFillListViewport(
            insertsInFirstBatch, 0, measuredSize.width, measuredSize.height, null)
      }

      ChoreographerCompatImpl.getInstance().postFrameCallback(applyReadyBatchesCallback)
    }

    hasFilledViewport = true
  }

  @ThreadConfined(ThreadConfined.UI)
  private fun maybeDispatchDataRendered() {
    ThreadUtils.assertMainThread()
    if (dataRenderedCallbacks.isEmpty()) {
      // early return if no pending dataRendered callbacks.
      return
    }

    if (!isInitMounted) {
      // The view isn't mounted yet, OnDataRendered callbacks are postponed until mount() is called,
      // and ViewGroup#dispatchDraw(Canvas) should take care triggering OnDataRendered callbacks.
      return
    }

    val recyclerView = if (isSubAdapter) subAdapterRecyclerView else mountedView

    // Execute onDataRendered callbacks immediately if the view has been unmounted, finishes
    // dispatchDraw (no pending updates), is detached, or is visible.
    if (recyclerView == null ||
        !recyclerView.hasPendingAdapterUpdates() ||
        !recyclerView.isAttachedToWindow ||
        !isVisibleToUser(recyclerView)) {
      val isMounted = recyclerView != null
      val snapshotCallbacks: Deque<ChangeSetCompleteCallback?> = ArrayDeque(dataRenderedCallbacks)
      dataRenderedCallbacks.clear()
      mainThreadHandler.postAtFrontOfQueue {
        val uptimeMillis = SystemClock.uptimeMillis()
        while (!snapshotCallbacks.isEmpty()) {
          snapshotCallbacks.pollFirst()?.onDataRendered(isMounted, uptimeMillis)
        }
      }
    } else {
      if (dataRenderedCallbacks.size > DATA_RENDERED_CALLBACKS_QUEUE_MAX_SIZE) {
        dataRenderedCallbacks.clear()
        val messageBuilder = StringBuilder()
        messageBuilder
            .append("recyclerView: ")
            .append(recyclerView)
            .append(", hasPendingAdapterUpdates(): ")
            .append(recyclerView.hasPendingAdapterUpdates())
            .append(", isAttachedToWindow(): ")
            .append(recyclerView.isAttachedToWindow)
            .append(", getWindowVisibility(): ")
            .append(recyclerView.windowVisibility)
            .append(", vie visible hierarchy: ")
            .append(getVisibleHierarchy(recyclerView))
            .append(", getGlobalVisibleRect(): ")
            .append(recyclerView.getGlobalVisibleRect(dummyRect))
            .append(", isComputingLayout(): ")
            .append(recyclerView.isComputingLayout)
            .append(", isSubAdapter: ")
            .append(isSubAdapter)
        messageBuilder
            .append(", visible range: [")
            .append(currentFirstVisiblePosition)
            .append(", ")
            .append(currentLastVisiblePosition)
            .append("]")
        ComponentsReporter.emitMessage(
            ComponentsReporter.LogLevel.ERROR,
            DATA_RENDERED_NOT_TRIGGERED,
            "@OnDataRendered callbacks aren't triggered as expected: $messageBuilder")
      }
    }

    // Otherwise we'll wait for ViewGroup#dispatchDraw(Canvas), which would call this method again
    // to execute onDataRendered callbacks.
  }

  @Synchronized
  private fun closeCurrentBatch(
      isDataChanged: Boolean,
      changeSetCompleteCallback: ChangeSetCompleteCallback
  ) {
    if (currentBatch == null) {
      // We create a batch here even if it doesn't have any operations: this is so we can still
      // invoke the OnDataBoundListener at the appropriate time (after all preceding batches
      // complete)
      currentBatch = AsyncBatch(commitPolicy)
    }

    currentBatch?.isDataChanged = isDataChanged
    currentBatch?.changeSetCompleteCallback = changeSetCompleteCallback
    asyncBatches.addLast(currentBatch)
    hasAsyncBatchesToCheck.set(true)
    currentBatch = null
  }

  private fun maybeUpdateRangeOrRemeasureForMutation() {
    if (!_isMeasured.get()) {
      return
    }

    if (requiresRemeasure.get() || isMainAxisWrapContent) {
      maybeRequestRemeasureIfBoundsChanged()
      if (!isMainAxisWrapContent) {
        return
      }
    }

    if (!hasComputedRange()) {
      val initialComponentPosition =
          findInitialComponentPosition(_componentTreeHolders, traverseLayoutBackwards)
      if (initialComponentPosition >= 0) {
        val measuredSize = checkNotNull(this.measuredSize)
        val holderRangeInfo =
            ComponentTreeHolderRangeInfo(initialComponentPosition, _componentTreeHolders)
        initRange(measuredSize.width, measuredSize.height, holderRangeInfo)
      }
    }

    maybePostUpdateViewportAndComputeRange()
  }

  private fun assertSingleThreadForChangeSet() {
    if (!LithoDebugConfigurations.isDebugModeEnabled &&
        !ComponentsConfiguration.isEndToEndTestRun) {
      return
    }

    val currentThreadId = Thread.currentThread().id
    val previousThreadId = currentChangeSetThreadId.getAndSet(currentThreadId)

    check(!(currentThreadId != previousThreadId && previousThreadId != -1L)) {
      ("Multiple threads applying change sets at once! (${previousThreadId} and ${currentThreadId})")
    }
  }

  private fun clearThreadForChangeSet() {
    if (!LithoDebugConfigurations.isDebugModeEnabled &&
        !ComponentsConfiguration.isEndToEndTestRun) {
      return
    }

    currentChangeSetThreadId.set(-1)
  }

  /**
   * Returns the [ComponentTree] for the item at index position. TODO 16212132 remove getComponentAt
   * from binder
   */
  @Synchronized
  override fun getComponentAt(position: Int): ComponentTree? =
      _componentTreeHolders[position].componentTree

  override fun getComponentForStickyHeaderAt(position: Int): ComponentTree? {
    val holder =
        if (ComponentsConfiguration.enableFixForStickyHeader) {
          // As this method is called from the main thread, we can safely access the list without a
          // lock.
          _componentTreeHolders[position]
        } else {
          getComponentTreeHolderAt(position)
        }

    val measuredSize: Size?
    val lastWidthSpec: Int
    val lastHeightSpec: Int
    synchronized(this) {
      measuredSize = this.measuredSize
      lastWidthSpec = this.lastWidthSpec
      lastHeightSpec = this.lastHeightSpec
    }
    val childrenWidthSpec = getActualChildrenWidthSpec(holder, measuredSize, lastWidthSpec)
    val childrenHeightSpec = getActualChildrenHeightSpec(holder, measuredSize, lastHeightSpec)

    if (holder.isTreeValidForSizeSpecs(childrenWidthSpec, childrenHeightSpec)) {
      return holder.componentTree
    }

    // This could happen when RecyclerView is populated with new data, and first position is not 0.
    // It is possible that sticky header is above the first visible position and also it is outside
    // calculated range and its layout has not been calculated yet.
    holder.computeLayoutSync(
        checkNotNull(componentContext), childrenWidthSpec, childrenHeightSpec, null)

    return holder.componentTree
  }

  /**
   * @return the RenderInfo at this position. Since this list is modified on the main thread, this
   *   function may only be called from the main thread.
   */
  @UiThread
  @Synchronized
  override fun getRenderInfoAt(position: Int): RenderInfo {
    ThreadUtils.assertMainThread()
    return _componentTreeHolders[position].renderInfo
  }

  /**
   * @return the ComponentTreeHolder at this position. Since this list is modified on the main
   *   thread, this function may only be called from the main thread.
   */
  @UiThread
  @Synchronized
  fun getComponentTreeHolderAt(position: Int): ComponentTreeHolder {
    ThreadUtils.assertMainThread()
    return _componentTreeHolders[position]
  }

  @get:Synchronized
  @get:VisibleForTesting
  val componentTreeHolders: List<ComponentTreeHolder>
    get() = _componentTreeHolders

  /**
   * A component mounting a RecyclerView can use this method to determine its size. A Recycler that
   * scrolls horizontally will leave the width unconstrained and will measure its children with a
   * sizeSpec for the height matching the heightSpec passed to this method.
   *
   * If padding is defined on the parent component it should be subtracted from the parent size
   * specs before passing them to this method.
   *
   * Currently we can't support the equivalent of MATCH_PARENT on the scrollDirection (so for
   * example we don't support MATCH_PARENT on width in an horizontal RecyclerView). This is mainly
   * because we don't have the equivalent of LayoutParams in components. We can extend the api of
   * the binder in the future to provide some more layout hints in order to support this.
   *
   * @param outSize will be populated with the measured dimensions for this Binder.
   * @param widthSpec the widthSpec to be used to measure the RecyclerView.
   * @param heightSpec the heightSpec to be used to measure the RecyclerView.
   * @param reMeasureEventHandler the EventHandler to invoke in order to trigger a re-measure.
   */
  override fun measure(
      outSize: Size,
      widthSpec: Int,
      heightSpec: Int,
      reMeasureEventHandler: EventHandler<ReMeasureEvent>?
  ) {
    // This is a hack to try to give a signal to applyReadyBatches whether it should even attempt
    // to acquire the lock or bail and let measure schedule it as a runnable. This can go away
    // once we break up the locking in measure.
    // TODO(t37195892): Do not hold lock throughout measure call in RecyclerBinder
    val canRemeasure = reMeasureEventHandler != null
    val scrollDirection = checkNotNull(layoutInfo).getScrollDirection()

    validateMeasureSpecs(mountedView, widthSpec, heightSpec, canRemeasure, scrollDirection)

    val shouldMeasureItemForSize =
        shouldMeasureItemForSize(widthSpec, heightSpec, scrollDirection, canRemeasure)
    if (hasManualEstimatedViewportCount && shouldMeasureItemForSize) {
      throw RuntimeException(
          "Cannot use manual estimated viewport count when the RecyclerBinder needs an item to determine its size!")
    }

    isInMeasure.set(true)

    try {
      synchronized(this) {
        if (lastWidthSpec != UNINITIALIZED && !requiresRemeasure.get() && !isMainAxisWrapContent) {
          val measuredSize = this.measuredSize
          when (scrollDirection) {
            VERTICAL ->
                if (measuredSize != null &&
                    MeasureComparisonUtils.isMeasureSpecCompatible(
                        lastWidthSpec, widthSpec, measuredSize.width)) {
                  outSize.width = measuredSize.width
                  outSize.height = getSize(heightSpec)

                  return
                }

            else ->
                if (measuredSize != null &&
                    MeasureComparisonUtils.isMeasureSpecCompatible(
                        lastHeightSpec, heightSpec, measuredSize.height)) {
                  outSize.width = getSize(widthSpec)
                  outSize.height = measuredSize.height

                  return
                }
          }

          _isMeasured.set(false)
          invalidateLayoutData()
        }
        // We have never measured before or the measures are not valid so we need to measure now.
        lastWidthSpec = widthSpec
        lastHeightSpec = heightSpec

        if (!hasComputedRange()) {
          val holderForRangeInfo = holderForRangeInfo
          if (holderForRangeInfo != null) {
            initRange(getSize(widthSpec), getSize(heightSpec), holderForRangeInfo)
          }
        }

        val initialMeasuredSize = this.getInitialMeasuredSize(widthSpec, heightSpec, canRemeasure)

        // At this point we might still not have a range. In this situation we should return the
        // best size we can detect from the size spec and update it when the first item comes in.
        when (scrollDirection) {
          VERTICAL ->
              if (!shouldMeasureItemForSize || sizeForMeasure != null) {
                reMeasureEventEventHandler =
                    if (isMainAxisWrapContent) reMeasureEventHandler else null
              } else {
                reMeasureEventEventHandler = reMeasureEventHandler
                requiresRemeasure.set(!isMainAxisWrapContent)
              }

          HORIZONTAL ->
              if (!shouldMeasureItemForSize || sizeForMeasure != null) {
                reMeasureEventEventHandler =
                    if (hasDynamicItemHeight || isMainAxisWrapContent) reMeasureEventHandler
                    else null
                requiresRemeasure.set(hasDynamicItemHeight)
              } else {
                reMeasureEventEventHandler = reMeasureEventHandler
                requiresRemeasure.set(!isMainAxisWrapContent)
              }

          else ->
              if (!shouldMeasureItemForSize || sizeForMeasure != null) {
                reMeasureEventEventHandler =
                    if (hasDynamicItemHeight || isMainAxisWrapContent) reMeasureEventHandler
                    else null
                requiresRemeasure.set(hasDynamicItemHeight)
              } else {
                reMeasureEventEventHandler = reMeasureEventHandler
                requiresRemeasure.set(!isMainAxisWrapContent)
              }
        }

        if (isMainAxisWrapContent) {
          val wrapSize = Size()
          fillListViewport(initialMeasuredSize.width, initialMeasuredSize.height, wrapSize)
          outSize.width = wrapSize.width
          outSize.height = wrapSize.height
        } else {
          outSize.width = initialMeasuredSize.width
          outSize.height = initialMeasuredSize.height
        }

        measuredSize = Size(outSize.width, outSize.height)
        _isMeasured.set(true)

        componentWarmer?.setComponentTreeHolderFactory(componentTreeHolderPreparer)

        maybeFillHScrollViewport()
        updateAsyncInsertOperations()
        if (estimatedViewportCount != RecyclerView.NO_POSITION) {
          computeRange(currentFirstVisiblePosition, currentLastVisiblePosition)
        }
      }
    } finally {
      isInMeasure.set(false)
      if (hasAsyncOperations) {
        ensureApplyReadyBatches()
      }
    }
  }

  private val isMeasured: Boolean
    /** @return true if the view is measured and doesn't need remeasuring. */
    get() = _isMeasured.get() && !requiresRemeasure.get()

  @GuardedBy("this")
  private fun fillListViewport(maxWidth: Int, maxHeight: Int, outSize: Size?) {
    val isTracing = isTracing
    if (isTracing) {
      ComponentsSystrace.beginSection("fillListViewport")
    }

    val firstVisiblePosition =
        if (isMainAxisWrapContent) 0 else checkNotNull(layoutInfo).findFirstVisibleItemPosition()

    // NB: This does not handle 1) partially visible items 2) item decorations
    val startIndex =
        if (firstVisiblePosition != RecyclerView.NO_POSITION) firstVisiblePosition else 0

    computeLayoutsToFillListViewport(
        _componentTreeHolders, startIndex, maxWidth, maxHeight, outSize)

    if (!hasComputedRange()) {
      val holderForRangeInfo = holderForRangeInfo
      if (holderForRangeInfo != null) {
        initRange(maxWidth, maxHeight, holderForRangeInfo)
      }
    }

    if (isTracing) {
      ComponentsSystrace.endSection()
    }
  }

  @VisibleForTesting
  @GuardedBy("this")
  fun computeLayoutsToFillListViewport(
      holders: List<ComponentTreeHolder?>,
      offset: Int,
      maxWidth: Int,
      maxHeight: Int,
      outputSize: Size?
  ): Int {
    val filler = layoutInfo?.createViewportFiller(maxWidth, maxHeight) ?: return 0

    val isTracing = isTracing
    if (isTracing) {
      ComponentsSystrace.beginSection("computeLayoutsToFillListViewport")
    }

    val widthSpec = makeSizeSpec(maxWidth, SizeSpec.EXACTLY)
    val heightSpec = makeSizeSpec(maxHeight, SizeSpec.EXACTLY)
    val outSize = Size()

    var numInserted = 0
    var index = offset
    while (filler.wantsMore() && index < holders.size) {
      val holder = checkNotNull(holders[index])
      val renderInfo = holder.renderInfo

      // Bail as soon as we see a View since we can't tell what height it is and don't want to
      // layout too much :(
      if (renderInfo.rendersView()) {
        break
      }

      holder.computeLayoutSync(
          checkNotNull(componentContext),
          layoutInfo.getChildWidthSpec(widthSpec, renderInfo),
          layoutInfo.getChildHeightSpec(heightSpec, renderInfo),
          outSize)

      filler.add(renderInfo, outSize.width, outSize.height)

      index++
      numInserted++
    }

    if (outputSize != null) {
      val fill = filler.getFill()
      if (layoutInfo.getScrollDirection() == VERTICAL) {
        outputSize.width = maxWidth
        outputSize.height = min(fill, maxHeight)
      } else {
        outputSize.width = min(fill, maxWidth)
        outputSize.height = maxHeight
      }
    }

    if (isTracing) {
      ComponentsSystrace.endSection()
    }

    logFillViewportInserted(numInserted, holders.size)

    return numInserted
  }

  private fun logFillViewportInserted(numInserted: Int, totalSize: Int) {
    if (SectionsDebug.ENABLED) {
      Log.d(
          SectionsDebug.TAG,
          ("(${hashCode()}) filled viewport with ${numInserted} items (holder.size() = ${totalSize})"))
    }
  }

  @GuardedBy("this")
  private fun updateAsyncInsertOperations() {
    for (batch in asyncBatches) {
      updateBatch(batch)
    }
    val currentBatch = this.currentBatch
    if (currentBatch != null) {
      updateBatch(currentBatch)
    }
  }

  @GuardedBy("this")
  private fun updateBatch(batch: AsyncBatch) {
    for (operation in batch.operations) {
      if (operation !is AsyncInsertOperation) {
        continue
      }

      val holder = operation.holder
      computeLayoutAsync(holder)
    }
  }

  @SuppressLint("VisibleForTests")
  @GuardedBy("this")
  private fun computeLayoutAsync(holder: ComponentTreeHolder) {
    // If there's an existing async layout that's compatible, this is a no-op. Otherwise, that
    // computation will be canceled (if it hasn't started) and this new one will run.
    val widthSpec = getActualChildrenWidthSpec(holder, measuredSize, lastWidthSpec)
    val heightSpec = getActualChildrenHeightSpec(holder, measuredSize, lastHeightSpec)

    if (holder.isTreeValidForSizeSpecs(widthSpec, heightSpec)) {
      if (holder.hasCompletedLatestLayout()) {
        val componentTree = holder.componentTree
        val listener = componentTree?.newLayoutStateReadyListener
        if (listener != null) {
          componentTree.newLayoutStateReadyListener = null
        }
      }

      return
    }

    if (asyncInsertHandler != null) {
      asyncInsertHandler.post(
          {
            holder.computeLayoutSync(checkNotNull(componentContext), widthSpec, heightSpec, Size())
          },
          "AsyncInsertLayout")
    } else {
      holder.computeLayoutAsync(checkNotNull(componentContext), widthSpec, heightSpec)
    }
  }

  /**
   * Gets the number of items currently in the adapter attached to this binder (i.e. the number of
   * items the underlying RecyclerView knows about).
   */
  override fun getItemCount(): Int = internalAdapter.itemCount

  /**
   * Insert operation is not supported in case of circular recycler unless it is initial insert
   * because the indexes universe gets messed.
   */
  private fun assertNoInsertOperationIfCircular() {
    if (isCircular && _componentTreeHolders.isNotEmpty()) {
      // Initialization of a list happens using insertRangeAt() or insertAt() operations,
      // so skip this check when mComponentTreeHolders was not populated yet
      throw UnsupportedOperationException("Circular lists do not support insert operation")
    }
  }

  /**
   * Remove operation is not supported in case of circular recycler unless it's a removal if all
   * items because indexes universe gets messed.
   */
  @GuardedBy("this")
  private fun assertNoRemoveOperationIfCircular(removeCount: Int) {
    if (isCircular &&
        _componentTreeHolders.isNotEmpty() &&
        _componentTreeHolders.size != removeCount) {
      // Allow only removal of all elements in case on notifyDataSetChanged() call
      throw UnsupportedOperationException("Circular lists do not support insert operation")
    }
  }

  @SuppressLint("NotifyDataSetChanged")
  @GuardedBy("this")
  private fun invalidateLayoutData() {
    val isTracing = isTracing
    if (isTracing) {
      ComponentsSystrace.beginSection("invalidateLayoutData")
    }

    if (!hasManualEstimatedViewportCount) {
      estimatedViewportCount = UNSET
    }

    sizeForMeasure = null
    for (i in 0 until _componentTreeHolders.size) {
      _componentTreeHolders[i].invalidateTree()
    }

    // We need to call this as we want to make sure everything is re-bound since we need new sizes
    // on all rows.
    if (Looper.myLooper() == Looper.getMainLooper() && !isRecyclerViewTargetComputingLayout) {
      internalAdapter.notifyDataSetChanged()
    } else {
      mainThreadHandler.removeCallbacks(notifyDatasetChangedRunnable)
      mainThreadHandler.post(notifyDatasetChangedRunnable)
    }

    if (isTracing) {
      ComponentsSystrace.endSection()
    }
  }

  @GuardedBy("this")
  private fun maybeScheduleAsyncLayoutsDuringInitRange(
      asyncRangeIterator: ComponentAsyncInitRangeIterator
  ) {
    if (_componentTreeHolders.isEmpty()) {
      // checked null for tests
      return
    }

    maybeScheduleOneAsyncLayoutDuringInitRange(asyncRangeIterator)
  }

  private fun maybeScheduleOneAsyncLayoutDuringInitRange(
      asyncRangeIterator: ComponentAsyncInitRangeIterator
  ) {
    val nextHolder = asyncRangeIterator.next()

    if (_componentTreeHolders.isEmpty() || nextHolder == null || estimatedViewportCount != UNSET) {
      // checked null for tests
      return
    }

    val measuredSize: Size?
    val lastWidthSpec: Int
    val lastHeightSpec: Int
    synchronized(this@RecyclerBinder) {
      measuredSize = this.measuredSize
      lastWidthSpec = this.lastWidthSpec
      lastHeightSpec = this.lastHeightSpec
    }
    val childWidthSpec = getActualChildrenWidthSpec(nextHolder, measuredSize, lastWidthSpec)
    val childHeightSpec = getActualChildrenHeightSpec(nextHolder, measuredSize, lastHeightSpec)
    if (nextHolder.isTreeValidForSizeSpecs(childWidthSpec, childHeightSpec)) {
      return
    }

    val measureListener: MeasureListener =
        object : MeasureListener {
          override fun onSetRootAndSizeSpec(
              layoutVersion: Int,
              w: Int,
              h: Int,
              stateUpdate: Boolean
          ) {
            maybeScheduleOneAsyncLayoutDuringInitRange(asyncRangeIterator)
            nextHolder.clearMeasureListener(this)
          }
        }

    nextHolder.computeLayoutAsync(
        checkNotNull(componentContext), childWidthSpec, childHeightSpec, measureListener)
  }

  @VisibleForTesting
  @GuardedBy("this")
  fun initRange(width: Int, height: Int, holderRangeInfo: ComponentTreeHolderRangeInfo) {
    if (hasManualEstimatedViewportCount) {
      return
    }
    val isTracing = isTracing
    val loggingForStartup = LithoStartupLogger.isEnabled(startupLogger)

    // We can schedule a maximum of number of items minus one (which is being calculated
    // synchronously) to run at the same time as the sync layout.
    val asyncInitRangeIterator =
        ComponentAsyncInitRangeIterator(
            holderRangeInfo.holders,
            holderRangeInfo.position,
            _componentTreeHolders.size - 1,
            traverseLayoutBackwards)

    if (isTracing) {
      ComponentsSystrace.beginSection("maybeScheduleAsyncLayoutsDuringInitRange")
    }
    maybeScheduleAsyncLayoutsDuringInitRange(asyncInitRangeIterator)
    if (isTracing) {
      ComponentsSystrace.endSection()
    }

    val holder = checkNotNull(holderRangeInfo.holders[holderRangeInfo.position])
    val childWidthSpec = getActualChildrenWidthSpec(holder, measuredSize, lastWidthSpec)
    val childHeightSpec = getActualChildrenHeightSpec(holder, measuredSize, lastHeightSpec)

    if (loggingForStartup) {
      startupLogger?.markPoint(
          LithoStartupLogger.FIRST_LAYOUT, LithoStartupLogger.START, startupLoggerAttribution)
    }
    if (isTracing) {
      ComponentsSystrace.beginSection("firstLayout")
    }
    val logger: ComponentsLogger?
    val logTag: String?
    val componentContext = checkNotNull(this.componentContext)
    if (componentContext.logger != null) {
      logger = componentContext.logger
      logTag = componentContext.logTag
    } else {
      logger = holder.renderInfo.componentsLogger
      logTag = holder.renderInfo.logTag
    }
    val logInitRange =
        if (logger == null) null
        else
            LogTreePopulator.populatePerfEventFromLogger(
                componentContext,
                logger,
                logTag,
                logger.newPerformanceEvent(FrameworkLogEvents.EVENT_INIT_RANGE))

    try {
      val size = Size()
      holder.computeLayoutSync(componentContext, childWidthSpec, childHeightSpec, size)

      val rangeSize =
          max(
              checkNotNull(layoutInfo).approximateRangeSize(size.width, size.height, width, height),
              1)

      sizeForMeasure = size
      estimatedViewportCount = rangeSize
    } finally {
      if (logInitRange != null) {
        logger?.logPerfEvent(logInitRange)
      }
      if (isTracing) {
        ComponentsSystrace.endSection()
      }
      if (loggingForStartup) {
        startupLogger?.markPoint(
            LithoStartupLogger.FIRST_LAYOUT, LithoStartupLogger.END, startupLoggerAttribution)
      }
    }
  }

  @GuardedBy("this")
  private fun resetMeasuredSize(width: Int) {
    // we will set a range anyway if it's null, no need to do this now.
    val sizeForMeasure = this.sizeForMeasure
    if (sizeForMeasure == null || hasManualEstimatedViewportCount) {
      return
    }
    var maxHeight = 0

    for (i in 0 until _componentTreeHolders.size) {
      val holder = _componentTreeHolders[i]
      val measuredItemHeight = holder.measuredHeight
      if (measuredItemHeight > maxHeight) {
        maxHeight = measuredItemHeight
      }
    }

    if (maxHeight == sizeForMeasure.height) {
      return
    }

    val rangeSize =
        max(
            checkNotNull(layoutInfo)
                .approximateRangeSize(
                    getSize(lastWidthSpec), getSize(lastHeightSpec), width, maxHeight),
            1)

    sizeForMeasure.height = maxHeight
    estimatedViewportCount = rangeSize
  }

  /**
   * This should be called when the owner [Component]'s onBoundsDefined is called. It will inform
   * the binder of the final measured size. The binder might decide to re-compute its children
   * layouts if the measures provided here are not compatible with the ones receive in onMeasure.
   */
  @Synchronized
  override fun setSize(width: Int, height: Int) {
    if (lastWidthSpec == UNINITIALIZED ||
        !isCompatibleSize(
            makeSizeSpec(width, SizeSpec.EXACTLY), makeSizeSpec(height, SizeSpec.EXACTLY))) {
      measure(
          dummySize,
          makeSizeSpec(width, SizeSpec.EXACTLY),
          makeSizeSpec(height, SizeSpec.EXACTLY),
          reMeasureEventEventHandler)
    }
  }

  /**
   * Call from the owning [Component]'s onMount. This is where the adapter is assigned to the
   * [RecyclerView].
   *
   * @param view the [RecyclerView] being mounted.
   */
  @UiThread
  override fun mount(view: RecyclerView) {
    ThreadUtils.assertMainThread()
    if (isSubAdapter) {
      throw RuntimeException("Can't mount a RecyclerView in sub adapter mode")
    }

    val mountedView = this.mountedView
    if (mountedView === view) {
      return
    }

    if (mountedView != null) {
      unmount(mountedView)
    }

    // In cases where we are mounting H-Scrolls, it's possible that the parent RecyclerView blocked
    // on their layout/section computation via a LayoutState future, but the runnable to update
    // the adapter on the main thread hasn't run -- this gives a chance to update the adapter before
    // we attach to this RecyclerView.
    if (hasAsyncOperations) {
      applyReadyBatches()
    }

    // The first time we mount with a circular collection, offset its mCurrentFirstVisiblePosition
    // by Integer.MAX_VALUE / 2. We only want to do this once if the position is invalid or hasn't
    // been mounted before. This is so that the value saved in unmount doesn't keep getting the
    // offset added to it.
    if (isCircular) {
      if (!isInitMounted || currentFirstVisiblePosition < 0) {
        val jumpToMiddle = Int.MAX_VALUE / 2
        val offsetFirstItem =
            if (_componentTreeHolders.isEmpty()) 0 else (jumpToMiddle % _componentTreeHolders.size)
        currentFirstVisiblePosition =
            max(0, currentFirstVisiblePosition) + jumpToMiddle - offsetFirstItem
      }
    }

    this.mountedView = view
    isInitMounted = true

    val layoutManager = layoutInfo?.getLayoutManager()

    // ItemPrefetching feature of RecyclerView clashes with RecyclerBinder's compute range
    // optimization and in certain scenarios (like sticky header) it might reset ComponentTree of
    // LithoView while it is still on screen making it render blank or zero height.
    layoutManager?.isItemPrefetchEnabled = recyclerViewItemPrefetch
    view.setItemViewCacheSize(itemViewCacheSize)

    // This will force padding to be resolved on the main thread before the LayoutManager finds out
    // about this view. This will keep padding from trying to be resolved later on from a bg thread.
    // See T41844038. Longer term, it isn't safe to ever get the padding from a bg thread and it
    // will need to be passed manually to the RecyclerBinder
    view.paddingLeft

    view.layoutManager = layoutManager
    view.adapter = internalAdapter
    view.addOnScrollListener(viewportManager.scrollListener)

    if (layoutManager is NeedsBgPaddingInfo) {
      (layoutManager as NeedsBgPaddingInfo).setBgPaddingInfo(
          Rect(view.paddingLeft, view.paddingTop, view.paddingRight, view.paddingBottom))
    }

    registerDrawListener(view)

    layoutInfo?.setRenderInfoCollection(this)

    viewportManager.addViewportChangedListener(viewportChangedListener)

    if (currentFirstVisiblePosition != RecyclerView.NO_POSITION &&
        currentFirstVisiblePosition >= 0 &&
        !isCircular) {
      val smoothScrollAlignmentType = this.smoothScrollAlignmentType
      if (smoothScrollAlignmentType != null) {
        scrollSmoothToPosition(
            currentFirstVisiblePosition, currentOffset, smoothScrollAlignmentType)
      } else {
        if (layoutInfo is StaggeredGridLayoutInfo) {
          // Run scrollToPositionWithOffset to restore positions for StaggeredGridLayout may cause a
          // layout issue. Posting it to the next UI update can solve this issue.
          view.post(
              object : ScrollToOffsetRunnable(currentFirstVisiblePosition, currentOffset) {
                override fun run() {
                  layoutInfo.scrollToPositionWithOffset(currentFirstVisiblePosition, currentOffset)
                }
              })
        } else {
          layoutInfo?.scrollToPositionWithOffset(currentFirstVisiblePosition, currentOffset)
        }
      }
    } else if (isCircular) {
      // Initialize circular RecyclerView position
      view.scrollToPosition(currentFirstVisiblePosition)
      // Circular RecyclerViews report their size as Integer.MAX_VALUE, which makes Talkback
      // actually announce "In List, 2147483674 items". This overrides the row/column count on the
      // AccessibilityNodeInfo to accurately reflect the real number of items in the list.
      view.setAccessibilityDelegateCompat(
          object : RecyclerViewAccessibilityDelegate(view) {
            override fun onInitializeAccessibilityNodeInfo(
                host: View,
                info: AccessibilityNodeInfoCompat
            ) {
              super.onInitializeAccessibilityNodeInfo(host, info)

              val itemCount = componentTreeHolders.size
              val rowCount = if (layoutManager?.canScrollVertically() == true) itemCount else 1
              val colCount = if (layoutManager?.canScrollHorizontally() == true) itemCount else 1

              val collectionInfo =
                  CollectionInfoCompat.obtain(
                      rowCount, colCount, false, CollectionInfoCompat.SELECTION_MODE_NONE)
              info.setCollectionInfo(collectionInfo)
            }
          })
    }

    enableStickyHeader(this.mountedView)
  }

  private fun enableStickyHeader(recyclerView: RecyclerView?) {
    if (isCircular) {
      Log.w(TAG, "Sticky header is not supported for circular RecyclerViews")
      return
    }
    if (recyclerView == null) {
      return
    }

    val sectionsRecycler = SectionsRecyclerView.getParentRecycler(recyclerView) ?: return

    stickyHeaderController =
        stickyHeaderControllerFactory?.getController(this as HasStickyHeader)
            ?: StickyHeaderControllerImpl(this as HasStickyHeader)

    stickyHeaderController?.init(sectionsRecycler)
  }

  /**
   * Call from the owning [Component]'s onUnmount. This is where the adapter is removed from the
   * [RecyclerView].
   *
   * @param view the [RecyclerView] being unmounted.
   */
  @UiThread
  override fun unmount(view: RecyclerView) {
    ThreadUtils.assertMainThread()
    if (isSubAdapter) {
      throw RuntimeException("Can't unmount a RecyclerView in sub adapter mode")
    }

    val layoutManager = checkNotNull(this.layoutInfo).getLayoutManager()
    val firstView = layoutManager.findViewByPosition(currentFirstVisiblePosition)

    if (firstView != null) {
      val reverseLayout = this.reverseLayout

      currentOffset =
          if (layoutInfo.getScrollDirection() == HORIZONTAL) {
            if (reverseLayout)
                (view.width -
                    layoutManager.paddingRight -
                    layoutManager.getDecoratedRight(firstView))
            else (layoutManager.getDecoratedLeft(firstView) - layoutManager.paddingLeft)
          } else {
            if (reverseLayout)
                (view.height -
                    layoutManager.paddingBottom -
                    layoutManager.getDecoratedBottom(firstView))
            else (layoutManager.getDecoratedTop(firstView) - layoutManager.paddingTop)
          }
    } else {
      currentOffset = 0
    }

    view.removeOnScrollListener(viewportManager.scrollListener)

    unregisterDrawListener(view)
    maybeDispatchDataRendered()

    view.adapter = null
    view.layoutManager = null

    viewportManager.removeViewportChangedListener(viewportChangedListener)

    // We might have already unmounted this view when calling mount with a different view. In this
    // case we can just return here.
    if (mountedView !== view) {
      return
    }

    mountedView = null
    stickyHeaderController?.reset()

    layoutInfo.setRenderInfoCollection(null)
  }

  private fun registerDrawListener(view: RecyclerView) {
    if (view is HasPostDispatchDrawListener) {
      val viewHasPostDispatchDrawListener = view as HasPostDispatchDrawListener
      viewHasPostDispatchDrawListener.registerPostDispatchDrawListener(postDispatchDrawListener)

      for (listener in additionalPostDispatchDrawListeners) {
        viewHasPostDispatchDrawListener.registerPostDispatchDrawListener(listener)
      }
    } else if (view.viewTreeObserver != null) {
      view.viewTreeObserver.addOnPreDrawListener(onPreDrawListener)
      // To make sure we unregister the OnPreDrawListener before RecyclerView is detached.
      view.addOnAttachStateChangeListener(onAttachStateChangeListener)
    }
  }

  private fun unregisterDrawListener(view: RecyclerView) {
    if (view is HasPostDispatchDrawListener) {
      val viewHasPostDispatchDrawListener = view as HasPostDispatchDrawListener
      viewHasPostDispatchDrawListener.unregisterPostDispatchDrawListener(postDispatchDrawListener)

      for (listener in additionalPostDispatchDrawListeners) {
        viewHasPostDispatchDrawListener.unregisterPostDispatchDrawListener(listener)
      }
    } else view.viewTreeObserver?.removeOnPreDrawListener(onPreDrawListener)
  }

  @UiThread
  fun scrollToPosition(position: Int) {
    if (mountedView == null) {
      currentFirstVisiblePosition = position
      return
    }
    val target =
        if (isCircular) getClosestIndexInCircularList(checkNotNull(mountedView), position)
        else position
    mountedView?.scrollToPosition(target)
  }

  @UiThread
  fun scrollSmoothToPosition(id: Any?, offset: Int, type: SmoothScrollAlignmentType) {
    val index = getPositionForId(id)
    scrollSmoothToPosition(index, offset, type)
  }

  @UiThread
  fun scrollSmoothToPosition(position: Int, offset: Int, type: SmoothScrollAlignmentType) {
    if (mountedView == null) {
      currentFirstVisiblePosition = position
      currentOffset = offset
      smoothScrollAlignmentType = type
      return
    }

    val target =
        if (isCircular) getClosestIndexInCircularList(checkNotNull(mountedView), position)
        else position
    val smoothScroller =
        SnapUtil.getSmoothScrollerWithOffset(
            checkNotNull(componentContext).androidContext, offset, type)
    smoothScroller.targetPosition = target
    mountedView?.layoutManager?.startSmoothScroll(smoothScroller)
  }

  @UiThread
  @Synchronized
  private fun getPositionForId(id: Any?): Int {
    if (id == null) {
      return -1
    }
    for (i in _componentTreeHolders.indices) {
      val componentTreeHolder = _componentTreeHolders[i]

      val renderInfo = componentTreeHolder.renderInfo
      val childId = renderInfo.getCustomAttribute(ID_CUSTOM_ATTR_KEY)

      if (id == childId) {
        return i
      }
    }
    return -1
  }

  @UiThread
  fun scrollToPositionWithOffset(id: Any?, offset: Int) {
    val index = getPositionForId(id)
    scrollToPositionWithOffset(index, offset)
  }

  @UiThread
  fun scrollToPositionWithOffset(position: Int, offset: Int) {
    if (mountedView == null) {
      currentFirstVisiblePosition = position
      currentOffset = offset
      return
    }

    val target =
        if (isCircular) getClosestIndexInCircularList(checkNotNull(mountedView), position)
        else position
    layoutInfo?.scrollToPositionWithOffset(target, offset)
  }

  @UiThread
  private fun getClosestIndexInCircularList(recyclerView: RecyclerView, target: Int): Int {
    // Since circular lists position us in the middle of 0->MAX_INT, scrolling to a specific
    // index interacts poorly. We either jump immediately to the beginning of the list (and ruin the
    // "circular illusion") or send the RV on a mission to do an animated scroll from index
    // MAX_INT/2 to some small number, animating forever effectively. This logic instead tries to
    // find the closest item corresponding to the given index within a circular list.
    val firstChild = recyclerView.getChildAt(0)
    val layoutManager = recyclerView.layoutManager
    if (firstChild == null || layoutManager == null) {
      return target
    }
    val numChildren = _componentTreeHolders.size
    val firstVisibleIndex = layoutManager.getPosition(firstChild)
    return firstVisibleIndex +
        (target - firstVisibleIndex % numChildren + numChildren) % numChildren
  }

  @GuardedBy("this")
  private fun isCompatibleSize(widthSpec: Int, heightSpec: Int): Boolean {
    val measuredSize = this.measuredSize
    if (measuredSize == null) {
      return false
    }

    val scrollDirection = layoutInfo?.getScrollDirection()

    if (lastWidthSpec != UNINITIALIZED) {
      when (scrollDirection) {
        HORIZONTAL ->
            return MeasureComparisonUtils.isMeasureSpecCompatible(
                lastHeightSpec, heightSpec, measuredSize.height)

        VERTICAL ->
            return MeasureComparisonUtils.isMeasureSpecCompatible(
                lastWidthSpec, widthSpec, measuredSize.width)
        else -> {}
      }
    }

    return false
  }

  override fun findFirstVisibleItemPosition(): Int =
      checkNotNull(layoutInfo).findFirstVisibleItemPosition()

  override fun findFirstFullyVisibleItemPosition(): Int =
      checkNotNull(layoutInfo).findFirstFullyVisibleItemPosition()

  override fun findLastVisibleItemPosition(): Int =
      checkNotNull(layoutInfo).findLastVisibleItemPosition()

  override fun findLastFullyVisibleItemPosition(): Int =
      checkNotNull(layoutInfo).findLastFullyVisibleItemPosition()

  @UiThread
  @GuardedBy("this")
  override fun isSticky(position: Int): Boolean =
      isValidPosition(position) && _componentTreeHolders[position].renderInfo.isSticky

  @UiThread
  @GuardedBy("this")
  override fun isValidPosition(position: Int): Boolean =
      position >= 0 && position < _componentTreeHolders.size

  class RangeCalculationResult {
    // The estimated number of items needed to fill the viewport.
    var estimatedViewportCount: Int = 0

    // The size computed for the first Component.
    var measuredSize: Int = 0
  }

  @UiThread
  override fun setViewportChangedListener(viewportChangedListener: ViewportChanged?) {
    viewportManager.addViewportChangedListener(viewportChangedListener)
  }

  @VisibleForTesting
  fun onNewVisibleRange(firstVisiblePosition: Int, lastVisiblePosition: Int) {
    currentFirstVisiblePosition = firstVisiblePosition
    currentLastVisiblePosition = lastVisiblePosition
    viewportManager.resetShouldUpdate()
    maybePostUpdateViewportAndComputeRange()
  }

  /**
   * Updates the visible range when in sub adapter mode. Do not call this otherwise. This method
   * exists because in sub adapter mode, the RecyclerBinder is never mounted to a RecyclerView and
   * needs outside signals from the multiplexing adapter to determine which of its indices are
   * visible.
   */
  fun updateSubAdapterVisibleRange(firstVisiblePosition: Int, lastVisiblePosition: Int) {
    if (!isSubAdapter) {
      throw RuntimeException("updateSubAdapterVisibleRange can only be called in sub adapter mode")
    }
    onNewVisibleRange(firstVisiblePosition, lastVisiblePosition)
  }

  /**
   * Updates the working range when in sub adapter mode. Do not call this otherwise. This method
   * exists because in sub adapter mode, the RecyclerBinder is never mounted to a RecyclerView and
   * needs outside signals from the multiplexing adapter to determine which of its indices are
   * visible.
   */
  fun updateSubAdapterWorkingRange(
      firstVisibleIndex: Int,
      lastVisibleIndex: Int,
      firstFullyVisibleIndex: Int,
      lastFullyVisibleIndex: Int
  ) {
    if (!isSubAdapter) {
      throw RuntimeException("updateSubAdapterWorkingRange can only be called in sub adapter mode")
    }
    onNewWorkingRange(
        firstVisibleIndex, lastVisibleIndex, firstFullyVisibleIndex, lastFullyVisibleIndex)
  }

  @VisibleForTesting
  fun onNewWorkingRange(
      firstVisibleIndex: Int,
      lastVisibleIndex: Int,
      firstFullyVisibleIndex: Int,
      lastFullyVisibleIndex: Int
  ) {
    if (estimatedViewportCount == UNSET ||
        firstVisibleIndex == RecyclerView.NO_POSITION ||
        lastVisibleIndex == RecyclerView.NO_POSITION) {
      return
    }

    val rangeSize = max(estimatedViewportCount, lastVisibleIndex - firstVisibleIndex)
    val layoutRangeSize = (rangeSize * rangeRatio).toInt()
    val rangeStart = max(0, firstVisibleIndex - layoutRangeSize)
    val rangeEnd =
        min(firstVisibleIndex + rangeSize + layoutRangeSize, _componentTreeHolders.size - 1)

    for (position in rangeStart..rangeEnd) {
      val holder = _componentTreeHolders[position]
      holder.checkWorkingRangeAndDispatch(
          position,
          firstVisibleIndex,
          lastVisibleIndex,
          firstFullyVisibleIndex,
          lastFullyVisibleIndex)
    }
  }

  @Suppress("DEPRECATION")
  private fun maybePostUpdateViewportAndComputeRange() {
    val mountedView = this.mountedView
    if (mountedView != null && viewportManager.shouldUpdate()) {
      mountedView.removeCallbacks(updateViewportRunnable)
      ViewCompat.postOnAnimation(mountedView, updateViewportRunnable)
    }
    computeRange(currentFirstVisiblePosition, currentLastVisiblePosition)
  }

  private fun computeRange(
      firstVisible: Int,
      lastVisible: Int,
      traverser: RecyclerRangeTraverser = rangeTraverser
  ) {
    var firstVisibleToUse = firstVisible
    var lastVisibleToUse = lastVisible
    val rangeSize: Int
    val rangeStart: Int
    val rangeEnd: Int
    val treeHoldersSize: Int
    val didRangeExtremitiesChange: Boolean

    synchronized(this) {
      if (!isMeasured || estimatedViewportCount == UNSET) {
        return
      }
      if (firstVisibleToUse == RecyclerView.NO_POSITION ||
          lastVisibleToUse == RecyclerView.NO_POSITION) {
        firstVisibleToUse = 0
        lastVisibleToUse = 0
      }
      rangeSize = max(estimatedViewportCount, lastVisibleToUse - firstVisibleToUse)
      treeHoldersSize = _componentTreeHolders.size
      if (isCircular) {
        rangeStart = 0
        rangeEnd = treeHoldersSize
      } else {
        rangeStart = firstVisibleToUse - (rangeSize * rangeRatio).toInt()
        rangeEnd = firstVisibleToUse + rangeSize + (rangeSize * rangeRatio).toInt()
      }
      if (rangeStart < lowestRangeStartSinceDeletes || rangeEnd > highestRangeStartSinceDeletes) {
        didRangeExtremitiesChange = true
        lowestRangeStartSinceDeletes = rangeStart
        highestRangeStartSinceDeletes = rangeEnd
      } else {
        didRangeExtremitiesChange = false
      }
    }
    val processor =
        when (recyclingStrategy) {
          RecyclingStrategy.RETAIN_MAXIMUM_RANGE ->
              object : RecyclerRangeTraverser.Processor {
                override fun process(index: Int): Boolean =
                    computeRangeLayoutWithRetainMaximumRange(
                        index, rangeStart, rangeEnd, treeHoldersSize, didRangeExtremitiesChange)
              }

          RecyclingStrategy.DEFAULT ->
              object : RecyclerRangeTraverser.Processor {
                override fun process(index: Int): Boolean =
                    computeRangeLayoutAt(index, rangeStart, rangeEnd, treeHoldersSize)
              }

          else ->
              object : RecyclerRangeTraverser.Processor {
                override fun process(index: Int): Boolean =
                    computeRangeLayoutAt(index, rangeStart, rangeEnd, treeHoldersSize)
              }
        }

    traverser.traverse(0, treeHoldersSize, firstVisibleToUse, lastVisibleToUse, processor)
  }

  /** @return Whether or not to continue layout computation for current range */
  private fun computeRangeLayoutAt(
      index: Int,
      rangeStart: Int,
      rangeEnd: Int,
      treeHoldersSize: Int
  ): Boolean {
    val holder: ComponentTreeHolder
    val childrenWidthSpec: Int
    val childrenHeightSpec: Int

    synchronized(this) {
      // Someone modified the ComponentsTreeHolders while we were computing this range. We
      // can just bail as another range will be computed.
      if (treeHoldersSize != _componentTreeHolders.size) {
        return false
      }

      holder = _componentTreeHolders[index]

      if (holder.renderInfo.rendersView()) {
        return true
      }

      childrenWidthSpec = getActualChildrenWidthSpec(holder, measuredSize, lastWidthSpec)
      childrenHeightSpec = getActualChildrenHeightSpec(holder, measuredSize, lastHeightSpec)
    }

    if ((index >= rangeStart || holder.renderInfo.isSticky) && index <= rangeEnd) {
      if (!holder.isTreeValidForSizeSpecs(childrenWidthSpec, childrenHeightSpec)) {
        holder.computeLayoutAsync(
            checkNotNull(componentContext), childrenWidthSpec, childrenHeightSpec)
      }
    } else {
      maybeReleaseOutOfRangeTree(holder)
    }

    return true
  }

  /** @return Whether or not to continue layout computation for current range */
  private fun computeRangeLayoutWithRetainMaximumRange(
      index: Int,
      rangeStart: Int,
      rangeEnd: Int,
      treeHoldersSize: Int,
      allowDeletions: Boolean
  ): Boolean {
    val holder: ComponentTreeHolder
    var childrenWidthSpec = 0
    var childrenHeightSpec = 0
    val shouldTryComputeLayout: Boolean

    synchronized(this) {
      // Someone modified the ComponentsTreeHolders while we were computing this range. We
      // can just bail as another range will be computed.
      if (treeHoldersSize != _componentTreeHolders.size) {
        return false
      }

      holder = _componentTreeHolders[index]

      if (holder.renderInfo.rendersView()) {
        return true
      }

      shouldTryComputeLayout =
          (index >= rangeStart || holder.renderInfo.isSticky) && index <= rangeEnd
      if (shouldTryComputeLayout) {
        childrenWidthSpec = getActualChildrenWidthSpec(holder, measuredSize, lastWidthSpec)
        childrenHeightSpec = getActualChildrenHeightSpec(holder, measuredSize, lastHeightSpec)
      }
    }

    if (shouldTryComputeLayout) {
      if (!holder.isTreeValidForSizeSpecs(childrenWidthSpec, childrenHeightSpec)) {
        holder.computeLayoutAsync(
            checkNotNull(componentContext), childrenWidthSpec, childrenHeightSpec)
      }
    } else if (allowDeletions && canReleaseTree(holder)) {
      maybeReleaseOutOfRangeTree(holder)
    }

    return true
  }

  private fun maybeReleaseOutOfRangeTree(holder: ComponentTreeHolder) {
    if (ThreadUtils.isMainThread) {
      maybeAcquireStateAndReleaseTree(holder)
    } else {
      synchronized(releaseTreeRunnableLock) {
        componentTreeHoldersToRelease.addLast(holder)
        if (!hasPendingReleaseTreeRunnable) {
          mainThreadHandler.post(releaseTreeRunnable)
          hasPendingReleaseTreeRunnable = true
        }
      }
    }
  }

  private val reverseLayout: Boolean
    get() {
      val layoutManager = layoutInfo?.getLayoutManager()
      return if (layoutManager is LinearLayoutManager) {
        layoutManager.reverseLayout
      } else {
        false
      }
    }

  private val stackFromEnd: Boolean
    get() {
      val layoutManager = layoutInfo?.getLayoutManager()
      return if (layoutManager is LinearLayoutManager) {
        layoutManager.stackFromEnd
      } else {
        false
      }
    }

  @get:VisibleForTesting
  val rangeCalculationResult: RangeCalculationResult?
    get() {
      if (sizeForMeasure == null && estimatedViewportCount == UNSET) {
        return null
      }

      val range = RangeCalculationResult()
      range.measuredSize = sizeForMeasuring
      range.estimatedViewportCount = this.estimatedViewportCount

      return range
    }

  private fun hasComputedRange(): Boolean =
      (sizeForMeasure != null && estimatedViewportCount != UNSET) || hasManualEstimatedViewportCount

  private val sizeForMeasuring: Int
    /**
     * If measure is called with measure specs that cannot be used to measure the recyclerview, the
     * size of one of an item will be used to determine how to measure instead.
     *
     * @return a size value that can be used to measure the dimension of the recycler that has
     *   unknown size, which is width for vertical scrolling recyclers or height for horizontal
     *   scrolling recyclers.
     */
    get() {
      val sizeForMeasure = this.sizeForMeasure
      if (sizeForMeasure == null) {
        return UNSET
      }

      return if (layoutInfo?.getScrollDirection() == HORIZONTAL) sizeForMeasure.height
      else sizeForMeasure.width
    }

  @Synchronized
  override fun getChildWidthSpec(index: Int): Int {
    val holder = _componentTreeHolders[index]
    return getActualChildrenWidthSpec(holder, measuredSize, lastWidthSpec)
  }

  @Synchronized
  override fun getChildHeightSpec(index: Int): Int {
    val holder = _componentTreeHolders[index]
    return getActualChildrenHeightSpec(holder, measuredSize, lastHeightSpec)
  }

  private fun getActualChildrenWidthSpec(
      treeHolder: ComponentTreeHolder,
      measuredSize: Size?,
      lastWidthSpec: Int
  ): Int {
    if (isMeasured) {
      check(measuredSize != null)
      if (isMatchingParentSize(treeHolder.renderInfo.parentWidthPercent)) {
        return makeSizeSpec(
            FastMath.round(measuredSize.width * treeHolder.renderInfo.parentWidthPercent / 100),
            SizeSpec.EXACTLY)
      }

      return checkNotNull(layoutInfo)
          .getChildWidthSpec(
              makeSizeSpec(measuredSize.width, SizeSpec.EXACTLY), treeHolder.renderInfo)
    }

    return checkNotNull(layoutInfo).getChildWidthSpec(lastWidthSpec, treeHolder.renderInfo)
  }

  private fun getActualChildrenHeightSpec(
      treeHolder: ComponentTreeHolder,
      measuredSize: Size?,
      lastHeightSpec: Int
  ): Int {
    if (hasDynamicItemHeight) {
      if (isMeasured && isMatchingParentSize(treeHolder.renderInfo.parentHeightPercent)) {
        check(measuredSize != null)
        return makeSizeSpec(
            FastMath.round(measuredSize.height * treeHolder.renderInfo.parentHeightPercent / 100),
            SizeSpec.EXACTLY)
      }

      return SizeSpec.UNSPECIFIED
    }

    if (isMeasured) {
      if (isMatchingParentSize(treeHolder.renderInfo.parentHeightPercent)) {
        check(measuredSize != null)
        return makeSizeSpec(
            FastMath.round(measuredSize.height * treeHolder.renderInfo.parentHeightPercent / 100),
            SizeSpec.EXACTLY)
      }

      return checkNotNull(layoutInfo)
          .getChildHeightSpec(
              makeSizeSpec(measuredSize?.height ?: 0, SizeSpec.EXACTLY), treeHolder.renderInfo)
    }

    return checkNotNull(layoutInfo).getChildHeightSpec(lastHeightSpec, treeHolder.renderInfo)
  }

  @AnyThread
  fun setCommitPolicy(@CommitPolicy commitPolicy: Int) {
    this.commitPolicy = commitPolicy
  }

  private fun createAsyncInsertOperation(
      position: Int,
      renderInfo: RenderInfo
  ): AsyncInsertOperation {
    val holder = createComponentTreeHolder(renderInfo)
    holder.isInserted = false
    return AsyncInsertOperation(position, holder)
  }

  /** Async operation types. */
  @IntDef(
      Operation.INSERT,
      Operation.UPDATE,
      Operation.UPDATE_RANGE,
      Operation.REMOVE,
      Operation.REMOVE_RANGE,
      Operation.MOVE)
  @Retention(AnnotationRetention.SOURCE)
  private annotation class Operation {
    companion object {
      const val INSERT: Int = 0
      const val UPDATE: Int = 1
      const val UPDATE_RANGE: Int = 2
      const val REMOVE: Int = 3
      const val REMOVE_RANGE: Int = 4
      const val MOVE: Int = 5
    }
  }

  /**
   * Defines when a batch should be committed: - IMMEDIATE: Commit batches to the RecyclerView as
   * soon as possible. - LAYOUT_BEFORE_INSERT: Commit batches to the RecyclerView only after the
   * layouts for all insert operations have been completed.
   */
  @IntDef(CommitPolicy.IMMEDIATE, CommitPolicy.LAYOUT_BEFORE_INSERT)
  @Retention(AnnotationRetention.SOURCE)
  annotation class CommitPolicy {
    companion object {
      const val IMMEDIATE: Int = 0
      const val LAYOUT_BEFORE_INSERT: Int = 1
    }
  }

  /** Strategies for recycling layouts of items in binder */
  @IntDef(RecyclingStrategy.DEFAULT, RecyclingStrategy.RETAIN_MAXIMUM_RANGE)
  annotation class RecyclingStrategy {
    companion object {
      const val DEFAULT: Int = 0
      const val RETAIN_MAXIMUM_RANGE: Int = 1
    }
  }

  @IntDef(
      PaginationStrategy.DEFAULT,
      PaginationStrategy.SCROLL_TO_LAST_VISIBLE,
      PaginationStrategy.SCROLL_TO_INSERT_POSITION)
  annotation class PaginationStrategy {
    companion object {
      const val DEFAULT: Int = 0
      const val SCROLL_TO_LAST_VISIBLE: Int = 1
      const val SCROLL_TO_INSERT_POSITION: Int = 2
    }
  }

  /** An operation received from one of the *Async methods, pending execution. */
  private abstract class AsyncOperation(val operation: Int)

  private class AsyncInsertOperation(val position: Int, val holder: ComponentTreeHolder) :
      AsyncOperation(Operation.INSERT)

  private class AsyncUpdateOperation(val position: Int, val renderInfo: RenderInfo) :
      AsyncOperation(Operation.UPDATE)

  private class AsyncUpdateRangeOperation(val position: Int, val renderInfos: List<RenderInfo>) :
      AsyncOperation(Operation.UPDATE_RANGE)

  private class AsyncRemoveOperation(val position: Int) : AsyncOperation(Operation.REMOVE)

  private class AsyncRemoveRangeOperation(val position: Int, val count: Int) :
      AsyncOperation(Operation.REMOVE_RANGE)

  private class AsyncMoveOperation(val fromPosition: Int, val toPosition: Int) :
      AsyncOperation(Operation.MOVE)

  /**
   * A batch of [AsyncOperation]s that should be applied all at once. The OnDataBoundListener should
   * be called once all these operations are applied.
   */
  private class AsyncBatch(@CommitPolicy val commitPolicy: Int) {
    val operations: ArrayList<AsyncOperation> = ArrayList()
    var isDataChanged: Boolean = false
    var changeSetCompleteCallback: ChangeSetCompleteCallback? = null
  }

  /** Default implementation of RecyclerBinderViewHolder */
  private class BaseViewHolder(itemView: View, val isLithoViewType: Boolean) :
      RecyclerBinderViewHolder(itemView) {
    var viewBinder: ViewBinder<View>? = null
    var viewRecycledRunnable: Runnable? = null

    override val lithoView: LithoView?
      get() {
        if (isLithoViewType) {
          return itemView as LithoView
        }
        return null
      }

    override fun setLithoViewLayoutParams(
        lithoView: LithoView?,
        width: Int,
        height: Int,
        widthSpec: Int,
        heightSpec: Int,
        isFullSpan: Boolean
    ) {
      val layoutParams =
          RecyclerViewLayoutManagerOverrideParams(width, height, widthSpec, heightSpec, isFullSpan)
      lithoView?.layoutParams = layoutParams
    }
  }

  private inner class DefaultRecyclerBinderAdapterDelegate :
      RecyclerBinderAdapterDelegate<RecyclerBinderViewHolder> {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
      val lithoViewFactory = recyclerBinderConfig?.lithoViewFactory
      val lithoView =
          lithoViewFactory?.createLithoView(checkNotNull(componentContext))
              ?: LithoView(componentContext, null)
      return BaseViewHolder(lithoView, true)
    }

    override fun onBindViewHolder(
        viewHolder: RecyclerBinderViewHolder,
        position: Int,
        componentTree: ComponentTree?,
        renderInfo: RenderInfo?
    ) = Unit

    override fun onViewRecycled(viewHolder: RecyclerBinderViewHolder) = Unit

    override fun hasStableIds(): Boolean = enableStableIds

    override fun getItemId(position: Int): Long = _componentTreeHolders[position].id.toLong()
  }

  // A simple class to enable ScrollToOffset to run after the layout is finished.
  private abstract class ScrollToOffsetRunnable(
      currentFirstVisiblePosition: Int,
      currentOffset: Int
  ) : Runnable {
    var currentFirstVisiblePosition: Int = RecyclerView.NO_POSITION
    var currentOffset: Int

    init {
      this.currentFirstVisiblePosition = currentFirstVisiblePosition
      this.currentOffset = currentOffset
    }
  }

  private inner class InternalAdapter :
      RecyclerView.Adapter<RecyclerBinderViewHolder>(), RecyclerBinderAdapter {
    init {
      setHasStableIds(recyclerBinderAdapterDelegate.hasStableIds())
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerBinderViewHolder {
      val viewCreator = renderInfoViewCreatorController.getViewCreator(viewType)
      if (viewCreator != null) {
        val view = viewCreator.createView(checkNotNull(componentContext).androidContext, parent)
        try {
          return BaseViewHolder(view, false)
        } catch (ex: IllegalArgumentException) {
          throw IllegalArgumentException(
              "createView() may not return null from :${getClassNameForDebug(viewCreator.javaClass)}",
              ex)
        }
      }
      return recyclerBinderAdapterDelegate.onCreateViewHolder(parent, viewType)
    }

    @Suppress("UNCHECKED_CAST")
    @GuardedBy("RecyclerBinder.this")
    override fun onBindViewHolder(holder: RecyclerBinderViewHolder, position: Int) {
      val loggingForStartup =
          LithoStartupLogger.isEnabled(startupLogger) && !startupLoggerAttribution.isEmpty()
      val normalizedPosition = this@RecyclerBinder.getNormalizedPosition(position)

      // We can ignore the synchronization here. We'll only add to this from the UiThread.
      // This read only happens on the UiThread as well and we are never writing this here.
      val componentTreeHolder = _componentTreeHolders[normalizedPosition]

      val renderInfo = componentTreeHolder.renderInfo
      if (renderInfo.rendersComponent()) {
        val lithoView = holder.lithoView
        val childrenWidthSpec =
            getActualChildrenWidthSpec(componentTreeHolder, measuredSize, lastWidthSpec)
        val childrenHeightSpec =
            getActualChildrenHeightSpec(componentTreeHolder, measuredSize, lastHeightSpec)
        if (!componentTreeHolder.isTreeValidForSizeSpecs(childrenWidthSpec, childrenHeightSpec)) {
          if (ComponentsConfiguration.computeRangeOnSyncLayout) {
            // Since synchronous layout is about to happen, and the ScrollListener that updates the
            // visible and working ranges will not fire until after the full frame is rendered,
            // we want to kick off background layout for the estimated visible range in the
            // scrolling direction in an attempt to take advantage of more parallel layout.
            if (currentFirstVisiblePosition != RecyclerView.NO_POSITION &&
                currentLastVisiblePosition != RecyclerView.NO_POSITION) {
              // Get the last known visible range if available.
              val range = currentLastVisiblePosition - currentFirstVisiblePosition
              if (position > currentLastVisiblePosition) {
                // Scrolling down
                computeRange(position, position + range, RecyclerRangeTraverser.FORWARD_TRAVERSER)
              } else if (position < currentFirstVisiblePosition) {
                // Scrolling up
                computeRange(position - range, position, RecyclerRangeTraverser.BACKWARD_TRAVERSER)
              }
            }
          }

          val size = Size()
          componentTreeHolder.computeLayoutSync(
              checkNotNull(componentContext), childrenWidthSpec, childrenHeightSpec, size)
        }
        val isOrientationVertical = layoutInfo?.getScrollDirection() == VERTICAL
        val width =
            if (getMode(childrenWidthSpec) == SizeSpec.EXACTLY) {
              getSize(childrenWidthSpec)
            } else if (isOrientationVertical) {
              MATCH_PARENT
            } else {
              WRAP_CONTENT
            }

        val height =
            if (getMode(childrenHeightSpec) == SizeSpec.EXACTLY) {
              getSize(childrenHeightSpec)
            } else if (isOrientationVertical) {
              WRAP_CONTENT
            } else {
              MATCH_PARENT
            }

        holder.setLithoViewLayoutParams(
            lithoView, width, height, childrenWidthSpec, childrenHeightSpec, renderInfo.isFullSpan)
        lithoView?.componentTree = componentTreeHolder.componentTree

        if (componentTreeHolder.renderInfo.renderCompleteEventHandler != null &&
            componentTreeHolder.renderState == ComponentTreeHolder.RENDER_UNINITIALIZED) {
          lithoView?.setOnPostDrawListener {
            val pos = checkNotNull(mountedView).getChildAdapterPosition(holder.itemView)
            if (pos != RecyclerView.NO_POSITION) {
              notifyItemRenderCompleteAt(pos, SystemClock.uptimeMillis())
              lithoView.setOnPostDrawListener(null)
            }
          }
        }
        if (loggingForStartup) {
          lithoView?.setMountStartupLoggingInfo(
              startupLogger,
              startupLoggerAttribution,
              firstMountLogged,
              lastMountLogged,
              position == itemCount,
              isOrientationVertical)
        } else {
          lithoView?.resetMountStartupLoggingInfo()
        }
        recyclerBinderAdapterDelegate.onBindViewHolder(
            holder, normalizedPosition, componentTreeHolder.componentTree, renderInfo)

        if (requestMountForPrefetchedItems) {
          // Try to pre-mount components marked as excludeFromIncrementalMount.
          MountHelper.requestMount(
              checkNotNull(componentTreeHolder.componentTree), emptyRect, false)
        }
      } else if (holder is BaseViewHolder) {
        if (!holder.isLithoViewType) {
          val viewBinder = renderInfo.viewBinder
          holder.viewBinder = viewBinder as ViewBinder<View>
          viewBinder.bind(holder.itemView)
        }
      }

      if (LithoDebugConfigurations.isRenderInfoDebuggingEnabled) {
        RenderInfoDebugInfoRegistry.setRenderInfoToViewMapping(
            holder.itemView,
            renderInfo.getDebugInfo(RenderInfoDebugInfoRegistry.SONAR_SECTIONS_DEBUG_INFO_TAG))
      }
    }

    fun getClassNameForDebug(c: Class<*>): String? {
      val enclosingClass = c.enclosingClass ?: return c.canonicalName
      return enclosingClass.canonicalName
    }

    @GuardedBy("RecyclerBinder.this")
    override fun getItemViewType(position: Int): Int {
      val renderInfo = getRenderInfoAt(position)
      return if (renderInfo.rendersComponent()) {
        // Special value for LithoViews
        renderInfoViewCreatorController.componentViewType
      } else {
        renderInfo.viewType
      }
    }

    @GuardedBy("RecyclerBinder.this")
    override fun getItemCount(): Int {
      // We can ignore the synchronization here. We'll only add to this from the UiThread.
      // This read only happens on the UiThread as well and we are never writing this here.

      // If the recycler is circular, we have to simulate having an infinite number of items in the
      // adapter by returning Integer.MAX_VALUE.

      val size = _componentTreeHolders.size
      return if (isCircular && size > 0) Int.MAX_VALUE else size
    }

    override fun onViewRecycled(holder: RecyclerBinderViewHolder) {
      onViewRecycledInternal(holder)
    }

    fun onViewRecycledInternal(holder: RecyclerBinderViewHolder) {
      val isTracing = isTracing
      if (isTracing) {
        ComponentsSystrace.beginSection("RecyclerBinder.InternalAdapter#onViewRecycledInternal")
      }
      val lithoView = holder.lithoView
      if (lithoView != null) {
        recyclerBinderAdapterDelegate.onViewRecycled(holder)
        lithoView.unmountAllItems()
        lithoView.componentTree = null
        lithoView.resetMountStartupLoggingInfo()
      } else if (holder is BaseViewHolder) {
        val baseViewHolder = holder
        if (!baseViewHolder.isLithoViewType) {
          if (baseViewHolder.viewBinder != null) {
            baseViewHolder.viewBinder?.unbind(baseViewHolder.itemView)
            baseViewHolder.viewBinder = null
          }
        }
      }

      if (isTracing) {
        ComponentsSystrace.endSection()
      }
    }

    override fun getItemId(position: Int): Long =
        if (enableStableIds) recyclerBinderAdapterDelegate.getItemId(position)
        else super.getItemId(position)

    override fun findFirstVisibleItemPosition(): Int =
        checkNotNull(layoutInfo).findFirstVisibleItemPosition()

    override fun findLastVisibleItemPosition(): Int =
        checkNotNull(layoutInfo).findLastVisibleItemPosition()

    override fun getRenderInfoAt(position: Int): RenderInfo =
        _componentTreeHolders[getNormalizedPosition(position)].renderInfo
  }

  /**
   * If the recycler is circular, returns the position of the [ComponentTreeHolder] that is used to
   * render the item at given position. Otherwise, it returns the position passed as parameter,
   * which is the same as the index of the [ComponentTreeHolder].
   */
  @GuardedBy("this")
  private fun getNormalizedPosition(position: Int): Int =
      if (isCircular) (position % _componentTreeHolders.size) else position

  private fun createComponentTreeHolder(renderInfo: RenderInfo): ComponentTreeHolder {
    if (componentWarmer != null) {
      val tag = renderInfo.getCustomAttribute(ComponentWarmer.COMPONENT_WARMER_TAG)
      if (tag is String) {
        val holder = componentWarmer?.consume(tag)
        if (holder != null) {
          if (SectionsDebug.ENABLED) {
            Log.d(SectionsDebug.TAG, "Got ComponentTreeHolder from ComponentWarner for key $tag")
          }
          val preventRelease =
              renderInfo.getCustomAttribute(ComponentTreeHolder.PREVENT_RELEASE_TAG)
          if (preventRelease != null) {
            holder.renderInfo.addCustomAttribute(
                ComponentTreeHolder.PREVENT_RELEASE_TAG, preventRelease)
          }
          holder.setPoolScope(poolScope)
          return holder
        }
      }
    }

    val layoutHandler = layoutHandlerFactory?.createLayoutCalculationHandler(renderInfo)

    val holder =
        checkNotNull(componentTreeHolderFactory)
            .create(
                renderInfo,
                layoutHandler,
                componentTreeMeasureListenerFactory,
                componentsConfiguration,
                lithoVisibilityEventsController)
    holder.setPoolScope(poolScope)
    return holder
  }

  val componentTreeHolderPreparer: ComponentTreeHolderPreparer
    get() =
        object : ComponentTreeHolderPreparer {
          override fun create(renderInfo: ComponentRenderInfo): ComponentTreeHolder =
              createComponentTreeHolder(renderInfo)

          override fun prepareSync(holder: ComponentTreeHolder, size: Size?) {
            val measuredSize: Size?
            val lastWidthSpec: Int
            val lastHeightSpec: Int
            synchronized(this@RecyclerBinder) {
              measuredSize = this@RecyclerBinder.measuredSize
              lastWidthSpec = this@RecyclerBinder.lastWidthSpec
              lastHeightSpec = this@RecyclerBinder.lastHeightSpec
            }

            val childrenWidthSpec = getActualChildrenWidthSpec(holder, measuredSize, lastWidthSpec)
            val childrenHeightSpec =
                getActualChildrenHeightSpec(holder, measuredSize, lastHeightSpec)

            if (size != null &&
                holder.isTreeValidForSizeSpecs(childrenWidthSpec, childrenHeightSpec)) {
              size.width = getSize(childrenWidthSpec)
              size.height = getSize(childrenHeightSpec)

              return
            }

            holder.computeLayoutSync(
                checkNotNull(componentContext), childrenWidthSpec, childrenHeightSpec, size)
          }

          override fun prepareAsync(holder: ComponentTreeHolder) {
            val measuredSize: Size?
            val lastWidthSpec: Int
            val lastHeightSpec: Int
            synchronized(this@RecyclerBinder) {
              measuredSize = this@RecyclerBinder.measuredSize
              lastWidthSpec = this@RecyclerBinder.lastWidthSpec
              lastHeightSpec = this@RecyclerBinder.lastHeightSpec
            }

            val childrenWidthSpec = getActualChildrenWidthSpec(holder, measuredSize, lastWidthSpec)
            val childrenHeightSpec =
                getActualChildrenHeightSpec(holder, measuredSize, lastHeightSpec)

            if (holder.isTreeValidForSizeSpecs(childrenWidthSpec, childrenHeightSpec)) {
              return
            }

            holder.computeLayoutAsync(
                checkNotNull(componentContext), childrenWidthSpec, childrenHeightSpec)
          }
        }

  @UiThread
  private fun updateHolder(holder: ComponentTreeHolder, renderInfo: RenderInfo) {
    val previousRenderInfo = holder.renderInfo
    holder.renderInfo = renderInfo
    if (layoutHandlerFactory?.shouldUpdateLayoutHandler(previousRenderInfo, renderInfo) == true) {
      holder.updateLayoutHandler(layoutHandlerFactory.createLayoutCalculationHandler(renderInfo))
    }
  }

  private val holderForRangeInfo: ComponentTreeHolderRangeInfo?
    get() {
      var holderForRangeInfo: ComponentTreeHolderRangeInfo? = null

      if (_componentTreeHolders.isNotEmpty()) {
        val positionToComputeLayout =
            findInitialComponentPosition(_componentTreeHolders, traverseLayoutBackwards)
        if (currentFirstVisiblePosition < _componentTreeHolders.size &&
            positionToComputeLayout >= 0) {
          holderForRangeInfo =
              ComponentTreeHolderRangeInfo(positionToComputeLayout, _componentTreeHolders)
        }
      } else if (asyncComponentTreeHolders.isNotEmpty()) {
        val positionToComputeLayout =
            findInitialComponentPosition(asyncComponentTreeHolders, traverseLayoutBackwards)
        if (positionToComputeLayout >= 0) {
          holderForRangeInfo =
              ComponentTreeHolderRangeInfo(positionToComputeLayout, asyncComponentTreeHolders)
        }
      }

      return holderForRangeInfo
    }

  init {

    this.componentContext = builder.componentContext
    this.lithoVisibilityEventsController = builder.lithoVisibilityEventsController

    this.componentTreeHolderFactory = builder.componentTreeHolderFactory

    /*
     * If there is no configuration set, then we retrieve it from the owning
     * [com.facebook.litho.ComponentContext]
     */
    val recyclerBinderConfigComponentsConfiguration = recyclerBinderConfig?.componentsConfiguration
    var tempConfiguration =
        recyclerBinderConfigComponentsConfiguration
            ?: checkNotNull(componentContext).lithoConfiguration.componentsConfig

    tempConfiguration =
        ComponentsConfiguration.create(tempConfiguration) /*
            Incremental mount will not work if this ComponentTree is nested in a parent with it turned off,
            so always disable it in that case
             */
            .incrementalMountEnabled(
                ComponentContext.isIncrementalMountEnabled(checkNotNull(componentContext)) &&
                    tempConfiguration.incrementalMountEnabled)
            .build()

    componentsConfiguration = tempConfiguration

    /*
     This is a work-around to use the client explicit config, or if that hasn't happened to resort
     to the one defined in the ComponentsConfiguration. We have to use this approach because atm the
     [RecyclerBinderConfig] has an optional components configuration.
    */
    val enableStableIdsToUse: Boolean =
        recyclerBinderConfig?.enableStableIds
            ?: componentsConfiguration.useStableIdsInRecyclerBinder

    // we cannot enable circular list and stable id at the same time
    this.enableStableIds = (!checkNotNull(recyclerBinderConfig).isCircular && enableStableIdsToUse)
    recyclerBinderAdapterDelegate =
        (builder.adapterDelegate ?: DefaultRecyclerBinderAdapterDelegate())
    this.additionalPostDispatchDrawListeners =
        (builder.additionalPostDispatchDrawListeners ?: ArrayList())
    internalAdapter =
        (builder.overrideInternalAdapter
            ?: InternalAdapter() as RecyclerView.Adapter<RecyclerView.ViewHolder>)
    notifyDatasetChangedRunnable = Runnable { internalAdapter.notifyDataSetChanged() }

    this.rangeRatio = recyclerBinderConfig.rangeRatio
    this.layoutInfo = builder.layoutInfo
    this.layoutHandlerFactory = recyclerBinderConfig.layoutHandlerFactory
    asyncInsertHandler = builder.asyncInsertLayoutHandler
    this.recyclerViewItemPrefetch = recyclerBinderConfig.recyclerViewItemPrefetch
    this.requestMountForPrefetchedItems = recyclerBinderConfig.requestMountForPrefetchedItems
    this.itemViewCacheSize = recyclerBinderConfig.itemViewCacheSize
    this.paginationStrategy = recyclerBinderConfig.paginationStrategy

    renderInfoViewCreatorController = RenderInfoViewCreatorController(builder.componentViewType)

    this.isCircular = recyclerBinderConfig.isCircular
    hasDynamicItemHeight =
        layoutInfo?.getScrollDirection() == HORIZONTAL &&
            (recyclerBinderConfig.crossAxisWrapMode == CrossAxisWrapMode.Dynamic)
    componentTreeMeasureListenerFactory =
        if (!hasDynamicItemHeight) null
        else ComponentTreeMeasureListenerFactory { holder -> getMeasureListener(holder) }

    isMainAxisWrapContent = recyclerBinderConfig.wrapContent
    isCrossAxisWrapContent = recyclerBinderConfig.crossAxisWrapMode != CrossAxisWrapMode.NoWrap
    traverseLayoutBackwards = stackFromEnd

    rangeTraverser =
        if (builder.recyclerRangeTraverser != null) {
          checkNotNull(builder.recyclerRangeTraverser)
        } else if (traverseLayoutBackwards) { // layout from end
          RecyclerRangeTraverser.BACKWARD_TRAVERSER
        } else {
          RecyclerRangeTraverser.FORWARD_TRAVERSER
        }

    viewportManager =
        ViewportManager(
            currentFirstVisiblePosition,
            currentLastVisiblePosition,
            checkNotNull(builder.layoutInfo))

    updateViewportRunnable =
        object : Runnable {
          override fun run() {
            val mountedView = this@RecyclerBinder.mountedView
            if (mountedView == null || !mountedView.hasPendingAdapterUpdates()) {
              if (viewportManager.shouldUpdate()) {
                viewportManager.onViewportChanged(ViewportInfo.State.DATA_CHANGES)
              }
              postUpdateViewportAttempts = 0
              return
            }

            // If the view gets detached, we might still have pending updates.
            // If the view's visibility is GONE, layout won't happen until it becomes visible. We
            // have to exit here, otherwise we keep posting this runnable to the next frame until it
            // becomes visible.
            if (!mountedView.isAttachedToWindow || mountedView.visibility == View.GONE) {
              postUpdateViewportAttempts = 0
              return
            }

            if (postUpdateViewportAttempts >= POST_UPDATE_VIEWPORT_AND_COMPUTE_RANGE_MAX_ATTEMPTS) {
              postUpdateViewportAttempts = 0
              if (viewportManager.shouldUpdate()) {
                viewportManager.onViewportChanged(ViewportInfo.State.DATA_CHANGES)
              }

              return
            }

            // If we have pending updates, wait until the sync operations are finished and try again
            // in the next frame.
            postUpdateViewportAttempts++
            ViewCompat.postOnAnimation(mountedView, this)
          }
        }

    if (recyclerBinderConfig.estimatedViewportCount != null) {
      this.estimatedViewportCount = recyclerBinderConfig.estimatedViewportCount
      hasManualEstimatedViewportCount = true
    } else {
      hasManualEstimatedViewportCount = false
    }

    this.hScrollAsyncMode = recyclerBinderConfig.hScrollAsyncMode

    this.stickyHeaderControllerFactory = builder.stickyHeaderControllerFactory
    this.isSubAdapter = builder.isSubAdapter
    this.componentWarmer = recyclerBinderConfig.componentWarmer
    this.startupLogger = builder.startupLogger
    this.recyclingStrategy = recyclerBinderConfig.recyclingStrategy
    this.errorHandler = builder.errorHandler
    this.poolScope = builder.poolScope
  }

  @VisibleForTesting
  class ComponentTreeHolderRangeInfo
  @VisibleForTesting
  constructor(val position: Int, val holders: List<ComponentTreeHolder?>)

  @VisibleForTesting
  /** Used for finding components to calculate layout during async init range */
  internal class ComponentAsyncInitRangeIterator(
      holders: List<ComponentTreeHolder?>,
      initialPosition: Int,
      private var numberOfItemsToProcess: Int,
      private val traverseLayoutBackwards: Boolean
  ) : MutableIterator<ComponentTreeHolder?> {
    private val holders: List<ComponentTreeHolder?> = ArrayList(holders)

    private var currentPosition =
        if (traverseLayoutBackwards) (initialPosition - 1) else (initialPosition + 1)

    override fun hasNext(): Boolean {
      while (numberOfItemsToProcess > 0 && isValidPosition(currentPosition)) {
        val holder = checkNotNull(holders[currentPosition])
        if (holder.renderInfo.rendersComponent() && !holder.isTreeValid) {
          return true
        } else {
          shiftToNextPosition()
        }
      }
      return false
    }

    fun isValidPosition(position: Int): Boolean = position >= 0 && position < holders.size

    override fun next(): ComponentTreeHolder? {
      return synchronized(this@ComponentAsyncInitRangeIterator) {
        if (!hasNext()) {
          return null
        }

        val holder = holders[currentPosition]
        shiftToNextPosition()
        numberOfItemsToProcess--
        holder
      }
    }

    private fun shiftToNextPosition() {
      if (traverseLayoutBackwards) {
        currentPosition--
      } else {
        currentPosition++
      }
    }

    override fun remove(): Unit = Unit
  }

  companion object {
    private val dummySize = Size()
    private val dummyRect = Rect()
    private val emptyRect = Rect()
    private val TAG: String = RecyclerBinder::class.java.simpleName
    private const val POST_UPDATE_VIEWPORT_AND_COMPUTE_RANGE_MAX_ATTEMPTS = 3
    private const val DATA_RENDERED_CALLBACKS_QUEUE_MAX_SIZE = 20
    private const val DATA_RENDERED_NOT_TRIGGERED = "RecyclerBinder:DataRenderedNotTriggered"
    private val UNINITIALIZED = MeasureSpecUtils.unspecified()
    const val UNSET: Int = -1
    const val APPLY_READY_BATCHES_RETRY_LIMIT: Int = 100
    const val ID_CUSTOM_ATTR_KEY: String = "id"

    @UiThread
    private fun releaseComponentTreeHolders(holders: List<ComponentTreeHolder?>) {
      for (i in 0 until holders.size) {
        holders[i]?.releaseTree()
      }
    }

    @UiThread
    private fun dispatchRenderCompleteEvent(
        renderCompleteEventHandler: EventHandler<RenderCompleteEvent>,
        renderState: RenderCompleteEvent.RenderState,
        timestampMillis: Long
    ) {
      ThreadUtils.assertMainThread()

      val event = RenderCompleteEvent()
      event.renderState = renderState
      event.timestampMillis = timestampMillis
      renderCompleteEventHandler.dispatchEvent(event)
    }

    private fun isMatchingParentSize(percent: Float): Boolean = percent in 0.0..100.0

    private fun isBatchReady(batch: AsyncBatch): Boolean {
      if (batch.commitPolicy == CommitPolicy.IMMEDIATE) {
        return true
      }

      for (i in 0 until batch.operations.size) {
        val operation = batch.operations[i]
        if (operation is AsyncInsertOperation && !operation.holder.hasCompletedLatestLayout()) {
          return false
        }
      }
      return true
    }

    /** @return return true if we need to interrupt the workflow. */
    private fun handleIndexOutOfBoundsException(
        operation: String,
        size: Int,
        position: Int,
        isAddingElement: Boolean,
        renderInfo: RenderInfo?,
        errorHandler: ((Exception) -> Unit)?
    ): Boolean {
      if (isAddingElement) {
        if (position in 0..size) {
          // Adding element is safe as the position is in a valid range
          return false
        }
      } else {
        if (position in 0..<size) {
          // Accessing element is safe as the position is in a valid range
          return false
        }
      }

      val e =
          RecyclerBinderException(
              ("Trying to [${operation}] while index is out of bounds (index=$position, size=$size). This likely means data passed to the section had duplicates or a mutable data model. Component involved in the error whose backing data model may have duplicates: ${renderInfo?.name ?: "NULL"}. Read more here: https://fblitho.com/docs/sections/best-practices/#avoiding-indexoutofboundsexception"))

      if (errorHandler != null) {
        errorHandler.invoke(e)
      } else {
        throw e
      }
      return true
    }

    /*
     * Print the view hierarchy to help identify the incorrect usage of Recycler.
     */
    private fun printViewHierarchy(
        parent: ViewParent?,
        hierarchy: MutableList<ViewParent>
    ): String {
      if (parent == null) {
        val builder = StringBuilder()
        val indent = "  "
        var level = 0
        for (i in hierarchy.indices.reversed()) {
          val view = hierarchy[i]
          for (j in 0..<level) {
            builder.append(indent)
          }
          builder.append(view).append("\n")
          level++
        }
        return builder.toString()
      }
      hierarchy.add(parent)
      return printViewHierarchy(parent.parent, hierarchy)
    }

    private fun validateMeasureSpecs(
        mountView: View?,
        widthSpec: Int,
        heightSpec: Int,
        canRemeasure: Boolean,
        scrollDirection: Int
    ) {
      when (scrollDirection) {
        HORIZONTAL -> {
          if (getMode(widthSpec) == SizeSpec.UNSPECIFIED) {
            val viewHierarchy =
                if (mountView != null) printViewHierarchy(mountView.parent, ArrayList())
                else "EMPTY"

            throw IllegalStateException(
                ("Width mode has to be EXACTLY OR AT MOST for an horizontal scrolling RecyclerView. The view hierarchy is: ${viewHierarchy}"))
          }

          if (!canRemeasure && getMode(heightSpec) == SizeSpec.UNSPECIFIED) {
            val viewHierarchy =
                if (mountView != null) printViewHierarchy(mountView.parent, ArrayList())
                else "EMPTY"

            throw IllegalStateException(
                ("Can't use Unspecified height on an horizontal scrolling Recycler if dynamic measurement is not allowed.The view hierarchy is: ${viewHierarchy}"))
          }
        }

        VERTICAL -> {
          if (getMode(heightSpec) == SizeSpec.UNSPECIFIED) {
            val viewHierarchy =
                if (mountView != null) printViewHierarchy(mountView.parent, ArrayList())
                else "EMPTY"

            throw IllegalStateException(
                ("Height mode has to be EXACTLY OR AT MOST for a vertical scrolling RecyclerView. The view hierarchy is: ${viewHierarchy}"))
          }

          if (!canRemeasure && getMode(widthSpec) == SizeSpec.UNSPECIFIED) {
            val viewHierarchy =
                if (mountView != null) printViewHierarchy(mountView.parent, ArrayList())
                else "EMPTY"

            throw IllegalStateException(
                ("Can't use Unspecified width on a vertical scrolling Recycler if dynamic measurement is not allowed. The view hierarchy is: ${viewHierarchy}"))
          }
        }

        else ->
            throw UnsupportedOperationException(
                "The orientation defined by LayoutInfo should be either OrientationHelper.HORIZONTAL or OrientationHelper.VERTICAL")
      }
    }

    /**
     * @return true if the measure specs we are trying to measure this with cannot be used and we
     *   need to measure an item to get a size.
     */
    @JvmStatic
    fun shouldMeasureItemForSize(
        widthSpec: Int,
        heightSpec: Int,
        scrollDirection: Int,
        canRemeasure: Boolean
    ): Boolean {
      val canUseSizeSpec =
          if (scrollDirection == VERTICAL) (getMode(widthSpec) == SizeSpec.EXACTLY)
          else (getMode(heightSpec) == SizeSpec.EXACTLY)

      return !canUseSizeSpec && canRemeasure
    }

    @JvmStatic
    fun findInitialComponentPosition(
        holders: List<ComponentTreeHolder?>,
        traverseBackwards: Boolean
    ): Int {
      if (traverseBackwards) {
        for (i in holders.indices.reversed()) {
          if (checkNotNull(holders[i]).renderInfo.rendersComponent()) {
            return i
          }
        }
      } else {
        for (i in 0 until holders.size) {
          if (checkNotNull(holders[i]).renderInfo.rendersComponent()) {
            return i
          }
        }
      }
      return -1
    }

    @UiThread
    private fun maybeAcquireStateAndReleaseTree(holder: ComponentTreeHolder) {
      val componentTree = holder.componentTree
      if (canReleaseTree(holder) && (componentTree != null && componentTree.lithoView == null)) {
        holder.acquireStateAndReleaseTree()
      }
    }

    private fun canReleaseTree(holder: ComponentTreeHolder): Boolean =
        holder.isTreeValid &&
            !holder.shouldPreventRelease() &&
            !holder.renderInfo.isSticky &&
            holder.componentTree != null

    /**
     * @return true if the given view is visible to user, false otherwise. The logic is leveraged
     *   from [View#isVisibleToUser()].
     */
    private fun isVisibleToUser(view: View): Boolean {
      if (view.windowVisibility != View.VISIBLE) {
        return false
      }

      var current: Any? = view
      while (current is View) {
        val currentView = current
        if (currentView.alpha <= 0 || currentView.visibility != View.VISIBLE) {
          return false
        }
        current = currentView.parent
      }

      return view.getGlobalVisibleRect(dummyRect)
    }

    /** @return a list of view's visibility, iterating from given view to its ancestor views. */
    private fun getVisibleHierarchy(view: View): List<String> {
      val hierarchy: MutableList<String> = ArrayList()
      var current: Any? = view
      while (current is View) {
        val currentView = current
        hierarchy.add(
            ("view=${currentView.javaClass.simpleName}, alpha=${currentView.alpha}, visibility=${currentView.visibility}"))
        if (currentView.alpha <= 0 || currentView.visibility != View.VISIBLE) {
          break
        }
        current = currentView.parent
      }
      return hierarchy
    }
  }
}
