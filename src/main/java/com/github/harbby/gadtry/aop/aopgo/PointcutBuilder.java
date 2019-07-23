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

import com.github.harbby.gadtry.aop.JoinPoint;
import com.github.harbby.gadtry.aop.mock.AopInvocationHandler;
import com.github.harbby.gadtry.base.JavaTypes;
import com.github.harbby.gadtry.function.Function1;
import com.github.harbby.gadtry.function.exception.Function;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Method Selector
 */
public class PointcutBuilder<T>
{
    private final AopInvocationHandler aopInvocationHandler;
    private final T proxy;
    private final Function<JoinPoint, Object, Throwable> function;
    protected final List<Function1<Method, Boolean>> filters = new ArrayList<>();

    public PointcutBuilder(AopInvocationHandler aopInvocationHandler, T proxy, Function<JoinPoint, Object, Throwable> function)
    {
        this.aopInvocationHandler = aopInvocationHandler;
        this.proxy = proxy;
        this.function = function;
    }

    public T when()
    {
        aopInvocationHandler.setHandler((proxy, method, args) -> {
            filters.add(method1 -> method1 == method);
            aopInvocationHandler.initHandler();
            return JavaTypes.getClassInitValue(method.getReturnType());
        });
        return proxy;
    }

    public PointcutBuilder<T> returnType(Class<?>... returnTypes)
    {
        filters.add(method -> Stream.of(returnTypes)
                .flatMap(aClass -> {
                    if (aClass.isPrimitive()) {
                        return Stream.of(aClass, JavaTypes.getWrapperClass(aClass));
                    }
                    else {
                        return Stream.of(aClass);
                    }
                })
                .anyMatch(returnType -> returnType.isAssignableFrom(method.getReturnType())));
        return this;
    }

    public PointcutBuilder<T> annotated(Class<? extends Annotation>... methodAnnotations)
    {
        filters.add(method -> Stream.of(methodAnnotations)
                .anyMatch(ann -> method.getAnnotation(ann) != null));
        return this;
    }

    public PointcutBuilder<T> whereMethod(Function1<Method, Boolean> whereMethod)
    {
        filters.add(whereMethod);
        return this;
    }

    Aspect build()
    {
        return new Aspect()
        {
            @Override
            public Pointcut getPointcut()
            {
                return () -> filters;
            }

            @Override
            public Advice[] getAdvices()
            {
                return new Advice[] {function::apply};
            }
        };
    }
}
