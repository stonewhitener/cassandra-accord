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

package accord.primitives;

import accord.local.CommandSummaries.SummaryStatus;
import accord.messages.BeginRecovery;
import accord.utils.Invariants;

import java.util.Collection;
import java.util.function.Function;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import static accord.local.CommandSummaries.SummaryStatus.ACCEPTED;
import static accord.local.CommandSummaries.SummaryStatus.APPLIED;
import static accord.local.CommandSummaries.SummaryStatus.COMMITTED;
import static accord.local.CommandSummaries.SummaryStatus.INVALIDATED;
import static accord.local.CommandSummaries.SummaryStatus.NOTACCEPTED;
import static accord.local.CommandSummaries.SummaryStatus.NOT_DIRECTLY_WITNESSED;
import static accord.local.CommandSummaries.SummaryStatus.PREACCEPTED;
import static accord.local.CommandSummaries.SummaryStatus.PRENOTACCEPTED_OR_ACCEPTED_INVALIDATE;
import static accord.local.CommandSummaries.SummaryStatus.STABLE;
import static accord.primitives.Known.PrivilegedVote.NoVote;
import static accord.primitives.Known.Definition.*;
import static accord.primitives.Known.*;
import static accord.primitives.Known.KnownDeps.*;
import static accord.primitives.Known.KnownExecuteAt.*;
import static accord.primitives.Known.KnownRoute.CoveringRoute;
import static accord.primitives.Known.KnownRoute.FullRoute;
import static accord.primitives.Known.KnownRoute.MaybeRoute;
import static accord.primitives.Known.Outcome.*;
import static accord.primitives.Status.Phase.*;

public enum Status
{
    NotDefined        (None,      NOT_DIRECTLY_WITNESSED,                  Nothing),
    PreAccepted       (PreAccept, PREACCEPTED,                             DefinitionAndRoute),

    /**
     * A recovery coordinator found a quorum of preaccept, i.e. no Accept or later was witnessed.
     * Once recorded to a quorum, any in-flight Accept from the original coordinator is defunct and will not be re-proposed.
     */
    PreNotAccepted    (Accept,    PRENOTACCEPTED_OR_ACCEPTED_INVALIDATE,   MaybeRoute,             DefinitionUnknown, ExecuteAtUnknown,      DepsUnknown,      Unknown), // may or may not have witnessed
    /**
     * A recovery coordinator found a quorum of preaccept, and has recorded this knowledge to a quorum.
     */
    NotAccepted       (Accept,    NOTACCEPTED,                             MaybeRoute,             DefinitionUnknown, ExecuteAtUnknown,      DepsUnknown,       Unknown), // may or may not have witnessed
    AcceptedInvalidate(Accept,    PRENOTACCEPTED_OR_ACCEPTED_INVALIDATE,   MaybeRoute,             DefinitionUnknown, ExecuteAtUnknown,      DepsUnknown,       Unknown), // may or may not have witnessed

    AcceptedMedium    (Accept,    ACCEPTED,                                CoveringRoute,          DefinitionUnknown, ExecuteAtProposed,     DepsProposedFixed, Unknown), // may or may not have witnessed
    AcceptedSlow      (Accept,    ACCEPTED,                                CoveringRoute,          DefinitionUnknown, ExecuteAtProposed,     DepsProposed,      Unknown), // may or may not have witnessed

