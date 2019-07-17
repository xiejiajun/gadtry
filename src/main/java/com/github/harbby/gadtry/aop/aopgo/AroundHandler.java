/*
 * Copyright (C) 2018 The GadTry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.harbby.gadtry.aop.aopgo;

import com.github.harbby.gadtry.aop.ProxyContext;
import com.github.harbby.gadtry.aop.model.After;
import com.github.harbby.gadtry.aop.model.AfterReturning;
import com.github.harbby.gadtry.aop.model.AfterThrowing;
import com.github.harbby.gadtry.aop.model.Before;
import com.github.harbby.gadtry.function.exception.Consumer;
import com.github.harbby.gadtry.function.exception.Function;

public interface AroundHandler
        extends Function<ProxyContext, Object, Throwable>
{
    static AroundHandler doBefore(Consumer<Before, Exception> before)
    {
        return f -> {
            before.apply(Before.of(f.getMethod(), f.getArgs()));
            return f.proceed();
        };
    }

    static AroundHandler doAfterReturning(Consumer<AfterReturning, Exception> afterReturning)
    {
        return f -> {
            Object value = f.proceed();
            afterReturning.apply(AfterReturning.of(f.getMethod(), f.getArgs(), value));
            return value;
        };
    }

    static AroundHandler doAfterThrowing(Consumer<AfterThrowing, Exception> afterThrowing)
    {
        return f -> {
            try {
                return f.proceed();
            }
            catch (Throwable e) {
                afterThrowing.apply(AfterThrowing.of(f.getMethod(), f.getArgs(), e));
                throw e;
            }
        };
    }

    static AroundHandler doAfter(Consumer<After, Exception> after)
    {
        return f -> {
            Object value = null;
            Throwable throwable = null;
            try {
                value = f.proceed();
                return value;
            }
            catch (Throwable e) {
                throwable = e;
                throw e;
            }
            finally {
                after.apply(After.of(f.getMethod(), f.getArgs(), value, throwable));
            }
        };
    }

    static AroundHandler doAround(Function<ProxyContext, Object, Throwable> aroundContext)
    {
        return aroundContext::apply;
    }
}
