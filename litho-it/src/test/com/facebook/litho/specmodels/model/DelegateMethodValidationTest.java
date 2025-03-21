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

import static com.facebook.litho.specmodels.model.DelegateMethodDescription.OptionalParameterType.STATE_VALUE;
import static com.facebook.litho.specmodels.model.DelegateMethodDescriptions.LAYOUT_SPEC_DELEGATE_METHODS_MAP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.facebook.litho.StateValue;
import com.facebook.litho.annotations.FromPrepare;
import com.facebook.litho.annotations.OnBind;
import com.facebook.litho.annotations.OnCreateInitialState;
import com.facebook.litho.annotations.OnCreateLayout;
import com.facebook.litho.annotations.OnCreateLayoutWithSizeSpec;
import com.facebook.litho.annotations.OnCreateMountContent;
import com.facebook.litho.annotations.OnEvent;
import com.facebook.litho.annotations.OnMount;
import com.facebook.litho.annotations.OnPrepare;
import com.facebook.litho.specmodels.internal.ImmutableList;
import com.facebook.litho.testing.specmodels.MockMethodParamModel;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.WildcardTypeName;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.Modifier;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests {@link DelegateMethodValidation} */
@RunWith(JUnit4.class)
public class DelegateMethodValidationTest {

  private final Object mModelRepresentedObject = new Object();
  private final Object mMountSpecObject = new Object();
  private final Object mDelegateMethodObject1 = new Object();
  private final Object mDelegateMethodObject2 = new Object();
  private final Object mMethodParamObject1 = new Object();
  private final Object mMethodParamObject2 = new Object();
  private final Object mMethodParamObject3 = new Object();
  private final Object mOnCreateMountContentObject = new Object();
  private final SimpleMethodParamModel mAndroidContextParamModel =
      MethodParamModelFactory.createSimpleMethodParamModel(
          new TypeSpec(ClassNames.ANDROID_CONTEXT), "context", new Object());
  private final SimpleMethodParamModel mComponentContextParamModel =
      MethodParamModelFactory.createSimpleMethodParamModel(
          new TypeSpec(ClassNames.COMPONENT_CONTEXT), "c", new Object());

  private LayoutSpecModel mLayoutSpecModel;
  private MountSpecModel mMountSpecModel;
  private SpecMethodModel<DelegateMethod, Void> mOnCreateMountContent;

  @Before
  public void setup() {
    mLayoutSpecModel = mock(LayoutSpecModel.class);
    mMountSpecModel = mock(MountSpecModel.class);
    when(mLayoutSpecModel.getRepresentedObject()).thenReturn(mModelRepresentedObject);
    when(mMountSpecModel.getRepresentedObject()).thenReturn(mMountSpecObject);
    when(mMountSpecModel.getContextClass()).thenReturn(ClassNames.COMPONENT_CONTEXT);

    mOnCreateMountContent =
        SpecMethodModel.<DelegateMethod, Void>builder()
            .annotations(ImmutableList.of((Annotation) () -> OnCreateMountContent.class))
            .modifiers(ImmutableList.of(Modifier.STATIC))
            .name("onCreateMountContent")
            .returnTypeSpec(new TypeSpec(ClassName.bestGuess("java.lang.MadeUpClass")))
            .typeVariables(ImmutableList.of())
            .methodParams(ImmutableList.of(mAndroidContextParamModel))
            .representedObject(mOnCreateMountContentObject)
            .typeModel(null)
            .build();

    // Initialize potential fields read from spec with empty
    when(mLayoutSpecModel.getStateValues()).thenReturn(ImmutableList.of());
    when(mLayoutSpecModel.getProps()).thenReturn(ImmutableList.<PropModel>of());
    when(mLayoutSpecModel.getTreeProps()).thenReturn(ImmutableList.<TreePropModel>of());
    when(mLayoutSpecModel.getInterStageInputs()).thenReturn(ImmutableList.of());
    when(mLayoutSpecModel.getPrepareInterStageInputs()).thenReturn(ImmutableList.of());

    when(mMountSpecModel.getStateValues()).thenReturn(ImmutableList.of());
    when(mMountSpecModel.getProps()).thenReturn(ImmutableList.<PropModel>of());
    when(mMountSpecModel.getTreeProps()).thenReturn(ImmutableList.<TreePropModel>of());
    when(mMountSpecModel.getInterStageInputs()).thenReturn(ImmutableList.of());
    when(mMountSpecModel.getPrepareInterStageInputs()).thenReturn(ImmutableList.of());
  }

  @Test
  public void testNoDelegateMethods() {
    when(mLayoutSpecModel.getDelegateMethods()).thenReturn(ImmutableList.of());

    final List<SpecModelValidationError> validationErrors =
        DelegateMethodValidation.validateLayoutSpecModel(mLayoutSpecModel);
    assertThat(validationErrors).hasSize(1);
    assertThat(validationErrors.get(0).element).isEqualTo(mModelRepresentedObject);
    assertThat(validationErrors.get(0).message)
        .isEqualTo(
            "You need to have a method annotated with either @OnCreateLayout "
                + "or @OnCreateLayoutWithSizeSpec in your spec. In most cases, @OnCreateLayout "
                + "is what you want.");
  }

