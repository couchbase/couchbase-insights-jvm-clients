/*
 * Copyright 2025 Couchbase, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.couchbase.insights.client.java.extension.reactor;

import org.jspecify.annotations.Nullable;

import java.util.AbstractQueue;
import java.util.Iterator;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;

/**
 * Delegates to a BlockingQueue.
 * <p>
 * Offering an item blocks until there is space in the blocking queue
 * or the calling thread is interrupted.
 */
class BlockingBuffer<T> extends AbstractQueue<T> {
  private final BlockingQueue<T> queue;

  BlockingBuffer(int capacity) {
    this.queue = new ArrayBlockingQueue<>(capacity);
  }

  @Override
  public Iterator<T> iterator() {
    return queue.iterator();
  }

  @Override
  public int size() {
    return queue.size();
  }

  @Override
  public boolean offer(T t) {
    try {
      queue.put(t);
      return true;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new CancellationException("Interrupted while offering item.");
    }
  }

  @Override
  public @Nullable T poll() {
    return queue.poll();
  }

  @Override
  public @Nullable T peek() {
    return queue.peek();
  }
}
