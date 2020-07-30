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
import org.apache.dubbo.rpc.support.RpcUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.apache.dubbo.common.constants.CommonConstants.COMMA_SPLIT_PATTERN;

/**
 * ConsistentHashLoadBalance
 */
public class ConsistentHashLoadBalance extends AbstractLoadBalance {
    public static final String NAME = "consistenthash";

    /**
     * Hash nodes name
     */
    public static final String HASH_NODES = "hash.nodes";

    /**
     * Hash arguments name
     */
    public static final String HASH_ARGUMENTS = "hash.arguments";

    private final ConcurrentMap<String, ConsistentHashSelector<?>> selectors = new ConcurrentHashMap<String, ConsistentHashSelector<?>>();

    @SuppressWarnings("unchecked")
    @Override
    protected <T> Invoker<T> doSelect(List<Invoker<T>> invokers, URL url, Invocation invocation) {
        String methodName = RpcUtils.getMethodName(invocation);
        String key = invokers.get(0).getUrl().getServiceKey() + "." + methodName;
        System.out.println("从selectors中获取value的Key=" + key);
        // using the hashcode of list to compute the hash only pay attention to the elements in the list
        // 获取 invoker 的 hashcode
        int invokersHashCode = invokers.hashCode();
        ConsistentHashSelector<T> selector = (ConsistentHashSelector<T>) selectors.get(key);
        // 如果 invokers 是一个新的List对象，意味着服务提供者数据发生了变化。可能新增也可能减少了
        // 此时selector.identityHashCode != invokersHashCode 成立
        // 如果是第一次调用此时 selector == null 条件成立
        if (selector == null || selector.identityHashCode != invokersHashCode) {
            System.out.println("是新的invokers:" + invokersHashCode + ", 原：" + (selector == null ? "null" : selector.identityHashCode));
            selectors.put(key, new ConsistentHashSelector<T>(invokers, methodName, invokersHashCode));
            selector = (ConsistentHashSelector<T>) selectors.get(key);
        }

        System.out.println("哈希环境构建完成，详情如下：");
        for (Map.Entry<Long, Invoker<T>> entry : selector.virtualInvokers.entrySet()) {
            System.out.println("key(hash)=" + entry.getKey() + ", value(虚拟节点)=" + entry.getValue());
        }
        // 调用 ConsistentHashSelector 的 select 方法选择 invoker
        System.out.println("开始调用 ConsistentHashSelector 的 select 方法选择 invoker");
        return selector.select(invocation);
    }

    private static final class ConsistentHashSelector<T> {
        // 使用 TreeMap存储 invoker 的虚拟节点
        private final TreeMap<Long, Invoker<T>> virtualInvokers;
        // 虚拟节点数
        private final int replicaNumber;
        // hashcode
        private final int identityHashCode;
        // 请求中的参数下标
        // 需要对请求中对应下标的参数进行哈希计算
        private final int[] argumentIndex;

        ConsistentHashSelector(List<Invoker<T>> invokers, String methodName, int identityHashCode) {
            this.virtualInvokers = new TreeMap<Long, Invoker<T>>();
            this.identityHashCode = identityHashCode;
            URL url = invokers.get(0).getUrl();
            System.out.println("CHS中URL为:" + url);
            // 即使启动了多个 invoker 每个invoker 对应的 URL 上的虚拟节点配置都是一样
            // 这个默认是 160 个 示例代码设置为4个，方便查看
            this.replicaNumber = url.getMethodParameter(methodName, HASH_NODES, 160);
            System.out.println("CHS中URL的【hashcode】为=" + this.replicaNumber);
            // 获取参与哈希计算的参数下标值，默认对第一个参数进行哈希计算
            // 参考示例代码使用默认配置，所以这里的 index = 1
            String[] index = COMMA_SPLIT_PATTERN.split(url.getMethodParameter(methodName, HASH_ARGUMENTS, "0"));
            System.out.println("CHS中URL的【hash.arguments】为=" + Arrays.toString(index));
            // for 循环 对 argumentIndex 进行赋值操作
            argumentIndex = new int[index.length];
            for (int i = 0; i < index.length; i++) {
                argumentIndex[i] = Integer.parseInt(index[i]);
            }
            System.out.println("CHS中argumentIndex数组为=" + Arrays.toString(argumentIndex));
            // 参数实例中启动了2个服务提供者，所以 invokers是2
            for (Invoker<T> invoker : invokers) {
                // 获取每一个 invoker 的地址
                String address = invoker.getUrl().getAddress();
                System.out.println("CHS中invoker的地址为="+address);
                for (int i = 0; i < replicaNumber / 4; i++) {
                    // 对 address + i 进行 MD5 运算得到一个长度为16的字节数组
                    byte[] digest = md5(address + i);
                    System.out.println("CHS中对" +address + i + "进行MD5计算");
                    // 对 digest 部分字节进行4次hash操作得到四个不同的long型正整数
                    for (int h = 0; h < 4; h++) {
                        // h=0时，取 digest中下标为 0~3的4个字节进行位运算
                        // h=1时，取 digest中下标为 4~7的4个字节进行位运算
                        // h=2 3 流程同上
                        long m = hash(digest, h);
                        System.out.println("CHS中对digest进行第" + h + "次Hash计算，计算会值=" + m + "，当前invoker=" + invoker);
                        // 将hash到invoker的映射关系储存到virtualInvokers中
                        // virtualInvokers需要提供高效的查询操作,因此选用TreeMap作为储存结构
                        virtualInvokers.put(m, invoker);
                    }
                }
            }
        }

        public Invoker<T> select(Invocation invocation) {
            String key = toKey(invocation.getArguments());
            System.out.println("CHS的select方法根据argumentIndex取出invocation中hash计算的key=" + key);
            byte[] digest = md5(key);
            // 取 digest 数组中前四个字节进行 hash 计算，再将 hash 值传给 selectForKey 方法
            // 寻找合适的invoker
            return selectForKey(hash(digest, 0));
        }

        /**
         * 根据 argumentIndex将参数转为 KEY
         * @param args
         * @return
         */
        private String toKey(Object[] args) {
            StringBuilder buf = new StringBuilder();
            for (int i : argumentIndex) {
                if (i >= 0 && i < args.length) {
                    buf.append(args[i]);
                }
            }
            return buf.toString();
        }

        private Invoker<T> selectForKey(long hash) {
            // 到 TreeMap 中查找第一个节点值大于或等于当前hash的invoker
            Map.Entry<Long, Invoker<T>> entry = virtualInvokers.ceilingEntry(hash);
            // 如果 hash 大于 invoker 在圆环中最大的位置，此时 entry = null
            // 需要将 TreeMap 的头节点赋值给entry
            if (entry == null) {
                entry = virtualInvokers.firstEntry();
            }
            System.out.println("CHS的selectForKey方法根据key=" + hash + "选择出来的invoker=" + entry.getValue());
            return entry.getValue();
        }

        private long hash(byte[] digest, int number) {
            return (((long) (digest[3 + number * 4] & 0xFF) << 24)
                    | ((long) (digest[2 + number * 4] & 0xFF) << 16)
                    | ((long) (digest[1 + number * 4] & 0xFF) << 8)
                    | (digest[number * 4] & 0xFF))
                    & 0xFFFFFFFFL;
        }

        private byte[] md5(String value) {
            MessageDigest md5;
            try {
                md5 = MessageDigest.getInstance("MD5");
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
            md5.reset();
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            md5.update(bytes);
            return md5.digest();
        }

    }

}