  @Test
  public void testOnCreateLayoutAndOnCreateLayoutWithSizeSpec() {
    when(mLayoutSpecModel.getDelegateMethods())
        .thenReturn(
            ImmutableList.of(
                SpecMethodModel.<DelegateMethod, Void>builder()
                    .annotations(ImmutableList.of((Annotation) () -> OnCreateLayout.class))
                    .modifiers(ImmutableList.of(Modifier.STATIC))
                    .name("name")
                    .returnTypeSpec(new TypeSpec(ClassNames.COMPONENT))
                    .typeVariables(ImmutableList.of())
                    .methodParams(ImmutableList.of(mComponentContextParamModel))
                    .representedObject(new Object())
                    .typeModel(null)
                    .build(),
                SpecMethodModel.<DelegateMethod, Void>builder()
                    .annotations(
                        ImmutableList.of((Annotation) () -> OnCreateLayoutWithSizeSpec.class))
                    .modifiers(ImmutableList.of(Modifier.STATIC))
                    .name("name")
                    .returnTypeSpec(new TypeSpec(ClassNames.COMPONENT))
                    .typeVariables(ImmutableList.of())
                    .methodParams(
                        ImmutableList.of(
                            mComponentContextParamModel,
                            MethodParamModelFactory.createSimpleMethodParamModel(
                                new TypeSpec(TypeName.INT), "widthSpec", new Object()),
                            MethodParamModelFactory.createSimpleMethodParamModel(
                                new TypeSpec(TypeName.INT), "heightSpec", new Object())))
                    .representedObject(new Object())
                    .typeModel(null)
                    .build()));

    final List<SpecModelValidationError> validationErrors =
        DelegateMethodValidation.validateLayoutSpecModel(mLayoutSpecModel);
    assertThat(validationErrors).hasSize(1);
    assertThat(validationErrors.get(0).element).isEqualTo(mModelRepresentedObject);
    assertThat(validationErrors.get(0).message)
        .isEqualTo(
            "Your LayoutSpec should have a method annotated with either @OnCreateLayout "
                + "or @OnCreateLayoutWithSizeSpec, but not both. In most cases, @OnCreateLayout "
                + "is what you want.");
  }

  @Test
  public void testDelegateMethodStartsWithDunder() {
    when(mLayoutSpecModel.getDelegateMethods())
        .thenReturn(
            ImmutableList.of(
                SpecMethodModel.<DelegateMethod, Void>builder()
                    .annotations(ImmutableList.of((Annotation) () -> OnEvent.class))
                    .modifiers(ImmutableList.of(Modifier.STATIC))
                    .name("__someEventHandler")
                    .returnTypeSpec(new TypeSpec(TypeName.VOID))
                    .typeVariables(ImmutableList.of())
                    .methodParams(ImmutableList.of())
                    .representedObject(null)
                    .typeModel(null)
                    .build()));

    final List<SpecModelValidationError> validationErrors =
        DelegateMethodValidation.validateLayoutSpecModel(mLayoutSpecModel);
    assertThat(validationErrors).hasSize(2);
    assertThat(validationErrors.get(0).message)
        .isEqualTo(
            "Methods in a component must not start with '__' as they are reserved"
                + " for internal use. Method '__someEventHandler' violates this contract.");
  }

  @Test
  public void testDelegateMethodDoesNotDefinedParamsOfCorrectType() {
    when(mLayoutSpecModel.getDelegateMethods())
        .thenReturn(
            ImmutableList.of(
                SpecMethodModel.<DelegateMethod, Void>builder()
                    .annotations(ImmutableList.of((Annotation) () -> OnCreateLayout.class))
                    .modifiers(ImmutableList.of(Modifier.STATIC))
                    .name("name")
                    .returnTypeSpec(new TypeSpec(ClassNames.COMPONENT))
                    .typeVariables(ImmutableList.of())
                    .methodParams(
                        ImmutableList.of(
                            MethodParamModelFactory.createSimpleMethodParamModel(
                                new TypeSpec(TypeName.BOOLEAN), "someBool", mMethodParamObject1)))
                    .representedObject(mDelegateMethodObject1)
                    .typeModel(null)
                    .build()));

    final List<SpecModelValidationError> validationErrors =
        DelegateMethodValidation.validateLayoutSpecModel(mLayoutSpecModel);
    assertThat(validationErrors).hasSize(1);
    assertThat(validationErrors.get(0).element).isEqualTo(mMethodParamObject1);
    assertThat(validationErrors.get(0).message)
        .isEqualTo(
            "Argument at index 0 (someBool) is not a valid parameter, should be one of the"
                + " following: @Prop T somePropName. @TreeProp T someTreePropName. @State T"
                + " someStateName. @CachedValue T value, where"
                + " the cached value has a corresponding @OnCalculateCachedValue method. ");
  }

