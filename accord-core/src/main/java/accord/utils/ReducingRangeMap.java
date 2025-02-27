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
package accord.utils;

import accord.api.RoutingKey;
import accord.primitives.*;

import java.util.Arrays;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import static accord.utils.SortedArrays.Search.FAST;
import static accord.utils.SortedArrays.exponentialSearch;

public class ReducingRangeMap<V> extends ReducingIntervalMap<RoutingKey, V>
{
    public interface ReduceFunction<V, V2, P1, P2>
    {
        V2 apply(V v, V2 v2, P1 p1, P2 p2, int startMatchingInput, int endMatchingInput, int matchingStartBound);
    }

    public static class SerializerSupport
    {
        public static <V> ReducingRangeMap<V> create(boolean inclusiveEnds, RoutingKey[] starts, V[] values)
        {
            return new ReducingRangeMap<>(inclusiveEnds, starts, values);
        }
    }

    public ReducingRangeMap()
    {
        super(false, RoutingKeys.EMPTY_KEYS_ARRAY, (V[])NO_OBJECTS);
    }

    protected ReducingRangeMap(boolean inclusiveEnds, RoutingKey[] starts, V[] values)
    {
        super(inclusiveEnds, starts, values);
    }

    public V foldl(Routables<?> routables, BiFunction<V, V, V> fold, V accumulator)
    {
        return foldl(routables, (a, b, f, ignore) -> f.apply(a, b), accumulator, fold, null, ignore -> false);
    }

    public <V2> V2 foldl(Routables<?> routables, BiFunction<V, V2, V2> fold, V2 accumulator, Predicate<V2> terminate)
    {
        return foldl(routables, (a, b, f, ignore) -> f.apply(a, b), accumulator, fold, null, terminate);
    }

    public <V2> V2 foldlWithBounds(Routables<?> routables, QuadFunction<V, V2, RoutingKey, RoutingKey, V2> fold, V2 accumulator, Predicate<V2> terminate)
    {
        return foldl(routables, (a, b, f, self, i, j, k) -> f.apply(a, b, self.starts[k], self.starts[k+1]), accumulator, fold, this, terminate);
    }

    // note: last end bound may be null
    // TODO (required): should there be a call where start bound is null? Should we always provide null end bound if we match to the end?
    //  I don't think it should functionally matter in caller, but for symmetry should probably be one or the other
    public <V2> V2 foldlWithDefaultAndBounds(Routables<?> routables, QuadFunction<V, V2, RoutingKey, RoutingKey, V2> fold, V defaultValue, V2 accumulator, Predicate<V2> terminate)
    {
        return foldlWithDefault(routables, (a, b, f, self, i, j, k) -> f.apply(a, b, self.starts[k], k + 1 >= self.starts.length ? null : self.starts[k+1]), defaultValue, accumulator, fold, this, terminate);
    }

    public <R extends Routable, V2> V2 foldlWithInputAndBounds(Routables<R> routables, IndexedRangeQuadFunction<V, V2, RoutingKey, RoutingKey, V2> fold, V2 accumulator, Predicate<V2> terminate)
    {
        return foldl(routables, (a, b, f, self, i, j, k) -> f.apply(a, b, self.starts[k], self.starts[k+1], i, j), accumulator, fold, this, terminate);
    }

    public <V2, P1> V2 foldl(Routables<?> routables, TriFunction<V, V2, P1, V2> fold, V2 accumulator, P1 p1, Predicate<V2> terminate)
    {
        return foldl(routables, (a, b, f, p) -> f.apply(a, b, p), accumulator, fold, p1, terminate);
    }

    public <V2, P1> V2 foldlWithKey(Routables<?> routables, IndexedTriFunction<V, V2, P1, V2> fold, V2 accumulator, P1 p1, Predicate<V2> terminate)
    {
        return foldl(routables, (v, v2, f, p, i, j, k) -> f.apply(v, v2, p, i), accumulator, fold, p1, terminate);
    }

