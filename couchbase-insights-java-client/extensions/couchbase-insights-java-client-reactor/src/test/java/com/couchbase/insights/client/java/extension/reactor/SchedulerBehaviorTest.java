package com.couchbase.insights.client.java.extension.reactor;

import org.junit.jupiter.api.Test;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.NoSuchElementException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Raises the alarm if Reactor scheduler behavior changes.
 */
class SchedulerBehaviorTest {
  static boolean isNonBlocking(Scheduler scheduler) {
    return Mono.fromCallable(Schedulers::isInNonBlockingThread)
      .subscribeOn(scheduler)
      .blockOptional().orElseThrow(NoSuchElementException::new);
  }

  @Test
  void cancellationInterruptsSchedulerThread() {
    ExecutorService executorService = Executors.newCachedThreadPool();
    Scheduler fromExecutor = Schedulers.fromExecutor(executorService);
    Scheduler boundedElastic = Schedulers.newBoundedElastic(10, 10, "text-scheduler");

    try {
      assertCancellationInterruptsThread(fromExecutor);
      assertCancellationInterruptsThread(boundedElastic);
    } finally {
      boundedElastic.dispose();
      fromExecutor.dispose();
      executorService.shutdownNow();
    }
  }

  private static void assertCancellationInterruptsThread(Scheduler scheduler) {
    assertFalse(isNonBlocking(scheduler), "scheduler is non-blocking");

    Duration probeTimeout = Duration.ofSeconds(15);

    CountDownLatch scheduledLatch = new CountDownLatch(1);
    CountDownLatch interruptedLatch = new CountDownLatch(1);

    Disposable subscription = Mono.fromRunnable(() -> {
        scheduledLatch.countDown();
        try {
          //noinspection BlockingMethodInNonBlockingContext (we already verified the scheduler supports blocking)
          MILLISECONDS.sleep(probeTimeout.multipliedBy(2).toMillis());

        } catch (InterruptedException expected) {
          Thread.currentThread().interrupt();
          interruptedLatch.countDown();
        }
      })
      .subscribeOn(scheduler)
      .subscribe();

    try {
      // Wait for task to be scheduled before canceling it, otherwise it never executes at all!
      if (scheduledLatch.await(probeTimeout.toMillis(), MILLISECONDS)) {
        subscription.dispose();
      } else {
        fail("Cancellation probe task did not start executing within " + probeTimeout + " on scheduler " + scheduler);
      }

      if (interruptedLatch.await(probeTimeout.toMillis(), MILLISECONDS)) {
        return;
      }

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(e);
    }

    fail("Thread is not interrupted when task is cancelled (waited for " + probeTimeout + ") on scheduler " + scheduler);
  }
}