  @Test
  public void testDelegateMethodDoesNotHaveAnyOptionalParametersAllowed() {
    when(mMountSpecModel.getDelegateMethods())
        .thenReturn(
            ImmutableList.of(
                SpecMethodModel.<DelegateMethod, Void>builder()
                    .annotations(ImmutableList.of((Annotation) () -> OnCreateMountContent.class))
                    .modifiers(ImmutableList.of(Modifier.STATIC))
                    .name("name")
                    .returnTypeSpec(new TypeSpec(ClassNames.OBJECT))
                    .typeVariables(ImmutableList.of())
                    .methodParams(
                        ImmutableList.of(
                            MethodParamModelFactory.createSimpleMethodParamModel(
                                new TypeSpec(TypeName.BOOLEAN), "someBool", mMethodParamObject1)))
                    .representedObject(mDelegateMethodObject1)
                    .typeModel(null)
                    .build()));

    final List<SpecModelValidationError> validationErrors =
        DelegateMethodValidation.validateMountSpecModel(mMountSpecModel);
    assertThat(validationErrors).hasSize(1);
    assertThat(validationErrors.get(0).element).isEqualTo(mMethodParamObject1);
    assertThat(validationErrors.get(0).message)
        .isEqualTo(
            "Argument at index 0 (someBool) is not a valid parameter.  No additional "
                + "parameters are allowed.");
  }

  @Test
  public void testDelegateMethodHasIncorrectOptionalParams() {
    when(mLayoutSpecModel.getDelegateMethods())
        .thenReturn(
            ImmutableList.of(
                SpecMethodModel.<DelegateMethod, Void>builder()
                    .annotations(ImmutableList.of((Annotation) () -> OnCreateLayout.class))
                    .modifiers(ImmutableList.of(Modifier.STATIC))
                    .name("name")
                    .returnTypeSpec(new TypeSpec(ClassNames.COMPONENT))
                    .typeVariables(ImmutableList.of())
                    .methodParams(
                        ImmutableList.of(
                            mComponentContextParamModel,
                            MockMethodParamModel.newBuilder()
                                .name("arg")
                                .type(TypeName.INT)
                                .representedObject(mMethodParamObject2)
                                .build()))
                    .representedObject(mDelegateMethodObject1)
                    .typeModel(null)
                    .build()));

    final List<SpecModelValidationError> validationErrors =
        DelegateMethodValidation.validateLayoutSpecModel(mLayoutSpecModel);
    assertThat(validationErrors).hasSize(1);
    assertThat(validationErrors.get(0).element).isEqualTo(mMethodParamObject2);
    assertThat(validationErrors.get(0).message)
        .isEqualTo(
            "Argument at index 1 (arg) is not a valid parameter, should be one of the following: "
                + "@Prop T somePropName. @TreeProp T someTreePropName. @State T someStateName. "
                + "@CachedValue T value, where the cached value "
                + "has a corresponding @OnCalculateCachedValue method. ");
  }

  @Test
  public void testDelegateMethodIsNotStatic() {
    when(mLayoutSpecModel.getSpecElementType()).thenReturn(SpecElementType.JAVA_CLASS);
    when(mLayoutSpecModel.getDelegateMethods())
        .thenReturn(
            ImmutableList.of(
                SpecMethodModel.<DelegateMethod, Void>builder()
                    .annotations(ImmutableList.of((Annotation) () -> OnCreateLayout.class))
                    .modifiers(ImmutableList.of())
                    .name("name")
                    .returnTypeSpec(new TypeSpec(ClassNames.COMPONENT))
                    .typeVariables(ImmutableList.of())
                    .methodParams(ImmutableList.of(mComponentContextParamModel))
                    .representedObject(mDelegateMethodObject1)
                    .typeModel(null)
                    .build()));

    final List<SpecModelValidationError> validationErrors =
        DelegateMethodValidation.validateLayoutSpecModel(mLayoutSpecModel);
    assertThat(validationErrors).hasSize(1);
    assertThat(validationErrors.get(0).element).isEqualTo(mDelegateMethodObject1);
    assertThat(validationErrors.get(0).message).isEqualTo("Methods in a spec must be static.");
  }

  @Test
  public void testDelegateMethodIsNotStaticWithKotlinSingleton() {
    when(mLayoutSpecModel.getSpecElementType()).thenReturn(SpecElementType.KOTLIN_SINGLETON);
    when(mLayoutSpecModel.getDelegateMethods())
        .thenReturn(
            ImmutableList.of(
                SpecMethodModel.<DelegateMethod, Void>builder()
                    .annotations(ImmutableList.of((Annotation) () -> OnCreateLayout.class))
                    .modifiers(ImmutableList.of())
                    .name("name")
                    .returnTypeSpec(new TypeSpec(ClassNames.COMPONENT))
                    .typeVariables(ImmutableList.of())
                    .methodParams(ImmutableList.of(mComponentContextParamModel))
                    .representedObject(mDelegateMethodObject1)
                    .typeModel(null)
                    .build()));

    final List<SpecModelValidationError> validationErrors =
        DelegateMethodValidation.validateLayoutSpecModel(mLayoutSpecModel);
    assertThat(validationErrors).isEmpty();
  }

