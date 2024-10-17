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

import accord.api.RoutingKey;
import accord.api.VisibleForImplementation;
import accord.local.SafeCommandStore;
import accord.primitives.RoutingKeys;
import accord.primitives.Timestamp;
import accord.primitives.TxnId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static accord.local.RedundantBefore.PreBootstrapOrStale.FULLY;
import static accord.utils.Invariants.illegalState;


public class TimestampsForKeys
{
    private static final Logger logger = LoggerFactory.getLogger(TimestampsForKeys.class);

    private TimestampsForKeys() {}

    public static TimestampsForKey updateLastExecutionTimestamps(SafeCommandStore safeStore, SafeTimestampsForKey tfk, TxnId txnId, Timestamp executeAt, boolean isForWriteTxn)
    {
        TimestampsForKey current = tfk.current();

        Timestamp lastWrite = current.lastWriteTimestamp();

        if (executeAt.compareTo(lastWrite) < 0)
        {
            if (safeStore.redundantBefore().preBootstrapOrStale(TxnId.min(txnId, current.lastWriteId()), RoutingKeys.of(tfk.key().toUnseekable())) == FULLY)
                return current;
            throw illegalState("%s is less than the most recent write timestamp %s", executeAt, lastWrite);
        }

        Timestamp lastExecuted = current.lastExecutedTimestamp();
        int cmp = executeAt.compareTo(lastExecuted);
        // execute can be in the past if it's for a read and after the most recent write
        if (cmp == 0 || (!isForWriteTxn && cmp < 0))
            return current;

        if (cmp < 0)
        {
            if (!safeStore.safeToReadAt(executeAt).contains(tfk.key().toUnseekable()))
                return current;
            throw illegalState("%s is less than the most recent executed timestamp %s", executeAt, lastExecuted);
        }

        long micros = executeAt.hlc();
        long lastMicros = current.lastExecutedHlc();

        Timestamp lastExecutedTimestamp = executeAt;
        long lastExecutedHlc = micros > lastMicros ? Long.MIN_VALUE : lastMicros + 1;
        TxnId lastWriteId = current.lastWriteId();
        Timestamp lastWriteTimestamp = current.lastWriteTimestamp();
        if (isForWriteTxn)
        {
            lastWriteId = txnId;
            lastWriteTimestamp = executeAt;
        }

        return tfk.updateLastExecutionTimestamps(lastExecutedTimestamp, lastExecutedHlc, lastWriteId, lastWriteTimestamp);
    }

    @VisibleForImplementation
    public static <D> TimestampsForKey updateLastExecutionTimestamps(SafeCommandStore safeStore, RoutingKey key, TxnId txnId, Timestamp executeAt, boolean isForWriteTxn)
    {
        return updateLastExecutionTimestamps(safeStore, safeStore.timestampsForKey(key), txnId, executeAt, isForWriteTxn);
    }
}