    public <V2, P1, P2> V2 foldl(Routables<?> routables, QuadFunction<V, V2, P1, P2, V2> fold, V2 accumulator, P1 p1, P2 p2, Predicate<V2> terminate)
    {
        return foldl(routables, (v, v2, param1, param2, i, j, k) -> fold.apply(v, v2, param1, param2), accumulator, p1, p2, terminate);
    }

    public <V2, P1, P2> V2 foldl(Routables<?> routables, IndexedRangeQuadFunction<V, V2, P1, P2, V2> fold, V2 accumulator, P1 p1, P2 p2, Predicate<V2> terminate)
    {
        return foldl(routables, (v, v2, param1, param2, i, j, k) -> fold.apply(v, v2, param1, param2, i, j), accumulator, p1, p2, terminate);
    }

    public V foldlWithDefault(Routables<?> routables, BiFunction<V, V, V> fold, V defaultValue, V accumulator)
    {
        return foldlWithDefault(routables, (a, b, f, ignore) -> f.apply(a, b), defaultValue, accumulator, fold, null, ignore -> false);
    }

    public <V2> V2 foldlWithDefault(Routables<?> routables, BiFunction<? super V, V2, V2> fold, V defaultValue, V2 accumulator, Predicate<V2> terminate)
    {
        return foldlWithDefault(routables, (a, b, f, ignore) -> f.apply(a, b), defaultValue, accumulator, fold, null, terminate);
    }

    public <V2, P1> V2 foldlWithDefault(Routables<?> routables, TriFunction<V, V2, P1, V2> fold, V defaultValue, V2 accumulator, P1 p1, Predicate<V2> terminate)
    {
        return foldlWithDefault(routables, (a, b, f, p) -> f.apply(a, b, p), defaultValue, accumulator, fold, p1, terminate);
    }

    public <V2, P1, P2> V2 foldlWithDefault(Routables<?> routables, QuadFunction<V, V2, P1, P2, V2> fold, V defaultValue, V2 accumulator, P1 p1, P2 p2, Predicate<V2> terminate)
    {
        return foldlWithDefault(routables, (v, v2, param1, param2, i, j, k) -> fold.apply(v, v2, param1, param2), defaultValue, accumulator, p1, p2, terminate);
    }

    public <V2, P1, P2> V2 foldl(Routables<?> routables, ReduceFunction<V, V2, P1, P2> fold, V2 accumulator, P1 p1, P2 p2, Predicate<V2> terminate)
    {
        switch (routables.domain())
        {
            default: throw new AssertionError("Unknown domain: " + routables.domain());
            case Key: return foldl((AbstractKeys<?>) routables, fold, accumulator, p1, p2, terminate);
            case Range: return foldl((AbstractRanges) routables, fold, accumulator, p1, p2, terminate);
        }
    }

    public <A, P1, P2> A foldl(AbstractKeys<?> keys, ReduceFunction<V, A, P1, P2> fold, A accumulator, P1 p1, P2 p2, Predicate<A> terminate)
    {
        if (values.length == 0)
            return accumulator;

        int i = 0, j = keys.find(starts[0], FAST);
        if (j < 0) j = -1 - j;
        else if (inclusiveEnds) ++j;

        while (j < keys.size())
        {
            // TODO (desired): first search should be binarySearch
            i = exponentialSearch(starts, i, starts.length, keys.get(j));
            if (i < 0) i = -2 - i;
            else if (inclusiveEnds) --i;

            if (i >= values.length)
                return accumulator;

            int nextj = keys.findNext(j, starts[i + 1], FAST);
            if (nextj < 0) nextj = -1 -nextj;
            else if (inclusiveEnds) ++nextj;

            if (j != nextj && values[i] != null)
            {
                accumulator = fold.apply(values[i], accumulator, p1, p2, j, nextj, i);
                if (terminate.test(accumulator))
                    return accumulator;
            }
            ++i;
            j = nextj;
        }
        return accumulator;
    }