  @Test
  public void testDelegateMethodHasIncorrectReturnType() {
    when(mLayoutSpecModel.getDelegateMethods())
        .thenReturn(
            ImmutableList.of(
                SpecMethodModel.<DelegateMethod, Void>builder()
                    .annotations(ImmutableList.of((Annotation) () -> OnCreateLayout.class))
                    .modifiers(ImmutableList.of(Modifier.STATIC))
                    .name("name")
                    .returnTypeSpec(new TypeSpec(ClassNames.COMPONENT_LAYOUT))
                    .typeVariables(ImmutableList.of())
                    .methodParams(ImmutableList.of(mComponentContextParamModel))
                    .representedObject(mDelegateMethodObject1)
                    .typeModel(null)
                    .build()));

    final List<SpecModelValidationError> validationErrors =
        DelegateMethodValidation.validateLayoutSpecModel(mLayoutSpecModel);
    assertThat(validationErrors).hasSize(1);
    assertThat(validationErrors.get(0).element).isEqualTo(mDelegateMethodObject1);
    assertThat(validationErrors.get(0).message)
        .isEqualTo(
            "A method annotated with @OnCreateLayout needs to return "
                + "com.facebook.litho.Component. Note that even if your return value is a "
                + "subclass of com.facebook.litho.Component, you should still use "
                + "com.facebook.litho.Component as the return type.");
  }

  @Test
  public void testMountSpecHasOnCreateMountContent() {
    when(mMountSpecModel.getDelegateMethods()).thenReturn(ImmutableList.of());

    final List<SpecModelValidationError> validationErrors =
        DelegateMethodValidation.validateMountSpecModel(mMountSpecModel);
    assertThat(validationErrors).hasSize(1);
    assertThat(validationErrors.get(0).element).isEqualTo(mMountSpecObject);
    assertThat(validationErrors.get(0).message)
        .isEqualTo("All MountSpecs need to have a method annotated with @OnCreateMountContent.");
  }

  @Test
  public void testSecondParameter() {
    when(mMountSpecModel.getDelegateMethods())
        .thenReturn(
            ImmutableList.of(
                mOnCreateMountContent,
                SpecMethodModel.<DelegateMethod, Void>builder()
                    .annotations(ImmutableList.of((Annotation) () -> OnMount.class))
                    .modifiers(ImmutableList.of(Modifier.STATIC))
                    .name("onMount")
                    .returnTypeSpec(new TypeSpec(TypeName.VOID))
                    .typeVariables(ImmutableList.of())
                    .methodParams(
                        ImmutableList.of(
                            mComponentContextParamModel,
                            MethodParamModelFactory.createSimpleMethodParamModel(
                                new TypeSpec(ClassNames.OBJECT), "content", mMethodParamObject2)))
                    .representedObject(mDelegateMethodObject1)
                    .typeModel(null)
                    .build(),
                SpecMethodModel.<DelegateMethod, Void>builder()
                    .annotations(ImmutableList.of((Annotation) () -> OnBind.class))
                    .modifiers(ImmutableList.of(Modifier.STATIC))
                    .name("onBind")
                    .returnTypeSpec(new TypeSpec(TypeName.VOID))
                    .typeVariables(ImmutableList.of())
                    .methodParams(
                        ImmutableList.of(
                            mComponentContextParamModel,
                            MethodParamModelFactory.createSimpleMethodParamModel(
                                new TypeSpec(ClassNames.OBJECT), "content", mMethodParamObject2)))
                    .representedObject(mDelegateMethodObject2)
                    .typeModel(null)
                    .build()));

    final List<SpecModelValidationError> validationErrors =
        DelegateMethodValidation.validateMountSpecModel(mMountSpecModel);
    assertThat(validationErrors).hasSize(2);
    assertThat(validationErrors.get(0).element).isEqualTo(mDelegateMethodObject1);
    assertThat(validationErrors.get(0).message)
        .isEqualTo(
            "The second parameter of a method annotated with interface "
                + "com.facebook.litho.annotations.OnMount must have the same type as the "
                + "return type of the method annotated with @OnCreateMountContent (i.e. "
                + "java.lang.MadeUpClass).");
    assertThat(validationErrors.get(1).element).isEqualTo(mDelegateMethodObject2);
    assertThat(validationErrors.get(1).message)
        .isEqualTo(
            "The second parameter of a method annotated with interface "
                + "com.facebook.litho.annotations.OnBind must have the same type as the "
                + "return type of the method annotated with @OnCreateMountContent (i.e. "
                + "java.lang.MadeUpClass).");
  }

  @Test
  public void testInterStageInputMethodDoesNotExist() {
    final InterStageInputParamModel interStageInputParamModel =
        mock(InterStageInputParamModel.class);
    when(interStageInputParamModel.getAnnotations())
        .thenReturn(ImmutableList.of((Annotation) () -> FromPrepare.class));
    when(interStageInputParamModel.getName()).thenReturn("interStageInput");
    when(interStageInputParamModel.getTypeName()).thenReturn(TypeName.INT);
    when(interStageInputParamModel.getRepresentedObject()).thenReturn(mMethodParamObject3);
    when(mMountSpecModel.getDelegateMethods())
        .thenReturn(
            ImmutableList.of(
                mOnCreateMountContent,
                SpecMethodModel.<DelegateMethod, Void>builder()
                    .annotations(ImmutableList.of((Annotation) () -> OnMount.class))
                    .modifiers(ImmutableList.of(Modifier.STATIC))
                    .name("onMount")
                    .returnTypeSpec(new TypeSpec(TypeName.VOID))
                    .typeVariables(ImmutableList.of())
                    .methodParams(
                        ImmutableList.of(
                            mComponentContextParamModel,
                            MethodParamModelFactory.createSimpleMethodParamModel(
                                new TypeSpec(ClassName.bestGuess("java.lang.MadeUpClass")),
                                "content",
                                mMethodParamObject2),
                            interStageInputParamModel))
                    .representedObject(mDelegateMethodObject1)
                    .typeModel(null)
                    .build()));

    final List<SpecModelValidationError> validationErrors =
        DelegateMethodValidation.validateMountSpecModel(mMountSpecModel);
    for (SpecModelValidationError validationError : validationErrors) {
      System.out.println(validationError.message);
    }
    assertThat(validationErrors).hasSize(1);
    assertThat(validationErrors.get(0).element).isEqualTo(mMethodParamObject3);
    assertThat(validationErrors.get(0).message)
        .isEqualTo(
            "To use interface com.facebook.litho.annotations.FromPrepare on param interStageInput"
                + " you must have a method annotated with interface"
                + " com.facebook.litho.annotations.OnPrepare that has a param"
                + " Output<java.lang.Integer> interStageInput");
  }

