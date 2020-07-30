/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.dubbo.rpc.cluster.loadbalance;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.RpcStatus;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * LeastActiveLoadBalance
 * 有最小活跃数用最小活跃数，没有最小活跃数根据权重选择，权重一样则随机返回的负载均衡算法。
 * <p>
 * Filter the number of invokers with the least number of active calls and count the weights and quantities of these invokers.
 * If there is only one invoker, use the invoker directly;
 * if there are multiple invokers and the weights are not the same, then random according to the total weight;
 * if there are multiple invokers and the same weight, then randomly called.
 *
 * 过滤器，有效呼叫次数最少调用者的数量并计算这些调用器的重量和数量。
 * 如果只有一个调用，直接使用调用;
 * 如果有多个调用器和权重是不一样的，然后随机根据总重量;
 * 如果有多个调用器和相同的权重，然后随机调用。
 *
 *
 *
 * 1.遍历 invokers 列表，寻找活跃数最小的 Invoker
 *
 * 2.如果有多个 Invoker 具有相同的最小活跃数，此时记录下这些 Invoker 在 invokers 集合中的下标，并累加它们的权重，比较它们的权重值是否相等
 *
 * 3.如果只有一个 Invoker 具有最小的活跃数，此时直接返回该 Invoker 即可
 *
 * 4.如果有多个 Invoker 具有最小活跃数，且它们的权重不相等，此时处理方式和 RandomLoadBalance 一致
 *
 * 5.如果有多个 Invoker 具有最小活跃数，但它们的权重相等，此时随机返回一个即可
 */
public class LeastActiveLoadBalance extends AbstractLoadBalance {

    public static final String NAME = "leastactive";

    @Override
    protected <T> Invoker<T> doSelect(List<Invoker<T>> invokers, URL url, Invocation invocation) {
        // Number of invokers
        // 首先获取服务提供者（下面统称invoker）的数量，比如我们的服务提供者为3台
        int length = invokers.size();
        // The least active value of all invokers
        // 初始化最小活跃数
        // 活跃次数一定是一个正数，这里设置为 -1 代表初始化
        int leastActive = -1;
        // The number of invokers having the same least active value (leastActive)
        // 最小活跃数相同的invoker的数量
        int leastCount = 0;
        // The index of invokers having the same least active value (leastActive)
        // invokers是一个集合 所有 leastIndexes 的作用是用来记录最小活跃数相同的invoker的下标
        int[] leastIndexes = new int[length];
        // the weight of every invokers
        // 权重相同的invoker的下标
        int[] weights = new int[length];
        // The sum of the warmup weights of all the least active invokers
        // 用于记录所有 invoker 的权重之和，比如我们的就是 300+200+100（默认）= 600
        int totalWeight = 0;
        // The weight of the first least active invoker
        // 记录第一个最小活跃数的invoker的权重
        // 在下面的循环中和其他的具有相同的最小活跃数的invoker进行比较
        // 作用是判断是否所有相同最小活跃数的invoker的权重是否都一样
        // 例子： A，B两个服务器权重都配置200，最小活跃数也相同，则随机选择一个。
        int firstWeight = 0;
        // Every least active invoker has the same weight value?
        // 先假设所有的 invoker 的权重都一样
        boolean sameWeight = true;


        // Filter out all the least active invokers
        for (int i = 0; i < length; i++) {
            // 遍历每一个invoker
            Invoker<T> invoker = invokers.get(i);
            // Get the active number of the invoker
            // 读取当前invoker的活跃数
            // 返回 RpcStatus 的 active 是一个 AtomicInteger
            int active = RpcStatus.getStatus(invoker.getUrl(), invocation.getMethodName()).getActive();
            // get the weight of the invoker's configuration. the default value is 100.
            // 获得调用者配置的权重。默认值为100
            int afterWarmup = getWeight(invoker, invocation);
            // save for later use
            weights[i] = afterWarmup;

            // 第一次循环的时候最小活跃数从leastActive=-1变成了leastActive=active（当前invoker的活跃数）
            // 后续缓存的时候，如果有invoker的活跃数比之前记录的leastActive还小，则替换
            // leastCount leastIndexes  totalWeight firstWeight sameWeight都从新开始
            // If it is the first invoker or the active number of the invoker is less than the current least active number
            if (leastActive == -1 || active < leastActive) {
                // Reset the active number of the current invoker to the least active number
                leastActive = active;
                // Reset the number of least active invokers
                leastCount = 1;
                // Put the first least active invoker first in leastIndexes
                leastIndexes[0] = i;
                // Reset totalWeight
                totalWeight = afterWarmup;
                // Record the weight the first least active invoker
                firstWeight = afterWarmup;
                // Each invoke has the same weight (only one invoker here)
                sameWeight = true;
                // If current invoker's active value equals with leaseActive, then accumulating.
            } else if (active == leastActive) {
                // 当前的 invoker 如果和之前记录的最小活跃数相等在，在 leastIndexes 中记录下标
                // Record the index of the least active invoker in leastIndexes order
                leastIndexes[leastCount++] = i;
                // Accumulate the total weight of the least active invoker
                // 更新总权重
                totalWeight += afterWarmup;
                // If every invoker has the same weight?
                // 判断这个具有相同权重最小活跃数的invoker，和之前记录的invoker的权重是否一致
                if (sameWeight && afterWarmup != firstWeight) {
                    sameWeight = false;
                }
            }
        }
        // leastCount = 最小活跃数相同的invoker的数量
        // Choose an invoker from all the least active invokers
        if (leastCount == 1) {
            // If we got exactly one invoker having the least active value, return this invoker directly.
            return invokers.get(leastIndexes[0]);
        }
        // 如果 leastCount 大于1，说明有两个以上的invoker 具有相同的最小活跃数
        // 这种情况就要根据权重进行比较
        if (!sameWeight && totalWeight > 0) {
            // If (not every invoker has the same weight & at least one invoker's weight>0), select randomly based on 
            // totalWeight.
            int offsetWeight = ThreadLocalRandom.current().nextInt(totalWeight);
            // Return a invoker based on the random value.
            // 下面的循环根据权重
            for (int i = 0; i < leastCount; i++) {
                int leastIndex = leastIndexes[i];
                // 让随机权重减去invoker的权重值
                offsetWeight -= weights[leastIndex];
                // 当offsetWeight 减到0后返回当前下标的invoker
                if (offsetWeight < 0) {
                    return invokers.get(leastIndex);
                }
            }
        }
        // 如果有两个以上的权重相同或权重都为0，则返回第一个invoker。
        // If all invokers have the same weight value or totalWeight=0, return evenly.
        return invokers.get(leastIndexes[ThreadLocalRandom.current().nextInt(leastCount)]);
    }
}
