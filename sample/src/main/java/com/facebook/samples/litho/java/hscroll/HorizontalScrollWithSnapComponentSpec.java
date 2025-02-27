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

package com.facebook.samples.litho.java.hscroll;

import static com.facebook.litho.widget.SnapUtil.SNAP_NONE;
import static com.facebook.litho.widget.SnapUtil.SNAP_TO_CENTER;
import static com.facebook.litho.widget.SnapUtil.SNAP_TO_CENTER_CHILD;
import static com.facebook.litho.widget.SnapUtil.SNAP_TO_START;

import android.graphics.Color;
import android.graphics.Rect;
import android.view.View;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.facebook.litho.Column;
import com.facebook.litho.Component;
import com.facebook.litho.ComponentContext;
import com.facebook.litho.Row;
import com.facebook.litho.StateValue;
import com.facebook.litho.StyleCompat;
import com.facebook.litho.annotations.FromEvent;
import com.facebook.litho.annotations.LayoutSpec;
import com.facebook.litho.annotations.OnCreateInitialState;
import com.facebook.litho.annotations.OnCreateLayout;
import com.facebook.litho.annotations.OnEvent;
import com.facebook.litho.annotations.OnUpdateState;
import com.facebook.litho.annotations.Param;
import com.facebook.litho.annotations.Prop;
import com.facebook.litho.annotations.State;
import com.facebook.litho.sections.SectionContext;
import com.facebook.litho.sections.common.DataDiffSection;
import com.facebook.litho.sections.common.RenderEvent;
import com.facebook.litho.sections.widget.ListRecyclerConfiguration;
import com.facebook.litho.sections.widget.RecyclerBinderConfiguration;
import com.facebook.litho.sections.widget.RecyclerCollectionComponent;
import com.facebook.litho.sections.widget.RecyclerCollectionEventsController;
import com.facebook.litho.sections.widget.RecyclerConfiguration;
import com.facebook.litho.widget.ComponentRenderInfo;
import com.facebook.litho.widget.RecyclerBinderConfig;
import com.facebook.litho.widget.RenderInfo;
import com.facebook.litho.widget.Spinner;
import com.facebook.litho.widget.Text;
import com.facebook.litho.widget.VerticalGravity;
import com.facebook.litho.widget.collection.CrossAxisWrapMode;
import com.facebook.yoga.YogaAlign;
import com.facebook.yoga.YogaEdge;
import com.facebook.yoga.YogaJustify;
import java.util.Arrays;

@LayoutSpec
public class HorizontalScrollWithSnapComponentSpec {

  static final String[] SNAP_MODE_STRING =
      new String[] {"SNAP_NONE", "SNAP_TO_START", "SNAP_TO_CENTER", "SNAP_TO_CENTER_CHILD"};

  static final int[] SNAP_MODE_INT =
      new int[] {SNAP_NONE, SNAP_TO_START, SNAP_TO_CENTER, SNAP_TO_CENTER_CHILD};

  @OnCreateInitialState
  static void onCreateInitialState(ComponentContext c, StateValue<Integer> snapMode) {
    snapMode.set(SNAP_NONE);
  }

  @OnCreateLayout
  static Component onCreateLayout(
      ComponentContext c,
      @Prop Integer[] colors,
      @Prop RecyclerCollectionEventsController eventsController,
      @State int snapMode) {

    final RecyclerConfiguration recyclerConfiguration =
        ListRecyclerConfiguration.create()
            .orientation(LinearLayoutManager.HORIZONTAL)
            .recyclerBinderConfiguration(
                RecyclerBinderConfiguration.create()
                    .recyclerBinderConfig(
                        RecyclerBinderConfig.create()
                            .crossAxisWrapMode(CrossAxisWrapMode.MatchFirstChild)
                            .build())
                    .build())
            .reverseLayout(false)
            .snapMode(snapMode)
            .build();

    return Column.create(c)
        .paddingDip(YogaEdge.ALL, 5)
        .child(
            Row.create(c)
                .alignContent(YogaAlign.STRETCH)
                .marginDip(YogaEdge.TOP, 10)
                .child(
                    Text.create(c)
                        .alignSelf(YogaAlign.CENTER)
                        .flexGrow(2f)
                        .text("Snap type: ")
                        .textSizeSp(20))
                .child(
                    new Spinner(
                        Arrays.asList(SNAP_MODE_STRING),
                        getSnapModeString(snapMode),
                        newSelection -> {
                          HorizontalScrollWithSnapComponent.updateSnapModeSync(
                              c, getSnapModeInt(newSelection));
                          return null;
                        },
                        StyleCompat.flexGrow(1f).build())))
        .child(
            RecyclerCollectionComponent.create(c)
                .key("snapMode" + snapMode)
                .disablePTR(true)
                .recyclerConfiguration(recyclerConfiguration)
                .section(
                    DataDiffSection.<Integer>create(new SectionContext(c))
                        .data(Arrays.asList(colors))
                        .renderEventHandler(HorizontalScrollWithSnapComponent.<Integer>onRender(c))
                        .build())
                .itemDecoration(
                    new RecyclerView.ItemDecoration() {
                      @Override
                      public void getItemOffsets(
                          Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
                        super.getItemOffsets(outRect, view, parent, state);
                        int spacingPx = 40;
                        int exteriorSpacingPx = 0;

                        int startPx = spacingPx;
                        int endPx = 0;
                        int position = parent.getChildLayoutPosition(view);
                        if (position == 0) {
                          startPx = exteriorSpacingPx;
                        }
                        if (position == state.getItemCount() - 1) {
                          endPx = exteriorSpacingPx;
                        }

                        outRect.left = startPx;
                        outRect.right = endPx;
                      }
                    })
                .eventsController(eventsController))
        .build();
  }

  @OnEvent(RenderEvent.class)
  static RenderInfo onRender(ComponentContext c, @FromEvent Integer model, @FromEvent int index) {
    return ComponentRenderInfo.create()
        .component(
            Row.create(c)
                .justifyContent(YogaJustify.CENTER)
                .widthDip(120)
                .heightDip(120)
                .backgroundColor((int) model)
                .child(
                    Text.create(c)
                        .textSizeSp(20)
                        .textColor(Color.LTGRAY)
                        .text(Integer.toString(index))
                        .verticalGravity(VerticalGravity.CENTER)))
        .build();
  }

  @OnUpdateState
  static void updateSnapMode(StateValue<Integer> snapMode, @Param int newSnapMode) {
    snapMode.set(newSnapMode);
  }

  private static String getSnapModeString(int snapMode) {
    for (int i = 0; i < SNAP_MODE_INT.length; i++) {
      if (snapMode == SNAP_MODE_INT[i]) {
        return SNAP_MODE_STRING[i];
      }
    }
    return SNAP_MODE_STRING[0];
  }

  private static int getSnapModeInt(String snapMode) {
    for (int i = 0; i < SNAP_MODE_STRING.length; i++) {
      if (snapMode.equals(SNAP_MODE_STRING[i])) {
        return SNAP_MODE_INT[i];
      }
    }
    return SNAP_NONE;
  }
}
