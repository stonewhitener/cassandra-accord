/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package accord.impl;

import accord.api.Journal;
import accord.api.LocalListeners;
import accord.local.*;
import accord.api.Agent;
import accord.api.DataStore;
import accord.api.ProgressLog;
import accord.utils.RandomSource;

public class InMemoryCommandStores
{
    public static class Synchronized extends CommandStores
    {
        public Synchronized(NodeCommandStoreService node, Agent agent, DataStore store, RandomSource random, Journal journal, ShardDistributor shardDistributor, ProgressLog.Factory progressLogFactory, LocalListeners.Factory listenersFactory)
        {
            super(node, agent, store, random, journal, shardDistributor, progressLogFactory, listenersFactory, InMemoryCommandStore.Synchronized::new);
        }
    }

    public static class SingleThread extends CommandStores
    {
        public SingleThread(NodeCommandStoreService node, Agent agent, DataStore store, RandomSource random, Journal journal, ShardDistributor shardDistributor, ProgressLog.Factory progressLogFactory, LocalListeners.Factory listenersFactory)
        {
            super(node, agent, store, random, journal, shardDistributor, progressLogFactory, listenersFactory, InMemoryCommandStore.SingleThread::new);
        }

        public SingleThread(NodeCommandStoreService node, Agent agent, DataStore store, RandomSource random, Journal journal, ShardDistributor shardDistributor, ProgressLog.Factory progressLogFactory, LocalListeners.Factory listenersFactory, CommandStore.Factory shardFactory)
        {
            super(node, agent, store, random, journal, shardDistributor, progressLogFactory, listenersFactory, shardFactory);
        }
    }

    public static class Debug extends InMemoryCommandStores.SingleThread
    {
        public Debug(NodeCommandStoreService node, Agent agent, DataStore store, RandomSource random, Journal journal, ShardDistributor shardDistributor, ProgressLog.Factory progressLogFactory, LocalListeners.Factory listenersFactory)
        {
            super(node, agent, store, random, journal, shardDistributor, progressLogFactory, listenersFactory, InMemoryCommandStore.Debug::new);
        }
    }
}