  @Test
  public void testInterStageInputOutputDoesNotExist() {
    final InterStageInputParamModel interStageInputParamModel =
        mock(InterStageInputParamModel.class);
    when(interStageInputParamModel.getAnnotations())
        .thenReturn(ImmutableList.of((Annotation) () -> FromPrepare.class));
    when(interStageInputParamModel.getName()).thenReturn("interStageInput");
    when(interStageInputParamModel.getTypeName()).thenReturn(TypeName.INT);
    when(interStageInputParamModel.getRepresentedObject()).thenReturn(mMethodParamObject3);
    when(mMountSpecModel.getDelegateMethods())
        .thenReturn(
            ImmutableList.of(
                mOnCreateMountContent,
                SpecMethodModel.<DelegateMethod, Void>builder()
                    .annotations(ImmutableList.of((Annotation) () -> OnMount.class))
                    .modifiers(ImmutableList.of(Modifier.STATIC))
                    .name("onMount")
                    .returnTypeSpec(new TypeSpec(TypeName.VOID))
                    .typeVariables(ImmutableList.of())
                    .methodParams(
                        ImmutableList.of(
                            mComponentContextParamModel,
                            MethodParamModelFactory.createSimpleMethodParamModel(
                                new TypeSpec(ClassName.bestGuess("java.lang.MadeUpClass")),
                                "param",
                                mMethodParamObject2),
                            interStageInputParamModel))
                    .representedObject(mDelegateMethodObject1)
                    .typeModel(null)
                    .build(),
                SpecMethodModel.<DelegateMethod, Void>builder()
                    .annotations(ImmutableList.of((Annotation) () -> OnPrepare.class))
                    .modifiers(ImmutableList.of(Modifier.STATIC))
                    .name("onPrepare")
                    .returnTypeSpec(new TypeSpec(TypeName.VOID))
                    .typeVariables(ImmutableList.of())
                    .methodParams(ImmutableList.of(mComponentContextParamModel))
                    .representedObject(mDelegateMethodObject2)
                    .typeModel(null)
                    .build()));

    final List<SpecModelValidationError> validationErrors =
        DelegateMethodValidation.validateMountSpecModel(mMountSpecModel);
    for (final SpecModelValidationError validationError : validationErrors) {
      System.out.println(validationError.message);
    }
    assertThat(validationErrors).hasSize(1);
    assertThat(validationErrors.get(0).element).isEqualTo(mMethodParamObject3);
    assertThat(validationErrors.get(0).message)
        .isEqualTo(
            "To use interface com.facebook.litho.annotations.FromPrepare on param interStageInput"
                + " your method annotated with interface com.facebook.litho.annotations.OnPrepare"
                + " must have a param Output<java.lang.Integer> interStageInput");
  }

  @Test
  public void testDelegateMethodWithOptionalParameters() throws Exception {
    final Map<Class<? extends Annotation>, DelegateMethodDescription> map = new HashMap<>();
    map.put(
        OnCreateLayout.class,
        DelegateMethodDescription.fromDelegateMethodDescription(
                LAYOUT_SPEC_DELEGATE_METHODS_MAP.get(OnCreateLayout.class))
            .optionalParameters(
                ImmutableList.of(
                    MethodParamModelFactory.createSimpleMethodParamModel(
                        new TypeSpec(TypeName.INT.box()), "matched", new Object()),
                    MethodParamModelFactory.createSimpleMethodParamModel(
                        new TypeSpec(TypeName.CHAR), "unmatched", new Object())))
            .build());

    when(mLayoutSpecModel.getDelegateMethods())
        .thenReturn(
            ImmutableList.of(
                SpecMethodModel.<DelegateMethod, Void>builder()
                    .annotations(ImmutableList.of((Annotation) () -> OnCreateLayout.class))
                    .modifiers(ImmutableList.of(Modifier.STATIC))
                    .name("name")
                    .returnTypeSpec(new TypeSpec(ClassNames.COMPONENT))
                    .typeVariables(ImmutableList.of())
                    .methodParams(
                        ImmutableList.of(
                            mComponentContextParamModel,
                            MethodParamModelFactory.createSimpleMethodParamModel(
                                new TypeSpec(TypeName.INT.box()), "matched", mMethodParamObject2),
                            MethodParamModelFactory.createSimpleMethodParamModel(
                                new TypeSpec(TypeName.OBJECT), "unmatched", mMethodParamObject3)))
                    .representedObject(mDelegateMethodObject1)
                    .typeModel(null)
                    .build()));

    final List<SpecModelValidationError> validationErrors =
        DelegateMethodValidation.validateMethods(
            mLayoutSpecModel, map, DelegateMethodDescriptions.INTER_STAGE_INPUTS_MAP);
    assertThat(validationErrors).hasSize(1);
    assertThat(validationErrors.get(0).element).isEqualTo(mMethodParamObject3);
    assertThat(validationErrors.get(0).message)
        .isEqualTo(
            "Argument at index 2 (unmatched) is not a valid parameter, should be one of the"
                + " following: @Prop T somePropName. @TreeProp T someTreePropName. @State T"
                + " someStateName. @CachedValue T value, where"
                + " the cached value has a corresponding @OnCalculateCachedValue method. Or one of"
                + " the following, where no annotations should be added to the parameter:"
                + " java.lang.Integer matched. char unmatched. ");
  }

