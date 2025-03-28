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

package com.facebook.litho.testing;

import androidx.annotation.Nullable;
import com.facebook.infer.annotation.Nullsafe;
import com.facebook.litho.Component;
import com.facebook.litho.EventHandler;
import com.facebook.litho.SpecGeneratedComponent;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Base class for test components which expose lifecycle information.
 *
 * @deprecated Component should not be directly subclassed, write a layout spec or mount spec
 *     instead
 */
@Nullsafe(Nullsafe.Mode.LOCAL)
@Deprecated
public abstract class TestComponent extends SpecGeneratedComponent {

  private final Map<EventHandler<?>, Object> mDispatchedEventHandlers = new HashMap<>();
  private boolean mOnMountCalled;
  private boolean mMounted;
  private boolean mOnUnmountCalled;
  private boolean mOnBoundsDefinedCalled;
  private boolean mOnBindCalled;
  private boolean mBound;
  private boolean mOnUnbindCalled;
  private boolean mOnMeasureCalled;
  private boolean mOnAttachedCalled;
  private boolean mOnDetachedCalled;
  private boolean mIsEquivalentToCalled;

  protected TestComponent(String simpleName) {
    super(simpleName);
  }

  protected TestComponent() {
    super("TestComponent");
  }

  void onMountCalled() {
    mOnMountCalled = true;
    mMounted = true;
  }

  void onUnmountCalled() {
    mOnUnmountCalled = true;
    mMounted = false;
  }

  void onMeasureCalled() {
    mOnMeasureCalled = true;
  }

  void onDefineBoundsCalled() {
    mOnBoundsDefinedCalled = true;
  }

  void onBindCalled() {
    mOnBindCalled = true;
    mBound = true;
  }

  void onUnbindCalled() {
    mOnUnbindCalled = true;
    mBound = false;
  }

  synchronized void onAttachedCalled() {
    mOnAttachedCalled = true;
  }

  synchronized void onDetachedCalled() {
    mOnDetachedCalled = true;
  }

  /**
   * @return Whether onMount has been called.
   */
  public boolean wasOnMountCalled() {
    return mOnMountCalled;
  }

  /**
   * @return Whether the component is currently mounted.
   */
  public boolean isMounted() {
    return mMounted;
  }

  /**
   * @return Whether onUnmount has been called.
   */
  public boolean wasOnUnmountCalled() {
    return mOnUnmountCalled;
  }

  /**
   * @return Whether onBoundsDefined has been called.
   */
  public boolean wasOnBoundsDefinedCalled() {
    return mOnBoundsDefinedCalled;
  }

  /**
   * @return Whether onBind has been called.
   */
  public boolean wasOnBindCalled() {
    return mOnBindCalled;
  }

  /**
   * @return Whether the component is bound.
   */
  public boolean isBound() {
    return mBound;
  }

  /**
   * @return Whether onUnbind has been called.
   */
  public boolean wasOnUnbindCalled() {
    return mOnUnbindCalled;
  }

  public boolean wasMeasureCalled() {
    return mOnMeasureCalled;
  }

  /**
   * @return Whether onAttached has been called.
   */
  public synchronized boolean wasOnAttachedCalled() {
    return mOnAttachedCalled;
  }

  /**
   * @return Whether onDetached has been called.
   */
  public synchronized boolean wasOnDetachedCalled() {
    return mOnDetachedCalled;
  }

  @Override
  public boolean isEquivalentProps(@Nullable Component other, boolean shouldCompareCommonProps) {
    mIsEquivalentToCalled = true;
    return super.isEquivalentProps(other, shouldCompareCommonProps);
  }

  /** Reset the tracking of which methods have been called on this component. */
  public synchronized void resetInteractions() {
    mOnMeasureCalled = false;
    mOnBoundsDefinedCalled = false;
    mOnBindCalled = false;
    mOnMountCalled = false;
    mOnUnbindCalled = false;
    mOnUnmountCalled = false;
    mOnAttachedCalled = false;
    mOnDetachedCalled = false;
    mIsEquivalentToCalled = false;
  }

  @Nullable
  @Override
  public Object dispatchOnEventImpl(EventHandler eventHandler, Object eventState) {
    mDispatchedEventHandlers.put(eventHandler, eventState);
    return null;
  }

  public Set<EventHandler<?>> getDispatchedEventHandlers() {
    return mDispatchedEventHandlers.keySet();
  }

  public @Nullable Object getEventState(EventHandler eventHandler) {
    return mDispatchedEventHandlers.get(eventHandler);
  }

  public boolean isEquivalentToCalled() {
    return mIsEquivalentToCalled;
  }
}
