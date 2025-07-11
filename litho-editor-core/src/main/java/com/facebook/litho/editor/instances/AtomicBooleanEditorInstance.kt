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

package com.facebook.litho.editor.instances

import com.facebook.litho.editor.Editor
import com.facebook.litho.editor.Reflection.getValueUNSAFE
import com.facebook.litho.editor.Reflection.setValueUNSAFE
import com.facebook.litho.editor.model.EditorBool
import com.facebook.litho.editor.model.EditorValue
import com.facebook.litho.editor.model.EditorValue.DefaultEditorVisitor
import java.lang.reflect.Field
import java.util.concurrent.atomic.AtomicBoolean

class AtomicBooleanEditorInstance : Editor {
  override fun read(f: Field, node: Any?): EditorValue {
    val atomicBoolean = getValueUNSAFE<AtomicBoolean>(f, node)
    return if (atomicBoolean == null) EditorValue.string("null")
    else EditorValue.bool(atomicBoolean.get())
  }

  override fun write(f: Field, node: Any?, values: EditorValue): Boolean {
    values.`when`(
        object : DefaultEditorVisitor() {
          override fun isBool(bool: EditorBool): Void? {
            setValueUNSAFE(f, node, AtomicBoolean(bool.value))
            return null
          }
        })
    return true
  }
}
