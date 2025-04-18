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

import static com.facebook.litho.specmodels.model.SpecMethodModelValidation.validateMethodIsStatic;

import com.facebook.litho.annotations.CachedValue;
import com.facebook.litho.annotations.FromEvent;
import com.facebook.litho.annotations.Param;
import com.facebook.litho.annotations.Prop;
import com.facebook.litho.annotations.State;
import com.facebook.litho.annotations.TreeProp;
import com.facebook.litho.specmodels.internal.ImmutableList;
import com.facebook.litho.specmodels.internal.RunMode;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeVariableName;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import javax.annotation.Nullable;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.MirroredTypeException;

/**
 * Class for validating that the event declarations and event methods within a {@link SpecModel} are
 * well-formed.
 */
public class EventValidation {
  private static final List<Class<? extends Annotation>> sDefaultPermittedAnnotations =
      ImmutableList.of(
          FromEvent.class, Prop.class, TreeProp.class, CachedValue.class, State.class, Param.class);

  static List<SpecModelValidationError> validate(
      SpecModel specModel,
      EnumSet<RunMode> runMode,
      @Nullable List<Class<? extends Annotation>> extraPermittedEventParamAnnotations) {
    List<SpecModelValidationError> validationErrors = new ArrayList<>();
    validationErrors.addAll(validateEventDeclarations(specModel));
    validationErrors.addAll(
        validateOnEventMethods(specModel, runMode, extraPermittedEventParamAnnotations));

    return validationErrors;
  }

  static List<SpecModelValidationError> validateEventDeclarations(SpecModel specModel) {
    List<SpecModelValidationError> validationErrors = new ArrayList<>();

    for (EventDeclarationModel eventDeclaration : specModel.getEventDeclarations()) {
      if (eventDeclaration.returnType == null) {
        validationErrors.add(
            new SpecModelValidationError(
                eventDeclaration.representedObject,
                "Event declarations must be annotated with @Event."));
      }

      for (FieldModel fieldModel : eventDeclaration.fields) {
        if (!fieldModel.field.modifiers.contains(Modifier.PUBLIC)
            || (fieldModel.field.modifiers.contains(Modifier.FINAL)
                && !fieldModel.field.modifiers.contains(Modifier.STATIC))) {
          validationErrors.add(
              new SpecModelValidationError(
                  fieldModel.representedObject,
                  "Event fields must be declared as public non-final."));
        }
      }
    }

    return validationErrors;
  }

  static List<SpecModelValidationError> validateOnEventMethods(
      SpecModel specModel,
      EnumSet<RunMode> runMode,
      @Nullable List<Class<? extends Annotation>> extraPermittedEventParamAnnotations) {
    final List<SpecModelValidationError> validationErrors = new ArrayList<>();

    final ImmutableList<SpecMethodModel<EventMethod, EventDeclarationModel>> eventMethods =
        specModel.getEventMethods();

    for (int i = 0, size = eventMethods.size(); i < size - 1; i++) {
      for (int j = i + 1; j < size; j++) {
        if (eventMethods.get(i).name.equals(eventMethods.get(j).name)) {
          validationErrors.add(
              new SpecModelValidationError(
                  eventMethods.get(i).representedObject,
                  "Two methods annotated with @OnEvent should not have the same name "
                      + "("
                      + eventMethods.get(i).name
                      + ")."));
        }
      }
    }

    if (!runMode.contains(RunMode.ABI)) {
      for (SpecMethodModel<EventMethod, EventDeclarationModel> eventMethod : eventMethods) {
        validationErrors.addAll(validateMethodIsStatic(specModel, eventMethod));

        if (!eventMethod.returnType.box().equals(eventMethod.typeModel.returnType.box())) {
          validationErrors.add(
              new SpecModelValidationError(
                  eventMethod.representedObject,
                  "Method must return "
                      + eventMethod.typeModel.returnType
                      + " since that is what "
                      + eventMethod.typeModel.name
                      + " expects."));
        }

        if (eventMethod.methodParams.isEmpty()
            || !eventMethod.methodParams.get(0).getTypeName().equals(specModel.getContextClass())) {
          validationErrors.add(
              new SpecModelValidationError(
                  eventMethod.representedObject,
                  "The first parameter for a method annotated with @OnEvent should be of type "
                      + specModel.getContextClass()
                      + "."));
        }

        for (int i = 1, size = eventMethod.methodParams.size(); i < size; i++) {
          final MethodParamModel methodParam = eventMethod.methodParams.get(i);
          if (MethodParamModelUtils.isAnnotatedWith(methodParam, FromEvent.class)
              && !hasMatchingField(methodParam, eventMethod.typeModel.fields)) {
            validationErrors.add(
                new SpecModelValidationError(
                    methodParam.getRepresentedObject(),
                    "Param with name "
                        + methodParam.getName()
                        + " and type "
                        + methodParam.getTypeName()
                        + " is not a member of "
                        + eventMethod.typeModel.name
                        + "."));
          }

          if (!hasPermittedAnnotation(methodParam, extraPermittedEventParamAnnotations)) {
            String errorMessage =
                "Param must be annotated with one of @FromEvent, @Prop, @TreeProp, "
                    + "@CachedValue, @State";

            if (extraPermittedEventParamAnnotations != null
                && !extraPermittedEventParamAnnotations.isEmpty()) {
              errorMessage += ", @Param";
              for (Class<? extends Annotation> extraPermittedAnnotation :
                  extraPermittedEventParamAnnotations) {
                errorMessage += ", @" + extraPermittedAnnotation.getSimpleName();
              }
            } else {
              errorMessage += " or @Param.";
            }

            validationErrors.add(
                new SpecModelValidationError(methodParam.getRepresentedObject(), errorMessage));
          }
        }
      }
    }

    return validationErrors;
  }

  private static boolean hasPermittedAnnotation(
      MethodParamModel methodParam,
      @Nullable List<Class<? extends Annotation>> extraPermittedEventParamAnnotations) {
    for (Class<? extends Annotation> annotation : sDefaultPermittedAnnotations) {
      if (MethodParamModelUtils.isAnnotatedWith(methodParam, annotation)) {
        return true;
      }
    }

    if (extraPermittedEventParamAnnotations != null) {
      for (Class<? extends Annotation> annotation : extraPermittedEventParamAnnotations) {
        if (MethodParamModelUtils.isAnnotatedWith(methodParam, annotation)) {
          return true;
        }
      }
    }

    return false;
  }

  private static boolean hasMatchingField(
      MethodParamModel param, ImmutableList<FieldModel> fields) {
    for (FieldModel field : fields) {
      if (param.getName().equals(field.field.name)
          && (param.getTypeName().box().equals(field.field.type.box())
              || isFromEventTypeSpecifiedInAnnotation(param, field.field.type))) {
        return true;
      }
    }

    return false;
  }

  private static boolean isFromEventTypeSpecifiedInAnnotation(
      MethodParamModel methodParamModel, TypeName eventFieldType) {
    FromEvent fromEvent =
        (FromEvent) MethodParamModelUtils.getAnnotation(methodParamModel, FromEvent.class);
    TypeName baseClassType;
    try {
      baseClassType = ClassName.get(fromEvent.baseClass());
    } catch (MirroredTypeException mte) {
      baseClassType = ClassName.get(mte.getTypeMirror());
    }

    if (eventFieldType instanceof TypeVariableName) {
      eventFieldType = ClassNames.OBJECT;
    }
    return baseClassType.equals(eventFieldType);
  }
}