    public <A, P1, P2> A foldl(QuadFunction<V, A, P1, P2, A> fold, A accumulator, P1 p1, P2 p2, Predicate<A> terminate)
    {
        if (values.length == 0)
            return accumulator;

        for (V value : values)
        {
            if (value == null) continue;
            accumulator = fold.apply(value, accumulator, p1, p2);
            if (terminate.test(accumulator))
                return accumulator;
        }

        return accumulator;
    }

    public <V2, P1, P2> V2 foldl(AbstractRanges ranges, ReduceFunction<V, V2, P1, P2> fold, V2 accumulator, P1 p1, P2 p2, Predicate<V2> terminate)
    {
        if (values.length == 0)
            return accumulator;

        int j = ranges.find(starts[0], FAST);
        if (j < 0) j = -1 - j;
        else if (inclusiveEnds && ranges.get(j).end().equals(starts[0])) ++j;

        int i = 0;
        while (j < ranges.size())
        {
            // TODO (desired): first search should be binarySearch
            int nexti = exponentialSearch(starts, i, starts.length, ranges.get(j).start());
            if (nexti < 0) i = Math.max(i, -2 - nexti);
            else if (nexti > i && inclusiveEnds) i = nexti - 1;
            else i = nexti;

            if (i >= values.length)
                return accumulator;

            int toj, nextj = ranges.findNext(j, starts[i + 1], FAST);
            if (nextj < 0)
            {
                toj = nextj = -1 - nextj;
            }
            else
            {
                toj = nextj + 1;
                if (inclusiveEnds && ranges.get(nextj).end().equals(starts[i + 1]))
                    ++nextj;
            }

            if (toj > j && values[i] != null)
            {
                accumulator = fold.apply(values[i], accumulator, p1, p2, j, toj, i);
                if (terminate.test(accumulator))
                    return accumulator;
            }
            ++i;
            j = nextj;
        }
        return accumulator;
    }

    public <V2, P1, P2> V2 foldlWithDefault(Routables<?> routables, ReduceFunction<V, V2, P1, P2> fold, V defaultValue, V2 accumulator, P1 p1, P2 p2, Predicate<V2> terminate)
    {
        switch (routables.domain())
        {
            default: throw new AssertionError("Unknown domain: " + routables.domain());
            case Key: return foldlWithDefault((AbstractKeys<?>) routables, fold, defaultValue, accumulator, p1, p2, terminate);
            case Range: return foldlWithDefault((AbstractRanges) routables, fold, defaultValue, accumulator, p1, p2, terminate);
        }
    }

    private <V2, P1, P2> V2 foldlWithDefault(AbstractKeys<?> keys, ReduceFunction<V, V2, P1, P2> fold, V defaultValue, V2 accumulator, P1 p1, P2 p2, Predicate<V2> terminate)
    {
        if (values.length == 0 || keys.isEmpty())
            return fold.apply(defaultValue, accumulator, p1, p2, 0, keys.size(), 0);

        int i = 0, j = keys.find(starts[0], FAST);
        if (j < 0) j = -1 - j;
        else if (inclusiveEnds) ++j;

        if (j > 0)
            accumulator = fold.apply(defaultValue, accumulator, p1, p2, 0, j, 0);

        while (j < keys.size())
        {
            // TODO (desired): first search should be binarySearch
            i = exponentialSearch(starts, i, starts.length, keys.get(j));
            if (i < 0) i = -2 - i;
            else if (inclusiveEnds) --i;

            if (i >= values.length)
                return fold.apply(defaultValue, accumulator, p1, p2, j, keys.size(), i);

            int nextj = keys.findNext(j, starts[i + 1], FAST);
            if (nextj < 0) nextj = -1 -nextj;
            else if (inclusiveEnds) ++nextj;

            if (j != nextj)
            {
                V value = values[i];
                if (value == null)
                    value = defaultValue;

                accumulator = fold.apply(value, accumulator, p1, p2, j, nextj, i);
                if (terminate.test(accumulator))
                    return accumulator;
            }
            ++i;
            j = nextj;
        }
        return accumulator;
    }

