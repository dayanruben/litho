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

package com.facebook.samples.litho.kotlin.collection

import com.facebook.litho.ClickEvent
import com.facebook.litho.Component
import com.facebook.litho.ComponentScope
import com.facebook.litho.KComponent
import com.facebook.litho.Style
import com.facebook.litho.kotlin.widget.Text
import com.facebook.litho.useCallback
import com.facebook.litho.view.onClick
import com.facebook.litho.widget.collection.LazyList
import com.facebook.litho.widget.collection.useLazyCollectionController

// start_scrolling_example
class ScrollingExample() : KComponent() {

  override fun ComponentScope.render(): Component {
    val controller = useLazyCollectionController()

    // Use one of these lambdas to scroll, e.g. in an onClick callback
    val scrollToTenth = useCallback<ClickEvent, Unit> { controller.scrollToIndex(index = 10) }
    val smoothScrollToEnd = useCallback<ClickEvent, Unit> { controller.smoothScrollToId("End") }

    return LazyList(
        lazyCollectionController = controller,
    ) {
      child(Text(style = Style.onClick(scrollToTenth), text = "Scroll to item 10"))
      child(Text(style = Style.onClick(smoothScrollToEnd), text = "Smooth Scroll to End"))
      children(items = (0..20), id = { it }) { Text("$it") }
      child(id = "End", component = Text("End"))
    }
  }
}

// end_scrolling_example

// start_pull_to_refresh_example
class PullToRefreshExample(
    val data: List<String>,
    val refresh: () -> Unit,
) : KComponent() {

  override fun ComponentScope.render(): Component {
    val controller = useLazyCollectionController()
    return LazyList(
        lazyCollectionController = controller,
        onPullToRefresh = {
          refresh()
          controller.setRefreshing(false)
        },
    ) { /* Add children */
    }
  }
}
// end_pull_to_refresh_example
