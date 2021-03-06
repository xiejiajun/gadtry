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
package com.github.harbby.gadtry.aop;

import com.github.harbby.gadtry.aop.mock.MockGo;
import com.github.harbby.gadtry.aop.mock.MockGoException;
import com.github.harbby.gadtry.aop.resource.PackageTestName;
import com.github.harbby.gadtry.aop.resource.impl.PackageTestUtil;
import com.github.harbby.gadtry.base.JavaTypes;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PackageProxyTests
{
    @Test
    public void packageProxyFindJavaKeyWorldTest()
    {
        List<String> actions = new ArrayList<>();
        try {
            AopGo.proxy(JavaTypes.<Set<String>>classTag(Set.class))
                    .byInstance(new HashSet<>())
                    .basePackage("test.aop.int")
                    .aop(binder -> {
                        binder.doBefore(before -> {
                            actions.add("before1");
                        }).when().size();
                    })
                    .build();
        }
        catch (MockGoException e) {
            Assert.assertEquals("package name [test.aop.int] find exists JAVA system keywords int", e.getMessage());
        }
    }

    @Test
    public void packageProxyTest()
    {
        String basePackage = "test.aop.package1";
        List<String> actions = new ArrayList<>();
        Set<String> set = AopGo.proxy(JavaTypes.<Set<String>>classTag(Set.class))
                .byInstance(new HashSet<>())
                .basePackage(basePackage)
                .aop(binder -> {
                    binder.doBefore(before -> {
                        actions.add("before1");
                    }).when().size();
                })
                .build();
        Assert.assertTrue(set.getClass().getName().startsWith(basePackage));

        set.size();
        Assert.assertEquals(Arrays.asList("before1"), actions);
    }

    @Test
    public void mockGoNotAccessMethod()
    {
        PackageTestName instance = PackageTestUtil.getInstance();
        PackageTestName mockito = (PackageTestName) MockGo.mock(instance.getClass().getInterfaces()[0]);
        MockGo.doAnswer(joinPoint -> {
            joinPoint.getMethod().setAccessible(true); //must
            Object value = joinPoint.getMethod().invoke(instance, joinPoint.getArgs());
            if (value == null && joinPoint.getMethod().getName().equals("getName")) {
                return "harbby";
            }
            else {
                return value;
            }
        }).when(mockito).getName();
        Assert.assertEquals(mockito.getName(), "harbby1");
        PackageTestUtil.getAge(mockito);
    }

    @Test
    public void mockitoGoNotAccessMethod()
    {
        PackageTestName instance = PackageTestUtil.getInstance();
        PackageTestName mockito = (PackageTestName) Mockito.mock(instance.getClass().getInterfaces()[0]);
        Mockito.doAnswer(joinPoint -> {
            //joinPoint.getMethod().setAccessible(true); //must
            Object value = joinPoint.getMethod().invoke(instance, joinPoint.getArguments());
            if (value == null && joinPoint.getMethod().getName().equals("getName")) {
                return "harbby";
            }
            else {
                return value;
            }
        }).when(mockito).getName();
        try {
            Assert.assertEquals(mockito.getName(), "harbby1");
        }
        catch (Exception e) {
            e.printStackTrace();
            Assert.assertTrue(e.getMessage()
                    .contains(this.getClass().getName()
                            + " can not access a member of class "
                            + instance.getClass().getInterfaces()[0].getName()));
        }
        PackageTestUtil.getAge(mockito);
    }

    /**
     * 这个例子，演示由于中间某个接口被包范围限制导致代理后无法访问,将抛出 UndeclaredThrowableException
     */
    @Test
    public void jdkProxyNotAccessInterfaceTest()
    {
        PackageTestName instance = PackageTestUtil.getInstance();
        InvocationHandler invocationHandler = (proxy, method, args) -> {
            //method.setAccessible(true);   //打开即可访问
            return method.invoke(instance, args);
        };

        PackageTestName proxy = (PackageTestName) Proxy.newProxyInstance(
                this.getClass().getClassLoader(),
                instance.getClass().getInterfaces(),
                invocationHandler);
        Assert.assertEquals(proxy.getName(), "harbby1");

        try {
            PackageTestUtil.getAge(proxy);
        }
        catch (java.lang.reflect.UndeclaredThrowableException e) {
            e.printStackTrace();
            Assert.assertTrue(e.getCause().getMessage()
                    .contains(this.getClass().getName()
                            + " can not access a member of class "
                            + instance.getClass().getInterfaces()[0].getName()));
        }
    }

    @Test
    public void gadtryProxyNotAccessInterfaceTest()
    {
        PackageTestName instance = PackageTestUtil.getInstance();
        Class<PackageTestName> interfaceClass = JavaTypes.classTag(instance.getClass().getInterfaces()[0]);

        PackageTestName proxy = AopGo.proxy(interfaceClass)
                .byInstance(instance)
                .aop(binder -> {
                    binder.doAround(joinPoint -> {
                        joinPoint.getMethod().setAccessible(true);  //must
                        return joinPoint.proceed();
                    }).allMethod();
                })
                .build();
        Assert.assertEquals(proxy.getName(), "harbby1");
        PackageTestUtil.getAge(proxy);
    }

    /**
     * 无权访问型
     */
    @Test
    public void cannotAccessClassTest()
    {
        String basePackage = "test.aop.package1";
        PackageTestName instance = PackageTestUtil.getInstance();
        try {
            AopGo.proxy(instance)
                    .basePackage(basePackage)
                    .aop(binder -> {
                        binder.doBefore(before -> {}).allMethod();
                    })
                    .build();
        }
        catch (MockGoException e) {
            e.printStackTrace();
            Assert.assertTrue(e.getMessage()
                    .contains("cannot access its superclass " + instance.getClass().getName()));
        }
    }

    /**
     * 无权访问型
     */
    @Test
    public void doAccessClassTest()
    {
        List<String> actions = new ArrayList<>();

        PackageTestName instance = PackageTestUtil.getInstance();
        PackageTestName proxy = AopGo.proxy(instance)
                .aop(binder -> {
                    binder.doBefore(before -> {
                        before.getMethod().setAccessible(true);
                        actions.add("before1");
                        actions.add(before.getName());
                    }).allMethod();
                })
                .build();
        Assert.assertTrue(proxy.getClass().getPackage() != null);
        Assert.assertTrue(proxy.getClass().getPackage() == instance.getClass().getPackage());
        System.out.println(proxy.getClass().getPackage());

        Assert.assertEquals(proxy.getName(), "harbby1");
        Assert.assertEquals(Arrays.asList("before1", "getName"), actions);
        Assert.assertEquals(PackageTestUtil.getAge(proxy), 18);
    }
}
