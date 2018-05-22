/*
 * Copyright (C) 2013 salesforce.com, inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.auraframework.integration.test.util;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestResult;
import org.auraframework.test.util.WebDriverProvider;
import org.auraframework.util.ServiceLocator;
import org.auraframework.util.test.annotation.ThreadHostileTest;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;

/**
 * This executor handles the execution of test cases
 */
public class TestExecutor {
    public static final int NUM_THREADS = Integer.parseInt(System.getProperty("testThreadCount", "4"));
    private static final Logger logger = Logger.getLogger("TestExecutor");
    private final ExecutorService executor;

    /**
     * Keep track of the number of pending/running tasks in the executor.
     */
    private final AtomicLong taskCount = new AtomicLong(0);

    private TestExecutor(int coreSize, int maxSize) {
        LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
        executor = new ThreadPoolExecutor(coreSize, maxSize, 3, TimeUnit.SECONDS, queue);
    }

    /**
     * Enqueue a task on the executor.
     */
    public synchronized Future<TestResult> submit(final TestRun c) {
        // After running the given task, check to see if the executor is empty.
        Callable<TestResult> wrapped = new Callable<TestResult>() {
            @Override
            public TestResult call() throws Exception {
                try {
                    return c.call();
                } finally {
                    if (taskCount.decrementAndGet() == 0) {
                        onExecutorEmpty();
                    }
                }
            }
        };
        taskCount.incrementAndGet();
        return executor.submit(wrapped);
    }

    /**
     * Called when the executor transitions from the active into the empty state. Synchronized on this so that no new
     * tasks may submit while we are running cleanup code.
     */
    private synchronized void onExecutorEmpty() {
        WebDriverProvider provider = ServiceLocator.get().get(WebDriverProvider.class);
        if (provider != null) {
            provider.release();
        }
    }

    /**
     * @return true if the executor is currently active (not empty).
     */
    public synchronized boolean isActive() {
        return taskCount.get() > 0;
    }

    /**
     * A helper to enable lazy static initialization of the {@link TestExecutor}.
     */
    private static class TestExecutorHolder {
        private static final TestExecutor INSTANCE;
        static {
            // default number of threads to the number of processing cores
            INSTANCE = new TestExecutor(NUM_THREADS, NUM_THREADS);
        }
    }

    /**
     * @return the singleton {@link TestExecutor} instance.
     */
    public static TestExecutor getInstance() {
        return TestExecutorHolder.INSTANCE;
    }

    /**
     * A {@link Callable} adapter to schedule a test for execution.
     */
    public static class TestRun implements Callable<TestResult> {
        protected final Test test;
        protected final TestResult result;

        /**
         * This lock ensures that a {@link ThreadHostileTest} is not run concurrently with any other tests because it
         * must obtain the write lock.
         */
        private static final ReadWriteLock globalStateLock = new ReentrantReadWriteLock();

        public TestRun(Test test, TestResult result) {
            assert (test != null) : "null test";
            this.test = test;
            this.result = (result != null) ? result : new TestResult();
        }

        @Override
        public TestResult call() throws Exception {
            Lock lock;
            if (isThreadHostile(test)) {
                // Thread hostile tests need to run alone and therefore require the write lock.
                lock = globalStateLock.writeLock();
            } else {
                // Regular tests can run concurrently
                lock = globalStateLock.readLock();
            }

            // Run the test.
            lock.lock();
            long start = System.nanoTime();
            try {
                test.run(result);
                return result;
            } finally {
                lock.unlock();
                if (test instanceof TestCase) {
                    logger.info(String.format("Finished: %s.%s {elapsed=%sms}", test.getClass().getName(),
                            ((TestCase) test).getName(), ((System.nanoTime() - start) / 1000000)));
                }
            }
        }
    }

    public static boolean isThreadHostile(Test test) {
        Class<?> testClass = test.getClass();
        if (testClass.isAnnotationPresent(ThreadHostileTest.class)) {
            return true;
        }
        if (test instanceof WebDriverTestCase) {
            if (((WebDriverTestCase) test).getTestLabels().contains("threadHostile")) {
                return true;
            }
        }
        try {
            return testClass.getMethod(((TestCase) test).getName()).isAnnotationPresent(ThreadHostileTest.class);
        } catch (Throwable t) {
            return false;
        }
    }
}
