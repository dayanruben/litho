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

package com.facebook.litho

import com.facebook.rendercore.PoolScope

/**
 * An internal [TreeProp] that allows to provide a custom [PoolScope] to the components in a Litho
 * hierarchy.
 */
@PublishedApi
@JvmField
internal val PoolScopeTreeProp: TreeProp<PoolScope> = treePropOf { PoolScope.None }
