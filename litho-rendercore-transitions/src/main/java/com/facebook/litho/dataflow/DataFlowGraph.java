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

package com.facebook.litho.dataflow;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.collection.ArraySet;
import androidx.collection.SimpleArrayMap;
import com.facebook.infer.annotation.Nullsafe;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.concurrent.GuardedBy;

/**
 * A directed acyclic graph (DAG) created from one or more {@link GraphBinding}s. These component
 * GraphBindings define how nodes in this graph are connected to each other: GraphBindings can add
 * nodes and connections when they are 'activated' and can remove nodes and connections when they're
 * deactivated.
 *
 * <p>Data flows through the graph on each frame, from input nodes to output nodes.
 */
@Nullsafe(Nullsafe.Mode.LOCAL)
public class DataFlowGraph {

  private static final String STATE_NOT_INTIALIZED_FOR_VALUE_NODE =
      "DataFlowGraph:StateNotInitializedForValueNode";
  @Nullable private static DataFlowGraph sInstance;

  public static DataFlowGraph getInstance() {
    if (sInstance == null) {
      final ChoreographerTimingSource timingSource = new ChoreographerTimingSource();
      sInstance = new DataFlowGraph(timingSource);
      timingSource.setDataFlowGraph(sInstance);
    }
    return sInstance;
  }

  private static class NodeState {

    private boolean isFinished = false;
    private int refCount = 0;
  }

  /**
   * For tests, let's the testing environment explicitly provide a specific DataFlowGraph instance
   * that can, for example, have a mocked TimingSource.
   */
  @VisibleForTesting
  public static void setInstance(@Nullable DataFlowGraph dataFlowGraph) {
    sInstance = dataFlowGraph;
  }

  @VisibleForTesting
  public static DataFlowGraph create(TimingSource timingSource) {
    DataFlowGraph instance = new DataFlowGraph(timingSource);
    timingSource.setDataFlowGraph(instance);
    return instance;
  }

  @GuardedBy("this")
  private final TimingSource mTimingSource;

  @GuardedBy("this")
  private final Set<GraphBinding> mBindings = new LinkedHashSet<>();

  @GuardedBy("this")
  private final ArrayList<ValueNode> mSortedNodes = new ArrayList<>();

  @GuardedBy("this")
  private final Map<ValueNode, NodeState> mNodeStates = new HashMap<>();

  @GuardedBy("this")
  private boolean mIsFinishingBindings = false;

  @GuardedBy("this")
  private final List<GraphBinding> mBindingsToUnregister = new ArrayList<>();

  @GuardedBy("this")
  private final List<GraphBinding> mBindingsToRegister = new ArrayList<>();

  private boolean mIsDirty = false;

  private DataFlowGraph(TimingSource timingSource) {
    mTimingSource = timingSource;
  }

  /**
   * Adds an activated {@link GraphBinding}. This means that binding's nodes are added to the
   * existing graph and data will flow through them on the next frame.
   */
  public synchronized void register(GraphBinding binding) {
    if (!binding.isActive()) {
      throw new RuntimeException("Expected added GraphBinding to be active: " + binding);
    }
    if (mIsFinishingBindings) {
      mBindingsToRegister.add(binding);
      return;
    }
    mBindings.add(binding);
    registerNodes(binding);
    if (mBindings.size() == 1) {
      mTimingSource.start();
    }
    mIsDirty = true;
  }

  /**
   * Removes a {@link GraphBinding}. This means any nodes that only belonged to that binding will be
   * removed from the graph.
   */
  public synchronized void unregister(GraphBinding binding) {
    if (mIsFinishingBindings) {
      mBindingsToUnregister.add(binding);
      return;
    }
    if (!mBindings.remove(binding)) {
      throw new RuntimeException("Tried to unregister non-existent binding");
    }
    unregisterNodes(binding);
    if (mBindings.isEmpty()) {
      mTimingSource.stop();
      mSortedNodes.clear();
      if (!mNodeStates.isEmpty()) {
        throw new RuntimeException("Failed to clean up all nodes");
      }
    }
    mIsDirty = true;
  }

  synchronized void doFrame(long frameTimeNanos) {
    if (mIsDirty) {
      regenerateSortedNodes();
    }

    propagate(frameTimeNanos);
    updateFinishedStates();
  }

  @GuardedBy("this")
  private void propagate(long frameTimeNanos) {
    final int size = mSortedNodes.size();
    for (int i = 0; i < size; i++) {
      final ValueNode node = mSortedNodes.get(i);
      node.doCalculateValue(frameTimeNanos);
    }
  }

