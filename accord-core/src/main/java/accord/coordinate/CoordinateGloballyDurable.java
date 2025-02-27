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

import accord.coordinate.tracking.QuorumTracker;
import accord.coordinate.tracking.RequestStatus;
import accord.local.DurableBefore;
import accord.local.Node;
import accord.messages.Callback;
import accord.messages.QueryDurableBefore;
import accord.messages.QueryDurableBefore.DurableBeforeReply;
import accord.messages.SetGloballyDurable;
import accord.topology.Topologies;
import accord.utils.SortedArrays;
import accord.utils.async.AsyncResult;
import accord.utils.async.AsyncResults.SettableResult;

// TODO (expected): this does not need to query every shard; can disseminate globally any sub-range of the ring
//  (indeed, we could slice both the query and dissemination only so that they always overlap)
public class CoordinateGloballyDurable extends SettableResult<Void> implements Callback<DurableBeforeReply>
{
    final Node node;
    // TODO (expected): this can be a ReadTracker, we only need one response from each shard
    final QuorumTracker tracker;
    private DurableBefore durableBefore = DurableBefore.EMPTY;

    private CoordinateGloballyDurable(Node node, long epoch)
    {
        Topologies topologies = node.topology().preciseEpochs(epoch);
        this.node = node;
        this.tracker = new QuorumTracker(topologies);
    }

    public static AsyncResult<Void> coordinate(Node node, long epoch)
    {
        CoordinateGloballyDurable coordinate = new CoordinateGloballyDurable(node, epoch);
        coordinate.start();
        return coordinate;
    }

    private void start()
    {
        SortedArrays.SortedArrayList<Node.Id> contact = tracker.filterAndRecordFaulty();
        if (contact == null) tryFailure(new Exhausted(null, null, null));
        else node.send(contact, to -> new QueryDurableBefore(tracker.topologies().currentEpoch()), this);
    }

    @Override
    public void onSuccess(Node.Id from, DurableBeforeReply reply)
    {
        durableBefore = DurableBefore.merge(durableBefore, reply.durableBeforeMap);
        if (tracker.recordSuccess(from) == RequestStatus.Success)
        {
            if (durableBefore != null && durableBefore.size() != 0)
                node.send(tracker.nodes(), new SetGloballyDurable(durableBefore));
            trySuccess(null);
        }
    }

    @Override
    public void onFailure(Node.Id from, Throwable failure)
    {
        if (tracker.recordFailure(from) == RequestStatus.Failed)
            tryFailure(new Exhausted(null, null, null));
    }

    @Override
    public boolean onCallbackFailure(Node.Id from, Throwable failure)
    {
        return tryFailure(failure);
    }
}
