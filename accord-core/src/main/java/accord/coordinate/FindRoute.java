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

import accord.coordinate.Infer.InvalidIf;
import accord.local.Node;
import accord.messages.CheckStatus.CheckStatusOk;
import accord.messages.CheckStatus.IncludeInfo;
import accord.primitives.*;

import static accord.primitives.Route.isFullRoute;

/**
 * Find the Route of a known (txnId, homeKey) pair
 */
public class FindRoute extends CheckShards<Route<?>>
{
    public static class Result
    {
        public final FullRoute<?> route;
        public final Timestamp executeAt;

        public Result(FullRoute<?> route, Timestamp executeAt)
        {
            this.route = route;
            this.executeAt = executeAt;
        }

        public Result(CheckStatusOk ok)
        {
            this.route = Route.castToFullRoute(ok.route);
            this.executeAt = ok.maxKnown().executeAt().isDecidedAndKnownToExecute() ? ok.executeAt : null;
        }
    }

    final BiConsumer<Result, Throwable> callback;
    FindRoute(Node node, TxnId txnId, InvalidIf invalidIf, Route<?> someRoute, BiConsumer<Result, Throwable> callback)
    {
        super(node, txnId, someRoute, IncludeInfo.Route, invalidIf);
        this.callback = callback;
    }

    public static FindRoute findRoute(Node node, TxnId txnId, InvalidIf invalidIf, Route<?> someRoute, BiConsumer<Result, Throwable> callback)
    {
        FindRoute findRoute = new FindRoute(node, txnId, invalidIf, someRoute, callback);
        findRoute.start();
        return findRoute;
    }

    @Override
    protected boolean isSufficient(CheckStatusOk ok)
    {
        return isFullRoute(ok.route);
    }

    @Override
    protected void onDone(Success success, Throwable failure)
    {
        if (failure != null) callback.accept(null, failure);
        else if (success == Success.Success) callback.accept(new Result(merged), null);
        else callback.accept(null, null);
    }
}