  @GuardedBy("this")
  private void regenerateSortedNodes() {
    mSortedNodes.clear();

    if (mBindings.size() == 0) {
      return;
    }

    final ArraySet<ValueNode> leafNodes = new ArraySet<>();
    final SimpleArrayMap<ValueNode, Integer> nodesToOutputsLeft = new SimpleArrayMap<>();
    final ArrayDeque<ValueNode> nodesToProcess = new ArrayDeque<>();

    for (final GraphBinding binding : mBindings) {
      final ArrayList<ValueNode> nodes = binding.getAllNodes();
      for (int j = 0, nodesSize = nodes.size(); j < nodesSize; j++) {
        final ValueNode node = nodes.get(j);
        final int outputCount = node.getOutputCount();
        if (outputCount == 0) {
          if (!leafNodes.contains(node)) {
            nodesToProcess.add(node);
            leafNodes.add(node);
          }
        } else {
          nodesToOutputsLeft.put(node, outputCount);
        }
      }
    }

    if (!nodesToOutputsLeft.isEmpty() && leafNodes.isEmpty()) {
      throw new DetectedCycleException(
          "Graph has nodes, but they represent a cycle with no leaf nodes!");
    }

    while (!nodesToProcess.isEmpty()) {
      final ValueNode next = nodesToProcess.pollFirst();
      mSortedNodes.add(next);
      // NULLSAFE_FIXME[Nullable Dereference]
      for (ValueNode input : next.getAllInputs()) {
        // NULLSAFE_FIXME[Nullable Dereference]
        final int outputsLeft = nodesToOutputsLeft.get(input) - 1;
        nodesToOutputsLeft.put(input, outputsLeft);
        if (outputsLeft == 0) {
          nodesToProcess.addLast(input);
        } else if (outputsLeft < 0) {
          throw new DetectedCycleException("Detected cycle.");
        }
      }
    }

    int expectedTotalNodes = nodesToOutputsLeft.size() + leafNodes.size();
    if (mSortedNodes.size() != expectedTotalNodes) {
      throw new DetectedCycleException(
          "Had unreachable nodes in graph -- this likely means there was a cycle");
    }

    Collections.reverse(mSortedNodes);
    mIsDirty = false;
  }

  @GuardedBy("this")
  private void updateFinishedStates() {
    updateFinishedNodes();
    notifyFinishedBindings();
  }

  @GuardedBy("this")
  private void updateFinishedNodes() {
    for (int i = 0, size = mSortedNodes.size(); i < size; i++) {
      final ValueNode node = mSortedNodes.get(i);
      final NodeState nodeState = mNodeStates.get(node);
      if (nodeState == null || nodeState.isFinished || !areInputsFinished(node)) {
        continue;
      }

      final boolean nodeIsNowFinished =
          !(node instanceof NodeCanFinish) || ((NodeCanFinish) node).isFinished();
      if (nodeIsNowFinished) {
        nodeState.isFinished = true;
      }
    }
  }

  @GuardedBy("this")
  private boolean areInputsFinished(ValueNode node) {
    for (ValueNode input : node.getAllInputs()) {
      final NodeState nodeState = mNodeStates.get(input);
      // NULLSAFE_FIXME[Nullable Dereference]
      if (!nodeState.isFinished) {
        return false;
      }
    }
    return true;
  }

  @GuardedBy("this")
  private void notifyFinishedBindings() {
    // We need loop the graph bindings and flag the ones we need to add/remove so that we don't
    // change the list while iterating.
    mIsFinishingBindings = true;
    for (final GraphBinding binding : mBindings) {
      boolean allAreFinished = true;
      final ArrayList<ValueNode> nodesToCheck = binding.getAllNodes();
      for (int j = 0, nodesSize = nodesToCheck.size(); j < nodesSize; j++) {
        final NodeState nodeState = mNodeStates.get(nodesToCheck.get(j));
        // NULLSAFE_FIXME[Nullable Dereference]
        if (!nodeState.isFinished) {
          allAreFinished = false;
          break;
        }
      }
      if (allAreFinished) {
        binding.notifyNodesHaveFinished();
      }
    }
    mIsFinishingBindings = false;
    for (final GraphBinding binding : mBindingsToRegister) {
      register(binding);
    }
    for (final GraphBinding binding : mBindingsToUnregister) {
      unregister(binding);
    }
    mBindingsToRegister.clear();
    mBindingsToUnregister.clear();
  }

  @GuardedBy("this")
  private void registerNodes(GraphBinding binding) {
    final ArrayList<ValueNode> nodes = binding.getAllNodes();
    for (int i = 0, size = nodes.size(); i < size; i++) {
      final ValueNode node = nodes.get(i);
      final NodeState nodeState = mNodeStates.get(node);
      if (nodeState != null) {
        nodeState.refCount++;
      } else {
        final NodeState newState = new NodeState();
        newState.refCount = 1;
        mNodeStates.put(node, newState);
      }
    }
  }

  @GuardedBy("this")
  private void unregisterNodes(GraphBinding binding) {
    final ArrayList<ValueNode> nodes = binding.getAllNodes();
    for (int i = 0, size = nodes.size(); i < size; i++) {
      final ValueNode node = nodes.get(i);
      final NodeState nodeState = mNodeStates.get(node);
      // NULLSAFE_FIXME[Nullable Dereference]
      nodeState.refCount--;
      // NULLSAFE_FIXME[Nullable Dereference]
      if (nodeState.refCount == 0) {
        mNodeStates.remove(node);
      }
    }
  }

  @VisibleForTesting
  @GuardedBy("this")
  boolean hasReferencesToNodes() {
    return !mBindings.isEmpty() || !mSortedNodes.isEmpty() || !mNodeStates.isEmpty();
  }
}
