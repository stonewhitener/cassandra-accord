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

import javax.annotation.Nullable;

import accord.api.RoutingKey;
import accord.primitives.TxnId;

import static accord.utils.Invariants.checkState;

/**
 * Thrown when a coordinator is preempted by another recovery
 * coordinator intending to complete the transaction
 */
public class Preempted extends CoordinationFailed
{
    public Preempted(TxnId txnId, @Nullable RoutingKey homeKey)
    {
        super(txnId, homeKey);
    }

    Preempted(TxnId txnId, @Nullable RoutingKey homeKey, Preempted cause)
    {
        super(txnId, homeKey, cause);
    }

    @Override
    public Preempted wrap()
    {
        checkState(this.getClass() == Preempted.class);
        return new Preempted(txnId(), homeKey(), this);
    }
}