    private <V2, P1, P2> V2 foldlWithDefault(AbstractRanges ranges, ReduceFunction<V, V2, P1, P2> fold, V defaultValue, V2 accumulator, P1 p1, P2 p2, Predicate<V2> terminate)
    {
        if (values.length == 0 || ranges.isEmpty())
            return fold.apply(defaultValue, accumulator, p1, p2, 0, ranges.size(), 0);

        int j = ranges.find(starts[0], FAST);
        if (j < 0) j = -1 - j;
        else if (inclusiveEnds && ranges.get(j).end().equals(starts[0])) ++j;

        if (j > 0 || starts[0].compareTo(ranges.get(0).start()) > 0)
            accumulator = fold.apply(defaultValue, accumulator, p1, p2, 0, j, 0);

        int i = 0;
        while (j < ranges.size())
        {
            // TODO (desired): first search should be binarySearch
            int nexti = exponentialSearch(starts, i, starts.length, ranges.get(j).start());
            if (nexti < 0) i = Math.max(i, -2 - nexti);
            else if (nexti > i && inclusiveEnds) i = nexti - 1;
            else i = nexti;

            if (i >= values.length)
                return fold.apply(defaultValue, accumulator, p1, p2, j, ranges.size(), i);

            int toj, nextj = ranges.findNext(j, starts[i + 1], FAST);
            if (nextj < 0)
            {
                toj = nextj = -1 -nextj;
            }
            else
            {
                toj = nextj + 1;
                if (inclusiveEnds && ranges.get(nextj).end().equals(starts[i + 1]))
                    ++nextj;
            }

            if (toj > j)
            {
                V value = values[i];
                if (value == null)
                    value = defaultValue;

                accumulator = fold.apply(value, accumulator, p1, p2, j, toj, i);
                if (terminate.test(accumulator))
                    return accumulator;
            }
            ++i;
            j = nextj;
        }

        return accumulator;
    }

    private int find(RoutableKey key)
    {
        int idx = Arrays.binarySearch(starts, key);
        if (idx < 0) idx = -2 - idx;
        else if (inclusiveEnds) --idx;
        return idx;
    }

    public V get(RoutableKey key)
    {
        int idx = find(key);
        if (idx < 0 || idx >= values.length)
            return null;
        return values[idx];
    }

    public static <V> ReducingRangeMap<V> create(AbstractRanges ranges, V value)
    {
        if (value == null)
            throw new IllegalArgumentException("value is null");

        if (ranges.isEmpty())
            return new ReducingRangeMap<>();

        return create(ranges, value, ReducingRangeMap.Builder::new);
    }

    public static <V> ReducingRangeMap<V> create(Range range, V value)
    {
        return new ReducingRangeMap<>(range.endInclusive(), new RoutingKey[] { range.start(), range.end() }, (V[])new Object[] { value });
    }

    public static <V, M extends ReducingRangeMap<V>> M create(Unseekables<?> keysOrRanges, V value, BuilderFactory<RoutingKey, V, M> builder)
    {
        switch (keysOrRanges.domain())
        {
            default: throw new AssertionError("Unhandled domain: " + keysOrRanges.domain());
            case Range: return create((AbstractRanges) keysOrRanges, value, builder);
            case Key: return create((AbstractUnseekableKeys) keysOrRanges, value, builder);
        }
    }

    public static <V, M extends ReducingRangeMap<V>> M create(Seekables<?, ?> keysOrRanges, V value, BuilderFactory<RoutingKey, V, M> builder)
    {
        switch (keysOrRanges.domain())
        {
            default: throw new AssertionError("Unhandled domain: " + keysOrRanges.domain());
            case Range: return create((AbstractRanges) keysOrRanges, value, builder);
            case Key: return create((Keys) keysOrRanges, value, builder);
        }
    }

