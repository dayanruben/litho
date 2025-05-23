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

import static com.facebook.litho.LithoRenderUnit.getComponentContext;
import static com.facebook.litho.LithoRenderUnit.getRenderUnit;

import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityEvent;
import androidx.core.view.AccessibilityDelegateCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.view.accessibility.AccessibilityNodeProviderCompat;
import androidx.customview.widget.ExploreByTouchHelper;
import com.facebook.infer.annotation.Nullsafe;
import com.facebook.rendercore.MountItem;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Class that is used to set up accessibility for {@link ComponentHost}s. Virtual nodes are only
 * exposed if the component implements support for extra accessibility nodes.
 */
@Nullsafe(Nullsafe.Mode.LOCAL)
class ComponentAccessibilityDelegate extends ExploreByTouchHelper {
  private static final String TAG = "ComponentAccessibility";
  // Maps to the private View.FOCUSABLE
  private static final int VIEW_FOCUSABLE = 1;

  private final View mView;
  private @Nullable NodeInfo mNodeInfo;
  private final AccessibilityDelegateCompat mSuperDelegate;
  private static final Rect sDefaultBounds = new Rect(0, 0, 1, 1);

  ComponentAccessibilityDelegate(
      View view,
      @Nullable NodeInfo nodeInfo,
      int originalFocus,
      int originalImportantForAccessibility) {
    super(view);
    mView = view;
    mNodeInfo = nodeInfo;
    mSuperDelegate = new SuperDelegate();

    // We need to reset these two properties, as ExploreByTouchHelper sets focusable to "true" and
    // importantForAccessibility to "Yes" (if it is Auto). If we don't reset these it would force
    // every element that has this delegate attached to be focusable, and not allow for
    // announcement coalescing.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      mView.setFocusable(originalFocus);
    } else {
      // In APIs prior to 26, the View.FOCUSABLE int was private, so check against its value.
      mView.setFocusable(originalFocus == VIEW_FOCUSABLE);
    }
    ViewCompat.setImportantForAccessibility(mView, originalImportantForAccessibility);
  }

  ComponentAccessibilityDelegate(
      View view, int originalFocus, int originalImportantForAccessibility) {
    this(view, null, originalFocus, originalImportantForAccessibility);
  }

  /**
   * {@link ComponentHost} contains the logic for setting the {@link NodeInfo} containing the {@link
   * EventHandler}s for its delegate instance whenever it is set/unset
   *
   * @see ComponentHost#setTag(int, Object)
   */
  void setNodeInfo(NodeInfo nodeInfo) {
    mNodeInfo = nodeInfo;
  }

  @Override
  public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfoCompat node) {
    final MountItem mountItem = getAccessibleMountItem(mView);

    if (mNodeInfo != null && mNodeInfo.getOnInitializeAccessibilityNodeInfoHandler() != null) {
      EventDispatcherUtils.dispatchOnInitializeAccessibilityNodeInfoEvent(
          mNodeInfo.getOnInitializeAccessibilityNodeInfoHandler(), host, node, mSuperDelegate);
      dispatchOnPopulateAccessibilityNodeEvent(host, node);
    } else if (mountItem != null) {
      super.onInitializeAccessibilityNodeInfo(host, node);
      // Coalesce the accessible mount item's information with the
      // the root host view's as they are meant to behave as a single
      // node in the accessibility framework.
      final Component component = getRenderUnit(mountItem).getComponent();
      final ComponentContext scopedContext = getComponentContext(mountItem.getRenderTreeNode());
      try {
        dispatchOnPopulateAccessibilityNodeEvent(host, node);
        if (component instanceof SpecGeneratedComponent) {
          ((SpecGeneratedComponent) component)
              .onPopulateAccessibilityNode(
                  scopedContext, host, node, getInterStageProps(mountItem));
        }
      } catch (Exception e) {
        if (scopedContext != null) {
          ComponentUtils.handle(scopedContext, e);
        }
      }
    } else {
      super.onInitializeAccessibilityNodeInfo(host, node);
    }

    if (mNodeInfo != null
        && mNodeInfo.getScreenReaderFocusState() != NodeInfo.SCREEN_READER_FOCUS_UNSET) {
      node.setScreenReaderFocusable(
          mNodeInfo.getScreenReaderFocusState() == NodeInfo.SCREEN_READER_FOCUS_SET_TRUE);
    }

    if (mNodeInfo != null
        && mNodeInfo.getInitialFocusState() != NodeInfo.REQUEST_INITIAL_ACCESSIBILITY_FOCUS_UNSET) {
      node.setRequestInitialAccessibilityFocus(
          mNodeInfo.getScreenReaderFocusState()
              == NodeInfo.REQUEST_INITIAL_ACCESSIBILITY_FOCUS_SET_TRUE);
    }

    // If an accessibilityRole has been set, set the className here.  It's important that this
    // happens *after* any calls to super, since the super call will set a className of its own and
    // override this one.
    if (mNodeInfo != null && mNodeInfo.getAccessibilityRole() != null) {
      node.setClassName(mNodeInfo.getAccessibilityRole());
    }

    if (mNodeInfo != null && mNodeInfo.getAccessibilityRoleDescription() != null) {
      node.setRoleDescription(mNodeInfo.getAccessibilityRoleDescription());

      // if no role was explicitly specified, set a role of "NONE".  This allows the role
      // description to still be announced without changing any other behavior.
      if (mNodeInfo.getAccessibilityRole() == null) {
        node.setClassName(AccessibilityRole.NONE);
      }
    }

    if (mNodeInfo != null && mNodeInfo.getStateDescription() != null) {
      node.setStateDescription(mNodeInfo.getStateDescription());
    }

    if (mNodeInfo != null
        && mNodeInfo.getAccessibilityHeadingState() != NodeInfo.ACCESSIBILITY_HEADING_UNSET) {
      node.setHeading(
          mNodeInfo.getAccessibilityHeadingState() == NodeInfo.ACCESSIBILITY_HEADING_SET_TRUE);
    }

    if (mNodeInfo != null && mNodeInfo.getMinDurationBetweenContentChangesMillis() != null) {
      node.setMinDurationBetweenContentChangesMillis(
          mNodeInfo.getMinDurationBetweenContentChangesMillis());
    }

    if (mNodeInfo != null && mNodeInfo.getLabeledBy() != null && mountItem != null) {
      final ComponentContext scopedContext = getComponentContext(mountItem.getRenderTreeNode());
      final View labeledByView = scopedContext.findViewWithTag(mNodeInfo.getLabeledBy());
      if (labeledByView != null) {
        node.setLabeledBy(labeledByView);
      }
    }

    if (mNodeInfo != null && mNodeInfo.getFocusOrder() != null && mountItem != null) {
      final ComponentContext scopedContext = getComponentContext(mountItem.getRenderTreeNode());
      final FocusOrderModel focusOrder = mNodeInfo.getFocusOrder();
      if (focusOrder.getNext() != null) {
        View nextView = getNextView(focusOrder, scopedContext);
        if (nextView != null) {
          node.setTraversalBefore(nextView);
        }
      }
    }

    if (mNodeInfo != null) {
      node.setContainerTitle(mNodeInfo.getContainerTitle());
    }
  }

  public static @Nullable View getNextView(
      FocusOrderModel focusOrderModel, ComponentContext scopedContext) {
    FocusOrderModel current = focusOrderModel;
    while (current != null) {
      View nextView = null;
      if (current.getNext() != null) {
        nextView =
            scopedContext.findViewWithTagValue(
                R.id.component_focus_order, current.getNext().getKey());
      }
      if (nextView != null) {
        return nextView;
      }
      current = current.getNext();
    }
    return null;
  }

  private void dispatchOnPopulateAccessibilityNodeEvent(
      View host, AccessibilityNodeInfoCompat node) {
    if (mNodeInfo != null && mNodeInfo.getOnPopulateAccessibilityNodeHandler() != null) {
      EventDispatcherUtils.dispatchOnPopulateAccessibilityNode(
          mNodeInfo.getOnPopulateAccessibilityNodeHandler(), host, node);
    }
  }

  private void dispatchOnVirtualViewKeyboardFocusChangedEvent(
      View host, @Nullable AccessibilityNodeInfoCompat node, int virtualViewId, boolean hasFocus) {
    if (mNodeInfo != null && mNodeInfo.getOnVirtualViewKeyboardFocusChangedHandler() != null) {
      EventDispatcherUtils.dispatchVirtualViewKeyboardFocusChanged(
          mNodeInfo.getOnVirtualViewKeyboardFocusChangedHandler(),
          host,
          node,
          virtualViewId,
          hasFocus,
          mSuperDelegate);
    }
  }

  private boolean dispatchOnPerformActionForVirtualViewEvent(
      View host,
      AccessibilityNodeInfoCompat node,
      int virtualViewId,
      int action,
      @Nullable Bundle arguments) {
    if (mNodeInfo != null && mNodeInfo.getOnPerformActionForVirtualViewHandler() != null) {
      return EventDispatcherUtils.dispatchPerformActionForVirtualView(
          mNodeInfo.getOnPerformActionForVirtualViewHandler(),
          host,
          node,
          virtualViewId,
          action,
          arguments);
    }
    return false;
  }

  @Override
  protected void getVisibleVirtualViews(List<Integer> virtualViewIds) {
    final MountItem mountItem = getAccessibleMountItem(mView);
    if (mountItem == null) {
      return;
    }

    final LithoRenderUnit renderUnit = getRenderUnit(mountItem);
    if (!(renderUnit.getComponent() instanceof SpecGeneratedComponent)) {
      return;
    }
    final SpecGeneratedComponent component = (SpecGeneratedComponent) renderUnit.getComponent();
    final ComponentContext scopedContext = getComponentContext(mountItem);

    try {
      final int extraAccessibilityNodesCount =
          component.getExtraAccessibilityNodesCount(scopedContext, getInterStageProps(mountItem));

      // Expose extra accessibility nodes declared by the component to the
      // accessibility framework. The actual nodes will be populated in
      // {@link #onPopulateNodeForVirtualView}.
      for (int i = 0; i < extraAccessibilityNodesCount; i++) {
        virtualViewIds.add(i);
      }
    } catch (Exception e) {
      if (scopedContext != null) {
        ComponentUtils.handle(scopedContext, e);
      }
    }
  }

  @Override
  protected void onPopulateNodeForVirtualView(int virtualViewId, AccessibilityNodeInfoCompat node) {
    final MountItem mountItem = getAccessibleMountItem(mView);
    if (mountItem == null) {
      // ExploreByTouchHelper insists that we set something.
      node.setContentDescription("");
      node.setBoundsInParent(getDefaultBounds());
      return;
    }

    final Drawable drawable = (Drawable) mountItem.getContent();
    final Rect bounds = drawable.getBounds();

    final LithoRenderUnit renderUnit = getRenderUnit(mountItem);
    if (!(renderUnit.getComponent() instanceof SpecGeneratedComponent)) {
      return;
    }
    final SpecGeneratedComponent component = (SpecGeneratedComponent) renderUnit.getComponent();
    final ComponentContext scopedContext = getComponentContext(mountItem);

    node.setClassName(component.getClass().getName());

    try {
      if (virtualViewId
          >= component.getExtraAccessibilityNodesCount(
              scopedContext, getInterStageProps(mountItem))) {
        // ExploreByTouchHelper insists that we set something.
        node.setContentDescription("");
        node.setBoundsInParent(getDefaultBounds());
        return;
      }

      if (component instanceof SpecGeneratedComponent) {
        ((SpecGeneratedComponent) component)
            .onPopulateExtraAccessibilityNode(
                scopedContext,
                node,
                virtualViewId,
                bounds.left,
                bounds.top,
                getInterStageProps(mountItem));
      }
    } catch (Exception e) {
      if (scopedContext != null) {
        ComponentUtils.handle(scopedContext, e);
      }
    }
  }

  /**
   * Finds extra accessibility nodes under the given event coordinates. Returns {@link #INVALID_ID}
   * otherwise.
   */
  @Override
  protected int getVirtualViewAt(float x, float y) {
    final MountItem mountItem = getAccessibleMountItem(mView);
    if (mountItem == null) {
      return INVALID_ID;
    }

    final LithoRenderUnit renderUnit = getRenderUnit(mountItem);
    if (!(renderUnit.getComponent() instanceof SpecGeneratedComponent)) {
      return INVALID_ID;
    }
    final SpecGeneratedComponent component = (SpecGeneratedComponent) renderUnit.getComponent();
    final ComponentContext scopedContext = getComponentContext(mountItem);

    try {
      if (component.getExtraAccessibilityNodesCount(scopedContext, getInterStageProps(mountItem))
          == 0) {
        return INVALID_ID;
      }

      final Drawable drawable = (Drawable) mountItem.getContent();
      final Rect bounds = drawable.getBounds();

      // Try to find an extra accessibility node that intersects with
      // the given coordinates.
      final int virtualViewId =
          component.getExtraAccessibilityNodeAt(
              scopedContext,
              (int) x - bounds.left,
              (int) y - bounds.top,
              getInterStageProps(mountItem));

      return (virtualViewId >= 0 ? virtualViewId : INVALID_ID);
    } catch (Exception e) {
      if (scopedContext != null) {
        ComponentUtils.handle(scopedContext, e);
      }
      return INVALID_ID;
    }
  }

  @Override
  protected void onVirtualViewKeyboardFocusChanged(int virtualViewId, boolean hasFocus) {
    AccessibilityNodeProviderCompat nodeProvider = this.getAccessibilityNodeProvider(mView);
    if (nodeProvider == null) {
      return;
    }

    AccessibilityNodeInfoCompat node =
        nodeProvider.findFocus(AccessibilityNodeInfoCompat.FOCUS_INPUT);

    final MountItem mountItem = getAccessibleMountItem(mView);
    if (mountItem == null) {
      return;
    }

    final LithoRenderUnit renderUnit = getRenderUnit(mountItem);
    if (!(renderUnit.getComponent() instanceof SpecGeneratedComponent)) {
      dispatchOnVirtualViewKeyboardFocusChangedEvent(mView, node, virtualViewId, hasFocus);
      return;
    }
    final SpecGeneratedComponent component = (SpecGeneratedComponent) renderUnit.getComponent();
    final ComponentContext scopedContext = getComponentContext(mountItem);

    if (scopedContext == null) {
      return;
    }

    try {
      if (virtualViewId
          >= component.getExtraAccessibilityNodesCount(
              scopedContext, getInterStageProps(mountItem))) {
        return;
      }

      if (component.implementsKeyboardFocusChangeForVirtualViews()) {
        component.onVirtualViewKeyboardFocusChanged(
            scopedContext, mView, node, virtualViewId, hasFocus, getInterStageProps(mountItem));
      }
    } catch (Exception e) {
      ComponentUtils.handle(scopedContext, e);
    }
  }

  @Override
  protected void onPopulateEventForVirtualView(int virtualViewId, AccessibilityEvent event) {
    // TODO (T10543861): ExploreByTouchHelper enforces subclasses to set a content description
    // or text on new events but components don't provide APIs to do so yet.
    event.setContentDescription("");
  }

  @Override
  protected boolean onPerformActionForVirtualView(
      int virtualViewId, int action, @Nullable Bundle arguments) {
    AccessibilityNodeProviderCompat nodeProvider = this.getAccessibilityNodeProvider(mView);
    if (nodeProvider == null) {
      return false;
    }

    AccessibilityNodeInfoCompat node =
        nodeProvider.findFocus(AccessibilityNodeInfoCompat.FOCUS_INPUT);
    if (node == null) {
      return false;
    }

    final MountItem mountItem = getAccessibleMountItem(mView);
    if (mountItem == null) {
      return false;
    }

    final LithoRenderUnit renderUnit = getRenderUnit(mountItem);
    if (!(renderUnit.getComponent() instanceof SpecGeneratedComponent)) {
      return dispatchOnPerformActionForVirtualViewEvent(
          mView, node, virtualViewId, action, arguments);
    }
    final SpecGeneratedComponent component = (SpecGeneratedComponent) renderUnit.getComponent();
    final ComponentContext scopedContext = getComponentContext(mountItem);
    if (scopedContext == null) {
      return false;
    }

    try {
      if (virtualViewId
          >= component.getExtraAccessibilityNodesCount(
              scopedContext, getInterStageProps(mountItem))) {
        return false;
      }

      if (component.implementsOnPerformActionForVirtualView()) {
        return component.onPerformActionForVirtualView(
            scopedContext,
            mView,
            node,
            virtualViewId,
            action,
            arguments,
            getInterStageProps(mountItem));
      }
    } catch (Exception e) {
      ComponentUtils.handle(scopedContext, e);
    }

    return false;
  }

  /**
   * Returns a {AccessibilityNodeProviderCompat} if the host contains a component that implements
   * custom accessibility logic. Returns {@code NULL} otherwise. Components with accessibility
   * content are automatically wrapped in hosts by {@link LayoutState}.
   */
  @Override
  public @Nullable AccessibilityNodeProviderCompat getAccessibilityNodeProvider(View host) {
    final MountItem mountItem = getAccessibleMountItem(mView);
    if (mountItem != null && getRenderUnit(mountItem) != null) {
      final Component component = getRenderUnit(mountItem).getComponent();
      if ((component instanceof SpecGeneratedComponent
          && ((SpecGeneratedComponent) component).implementsExtraAccessibilityNodes())) {
        return super.getAccessibilityNodeProvider(host);
      }
    }

    return null;
  }

  private static @Nullable MountItem getAccessibleMountItem(View view) {
    if (view instanceof ComponentHost) {
      return ((ComponentHost) view).getAccessibleMountItem();
    } else {
      final @Nullable ViewParent parentView = view.getParent();
      if (parentView == null) {
        return null;
      }
      int index = ((ViewGroup) parentView).indexOfChild(view);
      return ((ComponentHost) parentView).getMountItemAt(index);
    }
  }

  @Override
  public void onInitializeAccessibilityEvent(View host, AccessibilityEvent event) {
    if (mNodeInfo != null && mNodeInfo.getOnInitializeAccessibilityEventHandler() != null) {
      EventDispatcherUtils.dispatchOnInitializeAccessibilityEvent(
          mNodeInfo.getOnInitializeAccessibilityEventHandler(), host, event, mSuperDelegate);
    } else {
      super.onInitializeAccessibilityEvent(host, event);
    }
  }

  @Override
  public void sendAccessibilityEvent(View host, int eventType) {
    if (mNodeInfo != null && mNodeInfo.getSendAccessibilityEventHandler() != null) {
      EventDispatcherUtils.dispatchSendAccessibilityEvent(
          mNodeInfo.getSendAccessibilityEventHandler(), host, eventType, mSuperDelegate);
    } else {
      super.sendAccessibilityEvent(host, eventType);
    }
  }

  @Override
  public void sendAccessibilityEventUnchecked(View host, AccessibilityEvent event) {
    if (mNodeInfo != null && mNodeInfo.getSendAccessibilityEventUncheckedHandler() != null) {
      EventDispatcherUtils.dispatchSendAccessibilityEventUnchecked(
          mNodeInfo.getSendAccessibilityEventUncheckedHandler(), host, event, mSuperDelegate);
    } else {
      super.sendAccessibilityEventUnchecked(host, event);
    }
  }

  @Override
  public boolean dispatchPopulateAccessibilityEvent(View host, AccessibilityEvent event) {
    if (mNodeInfo != null && mNodeInfo.getDispatchPopulateAccessibilityEventHandler() != null) {
      return EventDispatcherUtils.dispatchDispatchPopulateAccessibilityEvent(
          mNodeInfo.getDispatchPopulateAccessibilityEventHandler(), host, event, mSuperDelegate);
    }

    return super.dispatchPopulateAccessibilityEvent(host, event);
  }

  @Override
  public void onPopulateAccessibilityEvent(View host, AccessibilityEvent event) {
    if (mNodeInfo != null && mNodeInfo.getOnPopulateAccessibilityEventHandler() != null) {
      EventDispatcherUtils.dispatchOnPopulateAccessibilityEvent(
          mNodeInfo.getOnPopulateAccessibilityEventHandler(), host, event, mSuperDelegate);
    } else {
      super.onPopulateAccessibilityEvent(host, event);
    }
  }

  @Override
  public boolean onRequestSendAccessibilityEvent(
      ViewGroup host, View child, AccessibilityEvent event) {
    if (mNodeInfo != null && mNodeInfo.getOnRequestSendAccessibilityEventHandler() != null) {
      return EventDispatcherUtils.dispatchOnRequestSendAccessibilityEvent(
          mNodeInfo.getOnRequestSendAccessibilityEventHandler(),
          host,
          child,
          event,
          mSuperDelegate);
    }

    return super.onRequestSendAccessibilityEvent(host, child, event);
  }

  @Override
  public boolean performAccessibilityAction(View host, int action, Bundle args) {
    if (mNodeInfo != null && mNodeInfo.getPerformAccessibilityActionHandler() != null) {
      return EventDispatcherUtils.dispatchPerformAccessibilityActionEvent(
          mNodeInfo.getPerformAccessibilityActionHandler(), host, action, args, mSuperDelegate);
    }

    return super.performAccessibilityAction(host, action, args);
  }

  private static Rect getDefaultBounds() {
    return sDefaultBounds;
  }

  private class SuperDelegate extends AccessibilityDelegateCompat {

    @Override
    public boolean dispatchPopulateAccessibilityEvent(View host, AccessibilityEvent event) {
      return ComponentAccessibilityDelegate.super.dispatchPopulateAccessibilityEvent(host, event);
    }

    @Override
    public void onInitializeAccessibilityEvent(View host, AccessibilityEvent event) {
      ComponentAccessibilityDelegate.super.onInitializeAccessibilityEvent(host, event);
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfoCompat node) {
      ComponentAccessibilityDelegate.super.onInitializeAccessibilityNodeInfo(host, node);
    }

    @Override
    public void onPopulateAccessibilityEvent(View host, AccessibilityEvent event) {
      ComponentAccessibilityDelegate.super.onPopulateAccessibilityEvent(host, event);
    }

    @Override
    public boolean onRequestSendAccessibilityEvent(
        ViewGroup host, View child, AccessibilityEvent event) {
      return ComponentAccessibilityDelegate.super.onRequestSendAccessibilityEvent(
          host, child, event);
    }

    @Override
    public boolean performAccessibilityAction(View host, int action, Bundle args) {
      return ComponentAccessibilityDelegate.super.performAccessibilityAction(host, action, args);
    }

    @Override
    public void sendAccessibilityEvent(View host, int eventType) {
      ComponentAccessibilityDelegate.super.sendAccessibilityEvent(host, eventType);
    }

    @Override
    public void sendAccessibilityEventUnchecked(View host, AccessibilityEvent event) {
      ComponentAccessibilityDelegate.super.sendAccessibilityEventUnchecked(host, event);
    }
  }

  public static @Nullable InterStagePropsContainer getInterStageProps(MountItem item) {
    return LithoLayoutData.getInterStageProps(item.getRenderTreeNode().getLayoutData());
  }
}
