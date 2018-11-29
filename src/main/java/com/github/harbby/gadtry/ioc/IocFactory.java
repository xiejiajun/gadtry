/*
 * Copyright (C) 2018 The Harbby Authors
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
package com.github.harbby.gadtry.ioc;

import com.github.harbby.gadtry.base.Lazys;
import com.github.harbby.gadtry.function.Creator;

/**
 * harbby ioc
 */
public interface IocFactory
{
    /**
     * @param driver Class waiting to be acquired
     * @param <T> is driver type
     * @return Driver instance object
     * @throws InjectorException Injector error
     */
    public <T> T getInstance(Class<T> driver);

    /**
     * @param driver Class waiting to be acquired
     * @param other User-provided implementation
     * @param <T> is driver type
     * @return T Driver instance object
     * @throws InjectorException Injector error
     */
    public <T> T getInstance(Class<T> driver, IocFactory.Function<Class<?>, ?> other);

    public <T> Creator<T> getCreator(Class<T> driver);

    public <T> Binds getAllBeans();

    public static IocFactory create(Bean... beans)
    {
        final Binds.Builder builder = Binds.builder();
        final InternalContext context = InternalContext.of(builder.build(), (x) -> null);
        final Binder binder = new Binder()
        {
            @Override
            public <T> void bind(Class<T> key, T instance)
            {
                builder.bind(key, () -> instance);
            }

            @Override
            public <T> BinderBuilder<T> bind(Class<T> key)
            {
                return new BinderBuilder<T>()
                {
                    @Override
                    public void withSingle()
                    {
                        builder.bind(key, Lazys.goLazy(() -> context.getByNew(key)));
                    }

                    @Override
                    public BindingSetting by(Class<? extends T> createClass)
                    {
                        Creator<T> creator = () -> context.getByNew(createClass);
                        builder.bind(key, creator);
                        return () -> builder.bindUpdate(key, Lazys.goLazy(creator));
                    }

                    @Override
                    public void byInstance(T instance)
                    {
                        builder.bind(key, () -> instance);
                    }

                    @Override
                    public BindingSetting byCreator(Creator<? extends T> creator)
                    {
                        builder.bind(key, creator);
                        return () -> builder.bindUpdate(key, Lazys.goLazy(creator));
                    }

                    @Override
                    public BindingSetting byCreator(Class<? extends Creator<T>> creatorClass)
                    {
                        try {
                            return this.byCreator(creatorClass.newInstance());
                        }
                        catch (InstantiationException | IllegalAccessException e) {
                            throw new InjectorException(e);
                        }
                    }
                };
            }
        };

        for (Bean bean : beans) {
            bean.configure(binder);
        }
        Binds binds = builder.build();
        return new IocFactoryImpl(binds);
    }

    @FunctionalInterface
    public static interface Function<F0, F1>
    {
        F1 apply(F0 f0)
                throws Exception;
    }
}
