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

package com.facebook.litho.intellij.actions;

import com.facebook.infer.annotation.Nullsafe;
import com.facebook.litho.intellij.LithoPluginUtils;
import com.facebook.litho.intellij.PsiSearchUtils;
import com.facebook.litho.intellij.extensions.EventLogger;
import com.facebook.litho.intellij.logging.LithoLoggerProvider;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiClass;
import java.util.Optional;

/**
 * User initiated action available for the Spec file. When invoked navigates user to the generated
 * class.
 */
@Nullsafe(Nullsafe.Mode.LOCAL)
public class GoToGeneratedClassAction extends AnAction {

  @Override
  public void update(AnActionEvent e) {
    Optional<PsiClass> component = getNavigatableComponent(e);
    e.getPresentation().setEnabledAndVisible(component.isPresent());
  }

  @Override
  public ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  private static Optional<PsiClass> getNavigatableComponent(AnActionEvent e) {
    final Project project = e.getProject();
    if (project == null) return Optional.empty();

    return Optional.ofNullable(e.getData(CommonDataKeys.PSI_FILE))
        .flatMap(psiFile -> LithoPluginUtils.getFirstClass(psiFile, LithoPluginUtils::isLithoSpec))
        .map(PsiClass::getQualifiedName)
        .map(LithoPluginUtils::getLithoComponentNameFromSpec)
        .map(
            qualifiedComponentName ->
                PsiSearchUtils.getInstance().findOriginalClass(project, qualifiedComponentName))
        // Copied from the GotoDeclarationAction#gotoTargetElement
        .filter(Navigatable::canNavigate);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    getNavigatableComponent(e)
        .map(GoToGeneratedClassAction::log)
        .ifPresent(navigatable -> navigatable.navigate(true));
  }

  private static <T> T log(T navigatable) {
    LithoLoggerProvider.getEventLogger().log(EventLogger.EVENT_GOTO_GENERATED);
    return navigatable;
  }
}
