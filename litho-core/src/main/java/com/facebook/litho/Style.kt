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

import com.facebook.kotlin.compilerplugins.dataclassgenerate.annotation.DataClassGenerate

interface StyleItemField

/**
 * Part of a [Style] that can apply an attribute to an underlying Component, e.g. width or click
 * handling.
 */
interface StyleItem<T> {

  val value: T
  val field: StyleItemField

  /** Sets this style item value on the given [CommonProps]. */
  fun applyCommonProps(context: ComponentContext, commonProps: CommonProps) = Unit
}

/**
 * An immutable ordered collection of attributes ( [StyleItem] s) that can be applied to a
 * component, like width or click handling.
 *
 * Adding new attributes to a Style (e.g. by calling `.background`) will return a new Style object.
 * Ordering matters in that the last definition of an attribute 'wins'.
 *
 * Styles can also be added via the `+` operator, with attributes from the right-hand side Style
 * taking precedence if the two define different values for the same attribute, similar to adding
 * maps.
 */
open class Style(
    /**
     * This is the Style we're adding to, e.g. `Style.padding()` when calling `.background()` in
     * `Style.padding().background()`
     */
    private val previousStyle: Style?,

    /**
     * This is the [StyleItem] we're adding, e.g. the background when calling `.background()` in
     * `Style.padding().background()`
     */
    private val item: StyleItem<*>?,
) {

  operator fun plus(other: Style?): Style {
    if (other == null) {
      return this
    }
    return CombinedStyle(if (this === Style) null else this, other)
  }

  inline operator fun plus(nextItem: StyleItem<*>?): Style {
    if (nextItem == null) {
      return this
    }
    return Style(if (this === Style) null else this, nextItem)
  }

  open fun forEach(lambda: (StyleItem<*>) -> Unit) {
    previousStyle?.forEach(lambda)
    if (item != null) {
      lambda(item)
    }
  }

  internal fun applyCommonProps(context: ComponentContext, commonProps: CommonProps) {
    forEach { it.applyCommonProps(context, commonProps) }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) {
      return true
    }
    if (other == null || javaClass != other.javaClass) {
      return false
    }
    other as Style
    return previousStyle == other.previousStyle && item == other.item
  }

  override fun hashCode(): Int {
    var result = previousStyle?.hashCode() ?: 0
    result = 31 * result + (item?.hashCode() ?: 0)
    return result
  }

  /**
   * An empty Style singleton that can be used to build a chain of style items.
   *
   * This is a bit of a trick that lets us make `Style.background()` look like a static call, but
   * actually be a member call since `Style` is now a singleton object. Otherwise we'd need to
   * define both a static `Style.background()` and a member function to support
   * `Style.padding(...).background()`.
   */
  companion object : Style(null, null)
}

/**
 * A subclass of [Style] which combines two Styles, as opposed to adding a single [StyleItem] to an
 * existing Style.
 *
 * A CombinedStyle is created by combining two Styles with `+`. Attributes from the right-hand side
 * Style take precedence if the two define different values for the same attribute, similar to
 * adding maps.
 */
@DataClassGenerate
private data class CombinedStyle(val first: Style?, val second: Style?) : Style(first, null) {

  override fun forEach(lambda: (StyleItem<*>) -> Unit) {
    first?.forEach(lambda)
    second?.forEach(lambda)
  }
}
