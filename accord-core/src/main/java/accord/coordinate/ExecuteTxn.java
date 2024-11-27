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

import java.util.function.BiConsumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import accord.api.Data;
import accord.api.Result;
import accord.local.Node;
import accord.local.Node.Id;
import accord.messages.Commit;
import accord.messages.ReadData.CommitOrReadNack;
import accord.messages.ReadData.ReadOk;
import accord.messages.ReadData.ReadReply;
import accord.messages.ReadTxnData;
import accord.primitives.Deps;
import accord.primitives.FullRoute;
import accord.primitives.Participants;
import accord.primitives.Ranges;
import accord.primitives.Timestamp;
import accord.primitives.Txn;
import accord.primitives.TxnId;
import accord.topology.Topologies;
import accord.utils.Invariants;
import org.agrona.collections.IntHashSet;

import static accord.coordinate.CoordinationAdapter.Factory.Kind.Standard;
import static accord.coordinate.ExecutePath.RECOVER;
import static accord.coordinate.ReadCoordinator.Action.Approve;
import static accord.coordinate.ReadCoordinator.Action.ApprovePartial;
import static accord.messages.Commit.Kind.StableFastPath;
import static accord.messages.Commit.Kind.StableSlowPath;
import static accord.messages.Commit.Kind.StableWithTxnAndDeps;
import static accord.utils.Invariants.illegalState;

public class ExecuteTxn extends ReadCoordinator<ReadReply>
{
    @SuppressWarnings("unused")
    private static final Logger logger = LoggerFactory.getLogger(ExecuteTxn.class);
    private static boolean SEND_MINIMUM_STABLE_MESSAGES = true;
    public static void setSendMinimumStableMessages(boolean sendMin) {SEND_MINIMUM_STABLE_MESSAGES = sendMin; }

    final ExecutePath path;
    final Txn txn;
    final Participants<?> readScope;
    final FullRoute<?> route;
    final Timestamp executeAt;
    final Deps stableDeps;
    final Topologies allTopologies;
    final BiConsumer<? super Result, Throwable> callback;
    private Data data;

    ExecuteTxn(Node node, Topologies topologies, FullRoute<?> route, ExecutePath path, TxnId txnId, Txn txn, Participants<?> readScope, Timestamp executeAt, Deps stableDeps, BiConsumer<? super Result, Throwable> callback)
    {
        super(node, topologies.forEpoch(executeAt.epoch()), txnId);
        Invariants.checkState(!txnId.awaitsOnlyDeps());
        Invariants.checkState(!txnId.awaitsPreviouslyOwned());
        this.path = path;
        this.txn = txn;
        this.route = route;
        this.allTopologies = topologies;
        this.readScope = readScope;
        this.executeAt = executeAt;
        this.stableDeps = stableDeps;
        this.callback = callback;
    }

    @Override
    protected void start(Iterable<Id> to)
    {
        IntHashSet readSet = new IntHashSet();
        to.forEach(i -> readSet.add(i.id));
        Commit.stableAndRead(node, allTopologies, commitKind(), txnId, txn, route, readScope, executeAt, stableDeps, readSet, this, SEND_MINIMUM_STABLE_MESSAGES && path != RECOVER);
    }

    private Commit.Kind commitKind()
    {
        switch (path)
        {
            default: throw new AssertionError("Unhandled path: " + path);
            case FAST:    return StableFastPath;
            case SLOW:    return StableSlowPath;
            case RECOVER: return StableWithTxnAndDeps;
        }
    }

    @Override
    public void contact(Id to)
    {
        if (SEND_MINIMUM_STABLE_MESSAGES && path != RECOVER)
        {
            // we are always sending to a replica in the latest epoch and requesting a read, so onlyContactOldAndReadSet is a redundant parameter
            Commit.stableAndRead(to, node, allTopologies, commitKind(), txnId, txn, route, readScope, executeAt, stableDeps, this, false);
        }
        else
        {
            node.send(to, new ReadTxnData(to, topologies(), txnId, readScope, executeAt.epoch()), this);
        }
    }

    @Override
    protected Ranges unavailable(ReadReply reply)
    {
        return ((ReadOk)reply).unavailable;
    }

    @Override
    protected Action process(Id from, ReadReply reply)
    {
        if (reply.isOk())
        {
            ReadOk ok = ((ReadOk) reply);
            Data next = ok.data;
            if (next != null)
                data = data == null ? next : data.merge(next);

            return ok.unavailable == null ? Approve : ApprovePartial;
        }

        CommitOrReadNack nack = (CommitOrReadNack) reply;
        switch (nack)
        {
            default: throw new IllegalStateException();
            case Redundant:
            case Rejected:
                callback.accept(null, new Preempted(txnId, route.homeKey()));
                return Action.Aborted;
            case Insufficient:
                // the replica may be missing the original commit, or the additional commit, so send everything
                Commit.stableMaximal(node, from, txn, txnId, executeAt, route, stableDeps);
                // also try sending a read command to another replica, in case they're ready to serve a response
                return Action.TryAlternative;
            case Invalid:
                callback.accept(null, illegalState("Submitted a read command to a replica that did not own the range"));
                return Action.Aborted;
        }
    }

    @Override
    protected void onDone(Success success, Throwable failure)
    {
        if (failure == null)
        {
            Result result = txn.result(txnId, executeAt, data);
            adapter().persist(node, allTopologies, route, txnId, txn, executeAt, stableDeps, txn.execute(txnId, executeAt, data), result, callback);
        }
        else
        {
            callback.accept(null, failure);
        }
    }

    protected CoordinationAdapter<Result> adapter()
    {
        return node.coordinationAdapter(txnId, Standard);
    }

    @Override
    public String toString()
    {
        return "ExecuteTxn{" +
               "txn=" + txn +
               ", route=" + route +
               '}';
    }
}
