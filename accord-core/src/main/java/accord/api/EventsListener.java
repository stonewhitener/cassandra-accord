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

package accord.api;

import accord.local.Command;
import accord.primitives.Deps;
import accord.primitives.Timestamp;
import accord.primitives.TxnId;

public interface EventsListener
{
    default void onCommitted(Command cmd)
    {
    }

    default void onStable(Command cmd)
    {
    }

    default void onExecuted(Command cmd)
    {
    }

    default void onApplied(Command cmd, long applyStartTimestamp)
    {
    }

    default void onFastPathTaken(TxnId txnId, Deps deps)
    {
    }

    default void onMediumPathTaken(TxnId txnId, Deps deps)
    {
    }

    default void onSlowPathTaken(TxnId txnId, Deps deps)
    {
    }

    default void onRecover(TxnId txnId, Timestamp recoveryTimestamp)
    {
    }

    default void onPreempted(TxnId txnId)
    {
    }

    default void onTimeout(TxnId txnId)
    {
    }

    default void onInvalidated(TxnId txnId)
    {
    }

    default void onRejected(TxnId txnId)
    {
    }

    default void onProgressLogSizeChange(TxnId txnId, int delta)
    {
    }

    EventsListener NOOP = new EventsListener()
    {
    };
}