    public static <V, M extends ReducingRangeMap<V>> M create(AbstractRanges ranges, V value, BuilderFactory<RoutingKey, V, M> factory)
    {
        if (value == null)
            throw new IllegalArgumentException("value is null");

        AbstractBoundariesBuilder<RoutingKey, V, M> builder = factory.create(ranges.get(0).endInclusive(), ranges.size() * 2);
        for (Range cur : ranges)
        {
            builder.append(cur.start(), value, (a, b) -> { throw new IllegalStateException(); });
            builder.append(cur.end(), null, (a, b) -> { throw new IllegalStateException(); });
        }

        return builder.build();
    }

    public static <V, M extends ReducingRangeMap<V>> M create(AbstractUnseekableKeys keys, V value, BuilderFactory<RoutingKey, V, M> factory)
    {
        if (value == null)
            throw new IllegalArgumentException("value is null");

        AbstractBoundariesBuilder<RoutingKey, V, M> builder = factory.create(keys.get(0).asRange().endInclusive(), keys.size() * 2);
        for (int i = 0 ; i < keys.size() ; ++i)
        {
            Range range = keys.get(i).asRange();
            builder.append(range.start(), value, (a, b) -> { throw new IllegalStateException(); });
            builder.append(range.end(), null, (a, b) -> { throw new IllegalStateException(); });
        }

        return builder.build();
    }

    public static <V, M extends ReducingRangeMap<V>> M create(Keys keys, V value, BuilderFactory<RoutingKey, V, M> factory)
    {
        if (value == null)
            throw new IllegalArgumentException("value is null");

        RoutingKey prev = keys.get(0).toUnseekable();
        AbstractBoundariesBuilder<RoutingKey, V, M> builder;
        {
            Range range = prev.asRange();
            builder = factory.create(prev.asRange().endInclusive(), keys.size() * 2);
            builder.append(range.start(), value, (a, b) -> { throw new IllegalStateException(); });
            builder.append(range.end(), null, (a, b) -> { throw new IllegalStateException(); });
        }

        for (int i = 1 ; i < keys.size() ; ++i)
        {
            RoutingKey unseekable = keys.get(i).toUnseekable();
            if (unseekable.equals(prev))
                continue;

            Range range = unseekable.asRange();
            builder.append(range.start(), value, (a, b) -> { throw new IllegalStateException(); });
            builder.append(range.end(), null, (a, b) -> { throw new IllegalStateException(); });
            prev = unseekable;
        }

        return builder.build();
    }

    public static <V> ReducingRangeMap<V> add(ReducingRangeMap<V> existing, Ranges ranges, V value, BiFunction<V, V, V> reduce)
    {
        ReducingRangeMap<V> add = create(ranges, value);
        return merge(existing, add, reduce);
    }

    public static ReducingRangeMap<Timestamp> add(ReducingRangeMap<Timestamp> existing, Ranges ranges, Timestamp value)
    {
        return add(existing, ranges, value, Timestamp::max);
    }

    public static <V> ReducingRangeMap<V> merge(ReducingRangeMap<V> historyLeft, ReducingRangeMap<V> historyRight, BiFunction<V, V, V> reduce)
    {
        return ReducingIntervalMap.merge(historyLeft, historyRight, reduce, ReducingRangeMap.Builder::new);
    }

    public static class Builder<V> extends AbstractBoundariesBuilder<RoutingKey, V, ReducingRangeMap<V>>
    {
        public Builder(boolean inclusiveEnds, int capacity)
        {
            super(inclusiveEnds, capacity);
        }

        @SuppressWarnings("unchecked")
        @Override
        protected ReducingRangeMap<V> buildInternal()
        {
            return new ReducingRangeMap<>(inclusiveEnds, starts.toArray(new RoutingKey[0]), (V[])values.toArray(new Object[0]));
        }
    }
}
