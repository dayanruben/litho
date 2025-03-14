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

package com.facebook.litho.intellij.navigation;

import com.facebook.infer.annotation.Nullsafe;
import com.facebook.litho.annotations.PropSetter;
import com.facebook.litho.intellij.LithoPluginUtils;
import com.facebook.litho.intellij.PsiSearchUtils;
import com.facebook.litho.intellij.extensions.EventLogger;
import com.facebook.litho.intellij.logging.DebounceEventLogger;
import com.facebook.litho.intellij.services.ComponentGenerateService;
import com.facebook.litho.specmodels.model.SpecModel;
import com.facebook.litho.specmodels.processor.PsiAnnotationProxyUtils;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandlerBase;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiImportStatement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.util.PsiTreeUtil;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.jetbrains.annotations.Nullable;

/** Navigates from Component method to Spec elements. */
@Nullsafe(Nullsafe.Mode.LOCAL)
public class ComponentsMethodDeclarationHandler extends GotoDeclarationHandlerBase {
  private static final EventLogger LOGGER = new DebounceEventLogger(4_000);

  @Nullable
  @Override
  public PsiElement getGotoDeclarationTarget(@Nullable PsiElement sourceElement, Editor editor) {
    final PsiElement[] targets = getGotoDeclarationTargets(sourceElement, 0, editor);
    if (targets.length == 0) return null;

    return targets[0];
  }

  @Override
  public PsiElement[] getGotoDeclarationTargets(
      @Nullable PsiElement sourceElement, int offset, Editor editor) {
    // Exclusions
    if (sourceElement == null
        || PsiTreeUtil.getParentOfType(sourceElement, PsiImportStatement.class) != null) {
      return PsiElement.EMPTY_ARRAY;
    }
    final Project project = sourceElement.getProject();
    return BaseLithoComponentsDeclarationHandler.resolve(sourceElement)
        .filter(PsiMethod.class::isInstance)
        .map(PsiMethod.class::cast)
        .flatMap(
            componentMethod -> {
              final PsiClass specCls = getSpecParent(componentMethod, project);
              if (specCls == null) return null;

              PsiElement[] result;
              final PropSetter propSetter =
                  PsiAnnotationProxyUtils.findAnnotationInHierarchy(
                      componentMethod, PropSetter.class);
              if (propSetter != null) {
                result = findSpecProps(propSetter, specCls);
              } else {
                result = findSpecMethods(componentMethod, specCls);
              }
              if (result.length == 0) return null;

              log(specCls, result);
              return Arrays.stream(result);
            })
        .filter(Objects::nonNull)
        .toArray(PsiElement[]::new);
  }

  private static void log(PsiClass specCls, PsiElement[] result) {
    final Map<String, String> data = new HashMap<>();
    data.put(
        EventLogger.KEY_TARGET,
        result[0] instanceof PsiMethod
            ? EventLogger.VALUE_NAVIGATION_TARGET_METHOD
            : EventLogger.VALUE_NAVIGATION_TARGET_PARAMETER);
    data.put(EventLogger.KEY_TYPE, EventLogger.VALUE_NAVIGATION_TYPE_GOTO);
    String cls;
    if (LithoPluginUtils.isComponentClass(specCls)) {
      cls = EventLogger.VALUE_NAVIGATION_CLASS_COMPONENT;
    } else if (LithoPluginUtils.isSectionClass(specCls)) {
      cls = EventLogger.VALUE_NAVIGATION_CLASS_SECTION;
    } else {
      cls = "other";
    }
    data.put(EventLogger.KEY_CLASS, cls);
    LOGGER.log(EventLogger.EVENT_NAVIGATION, data);
  }

  @Nullable
  private static PsiClass getSpecParent(PsiMethod componentMethod, Project project) {
    final PsiClass containingCls =
        LithoPluginUtils.getFirstClass(
                componentMethod.getContainingFile(), LithoPluginUtils::isGeneratedClass)
            .orElse(null);
    if (containingCls == null) return null;

    // For Unit testing we don't care about package
    final String containingClsName =
        ApplicationManager.getApplication().isUnitTestMode()
            ? containingCls.getName()
            : containingCls.getQualifiedName();
    final PsiClass specCls =
        PsiSearchUtils.getInstance()
            .findOriginalClass(
                project,
                // NULLSAFE_FIXME[Parameter Not Nullable]
                LithoPluginUtils.getLithoComponentSpecNameFromComponent(containingClsName));
    return specCls;
  }

  private static PsiElement[] findSpecMethods(PsiMethod componentMethod, PsiClass specCls) {
    if (componentMethod.getModifierList().hasModifierProperty(PsiModifier.STATIC)) {
      return specCls.findMethodsByName(componentMethod.getName(), false);
    }
    return PsiMethod.EMPTY_ARRAY;
  }

  private static PsiElement[] findSpecProps(PropSetter propSetter, PsiClass specCls) {
    SpecModel specModel = ComponentGenerateService.getInstance().getOrCreateSpecModel(specCls);
    if (specModel == null) return PsiElement.EMPTY_ARRAY;

    final String prop = propSetter.value();
    return specModel.getProps().stream()
        .filter(propModel -> prop.equals(propModel.getName()))
        .map(propModel -> (PsiElement) propModel.getRepresentedObject())
        .toArray(PsiElement[]::new);
  }
}
