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

package com.facebook.litho.specmodels.model;

import com.squareup.javapoet.ClassName;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Constants used in {@link SpecModel}s. */
public interface ClassNames {
  ClassName CLASS = ClassName.bestGuess("java.lang.Class");
  ClassName OBJECT = ClassName.bestGuess("java.lang.Object");
  ClassName STRING = ClassName.bestGuess("java.lang.String");
  ClassName EXCEPTION = ClassName.bestGuess("java.lang.Exception");
  ClassName COMMON_UTILS = ClassName.bestGuess("com.facebook.litho.CommonUtils");
  ClassName EQUIVALENCE_UTILS =
      ClassName.bestGuess("com.facebook.rendercore.utils.EquivalenceUtils");
  ClassName NULLABLE = ClassName.bestGuess("androidx.annotation.Nullable");

  String VIEW_NAME = "android.view.View";
  ClassName VIEW = ClassName.bestGuess(VIEW_NAME);
  String DRAWABLE_NAME = "android.graphics.drawable.Drawable";
  ClassName DRAWABLE = ClassName.bestGuess(DRAWABLE_NAME);
  ClassName ANDROID_CONTEXT = ClassName.bestGuess("android.content.Context");

  ClassName ACCESSIBILITY_NODE =
      ClassName.bestGuess("androidx.core.view.accessibility.AccessibilityNodeInfoCompat");

  ClassName STRING_RES = ClassName.bestGuess("androidx.annotation.StringRes");
  ClassName INT_RES = ClassName.bestGuess("androidx.annotation.IntegerRes");
  ClassName BOOL_RES = ClassName.bestGuess("androidx.annotation.BoolRes");
  ClassName COLOR_RES = ClassName.bestGuess("androidx.annotation.ColorRes");
  ClassName COLOR_INT = ClassName.bestGuess("androidx.annotation.ColorInt");
  ClassName DIMEN_RES = ClassName.bestGuess("androidx.annotation.DimenRes");
  ClassName ATTR_RES = ClassName.bestGuess("androidx.annotation.AttrRes");
  ClassName DRAWABLE_RES = ClassName.bestGuess("androidx.annotation.DrawableRes");
  ClassName ARRAY_RES = ClassName.bestGuess("androidx.annotation.ArrayRes");
  ClassName DIMENSION = ClassName.bestGuess("androidx.annotation.Dimension");
  ClassName PX = ClassName.bestGuess("androidx.annotation.Px");

  ClassName LIST = ClassName.get(List.class);
  ClassName ARRAY_LIST = ClassName.get(ArrayList.class);
  ClassName COLLECTIONS = ClassName.get(Collections.class);
  ClassName COLLECTION = ClassName.bestGuess("java.util.Collection");

  ClassName MOUNT_CONTENT_POOL =
      ClassName.bestGuess("com.facebook.rendercore.MountContentPools.ContentPool");

  ClassName LAYOUT_SPEC = ClassName.bestGuess("com.facebook.litho.annotations.LayoutSpec");
  ClassName MOUNT_SPEC = ClassName.bestGuess("com.facebook.litho.annotations.MountSpec");
  ClassName TEST_SPEC = ClassName.bestGuess("com.facebook.litho.annotations.TestSpec");

  ClassName OUTPUT = ClassName.bestGuess("com.facebook.litho.Output");
  ClassName DIFF = ClassName.bestGuess("com.facebook.litho.Diff");
  ClassName SIZE = ClassName.bestGuess("com.facebook.litho.Size");

  ClassName RESOURCE_RESOLVER = ClassName.bestGuess("com.facebook.rendercore.ResourceResolver");

  ClassName TRANSITION = ClassName.bestGuess("com.facebook.litho.Transition");

  ClassName COMPONENT_CONTEXT = ClassName.bestGuess("com.facebook.litho.ComponentContext");
  ClassName COMPONENT_LAYOUT = ClassName.bestGuess("com.facebook.litho.ComponentLayout");
  ClassName COMPONENT_TREE = ClassName.bestGuess("com.facebook.litho.ComponentTree");

  ClassName COMPONENT = ClassName.bestGuess("com.facebook.litho.Component");
  ClassName SPEC_GENERATED_COMPONENT =
      ClassName.bestGuess("com.facebook.litho.SpecGeneratedComponent");
  ClassName COMPONENT_BUILDER = ClassName.bestGuess("com.facebook.litho.Component.Builder");
  ClassName COMPONENT_MOUNT_TYPE = ClassName.bestGuess("com.facebook.litho.Component.MountType");
  ClassName COMPONENT_MOUNT_TYPE_DRAWABLE =
      ClassName.bestGuess("com.facebook.litho.Component.MountType.DRAWABLE");
  ClassName COMPONENT_MOUNT_TYPE_VIEW =
      ClassName.bestGuess("com.facebook.litho.Component.MountType.VIEW");
  ClassName COMPONENT_MOUNT_TYPE_NONE =
      ClassName.bestGuess("com.facebook.litho.Component.MountType.NONE");

