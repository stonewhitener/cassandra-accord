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

package accord.coordinate;

import accord.coordinate.tracking.FastPathTracker;
import accord.local.Node;
import accord.primitives.Ranges;
import accord.primitives.TxnId;
import accord.topology.Topology;
import accord.topology.TopologyUtils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static accord.Utils.*;
import static accord.utils.Utils.toArray;

public class PreAcceptTrackerTest
{
    private static final Node.Id[] ids = toArray(ids(5), Node.Id[]::new);
    private static final Ranges ranges = TopologyUtils.initialRanges(5, 500);
    private static final Topology topology = TopologyUtils.initialTopology(ids, ranges, 3);
        /*
        (000, 100](100, 200](200, 300](300, 400](400, 500]
        [1, 2, 3] [2, 3, 4] [3, 4, 5] [4, 5, 1] [5, 1, 2]
         */

    private static void assertResponseState(FastPathTracker responses,
                                            boolean quorumReached,
                                            boolean fastPathAccepted,
                                            boolean failed)
    {
        Assertions.assertEquals(quorumReached, responses.hasReachedQuorum());
        Assertions.assertEquals(fastPathAccepted, responses.hasFastPathAccepted());
        Assertions.assertEquals(failed, responses.hasFailed());
    }

    @Test
    void singleShard()
    {
        Topology subTopology = topology(topology.get(0));
        FastPathTracker responses = new FastPathTracker(topologies(subTopology), TxnId.NONE);

        responses.recordSuccess(ids[0], false);
        assertResponseState(responses, false, false, false);

        responses.recordSuccess(ids[1], false);
        assertResponseState(responses, true, false, false);

        responses.recordSuccess(ids[2], false);
        assertResponseState(responses, true, false, false);
    }

    @Test
    void singleShardFastPath()
    {
        Topology subTopology = topology(topology.get(0));
        FastPathTracker responses = new FastPathTracker(topologies(subTopology), TxnId.NONE);

        responses.recordSuccess(ids[0], true);
        assertResponseState(responses, false, false, false);

        responses.recordSuccess(ids[1], true);
        assertResponseState(responses, true, false, false);

        responses.recordSuccess(ids[2], true);
        assertResponseState(responses, true, true, false);
    }

    /**
     * responses from unexpected endpoints should be ignored
     */
    @Test
    void unexpectedResponsesAreIgnored()
    {
        Topology subTopology = topology(topology.get(0));
        FastPathTracker responses = new FastPathTracker(topologies(subTopology), TxnId.NONE);

        responses.recordSuccess(ids[0], false);
        assertResponseState(responses, false, false, false);

        responses.recordSuccess(ids[1], false);
        assertResponseState(responses, true, false, false);

        Assertions.assertFalse(subTopology.get(0).nodes.contains(ids[4]));
        responses.recordSuccess(ids[4], false);
        assertResponseState(responses, true, false, false);
    }

    @Test
    void failure()
    {
        Topology subTopology = topology(topology.get(0));
        FastPathTracker responses = new FastPathTracker(topologies(subTopology), TxnId.NONE);

        responses.recordSuccess(ids[0], true);
        assertResponseState(responses, false, false, false);

        responses.recordFailure(ids[1]);
        assertResponseState(responses, false, false, false);

        responses.recordFailure(ids[2]);
        assertResponseState(responses, false, false, true);
    }

    @Test
    void multiShard()
    {
        Topology subTopology = new Topology(1, topology.get(0), topology.get(1), topology.get(2));
        FastPathTracker responses = new FastPathTracker(topologies(subTopology), TxnId.NONE);
        /*
        (000, 100](100, 200](200, 300]
        [1, 2, 3] [2, 3, 4] [3, 4, 5]
         */

        Assertions.assertSame(subTopology.get(0), responses.get(0).shard);
        Assertions.assertSame(subTopology.get(1), responses.get(1).shard);
        Assertions.assertSame(subTopology.get(2), responses.get(2).shard);

        responses.recordSuccess(ids[1], true);
        assertResponseState(responses, false, false, false);

        responses.recordSuccess(ids[2], true);
        assertResponseState(responses, false, false, false);

        responses.recordSuccess(ids[3], true);
        // the middle shard will have reached fast path
        Assertions.assertTrue(responses.get(1).hasMetFastPathCriteria());
        // but since the others haven't, it won't report it as accepted
        assertResponseState(responses, true, false, false);

        responses.recordSuccess(ids[0], true);
        responses.recordSuccess(ids[4], true);
        assertResponseState(responses, true, true, false);
    }
}
