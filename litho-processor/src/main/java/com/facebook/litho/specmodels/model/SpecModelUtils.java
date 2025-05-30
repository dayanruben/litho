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

import static com.facebook.litho.specmodels.model.ClassNames.OUTPUT;

import com.facebook.litho.annotations.Prop;
import com.facebook.litho.annotations.State;
import com.facebook.litho.specmodels.internal.ImmutableList;
import com.facebook.litho.specmodels.internal.SimpleMemoizingSupplier;
import com.google.common.base.Preconditions;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleTypeVisitor6;

/** Utility methods for {@link SpecModel}s. */
public class SpecModelUtils {

  public static String getSpecAccessor(SpecModel specModel) {
    if (specModel.getSpecElementType() == SpecElementType.KOTLIN_CLASS) {
      return specModel.getSpecName() + ".Companion";
    } else if (specModel.getSpecElementType() == SpecElementType.KOTLIN_SINGLETON) {
      return specModel.getSpecName() + ".INSTANCE";
    }

    return specModel.getSpecName();
  }

  @Nullable
  public static PropModel getPropWithName(SpecModel specModel, String name) {
    for (PropModel prop : specModel.getProps()) {
      if (prop.getName().equals(name)) {
        return prop;
      }
    }

    return null;
  }

  @Nullable
  public static StateParamModel getStateValueWithName(SpecModel specModel, String name) {
    for (StateParamModel stateValue : specModel.getStateValues()) {
      if (stateValue.getName().equals(name)) {
        return stateValue;
      }
    }

    return null;
  }

  @Nullable
  public static SpecMethodModel<DelegateMethod, Void> getMethodModelWithAnnotation(
      SpecModel specModel, Class<? extends Annotation> annotationClass) {
    for (SpecMethodModel<DelegateMethod, Void> delegateMethodModel :
        specModel.getDelegateMethods()) {
      for (Annotation annotation : delegateMethodModel.annotations) {
        if (annotation.annotationType().equals(annotationClass)) {
          return delegateMethodModel;
        }
      }
    }

    return null;
  }

  public static List<SpecMethodModel<DelegateMethod, Void>> getMethodModelsWithAnnotation(
      SpecModel specModel, Class<? extends Annotation> annotationClass) {
    final List<SpecMethodModel<DelegateMethod, Void>> methodModels = new ArrayList<>();
    for (SpecMethodModel<DelegateMethod, Void> delegateMethodModel :
        specModel.getDelegateMethods()) {
      for (Annotation annotation : delegateMethodModel.annotations) {
        if (annotation.annotationType().equals(annotationClass)) {
          methodModels.add(delegateMethodModel);
        }
      }
    }

    return methodModels;
  }

  public static boolean isPropOutput(SpecModel specModel, MethodParamModel methodParamModel) {
    final PropModel prop = getPropWithName(specModel, methodParamModel.getName());
    return prop != null
        && methodParamModel.getTypeName() instanceof ParameterizedTypeName
        && ((ParameterizedTypeName) methodParamModel.getTypeName()).rawType.equals(OUTPUT)
        && ((ParameterizedTypeName) methodParamModel.getTypeName()).typeArguments.size() == 1
        && ((ParameterizedTypeName) methodParamModel.getTypeName())
            .typeArguments
            .get(0)
            .equals(prop.getTypeName().box());
  }

  public static boolean isStateOutput(SpecModel specModel, MethodParamModel methodParamModel) {
    final StateParamModel stateValue =
        SpecModelUtils.getStateValueWithName(specModel, methodParamModel.getName());
    return stateValue != null
        && methodParamModel.getTypeName() instanceof ParameterizedTypeName
        && ((ParameterizedTypeName) methodParamModel.getTypeName()).rawType.equals(OUTPUT)
        && ((ParameterizedTypeName) methodParamModel.getTypeName()).typeArguments.size() == 1
        && ((ParameterizedTypeName) methodParamModel.getTypeName())
            .typeArguments
            .get(0)
            .equals(stateValue.getTypeName().box());
  }

  /** Determines whether the given method parameter is a state value. */
  public static boolean isStateValue(SpecModel specModel, MethodParamModel methodParamModel) {
    final StateParamModel stateValue =
        SpecModelUtils.getStateValueWithName(specModel, methodParamModel.getName());
    return isStateValueHolder(specModel, methodParamModel)
        && doesStateValueTypeMatchState(
            stateValue,
            ((ParameterizedTypeName) methodParamModel.getTypeName()).typeArguments.get(0));
  }

