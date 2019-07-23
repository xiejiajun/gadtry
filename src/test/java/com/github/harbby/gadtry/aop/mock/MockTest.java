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
package com.github.harbby.gadtry.aop.mock;

import com.github.harbby.gadtry.base.Throwables;
import com.github.harbby.gadtry.collection.mutable.MutableList;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static com.github.harbby.gadtry.aop.mock.MockGo.doAround;
import static com.github.harbby.gadtry.aop.mock.MockGo.doReturn;
import static com.github.harbby.gadtry.aop.mock.MockGo.doThrow;
import static com.github.harbby.gadtry.aop.mock.MockGo.when;
import static com.github.harbby.gadtry.aop.mock.MockGoArgument.anyInt;

public class MockTest
{
    @Test
    public void mockSpy()
    {
        List<String> list = MutableList.of("1", "2", "3");
        List<String> proxy = MockGo.spy(list);
        doReturn(7).when(proxy).size();
        doAround(proxyContext -> "123").when(proxy).toString();
        doThrow(new IOException("mockDoThrow")).when(proxy).get(anyInt());

        Assert.assertEquals(proxy.size(), 7);
        Assert.assertEquals("123", proxy.toString());
        Assert.assertTrue(proxy.stream() instanceof Stream);
        try {
            proxy.get(0);
            Assert.fail();
            Throwables.throwsException(IOException.class);
        }
        catch (IOException e) {
            Assert.assertEquals(e.getMessage(), "mockDoThrow");
        }
    }

    @Test
    public void mockSpyByWhen()
    {
        List<String> proxy = MockGo.spy(MutableList.of("1", "2", "3"));
        when(proxy.size()).thenReturn(7);
        when(proxy.toString()).thenAround(proxyContext -> "123");
        when(proxy.get(anyInt())).thenThrow(new IOException("mockDoThrow"));

        Assert.assertEquals(proxy.size(), 7);
        Assert.assertEquals("123", proxy.toString());
        Assert.assertTrue(proxy.stream() instanceof Stream);
        try {
            proxy.get(0);
            Assert.fail();
            Throwables.throwsException(IOException.class);
        }
        catch (IOException e) {
            Assert.assertEquals(e.getMessage(), "mockDoThrow");
        }
    }

    @Test
    public void mockUseDoWhen()
    {
        List<String> proxy = MockGo.mock(List.class);
        doReturn(7).when(proxy).size();
        doAround(proxyContext -> {
            proxyContext.proceed();
            return "123";
        }).when(proxy).toString();
        doThrow(new RuntimeException("mockDoThrow")).when(proxy).get(anyInt());

        Assert.assertEquals(proxy.size(), 7);
        Assert.assertEquals("123", proxy.toString());
        Assert.assertEquals(null, proxy.stream());
        try {
            proxy.get(0);
            Assert.fail();
        }
        catch (RuntimeException e) {
            Assert.assertEquals(e.getMessage(), "mockDoThrow");
        }
    }

    @Test
    public void duplicateMockWhenThen()
    {
        List<String> proxy = MockGo.mock(List.class);
        when(proxy.size()).thenReturn(7);
        Assert.assertEquals(proxy.size(), 7);
        when(proxy.size()).thenReturn(8);
        Assert.assertEquals(proxy.size(), 8);
    }

    @Test
    public void duplicateMockDoWhen()
    {
        List<String> proxy = MockGo.mock(List.class);
        doReturn(7).when(proxy).size();
        Assert.assertEquals(proxy.size(), 7);
        doReturn(8).when(proxy).size();
        Assert.assertEquals(proxy.size(), 8);
    }

    @Test
    public void verify()
    {
//        Mockito.verify(proxy, Mockito.times(5)).get(anyInt());
//        Mockito.verify(proxy, Mockito.times(5)).get(2);
//        Mockito.verify(proxy, VerificationModeFactory.only()).size();
//        Mockito.verify(proxy, Mockito.timeout(1)).size();
//        Assert.assertEquals(Mockito.analaysis(proxy.get(2)).getTirggTimes(), 5);
    }

    @Test
    public void mockByWhen()
    {
        List<String> proxy = MockGo.mock(List.class);
        when(proxy.size()).thenReturn(7);
        when(proxy.toString()).thenAround(proxyContext -> "123");
        when(proxy.get(anyInt())).thenThrow(new RuntimeException("mockDoThrow"));

        Assert.assertEquals(proxy.size(), 7);
        Assert.assertEquals("123", proxy.toString());
        Assert.assertEquals(null, proxy.stream());
        try {
            proxy.get(0);
            Assert.fail();
        }
        catch (RuntimeException e) {
            Assert.assertEquals(e.getMessage(), "mockDoThrow");
        }
    }

    @Test(expected = MockGoException.class)
    public void doNothingTest()
    {
        List<String> proxy = MockGo.spy(new ArrayList<>());
        MockGo.doNothing().when(proxy).size();
        proxy.size();
    }
}