    /**
     * PreCommitted is a peculiar state, half-way between Accepted and Committed.
     * We know the transaction is Committed and its execution timestamp, but we do
     * not know its dependencies, and we may still have state leftover from the Accept round
     * that is necessary for recovery.
     *
     * So, for execution of other transactions we may treat a PreCommitted transaction as Committed,
     * using the timestamp to update our dependency set to rule it out as a dependency.
     * But we do not have enough information to execute the transaction, and when recovery calculates
     * {@link BeginRecovery#acceptedStartedBeforeWithoutWitnessing},
     * {@link BeginRecovery#hasCommittedExecutesAfterWithoutWitnessing},
     * and {@link BeginRecovery#committedStartedBeforeAndWitnessed} we may not have the dependencies
     * to calculate the result. For these operations we treat ourselves as whatever Accepted status
     * we may have previously taken, using any proposed dependencies to compute the result.
     *
     * This state exists primarily to permit us to efficiently separate work between different home shards.
     * Take a transaction A that reaches the Committed status and commits to all of its home shard A*'s replicas,
     * but fails to commit to all shards. A takes an execution time later than its TxnId, and in the process
     * adopts a dependency on a transaction B that is coordinated by its home shard B*, that has itself taken
     * a dependency upon A. Importantly, B commits a lower executeAt than A and so will execute first, and once A*
     * commits B, A will remove it from its dependencies. However, there is insufficient information on A*
     * to commit B since it does not know A*'s dependencies, and B* will not process B until A* executes A.
     * To solve this problem we simply permit the executeAt we discover for B to be propagated to A* without
     * its dependencies. Though this does complicate the state machine a little.
     */
    PreCommitted      (Accept,     PREACCEPTED,  FullRoute,  DefinitionUnknown, ExecuteAtKnown,   DepsUnknown,   Unknown),
    Committed         (Commit,     COMMITTED,    FullRoute,  DefinitionKnown,   ExecuteAtKnown,   DepsCommitted, Unknown),
    Stable            (Execute,    STABLE,       FullRoute,  DefinitionKnown,   ExecuteAtKnown,   DepsKnown,     Unknown),
    PreApplied        (Persist,    STABLE,       FullRoute,  DefinitionKnown,   ExecuteAtKnown,   DepsKnown,     Outcome.Apply),
    Applied           (Persist,    APPLIED,      FullRoute,  DefinitionKnown,   ExecuteAtKnown,   DepsKnown,     Outcome.Apply),
    // TODO (required): TruncatedApply should be treated as APPLIED for summary status; when computing recovery decisions
    //  anything already APPLIED should be treated as not witnessing anything being recovered from preaccept status
    //  EXCEPT this cannot apply for touches \notin owns... consider some more how we handle this case
    Truncated         (Cleanup,  null,  MaybeRoute, DefinitionErased,  ExecuteAtErased,  DepsErased,    Outcome.Erased),
    Invalidated       (Invalidate, INVALIDATED,  MaybeRoute, NoOp,              NoExecuteAt,      NoDeps,        Outcome.Abort),
    ;

    static
    {
        // We require that Commands.notAccept can safely transition a reCoveringRoute transaction
        // PreNotAccepted->NotAccepted->{AcceptedInvalidate,Accepted} using the same ballot,
        // but must guard against message arrival order permitting us to go backwards
        // (i.e. we need to reject equal ballot updates where the status would move us 'backwards')
        // So we require that this order isn't changed
        Invariants.partiallyOrdered(PreNotAccepted, NotAccepted, AcceptedInvalidate, AcceptedMedium);
    }

    /**
     * Represents the phase of a transaction from the perspective of coordination
     * None:       the transaction is not currently being processed by us (it may be known to us, but only transitively)
     * PreAccept:  the transaction is being disseminated and is seeking an execution order
     * Accept:     the transaction did not achieve 1RT consensus and is making durable its execution order
     * Commit:     the transaction's execution time has been durably decided, and dependencies are being disseminated
     * Execute:    the transaction's execution dependencies have been durably disseminated, and the transaction is waiting to execute
     * Persist:    the transaction has executed, and its outcome is being persisted
     * Cleanup:    the transaction has completed, and state used for processing it is being reclaimed
     */
    public enum Phase
    {
        None(false),
        PreAccept(false),
        Accept(true),
        Commit(true),
        Execute(false),
        Persist(false),
        Cleanup(false),
        Invalidate(false)
        ;

        public final boolean tieBreakWithBallot;

        Phase(boolean tieBreakWithBallot)
        {
            this.tieBreakWithBallot = tieBreakWithBallot;
        }
    }

    /**
     * Represents the durability of a transaction's Persist phase.
     * NotDurable: the outcome has not been durably recorded
     * Local:      the outcome has been durably recorded at least locally
     * ShardUniversalOrInvalidated: the outcome has been durably recorded at all healthy replicas of the shard, or is invalidated
     * ShardUniversal:      the outcome has been durably recorded at all healthy replicas of the shard
     * MajorityOrInvalidated:   the outcome has been durably recorded to a majority of each participating shard
     * Majority:   the outcome has been durably recorded to a majority of each participating shard
     * Universal:  the outcome has been durably recorded to every healthy replica
     * DurableOrInvalidated:  the outcome was either invalidated, or has been durably recorded to every healthy replica
     */
    public enum Durability
    {
        NotDurable, Local, ShardUniversal,
        MajorityOrInvalidated, Majority,
        UniversalOrInvalidated, Universal;

        private static final Durability[] lookup = values();