  ClassName TRANSITON = ClassName.bestGuess("com.facebook.litho.Transition");
  ClassName TRANSITION_CONTAINER =
      ClassName.bestGuess("com.facebook.litho.SpecGeneratedComponent.TransitionContainer");

  ClassName COMPARABLE_DRAWABLE =
      ClassName.bestGuess("com.facebook.litho.drawable.ComparableDrawable");

  ClassName TREE_PROPS = ClassName.bestGuess("com.facebook.litho.TreePropContainer");

  ClassName STATE_VALUE = ClassName.bestGuess("com.facebook.litho.StateValue");
  ClassName COMPONENT_STATE_UPDATE =
      ClassName.bestGuess("com.facebook.litho.StateContainer.StateUpdate");
  ClassName STATE_CONTAINER = ClassName.bestGuess("com.facebook.litho.StateContainer");
  ClassName INTER_STAGE_PROPS_CONTAINER =
      ClassName.bestGuess("com.facebook.litho.InterStagePropsContainer");
  ClassName PREPARE_INTER_STAGE_PROPS_CONTAINER =
      ClassName.bestGuess("com.facebook.litho.PrepareInterStagePropsContainer");
  ClassName RENDER_DATA = ClassName.bestGuess("com.facebook.litho.Component.RenderData");

  ClassName EVENT_DISPATCHER = ClassName.bestGuess("com.facebook.litho.EventDispatcher");
  ClassName HAS_EVENT_DISPATCHER_CLASSNAME =
      ClassName.bestGuess("com.facebook.litho.HasEventDispatcher");
  ClassName EVENT_HANDLER = ClassName.bestGuess("com.facebook.litho.EventHandler");

  ClassName EVENT_TRIGGER_TARGET = ClassName.bestGuess("com.facebook.litho.EventTriggerTarget");
  ClassName EVENT_TRIGGER = ClassName.bestGuess("com.facebook.litho.EventTrigger");
  ClassName EVENT_TRIGGER_CONTAINER =
      ClassName.bestGuess("com.facebook.litho.EventTriggersContainer");
  ClassName ERROR_EVENT = ClassName.bestGuess("com.facebook.litho.ErrorEvent");
  ClassName FROM_EVENT = ClassName.bestGuess("com.facebook.litho.annotations.FromEvent");

  ClassName SECTION = ClassName.bestGuess("com.facebook.litho.sections.Section");
  ClassName SECTION_CONTEXT = ClassName.bestGuess("com.facebook.litho.sections.SectionContext");
  ClassName SECTION_BUILDER = ClassName.bestGuess("com.facebook.litho.sections.Section.Builder");

  ClassName SURFACE = ClassName.bestGuess("com.facebook.surfaces.Surface");
  ClassName SURFACE_CONTEXT = ClassName.bestGuess("com.facebook.surfaces.SurfaceContext");

  ClassName BASE_MATCHER = ClassName.bestGuess("com.facebook.litho.BaseMatcher");
  ClassName BASE_MATCHER_BUILDER = ClassName.bestGuess("com.facebook.litho.BaseMatcherBuilder");
  ClassName INSPECTABLE_COMPONENT =
      ClassName.bestGuess("com.facebook.litho.testing.subcomponents.InspectableComponent");
  ClassName HAMCREST_MATCHER = ClassName.bestGuess("org.hamcrest.Matcher");
  ClassName HAMCREST_CORE_IS = ClassName.bestGuess("org.hamcrest.core.Is");
  ClassName ASSERTJ_CONDITION = ClassName.bestGuess("org.assertj.core.api.Condition");
  ClassName ASSERTJ_TEXT_DESCRIPTION =
      ClassName.bestGuess("org.assertj.core.description.TextDescription");
  ClassName ASSERTJ_ASSERTIONS = ClassName.bestGuess("org.assertj.core.api.Assertions");

  ClassName NON_EXISTENT_CLASS = ClassName.bestGuess("error.NonExistentClass");

  ClassName WORKING_RANGE = ClassName.bestGuess("com.facebook.litho.WorkingRange");

  ClassName DYNAMIC_VALUE = ClassName.bestGuess("com.facebook.litho.DynamicValue");

  ClassName HANDLE = ClassName.bestGuess("com.facebook.litho.Handle");

  ClassName BUNDLE = ClassName.bestGuess("android.os.Bundle");

  ClassName Unit = ClassName.bestGuess("kotlin.Unit");
  ClassName Function1 = ClassName.bestGuess("kotlin.jvm.functions.Function1");
}
