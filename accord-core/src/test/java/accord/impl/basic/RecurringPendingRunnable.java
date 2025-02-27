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

package accord.impl.basic;

import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

import accord.api.Scheduler.Scheduled;

class RecurringPendingRunnable implements PendingRunnable, Scheduled
{
    final int source;
    final PendingQueue requeue;
    final LongSupplier delay;
    final TimeUnit units;
    final boolean isRecurring;
    Runnable run;
    Runnable onCancellation;

    RecurringPendingRunnable(int source, PendingQueue requeue, Runnable run, LongSupplier delay, TimeUnit units, boolean isRecurring)
    {
        this.source = source;
        this.requeue = requeue;
        this.run = run;
        this.delay = delay;
        this.units = units;
        this.isRecurring = isRecurring;
    }

    @Override
    public Pending origin()
    {
        return this;
    }

    @Override
    public void run()
    {
        if (run != null)
        {
            run.run();
            maybeRequeue();
        }
    }

    public void maybeRequeue()
    {
        if (requeue != null) requeue.add(this, delay.getAsLong(), units);
        else run = null;
    }

    @Override
    public void cancel()
    {
        run = null;
        if (onCancellation != null)
        {
            onCancellation.run();
            onCancellation = null;
        }
    }

    @Override
    public boolean isDone()
    {
        return run == null;
    }

    public void onCancellation(Runnable run)
    {
        this.onCancellation = run;
    }

    @Override
    public String toString()
    {
        if (run == null)
            return "Done/Cancelled";

        return run + " with " + delay + " " + units + " delay";
    }

    public static boolean isRecurring(Pending pending)
    {
        Pending origin = pending.origin();
        return origin instanceof RecurringPendingRunnable && ((RecurringPendingRunnable) origin).isRecurring;
    }
}