        public boolean isDurable()
        {
            return this == Majority || this == Universal;
        }

        public boolean isDurableOrInvalidated()
        {
            return compareTo(MajorityOrInvalidated) >= 0;
        }

        public boolean isMaybeInvalidated()
        {
            return this == NotDurable || this == MajorityOrInvalidated || this == UniversalOrInvalidated;
        }

        public static Durability nonNullOrMerge(@Nullable Durability a, @Nullable Durability b)
        {
            if (a == null) return b;
            if (b == null) return a;
            return merge(a, b);
        }

        public static Durability merge(Durability a, Durability b)
        {
            int c = a.compareTo(b);
            if (c < 0) { Durability tmp = a; a = b; b = tmp; }
            // if we know we are applied, we can remove the OrInvalidated qualifier
            if (a == UniversalOrInvalidated && (b == Majority || b == ShardUniversal || b == Local)) a = Universal;
            // TODO (required, minor cleanup): should ShardUniversal+NotDurable=Local? It might be that we are stale.
            if ((a == ShardUniversal) && (b == Local || b == NotDurable)) a = Local;
            if (b == NotDurable && a.compareTo(MajorityOrInvalidated) < 0) a = NotDurable;
            return a;
        }

        public static Durability mergeAtLeast(Durability a, Durability b)
        {
            int c = a.compareTo(b);
            if (c < 0) { Durability tmp = a; a = b; b = tmp; }
            if (a == UniversalOrInvalidated && (b == Majority || b == ShardUniversal || b == Local)) a = Universal;
            return a;
        }

        public static Durability max(Durability a, Durability b)
        {
            return a.compareTo(b) >= 0 ? a : b;
        }

        public static Durability forOrdinal(int ordinal)
        {
            if (ordinal < 0 || ordinal > lookup.length)
                throw new IndexOutOfBoundsException(ordinal);
            return lookup[ordinal];
        }

        public static int maxOrdinal()
        {
            return lookup.length;
        }
    }

    public final Phase phase;
    public final SummaryStatus summary;
    public final Known minKnown;

    Status(Phase phase, SummaryStatus summary, Known minKnown)
    {
        this.phase = phase;
        this.summary = summary;
        this.minKnown = minKnown;
    }

    Status(Phase phase, SummaryStatus summary, KnownRoute route, Definition definition, KnownExecuteAt executeAt, KnownDeps deps, Outcome outcome)
    {
        this.phase = phase;
        this.summary = summary;
        this.minKnown = new Known(route, definition, executeAt, deps, outcome, NoVote);
    }

    // TODO (desired, clarity): investigate all uses of hasBeen, and migrate as many as possible to testing
    //                          Phase, ReplicationPhase and ExecutionStatus where these concepts are inadequate,
    //                          see if additional concepts can be introduced
    public boolean hasBeen(Status equalOrGreaterThan)
    {
        return compareTo(equalOrGreaterThan) >= 0;
    }

    public static <T> T max(Collection<T> list, Function<T, Status> getStatus, Function<T, Ballot> getAcceptedOrCommittedBallot, Predicate<T> filter)
    {
        T max = null;
        Status maxStatus = null;
        Ballot maxBallot = null;
        for (T item : list)
        {
            if (!filter.test(item))
                continue;

            Status status = getStatus.apply(item);
            Ballot ballot = getAcceptedOrCommittedBallot.apply(item);
            if (max == null || isGreater(status, ballot, maxStatus, maxBallot))
            {
                max = item;
                maxStatus = status;
                maxBallot = ballot;
            }
        }

        return max;
    }

    private static boolean isGreater(Status testStatus, Ballot testBallot, Status thanStatus, Ballot thanBallot)
    {
        Phase phase = testStatus.phase;
        int c = phase.compareTo(thanStatus.phase);
        if (c != 0)
            return c > 0;

        if (phase.tieBreakWithBallot)
        {
            c = testBallot.compareTo(thanBallot);
            if (c != 0)
                return c > 0;
        }

        return testStatus.compareTo(thanStatus) > 0;
    }

    public static <T> T max(T a, Status statusA, Ballot ballotA, T b, Status statusB, Ballot ballotB)
    {
        int c = statusA.phase.compareTo(statusB.phase);
        if (c > 0) return a;
        if (c < 0) return b;
        if ((statusA.phase.tieBreakWithBallot ? ballotA.compareTo(ballotB) : statusA.compareTo(statusB)) >= 0)
            return a;
        return b;
    }
}
