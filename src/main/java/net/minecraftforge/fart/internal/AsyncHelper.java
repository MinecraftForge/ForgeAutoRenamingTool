/*
 * Forge Auto Renaming Tool
 * Copyright (c) 2021
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package net.minecraftforge.fart.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

class AsyncHelper {
    //ExecutorService exec = Executors.newSingleThreadExecutor();
    ExecutorService exec = Executors.newWorkStealingPool();

    public <I,O> void consumeAll(Collection<? extends I> inputs, Consumer<I> consumer) {
        Function<I, Callable<Void>> toCallable = i -> () -> {
            consumer.accept(i);
            return null;
        };
        invokeAll(inputs.stream().map(toCallable).collect(Collectors.toList()));
    }

    public <I,O> List<O> invokeAll(Collection<? extends I> inputs, Function<I, O> converter) {
        Function<I, Callable<O>> toCallable = i -> () -> converter.apply(i);
        return invokeAll(inputs.stream().map(toCallable).collect(Collectors.toList()));
    }

    public <O> List<O> invokeAll(Collection<? extends Callable<O>> tasks) {
        try {
            List<O> ret = new ArrayList<>();
            List<Future<O>> processed = exec.invokeAll(tasks);
            for (Future<O> future : processed) {
                O done = future.get();
                if (done != null)
                    ret.add(done);
            }
            return ret;
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    public void shutdown() {
        exec.shutdown();
    }
}