  @Test
  public void testDelegateMethodWithUnmatchedStateValue() throws Exception {
    final Map<Class<? extends Annotation>, DelegateMethodDescription> map = new HashMap<>();
    map.put(
        OnCreateInitialState.class,
        DelegateMethodDescription.fromDelegateMethodDescription(
                LAYOUT_SPEC_DELEGATE_METHODS_MAP.get(OnCreateInitialState.class))
            .optionalParameters(
                ImmutableList.of(
                    MethodParamModelFactory.createSimpleMethodParamModel(
                        new TypeSpec(
                            ParameterizedTypeName.get(
                                ClassName.get(StateValue.class), TypeName.OBJECT)),
                        "unmatched",
                        mDelegateMethodObject1)))
            .build());

    when(mLayoutSpecModel.getDelegateMethods())
        .thenReturn(
            ImmutableList.of(
                SpecMethodModel.<DelegateMethod, Void>builder()
                    .annotations(ImmutableList.of((Annotation) () -> OnCreateInitialState.class))
                    .modifiers(ImmutableList.of(Modifier.STATIC))
                    .name("onCreateInitialState")
                    .returnTypeSpec(new TypeSpec(TypeName.VOID))
                    .typeVariables(ImmutableList.of())
                    .methodParams(
                        ImmutableList.of(
                            mComponentContextParamModel,
                            MethodParamModelFactory.createSimpleMethodParamModel(
                                new TypeSpec(TypeName.CHAR), "unmatched", mMethodParamObject3)))
                    .representedObject(mDelegateMethodObject1)
                    .typeModel(null)
                    .build()));

    final List<SpecModelValidationError> validationErrors =
        DelegateMethodValidation.validateMethods(
            mLayoutSpecModel, map, DelegateMethodDescriptions.INTER_STAGE_INPUTS_MAP);
    assertThat(validationErrors).hasSize(1);
    assertThat(validationErrors.get(0).element).isEqualTo(mMethodParamObject3);
    assertThat(validationErrors.get(0).message)
        .isEqualTo(
            "Argument at index 1 (unmatched) is not a valid parameter, should be one of the"
                + " following: @Prop T somePropName. @TreeProp T someTreePropName. StateValue<T>"
                + " stateName, where a state param with type T and name stateName is declared"
                + " elsewhere in the spec. Or one of the"
                + " following, where no annotations should be added to the parameter:"
                + " com.facebook.litho.StateValue<java.lang.Object> unmatched. ");
  }

  @Test
  public void testDelegateMethodWithUnmatchedStateValueKotlin() throws Exception {
    final Map<Class<? extends Annotation>, DelegateMethodDescription> map = new HashMap<>();
    map.put(
        OnCreateInitialState.class,
        DelegateMethodDescription.fromDelegateMethodDescription(
                LAYOUT_SPEC_DELEGATE_METHODS_MAP.get(OnCreateInitialState.class))
            .optionalParameters(
                ImmutableList.of(
                    MethodParamModelFactory.createSimpleMethodParamModel(
                        new TypeSpec(
                            ParameterizedTypeName.get(
                                ClassName.get(StateValue.class), TypeName.OBJECT)),
                        "unmatched",
                        mDelegateMethodObject1)))
            .build());

    when(mLayoutSpecModel.getDelegateMethods())
        .thenReturn(
            ImmutableList.of(
                SpecMethodModel.<DelegateMethod, Void>builder()
                    .annotations(ImmutableList.of((Annotation) () -> OnCreateInitialState.class))
                    .modifiers(ImmutableList.of(Modifier.STATIC))
                    .name("onCreateInitialState")
                    .returnTypeSpec(new TypeSpec(TypeName.VOID))
                    .typeVariables(ImmutableList.of())
                    .methodParams(
                        ImmutableList.of(
                            mComponentContextParamModel,
                            MethodParamModelFactory.createSimpleMethodParamModel(
                                new TypeSpec(TypeName.CHAR), "unmatched", mMethodParamObject3)))
                    .representedObject(mDelegateMethodObject1)
                    .typeModel(null)
                    .build()));
    when(mLayoutSpecModel.getSpecElementType()).thenReturn(SpecElementType.KOTLIN_CLASS);

    final List<SpecModelValidationError> validationErrors =
        DelegateMethodValidation.validateMethods(
            mLayoutSpecModel, map, DelegateMethodDescriptions.INTER_STAGE_INPUTS_MAP);
    assertThat(validationErrors).hasSize(1);
    assertThat(validationErrors.get(0).element).isEqualTo(mMethodParamObject3);
    assertThat(validationErrors.get(0).message)
        .isEqualTo(
            "Argument at index 1 (unmatched) is not a valid parameter, should be one of the"
                + " following: @Prop T somePropName. @TreeProp T someTreePropName. StateValue<T>"
                + " stateName, where a state param with type T and name stateName is declared"
                + " elsewhere in the spec. Or one of the"
                + " following, where no annotations should be added to the parameter:"
                + " com.facebook.litho.StateValue<java.lang.Object> unmatched. ");
  }