  /**
   * Determines whether the given method parameter is a state value, when ignoring Kotlin variant
   * types.
   *
   * <p>The expectation is that this function will return true for cases that fail {@link
   * #isStateValue}, only due to covariance. Therefore, failing {@link #isStateValue} is an
   * invariant of this function.
   *
   * @return true if the given method parameter is a state value, when ignoring Kotlin covariance.
   */
  public static boolean isStateValueOnlyIfIgnoringKotlinCovariance(
      final SpecModel specModel, final MethodParamModel methodParamModel) {
    if (!isStateValueHolder(specModel, methodParamModel)) {
      return false;
    }

    final StateParamModel stateValue =
        Preconditions.checkNotNull(
            SpecModelUtils.getStateValueWithName(specModel, methodParamModel.getName()));
    final TypeName methodParamStateTypeName =
        ((ParameterizedTypeName) methodParamModel.getTypeName()).typeArguments.get(0);

    return !doesStateValueTypeMatchState(stateValue, methodParamStateTypeName)
        && areTypesEqualIgnoringKotlinCovariance(
            specModel, stateValue.getTypeName(), methodParamStateTypeName);
  }

  /**
   * Determines if the given MethodParamModel is a state value holder (ie. of the type
   * StateValue<T>).
   *
   * @param specModel the specific specModel from where the types are coming from
   * @param methodParamModel the method param model to check
   * @return true if the methodParamModel is a state value holder, otherwise false
   */
  static boolean isStateValueHolder(SpecModel specModel, MethodParamModel methodParamModel) {
    final StateParamModel stateValue =
        SpecModelUtils.getStateValueWithName(specModel, methodParamModel.getName());
    return stateValue != null
        && methodParamModel.getTypeName() instanceof ParameterizedTypeName
        && ((ParameterizedTypeName) methodParamModel.getTypeName())
            .rawType.equals(ClassNames.STATE_VALUE)
        && ((ParameterizedTypeName) methodParamModel.getTypeName()).typeArguments.size() == 1;
  }

  /**
   * @param stateValue the state value to check against
   * @param underlyingStateTypeName the underlying typename of the state value. (ie. T in
   *     StateValue<T>)</T>
   * @return true if the state value matches the underlying type, otherwise false.
   */
  static boolean doesStateValueTypeMatchState(
      @Nullable final StateParamModel stateValue, final TypeName underlyingStateTypeName) {
    return stateValue != null && underlyingStateTypeName.equals(stateValue.getTypeName().box());
  }

  /**
   * Checks equality against two types, ignoring Kotlin covariance ie. List<? extends T> would equal
   * List<T>.
   *
   * @param specModel the specific specModel from where the types are coming from
   * @param typeName1 the first type to compare
   * @param typeName2 the second type to compare
   * @return if the specModel is a Kotlin spec, and the two types are equal after ignoring Kotlin
   *     covariance.
   */
  public static boolean areTypesEqualIgnoringKotlinCovariance(
      SpecModel specModel, TypeName typeName1, TypeName typeName2) {
    final TypeName typeName1WithoutWildcards =
        KotlinSpecHelper.maybeRemoveWildcardFromContainerIfKotlinSpec(specModel, typeName1);
    final TypeName typeName2WithoutWildcards =
        KotlinSpecHelper.maybeRemoveWildcardFromContainerIfKotlinSpec(specModel, typeName2);

    return typeName1WithoutWildcards.box().equals(typeName2WithoutWildcards.box());
  }

  /**
   * @return the model for state/prop that this Diff is refering to.
   */
  public static MethodParamModel getReferencedParamModelForDiff(
      SpecModel specModel, RenderDataDiffModel diffModel) {
    if (MethodParamModelUtils.isAnnotatedWith(diffModel, Prop.class)) {
      return SpecModelUtils.getPropWithName(specModel, diffModel.getName());
    } else if (MethodParamModelUtils.isAnnotatedWith(diffModel, State.class)) {
      return SpecModelUtils.getStateValueWithName(specModel, diffModel.getName());
    }

    throw new RuntimeException(
        "Diff model wasn't annotated with @State or @Prop, some validation failed");
  }

  public static boolean hasAnnotation(SpecMethodModel<?, ?> method, Class<?> annotationClass) {
    for (Annotation annotation : method.annotations) {
      if (annotation.annotationType().equals(annotationClass)) {
        return true;
      }
    }

    return false;
  }

  public static boolean hasAnnotation(MethodParamModel methodParam, Class<?> annotationClass) {
    for (Annotation annotation : methodParam.getAnnotations()) {
      if (annotation.annotationType().equals(annotationClass)) {
        return true;
      }
    }

    return false;
  }

