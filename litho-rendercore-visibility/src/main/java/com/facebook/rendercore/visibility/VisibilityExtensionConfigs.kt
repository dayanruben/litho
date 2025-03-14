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

package com.facebook.rendercore.visibility

/**
 * This class contains mainly flags for features that are either under active development and not
 * ready for public consumption, or for use in experiments.
 *
 * These values are safe defaults and should not require manual changes.
 */
object VisibilityExtensionConfigs {
  const val DEBUG_TAG: String = "VisibilityExtension"
  /** Set this to true to enable debug logs for the visibility extension. */
  @JvmField var isDebugLoggingEnabled: Boolean = false
}