  @Test
  public void testDelegateMethodWithCovariantStateValueJava() throws Exception {
    final Map<Class<? extends Annotation>, DelegateMethodDescription> map = new HashMap<>();
    map.put(
        OnCreateInitialState.class,
        DelegateMethodDescription.fromDelegateMethodDescription(
                LAYOUT_SPEC_DELEGATE_METHODS_MAP.get(OnCreateInitialState.class))
            .optionalParameters(
                ImmutableList.of(
                    MethodParamModelFactory.createSimpleMethodParamModel(
                        new TypeSpec(
                            ParameterizedTypeName.get(
                                ClassName.get(StateValue.class),
                                WildcardTypeName.subtypeOf(TypeName.OBJECT))),
                        "name1",
                        mDelegateMethodObject1)))
            .build());

    when(mLayoutSpecModel.getDelegateMethods())
        .thenReturn(
            ImmutableList.of(
                SpecMethodModel.<DelegateMethod, Void>builder()
                    .annotations(ImmutableList.of((Annotation) () -> OnCreateInitialState.class))
                    .modifiers(ImmutableList.of(Modifier.STATIC))
                    .name("onCreateInitialState")
                    .returnTypeSpec(new TypeSpec(TypeName.VOID))
                    .typeVariables(ImmutableList.of())
                    .methodParams(
                        ImmutableList.of(
                            mComponentContextParamModel,
                            MethodParamModelFactory.createSimpleMethodParamModel(
                                new TypeSpec(
                                    ParameterizedTypeName.get(
                                        ClassName.get(StateValue.class),
                                        ParameterizedTypeName.get(
                                            ClassName.get(List.class), TypeName.OBJECT))),
                                "name1",
                                mMethodParamObject3)))
                    .representedObject(mDelegateMethodObject1)
                    .typeModel(null)
                    .build()));
    when(mLayoutSpecModel.getStateValues())
        .thenReturn(
            ImmutableList.of(
                new StateParamModel(
                    MockMethodParamModel.newBuilder()
                        .type(
                            ParameterizedTypeName.get(
                                ClassName.get(List.class),
                                WildcardTypeName.subtypeOf(TypeName.OBJECT)))
                        .name("name1")
                        .representedObject(mDelegateMethodObject2)
                        .build(),
                    false /* canUpdateLazily */)));

    final List<SpecModelValidationError> validationErrors =
        DelegateMethodValidation.validateMethods(
            mLayoutSpecModel, map, DelegateMethodDescriptions.INTER_STAGE_INPUTS_MAP);
    assertThat(validationErrors).hasSize(1);
    assertThat(validationErrors.get(0).element).isEqualTo(mMethodParamObject3);
    assertThat(validationErrors.get(0).message)
        .isEqualTo(
            "Argument at index 1 (name1) is not a valid parameter, should be one of the "
                + "following: @Prop T somePropName. @TreeProp T someTreePropName. "
                + "StateValue<T> stateName, where a state param with type T and name stateName is "
                + "declared elsewhere in the spec. Or one of the "
                + "following, where no annotations should be added to the parameter: "
                + "com.facebook.litho.StateValue<?> name1. ");
  }

