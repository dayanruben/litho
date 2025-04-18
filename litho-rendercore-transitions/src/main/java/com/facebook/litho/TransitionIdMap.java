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

package com.facebook.litho;

import com.facebook.infer.annotation.Nullsafe;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * An object that maps {@link TransitionId}s to values. It keeps track of TransitionId types, thus
 * allowing efficient look for keys of a particular type.
 *
 * <p>This is important for {@link TransitionManager} that needs to map {@link TransitionId}s to
 * {@link TransitionManager.AnimationState}s, and quickly find a value when creating transition for
 * the given {@link Transition.TransitionUnit} - {@link
 * TransitionManager#createAnimationsForTransitionUnit(Transition.TransitionUnit)}
 */
@Nullsafe(Nullsafe.Mode.LOCAL)
class TransitionIdMap<V> {
  private final Map<String, TransitionId> mGlobalIds = new LinkedHashMap<>();
  private final Map<String, Map<String, TransitionId>> mScopedIdsByOwner = new LinkedHashMap<>();
  private final Map<String, TransitionId> mAutogeneratedIds = new LinkedHashMap<>();

  private final Map<TransitionId, V> mIds = new HashMap<>();

  /**
   * Checks if this map contains a mapping for the specified {@link TransitionId}.
   *
   * @param id {@link TransitionId} whose presence in this map is to be tested
   * @return {@code true} if this map contains a mapping for the specified key
   */
  boolean contains(TransitionId id) {
    return mIds.containsKey(id);
  }

  /**
   * Associates the specified value with the specified {@link TransitionId} in this map. If the map
   * previously contained a mapping for the key, the old value is replaced by the specified value.
   *
   * @param id {@link TransitionId} with which the specified value is to be associated
   * @param value value to be associated with the specified {@link TransitionId}
   */
  void put(TransitionId id, V value) {
    if (mIds.put(id, value) == null) {
      // New transition id for this set
      switch (id.mType) {
        case TransitionId.Type.GLOBAL:
          mGlobalIds.put(id.mReference, id);
          break;

        case TransitionId.Type.SCOPED:
          final String owner = id.mExtraData;
          Map<String, TransitionId> siblingsIds = mScopedIdsByOwner.get(owner);
          if (siblingsIds == null) {
            siblingsIds = new LinkedHashMap<>();
            mScopedIdsByOwner.put(owner, siblingsIds);
          }
          siblingsIds.put(id.mReference, id);
          break;

        case TransitionId.Type.AUTOGENERATED:
          mAutogeneratedIds.put(id.mReference, id);
          break;

        default:
          throw new RuntimeException("Unknown TransitionId type " + id.mType);
      }
    }
  }

  /**
   * Returns the value to which the specified {@link TransitionId} is mapped, or {@code null} if
   * this map contains no mapping for the key.
   *
   * @see #put(TransitionId, Object)
   */
  @Nullable
  V get(@Nullable TransitionId id) {
    return mIds.get(id);
  }

  /**
   * Removes the mapping for the specified {@link TransitionId} from this map if present.
   *
   * @param id {@link TransitionId} whose mapping is to be removed from the map
   */
  void remove(TransitionId id) {
    if (mIds.remove(id) == null) {
      // There is no mapping for the id, return early
      return;
    }

    switch (id.mType) {
      case TransitionId.Type.GLOBAL:
        mGlobalIds.remove(id.mReference);
        break;

      case TransitionId.Type.SCOPED:
        final String owner = id.mExtraData;
        Map<String, TransitionId> siblingsIds = mScopedIdsByOwner.get(owner);
        // NULLSAFE_FIXME[Nullable Dereference]
        siblingsIds.remove(id.mReference);
        // NULLSAFE_FIXME[Nullable Dereference]
        if (siblingsIds.isEmpty()) {
          mScopedIdsByOwner.remove(owner);
        }
        break;

      case TransitionId.Type.AUTOGENERATED:
        mAutogeneratedIds.remove(id.mReference);
        break;
    }
  }

  /**
   * Returns a {@link TransitionId} of type {@link TransitionId.Type.GLOBAL} with the specified
   * reference if this map contains mapping for such {@link TransitionId}, or {@code null}
   * otherwise.
   */
  @Nullable
  TransitionId getGlobalId(String reference) {
    return mGlobalIds.get(reference);
  }

  /**
   * Returns a {@link TransitionId} of type {@link TransitionId.Type.SCOPED} with the specified
   * owner and reference if this map contains mapping for such {@link TransitionId}, or {@code null}
   * otherwise.
   */
  @Nullable
  TransitionId getScopedId(String owner, String reference) {
    final Map<String, TransitionId> siblingsIds = mScopedIdsByOwner.get(owner);
    return siblingsIds != null ? siblingsIds.get(reference) : null;
  }

  /**
   * Returns a {@link Set} view of the {@link TransitionId}s mappings for which are contained in
   * this map.
   */
  Set<TransitionId> ids() {
    return mIds.keySet();
  }

  /** Returns a {@link Collection} view of the values contained in this map. */
  Collection<V> values() {
    return mIds.values();
  }

  /** Removes all of the mappings from this map. */
  void clear() {
    mGlobalIds.clear();
    mScopedIdsByOwner.clear();
    mAutogeneratedIds.clear();

    mIds.clear();
  }
}
