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

package com.facebook.litho.intellij.logging;

import com.facebook.infer.annotation.Nullsafe;
import com.facebook.litho.intellij.extensions.EventLogger;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Provides logger to track common user flow events: completion action usage, dialog opening, etc.
 */
@Nullsafe(Nullsafe.Mode.LOCAL)
public final class LithoLoggerProvider {

  private LithoLoggerProvider() {}

  public static EventLogger getEventLogger() {
    return LithoEventLogger.INSTANCE;
  }

  static class LithoEventLogger implements EventLogger {
    static final ExtensionPointName<EventLogger> EP_NAME =
        ExtensionPointName.create("com.facebook.litho.intellij.eventLogger");
    private final EventLogger[] loggers;
    private final Executor executor;
    static final EventLogger INSTANCE = new LithoEventLogger();

    LithoEventLogger() {
      this(Extensions.getExtensions(EP_NAME));
    }

    @VisibleForTesting
    LithoEventLogger(EventLogger[] loggers) {
      this(loggers, action -> ApplicationManager.getApplication().executeOnPooledThread(action));
    }

    @VisibleForTesting
    LithoEventLogger(EventLogger[] loggers, Executor executor) {
      this.loggers = loggers;
      this.executor = executor;
    }

    @Override
    public void log(String event, Map<String, String> metadata) {
      executor.execute(
          () -> Arrays.stream(loggers).forEach(eventLogger -> eventLogger.log(event, metadata)));
    }
  }
}