  @Test
  public void testDelegateMethodWithCovariantStateValueKotlin() throws Exception {
    final Map<Class<? extends Annotation>, DelegateMethodDescription> map = new HashMap<>();
    map.put(
        OnCreateInitialState.class,
        DelegateMethodDescription.fromDelegateMethodDescription(
                LAYOUT_SPEC_DELEGATE_METHODS_MAP.get(OnCreateInitialState.class))
            .optionalParameters(
                ImmutableList.of(
                    MethodParamModelFactory.createSimpleMethodParamModel(
                        new TypeSpec(
                            ParameterizedTypeName.get(
                                ClassName.get(StateValue.class),
                                WildcardTypeName.subtypeOf(TypeName.OBJECT))),
                        "name1",
                        mDelegateMethodObject1)))
            .build());

    when(mLayoutSpecModel.getDelegateMethods())
        .thenReturn(
            ImmutableList.of(
                SpecMethodModel.<DelegateMethod, Void>builder()
                    .annotations(ImmutableList.of((Annotation) () -> OnCreateInitialState.class))
                    .modifiers(ImmutableList.of(Modifier.STATIC))
                    .name("onCreateInitialState")
                    .returnTypeSpec(new TypeSpec(TypeName.VOID))
                    .typeVariables(ImmutableList.of())
                    .methodParams(
                        ImmutableList.of(
                            mComponentContextParamModel,
                            MethodParamModelFactory.createSimpleMethodParamModel(
                                new TypeSpec(
                                    ParameterizedTypeName.get(
                                        ClassName.get(StateValue.class),
                                        ParameterizedTypeName.get(
                                            ClassName.get(List.class), TypeName.OBJECT))),
                                "name1",
                                mMethodParamObject3)))
                    .representedObject(mDelegateMethodObject1)
                    .typeModel(null)
                    .build()));
    when(mLayoutSpecModel.getSpecElementType()).thenReturn(SpecElementType.KOTLIN_CLASS);
    when(mLayoutSpecModel.getStateValues())
        .thenReturn(
            ImmutableList.of(
                new StateParamModel(
                    MockMethodParamModel.newBuilder()
                        .type(
                            ParameterizedTypeName.get(
                                ClassName.get(List.class),
                                WildcardTypeName.subtypeOf(TypeName.OBJECT)))
                        .name("name1")
                        .representedObject(mDelegateMethodObject2)
                        .build(),
                    false /* canUpdateLazily */)));

    final List<SpecModelValidationError> validationErrors =
        DelegateMethodValidation.validateMethods(
            mLayoutSpecModel, map, DelegateMethodDescriptions.INTER_STAGE_INPUTS_MAP);
    assertThat(validationErrors).hasSize(1);
    assertThat(validationErrors.get(0).element).isEqualTo(mMethodParamObject3);
    assertThat(validationErrors.get(0).message)
        .isEqualTo(
            "Argument at index 1 (name1) is not a valid parameter. This StateValue<T> has "
                + "unsuppressed wildcards from a Kotlin spec. Wildcards need to be suppressed via "
                + "@JvmSuppressWildcards StateValue<ContainerType<T>> or "
                + "StateValue<ContainerType<@JvmSuppressWildcards T>>.");
  }

  @Test
  public void testDelegateMethodWithCovariantStateValueKotlinButStateNotAllowedType()
      throws Exception {
    final DelegateMethodDescription onCreateInitialStateMethodDescription =
        LAYOUT_SPEC_DELEGATE_METHODS_MAP.get(OnCreateInitialState.class);
    final List<DelegateMethodDescription.OptionalParameterType> optionalParamTypeWithoutState =
        new ArrayList<>(onCreateInitialStateMethodDescription.optionalParameterTypes);
    optionalParamTypeWithoutState.remove(STATE_VALUE);

    final Map<Class<? extends Annotation>, DelegateMethodDescription> map = new HashMap<>();
    map.put(
        OnCreateInitialState.class,
        DelegateMethodDescription.fromDelegateMethodDescription(
                onCreateInitialStateMethodDescription)
            .optionalParameters(
                ImmutableList.of(
                    MethodParamModelFactory.createSimpleMethodParamModel(
                        new TypeSpec(
                            ParameterizedTypeName.get(
                                ClassName.get(StateValue.class),
                                WildcardTypeName.subtypeOf(TypeName.OBJECT))),
                        "name1",
                        mDelegateMethodObject1)))
            .optionalParameterTypes(ImmutableList.copyOf(optionalParamTypeWithoutState))
            .build());

    when(mLayoutSpecModel.getDelegateMethods())
        .thenReturn(
            ImmutableList.of(
                SpecMethodModel.<DelegateMethod, Void>builder()
                    .annotations(ImmutableList.of((Annotation) () -> OnCreateInitialState.class))
                    .modifiers(ImmutableList.of(Modifier.STATIC))
                    .name("onCreateInitialState")
                    .returnTypeSpec(new TypeSpec(TypeName.VOID))
                    .typeVariables(ImmutableList.of())
                    .methodParams(
                        ImmutableList.of(
                            mComponentContextParamModel,
                            MethodParamModelFactory.createSimpleMethodParamModel(
                                new TypeSpec(
                                    ParameterizedTypeName.get(
                                        ClassName.get(StateValue.class),
                                        ParameterizedTypeName.get(
                                            ClassName.get(List.class), TypeName.OBJECT))),
                                "name1",
                                mMethodParamObject3)))
                    .representedObject(mDelegateMethodObject1)
                    .typeModel(null)
                    .build()));
    when(mLayoutSpecModel.getSpecElementType()).thenReturn(SpecElementType.KOTLIN_CLASS);
    when(mLayoutSpecModel.getStateValues())
        .thenReturn(
            ImmutableList.of(
                new StateParamModel(
                    MockMethodParamModel.newBuilder()
                        .type(
                            ParameterizedTypeName.get(
                                ClassName.get(List.class),
                                WildcardTypeName.subtypeOf(TypeName.OBJECT)))
                        .name("name1")
                        .representedObject(mDelegateMethodObject2)
                        .build(),
                    false /* canUpdateLazily */)));

    final List<SpecModelValidationError> validationErrors =
        DelegateMethodValidation.validateMethods(
            mLayoutSpecModel, map, DelegateMethodDescriptions.INTER_STAGE_INPUTS_MAP);
    assertThat(validationErrors).hasSize(1);
    assertThat(validationErrors.get(0).element).isEqualTo(mMethodParamObject3);
    assertThat(validationErrors.get(0).message)
        .isEqualTo(
            "Argument at index 1 (name1) is not a valid parameter, should be one of the "
                + "following: @Prop T somePropName. @TreeProp T someTreePropName. "
                + "Or one of the following, where no annotations "
                + "should be added to the parameter: com.facebook.litho.StateValue<?> name1. ");
  }
}
