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

package com.facebook.rendercore

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import java.util.Locale
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ResourceCacheTest {

  @Test
  fun testSameConfigurationDoesNotUpdateResourceCache() {
    val configuration = ApplicationProvider.getApplicationContext<Context>().resources.configuration
    val cache = ResourceCache.getLatest(configuration)
    assertThat(cache).isEqualTo(ResourceCache.getLatest(configuration))
  }

  @Test
  fun testSameConfigurationNewInstanceDoesNotUpdateResourceCache() {
    val configuration = ApplicationProvider.getApplicationContext<Context>().resources.configuration
    val cache = ResourceCache.getLatest(configuration)
    assertThat(cache).isEqualTo(ResourceCache.getLatest(Configuration(configuration)))
  }

  @Suppress("AppBundleLocaleChanges")
  @Test
  fun testDifferentLocaleUpdatesResourceCache() {
    val configuration =
        Configuration(ApplicationProvider.getApplicationContext<Context>().resources.configuration)
    configuration.setLocale(Locale("en"))
    val cache = ResourceCache.getLatest(configuration)
    configuration.setLocale(Locale("it"))
    assertThat(cache).isNotEqualTo(ResourceCache.getLatest(configuration))
  }
}
