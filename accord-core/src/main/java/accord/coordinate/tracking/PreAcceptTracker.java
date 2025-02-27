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

package accord.coordinate.tracking;

import java.util.function.Function;
import java.util.function.IntFunction;

import accord.local.Node;
import accord.topology.Shard;
import accord.topology.Topologies;

public abstract class PreAcceptTracker<ST extends ShardTracker> extends AbstractTracker<ST>
{
    public PreAcceptTracker(Topologies topologies, IntFunction<ST[]> arrayFactory, Function<Shard, ST> trackerFactory)
    {
        super(topologies, arrayFactory, trackerFactory);
    }

    public PreAcceptTracker(Topologies topologies, IntFunction<ST[]> arrayFactory, ShardFactory<ST> trackerFactory)
    {
        super(topologies, arrayFactory, trackerFactory);
    }

    public abstract RequestStatus recordSuccess(Node.Id from, boolean withFastPathTimestamp);
    public abstract RequestStatus recordDelayed(Node.Id from);
    public abstract boolean hasFastPathAccepted();
    public abstract boolean hasMediumPathAccepted();
}
