/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
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
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.agent.core.sampling;

import org.skywalking.apm.agent.core.boot.BootService;
import org.skywalking.apm.agent.core.boot.DefaultNamedThreadFactory;
import org.skywalking.apm.agent.core.conf.Config;
import org.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.skywalking.apm.agent.core.logging.api.ILog;
import org.skywalking.apm.agent.core.logging.api.LogManager;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 抽样服务
 *
 * The <code>SamplingService</code> take charge of how to sample the {@link TraceSegment}. Every {@link TraceSegment}s
 * have been traced, but, considering CPU cost of serialization/deserialization, and network bandwidth, the agent do NOT
 * send all of them to collector, if SAMPLING is on.
 * <p>
 * By default, SAMPLING is on, and {@see {@link Config.Agent#SAMPLE_N_PER_3_SECS }}
 *
 * @author wusheng
 */
public class SamplingService implements BootService {
    private static final ILog logger = LogManager.getLogger(SamplingService.class);

    /**
     * 是否开启
     */
    private volatile boolean on = false;
    /**
     * 抽样计数器
     */
    private volatile AtomicInteger samplingFactorHolder;
    /**
     * 定时任务
     */
    private volatile ScheduledFuture<?> scheduledFuture;

    @Override
    public void beforeBoot() throws Throwable {

    }

    @Override
    public void boot() throws Throwable {
        // 若原有定时任务存在，先进行关闭
        if (scheduledFuture != null) {
            /**
             * If {@link #boot()} invokes twice, mostly in test cases,
             * cancel the old one.
             */
            scheduledFuture.cancel(true);
        }
        if (Config.Agent.SAMPLE_N_PER_3_SECS > 0) {
            on = true;
            // 重置
            this.resetSamplingFactor();
            // 创建定时任务，定时重置
            ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor(new DefaultNamedThreadFactory("SamplingService"));
            scheduledFuture = service.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    resetSamplingFactor();
                }
            }, 0, 3, TimeUnit.SECONDS);
            logger.debug("Agent sampling mechanism started. Sample {} traces in 10 seconds.", Config.Agent.SAMPLE_N_PER_3_SECS);
        }
    }

    @Override
    public void afterBoot() throws Throwable {

    }

    @Override
    public void shutdown() throws Throwable {
        scheduledFuture.cancel(true);
    }

    /**
     * @return true, if sampling mechanism is on, and getDefault the sampling factor successfully.
     */
    public boolean trySampling() {
        if (on) {
            int factor = samplingFactorHolder.get();
            if (factor < Config.Agent.SAMPLE_N_PER_3_SECS) {
                boolean success = samplingFactorHolder.compareAndSet(factor, factor + 1);
                return success;
            } else {
                return false;
            }
        }
        return true;
    }

    /**
     * Increase the sampling factor by force,
     * to avoid sampling too many traces.
     * If many distributed traces require sampled,
     * the trace beginning at local, has less chance to be sampled.
     */
    public void forceSampled() {
        if (on) {
            samplingFactorHolder.incrementAndGet();
        }
    }

    private void resetSamplingFactor() {
        samplingFactorHolder = new AtomicInteger(0);
    }
}
