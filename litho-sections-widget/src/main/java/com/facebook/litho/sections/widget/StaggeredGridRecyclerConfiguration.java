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

package com.facebook.litho.sections.widget;

import static com.facebook.litho.widget.SnapUtil.SNAP_NONE;

import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SnapHelper;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import com.facebook.infer.annotation.Nullsafe;
import com.facebook.litho.ComponentContext;
import com.facebook.litho.widget.LayoutInfo;
import com.facebook.litho.widget.StaggeredGridLayoutInfo;
import javax.annotation.Nullable;

/**
 * A configuration object for {@link RecyclerCollectionComponent} that will create a {@link
 * androidx.recyclerview.widget.StaggeredGridLayoutManager} for the {@link RecyclerView}.
 */
@Nullsafe(Nullsafe.Mode.LOCAL)
public class StaggeredGridRecyclerConfiguration implements RecyclerConfiguration {
  private final int mNumSpans;
  private final int mOrientation;
  private final boolean mReverseLayout;
  private final boolean mStackFromEnd;
  private final int mGapStrategy;
  private final RecyclerBinderConfiguration mRecyclerBinderConfiguration;
  private final StaggeredGridLayoutInfoFactory mLayoutInfoFactory;

  public static Builder create() {
    return new Builder();
  }

  private StaggeredGridRecyclerConfiguration(
      int numSpans,
      int orientation,
      boolean reverseLayout,
      boolean stackFromEnd,
      int gapStrategy,
      RecyclerBinderConfiguration recyclerBinderConfiguration,
      @Nullable StaggeredGridLayoutInfoFactory layoutInfoFactory) {
    mNumSpans = numSpans;
    mOrientation = orientation;
    mReverseLayout = reverseLayout;
    mStackFromEnd = stackFromEnd;
    mGapStrategy = gapStrategy;
    mRecyclerBinderConfiguration = recyclerBinderConfiguration;
    mLayoutInfoFactory =
        layoutInfoFactory == null ? Builder.STAGGERED_GRID_LAYOUT_INFO_FACTORY : layoutInfoFactory;
  }

  @Override
  public Builder acquireBuilder() {
    return new Builder(this);
  }

  @Override
  public @Nullable SnapHelper getSnapHelper() {
    return null;
  }

  @Override
  public int getSnapMode() {
    return SNAP_NONE;
  }

  @Override
  public int getOrientation() {
    return mOrientation;
  }

  @Override
  public boolean getReverseLayout() {
    return mReverseLayout;
  }

  @Override
  public boolean getStackFromEnd() {
    return mStackFromEnd;
  }

  @Override
  public LayoutInfo getLayoutInfo(ComponentContext c) {
    return mLayoutInfoFactory.createStaggeredGridLayoutInfo(
        mNumSpans, mOrientation, mReverseLayout, mGapStrategy);
  }

  @Override
  public RecyclerBinderConfiguration getRecyclerBinderConfiguration() {
    return mRecyclerBinderConfiguration;
  }

  public int getNumSpans() {
    return mNumSpans;
  }

  public int getGapStrategy() {
    return mGapStrategy;
  }

  private static class DefaultStaggeredGridLayoutInfoFactory
      implements StaggeredGridLayoutInfoFactory {
    @Override
    public StaggeredGridLayoutInfo createStaggeredGridLayoutInfo(
        int spanCount, int orientation, boolean reverseLayout, int gapStrategy) {
      return new StaggeredGridLayoutInfo(spanCount, orientation, reverseLayout, gapStrategy);
    }
  }

  public static final class Builder implements RecyclerConfiguration.Builder {
    static final RecyclerBinderConfiguration RECYCLER_BINDER_CONFIGURATION =
        RecyclerBinderConfiguration.create().build();
    public static final StaggeredGridLayoutInfoFactory STAGGERED_GRID_LAYOUT_INFO_FACTORY =
        new DefaultStaggeredGridLayoutInfoFactory();

    private int mNumSpans = 2;
    private int mOrientation = StaggeredGridLayoutManager.VERTICAL;
    private boolean mReverseLayout = false;
    private boolean mStackFromEnd = false;
    private int mGapStrategy = StaggeredGridLayoutManager.GAP_HANDLING_NONE;
    private RecyclerBinderConfiguration mRecyclerBinderConfiguration =
        RECYCLER_BINDER_CONFIGURATION;
    private @Nullable StaggeredGridLayoutInfoFactory mLayoutInfoFactory;

    Builder() {}

    Builder(StaggeredGridRecyclerConfiguration configuration) {
      this.mNumSpans = configuration.mNumSpans;
      this.mOrientation = configuration.mOrientation;
      this.mReverseLayout = configuration.mReverseLayout;
      this.mStackFromEnd = configuration.mStackFromEnd;
      this.mGapStrategy = configuration.mGapStrategy;
      this.mRecyclerBinderConfiguration = configuration.mRecyclerBinderConfiguration;
      this.mLayoutInfoFactory = configuration.mLayoutInfoFactory;
    }

    @Override
    public Builder snapMode(int snapMode) {
      throw new UnsupportedOperationException(
          "SnapMode is not supported for StaggeredGridRecyclerConfiguration");
    }

    public Builder numSpans(int numSpans) {
      mNumSpans = numSpans;
      return this;
    }

    @Override
    public Builder orientation(int orientation) {
      mOrientation = orientation;
      return this;
    }

    @Override
    public Builder reverseLayout(boolean reverseLayout) {
      mReverseLayout = reverseLayout;
      return this;
    }

    @Override
    public Builder stackFromEnd(boolean stackFromEnd) {
      mStackFromEnd = stackFromEnd;
      return this;
    }

    public Builder gapStrategy(int gapStrategy) {
      mGapStrategy = gapStrategy;
      return this;
    }

    @Override
    public Builder recyclerBinderConfiguration(
        RecyclerBinderConfiguration recyclerBinderConfiguration) {
      mRecyclerBinderConfiguration = recyclerBinderConfiguration;
      return this;
    }

    /**
     * Provide a customized {@link StaggeredGridLayoutInfo} through {@link
     * StaggeredGridLayoutInfoFactory} interface.
     */
    public Builder staggeredGridLayoutInfoFactory(
        @Nullable StaggeredGridLayoutInfoFactory staggeredGridLayoutInfoFactory) {
      mLayoutInfoFactory = staggeredGridLayoutInfoFactory;
      return this;
    }

    /**
     * Builds a {@link StaggeredGridRecyclerConfiguration} using the parameters specified in this
     * builder.
     */
    @Override
    public StaggeredGridRecyclerConfiguration build() {
      return new StaggeredGridRecyclerConfiguration(
          mNumSpans,
          mOrientation,
          mReverseLayout,
          mStackFromEnd,
          mGapStrategy,
          mRecyclerBinderConfiguration,
          mLayoutInfoFactory);
    }
  }
}
