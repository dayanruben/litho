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

package com.facebook.litho;

import android.content.Context;
import android.util.SparseArray;
import androidx.annotation.Nullable;
import com.facebook.infer.annotation.Nullsafe;
import com.facebook.litho.config.ComponentsConfiguration;
import com.facebook.rendercore.MountContentPools;
import com.facebook.rendercore.PoolingPolicy;

@Nullsafe(Nullsafe.Mode.LOCAL)
class HostComponent extends SpecGeneratedComponent {

  /**
   * We duplicate mComponentDynamicProps here, in order to provide {@link
   * #setCommonDynamicProps(SparseArray)} to HostComponent only, which is used in LayoutState to
   * pass Common Dynamic Props from other Components that do not mount a view
   */
  @Nullable private SparseArray<DynamicValue<?>> mCommonDynamicProps;

  private boolean mImplementsVirtualViews = false;
  private final PoolingPolicy mPoolingPolicy;
  private final @Nullable ComponentHost.UnsafeModificationPolicy mUnsafeModificationPolicy;

  protected HostComponent(
      PoolingPolicy poolingPolicy,
      @Nullable ComponentHost.UnsafeModificationPolicy unsafeModificationPolicy) {
    super("HostComponent");
    mUnsafeModificationPolicy = unsafeModificationPolicy;
    mPoolingPolicy = poolingPolicy;
  }

  @Override
  public PoolingPolicy getPoolingPolicy() {
    return mPoolingPolicy;
  }

  @Override
  public boolean canPreallocate() {
    return mPoolingPolicy.canAcquireContent;
  }

  @Override
  protected Object onCreateMountContent(Context c) {
    return new ComponentHost(c, mUnsafeModificationPolicy);
  }

  @Override
  public MountContentPools.ContentPool onCreateMountContentPool(int poolSizeOverride) {
    return new HostMountContentPool(
        poolSizeOverride, mPoolingPolicy.canAcquireContent || mPoolingPolicy.canReleaseContent);
  }

  @Override
  protected void onMount(
      final @Nullable ComponentContext c,
      final Object convertContent,
      final @Nullable InterStagePropsContainer interStagePropsContainer) {
    final ComponentHost host = (ComponentHost) convertContent;

    host.setImplementsVirtualViews(mImplementsVirtualViews);
    if (c != null) {
      ComponentsConfiguration componentsConfiguration = c.getLithoConfiguration().componentsConfig;
      host.setClipChildren(componentsConfiguration.enableChildClipping);
    }
  }

  @Override
  protected void onUnmount(
      final @Nullable ComponentContext c,
      final Object mountedContent,
      final @Nullable InterStagePropsContainer interStagePropsContainer) {
    final ComponentHost host = (ComponentHost) mountedContent;

    // Some hosts might be duplicating parent state which could be 'pressed' and under certain
    // conditions that state might not be cleared from this host and carried to next reuse,
    // therefore applying wrong drawable state. Particular case where this might happen is when
    // host is unmounted as soon as click event is triggered, and host is unmounted before it has
    // chance to reset its internal pressed state.
    if (host.isPressed()) {
      host.setPressed(false);
    }

    host.setClipChildren(true);

    host.setImplementsVirtualViews(false);
  }

  @Override
  protected void onBind(
      @Nullable ComponentContext c,
      Object mountedContent,
      @Nullable InterStagePropsContainer interStagePropsContainer) {
    final ComponentHost host = (ComponentHost) mountedContent;
    host.maybeInvalidateAccessibilityState();
  }

  @Override
  public MountType getMountType() {
    return MountType.VIEW;
  }

  static HostComponent create(ComponentContext context) {
    ComponentsConfiguration componentsConfiguration =
        context.getLithoConfiguration().componentsConfig;

    ComponentHost.UnsafeModificationPolicy policy =
        componentsConfiguration.componentHostInvalidModificationPolicy;

    /* If we are testing host recycling and no policy was set, then we override to be LOG */
    if (policy != ComponentHost.UnsafeModificationPolicy.CRASH
        && componentsConfiguration.componentHostPoolingPolicy.canReleaseContent) {
      policy = ComponentHost.UnsafeModificationPolicy.LOG;
    }

    return new HostComponent(componentsConfiguration.componentHostPoolingPolicy, policy);
  }

  @Override
  public boolean isEquivalentProps(@Nullable Component other, boolean shouldCompareCommonProps) {
    return this == other;
  }

  @Override
  public int poolSize() {
    return ComponentsConfiguration.hostComponentPoolSize;
  }

  @Override
  protected boolean shouldUpdate(
      final Component previous,
      final @Nullable StateContainer previousStateContainer,
      final Component next,
      final @Nullable StateContainer nextStateContainer) {
    return true;
  }

  @Nullable
  @Override
  SparseArray<DynamicValue<?>> getCommonDynamicProps() {
    return mCommonDynamicProps;
  }

  @Override
  boolean hasCommonDynamicProps() {
    return CollectionsUtils.isNotNullOrEmpty(mCommonDynamicProps);
  }

  /**
   * Sets common dynamic Props. Used in {@link LayoutState} to pass dynamic props from a component,
   * to the host, that's wrapping it
   *
   * @param commonDynamicProps common dynamic props to set.
   * @see LayoutState#createHostLayoutOutput(LayoutState, LithoNode, boolean)
   */
  void setCommonDynamicProps(@Nullable SparseArray<DynamicValue<?>> commonDynamicProps) {
    mCommonDynamicProps = commonDynamicProps;
  }

  void setImplementsVirtualViews() {
    mImplementsVirtualViews = true;
  }
}
