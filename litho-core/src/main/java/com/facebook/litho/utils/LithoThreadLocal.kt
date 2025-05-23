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

package com.facebook.litho.utils

import com.facebook.litho.ThreadUtils

/**
 * This is similar to a [ThreadLocal] but has lower overhead because it avoids a weak reference.
 * This is especially preferred for writes that are delimited by a try...finally call which will
 * clean up the reference and avoid it potentially getting pinned by the thread local causing a
 * leak.
 *
 * Implementation copied and modified from [AOSP](https://fburl.com/ug9ubgka)
 */
internal class LithoThreadLocal<T> {
  @Volatile private var map = emptyThreadMap
  private val writeMutex = Any()

  private var mainThreadValue: T? = null
  private var defaultLayoutThreadValue: T? = null

  @Suppress("UNCHECKED_CAST")
  fun get(): T? {
    return when {
      ThreadUtils.isMainThread -> mainThreadValue
      ThreadUtils.isDefaultLayoutThread -> defaultLayoutThreadValue
      else -> map.get(Thread.currentThread().id) as T?
    }
  }

  fun set(value: T?) {
    when {
      ThreadUtils.isMainThread -> mainThreadValue = value
      ThreadUtils.isDefaultLayoutThread -> defaultLayoutThreadValue = value
      else ->
          synchronized(writeMutex) {
            val current = map
            val key = Thread.currentThread().id
            if (current.trySet(key, value)) return
            map = current.newWith(key, value)
          }
    }
  }
}

internal inline fun <T : Any> LithoThreadLocal<T>.getOrSet(default: () -> T): T {
  return get() ?: default().also(::set)
}

internal class ThreadMap(
    private val size: Int,
    private val keys: LongArray,
    private val values: Array<Any?>
) {
  fun get(key: Long): Any? {
    val index = find(key)
    return if (index >= 0) values[index] else null
  }

  /**
   * Set the value if it is already in the map. Otherwise a new map must be allocated to contain the
   * new entry.
   */
  fun trySet(key: Long, value: Any?): Boolean {
    val index = find(key)
    if (index < 0) return false
    values[index] = value
    return true
  }

  fun newWith(key: Long, value: Any?): ThreadMap {
    val newSize = values.count { it != null } + 1
    val newKeys = LongArray(newSize)
    val newValues = arrayOfNulls<Any?>(newSize)
    if (newSize > 1) {
      var dest = 0
      var source = 0
      while (dest < newSize && source < size) {
        val oldKey = keys[source]
        val oldValue = values[source]
        if (oldKey > key) {
          newKeys[dest] = key
          newValues[dest] = value
          dest++
          // Continue with a loop without this check
          break
        }
        if (oldValue != null) {
          newKeys[dest] = oldKey
          newValues[dest] = oldValue
          dest++
        }
        source++
      }
      if (source == size) {
        // Appending a value to the end.
        newKeys[newSize - 1] = key
        newValues[newSize - 1] = value
      } else {
        while (dest < newSize) {
          val oldKey = keys[source]
          val oldValue = values[source]
          if (oldValue != null) {
            newKeys[dest] = oldKey
            newValues[dest] = oldValue
            dest++
          }
          source++
        }
      }
    } else {
      // The only element
      newKeys[0] = key
      newValues[0] = value
    }
    return ThreadMap(newSize, newKeys, newValues)
  }

  private fun find(key: Long): Int {
    var high = size - 1
    when (high) {
      -1 -> return -1
      0 -> return if (keys[0] == key) 0 else if (keys[0] > key) -2 else -1
    }
    var low = 0

    while (low <= high) {
      val mid = (low + high).ushr(1)
      val midVal = keys[mid]
      val comparison = midVal - key
      when {
        comparison < 0 -> low = mid + 1
        comparison > 0 -> high = mid - 1
        else -> return mid
      }
    }
    return -(low + 1)
  }
}

private val emptyThreadMap = ThreadMap(0, LongArray(0), emptyArray())
