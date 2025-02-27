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

package accord.topology;

import accord.local.Node;
import accord.utils.SortedArrays.SortedArrayList;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static accord.Utils.ids;
import static accord.impl.IntKey.range;

public class ShardTest
{
    @Test
    void toleratedFailures()
    {
        Assertions.assertEquals(0, Shard.maxToleratedFailures(1));
        Assertions.assertEquals(0, Shard.maxToleratedFailures(2));
        Assertions.assertEquals(1, Shard.maxToleratedFailures(3));
        Assertions.assertEquals(1, Shard.maxToleratedFailures(4));
        Assertions.assertEquals(2, Shard.maxToleratedFailures(5));
        Assertions.assertEquals(2, Shard.maxToleratedFailures(6));
        Assertions.assertEquals(3, Shard.maxToleratedFailures(7));
        Assertions.assertEquals(3, Shard.maxToleratedFailures(8));
        Assertions.assertEquals(4, Shard.maxToleratedFailures(9));
        Assertions.assertEquals(4, Shard.maxToleratedFailures(10));
        Assertions.assertEquals(5, Shard.maxToleratedFailures(11));
        Assertions.assertEquals(5, Shard.maxToleratedFailures(12));
        Assertions.assertEquals(6, Shard.maxToleratedFailures(13));
        Assertions.assertEquals(6, Shard.maxToleratedFailures(14));
        Assertions.assertEquals(7, Shard.maxToleratedFailures(15));
        Assertions.assertEquals(7, Shard.maxToleratedFailures(16));
        Assertions.assertEquals(8, Shard.maxToleratedFailures(17));
        Assertions.assertEquals(8, Shard.maxToleratedFailures(18));
        Assertions.assertEquals(9, Shard.maxToleratedFailures(19));
        Assertions.assertEquals(9, Shard.maxToleratedFailures(20));
    }

    void assertSimpleFastQuorumSize(int expected, int replicas, int fpElectorate)
    {
        int f = Shard.maxToleratedFailures(replicas);
        int actual = Shard.simpleFastQuorumSize(replicas, fpElectorate, replicas - f);
        Assertions.assertEquals(expected, actual);
    }

    void assertIllegalArgument(Runnable runnable, String msg)
    {
        try
        {
            runnable.run();
            Assertions.fail(msg);
        }
        catch (IllegalArgumentException e)
        {
            // noop
        }
    }

    void assertInvalidFastPathElectorateSize(int replicas, int fpElectorate)
    {
        int f = Shard.maxToleratedFailures(replicas);
        assertIllegalArgument(() -> Shard.simpleFastQuorumSize(replicas, fpElectorate, replicas - f),
                              String.format("Expected exception for fp electorate size %s for replica set size %s (f %s)",
                                            fpElectorate, replicas, f));
    }

    @Test
    void slowQuorumSizeTest()
    {
        Assertions.assertEquals(1, Shard.slowQuorumSize(1));
        Assertions.assertEquals(2, Shard.slowQuorumSize(2));
        Assertions.assertEquals(2, Shard.slowQuorumSize(3));
        Assertions.assertEquals(3, Shard.slowQuorumSize(4));
        Assertions.assertEquals(3, Shard.slowQuorumSize(5));
    }

    @Test
    void simpleFastQuorumSizeTest()
    {
        // rf=3
        assertSimpleFastQuorumSize(3, 3, 3);
        assertSimpleFastQuorumSize(2, 3, 2);
        assertInvalidFastPathElectorateSize(3, 1);

        // rf=4
        assertSimpleFastQuorumSize(3, 4, 4);
        assertSimpleFastQuorumSize(3, 4, 3);
        assertInvalidFastPathElectorateSize(4, 2);

        // rf=5
        assertSimpleFastQuorumSize(4, 5, 5);
        assertSimpleFastQuorumSize(4, 5, 4);
        assertSimpleFastQuorumSize(3, 5, 3);
        assertInvalidFastPathElectorateSize(5, 2);

        // rf=6
        assertSimpleFastQuorumSize(5, 6, 6);
        assertSimpleFastQuorumSize(4, 6, 5);
        assertSimpleFastQuorumSize(4, 6, 4);
        assertInvalidFastPathElectorateSize(6, 3);

        // rf=7
        assertSimpleFastQuorumSize(6, 7, 7);
        assertSimpleFastQuorumSize(5, 7, 6);
        assertSimpleFastQuorumSize(5, 7, 5);
        assertSimpleFastQuorumSize(4, 7, 4);
        assertInvalidFastPathElectorateSize(7, 3);

        // rf=8
        assertSimpleFastQuorumSize(6, 8, 8);
        assertSimpleFastQuorumSize(6, 8, 7);
        assertSimpleFastQuorumSize(5, 8, 6);
        assertInvalidFastPathElectorateSize(8, 4);

        // rf=9
        assertSimpleFastQuorumSize(7, 9, 9);
        assertSimpleFastQuorumSize(7, 9, 8);
        assertSimpleFastQuorumSize(6, 9, 7);
        assertSimpleFastQuorumSize(6, 9, 6);
        assertSimpleFastQuorumSize(5, 9, 5);
        assertInvalidFastPathElectorateSize(9, 4);
    }

    @Test
    void pendingNodeValidation()
    {
        SortedArrayList<Node.Id> nodes = ids(0, 3);
        Set<Node.Id> fpNodes = new HashSet<>(ids(0, 2));
        // pending nodes are part of electorate
        Shard.create(range(0, 100), nodes, fpNodes, new HashSet<>(ids(3, 3)));
        // pending nodes are not part of electorate
        assertIllegalArgument(() -> Shard.create(range(0, 100), nodes, fpNodes, new HashSet<>(ids(4, 4))),
                              "Expected exception for non-electorate pending nodes");
    }
}