  public static boolean hasLazyState(SpecModel specModel) {
    for (StateParamModel stateParamModel : specModel.getStateValues()) {
      if (stateParamModel.canUpdateLazily()) {
        return true;
      }
    }

    return false;
  }

  /**
   * This method will "expand" the typeArguments of the given type, only if the type is a {@link
   * ClassNames#DIFF} or a {@link java.util.Collection}. Otherwise the typeArguments won't be
   * traversed and recorded.
   */
  public static TypeSpec generateTypeSpec(TypeMirror type) {
    final TypeSpec defaultValue =
        new TypeSpec(safelyGetTypeName(type), type.getKind() != TypeKind.ERROR);

    return type.accept(
        new SimpleTypeVisitor6<TypeSpec, Void>(defaultValue) {
          @Override
          public TypeSpec visitDeclared(DeclaredType t, Void aVoid) {
            final TypeElement typeElement = (TypeElement) t.asElement();
            final String qualifiedName = typeElement.getQualifiedName().toString();
            final Supplier<TypeSpec> superclass =
                new SimpleMemoizingSupplier<>(
                    () -> {
                      final TypeMirror mirror = typeElement.getSuperclass();
                      return mirror.getKind() != TypeKind.DECLARED
                          ? null
                          : generateTypeSpec(mirror);
                    });

            final Supplier<ImmutableList<TypeSpec>> superinterfaces =
                new SimpleMemoizingSupplier<>(
                    () -> {
                      final List<? extends TypeMirror> mirrors = typeElement.getInterfaces();
                      return ImmutableList.copyOf(
                          mirrors != null && !mirrors.isEmpty()
                              ? mirrors.stream()
                                  .filter(mirror -> mirror.getKind() == TypeKind.DECLARED)
                                  .map(SpecModelUtils::generateTypeSpec)
                                  .collect(Collectors.toList())
                              : Collections.emptyList());
                    });

            final Supplier<ImmutableList<TypeSpec>> typeArguments =
                new SimpleMemoizingSupplier<>(
                    () ->
                        ImmutableList.copyOf(
                            ClassName.bestGuess(qualifiedName).equals(ClassNames.DIFF)
                                    || superinterfaces.get().stream()
                                        .anyMatch(
                                            typeSpec ->
                                                typeSpec.isSubInterface(ClassNames.COLLECTION))
                                ? ((DeclaredType) type)
                                    .getTypeArguments().stream()
                                        .map(SpecModelUtils::generateTypeSpec)
                                        .collect(Collectors.toList())
                                : Collections.emptyList()));

            return new TypeSpec.DeclaredTypeSpec(
                safelyGetTypeName(t), qualifiedName, superclass, superinterfaces, typeArguments);
          }
        },
        null);
  }

  public static boolean isTypeElement(final SpecModel specModel) {
    final Object representedObject = specModel.getRepresentedObject();
    return representedObject instanceof TypeElement;
  }

  public static List<PropModel> getDynamicProps(SpecModel specModel) {
    final List<PropModel> dynamicProps = new ArrayList<>();
    for (PropModel prop : specModel.getProps()) {
      if (prop.isDynamic()) {
        dynamicProps.add(prop);
      }
    }
    return dynamicProps;
  }

  public static SpecMethodModel<BindDynamicValueMethod, Void> getBindDelegateMethodForDynamicProp(
      SpecModel specModel, PropModel prop) {
    for (SpecMethodModel<BindDynamicValueMethod, Void> method :
        ((MountSpecModel) specModel).getBindDynamicValueMethods()) {
      if (prop.getName().equals(method.methodParams.get(1).getName())) {
        return method;
      }
    }
    return null;
  }

  /**
   * There are a few cases of classes with typeArgs (e.g. {@literal MyClass<SomeClass, ..>}) where
   *
   * <p>TypeName.get() throws an error like: "com.sun.tools.javac.code.Symbol$CompletionFailure:
   * class file for SomeClass not found". Therefore we manually get the qualified name and create
   * the TypeName from the path String.
   */
  private static TypeName safelyGetTypeName(TypeMirror t) {
    TypeName typeName;
    try {
      typeName = TypeName.get(t);
    } catch (Exception e) {
      final String qualifiedName;
      if (t instanceof DeclaredType) {
        qualifiedName =
            ((TypeElement) ((DeclaredType) t).asElement()).getQualifiedName().toString();
      } else {
        String tmp = t.toString();
        qualifiedName = tmp.substring(0, tmp.indexOf('<'));
      }

      typeName = ClassName.bestGuess(qualifiedName);
    }

    return typeName;
  }
}
