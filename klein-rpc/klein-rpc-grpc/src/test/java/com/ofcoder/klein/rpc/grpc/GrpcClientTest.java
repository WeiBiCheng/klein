package com.ofcoder.klein.rpc.grpc;

import com.ofcoder.klein.rpc.facade.Endpoint;
import com.ofcoder.klein.rpc.facade.InvokeParam;
import com.ofcoder.klein.rpc.facade.RpcClient;
import com.ofcoder.klein.rpc.facade.RpcServer;
import com.ofcoder.klein.rpc.facade.config.RpcProp;
import com.ofcoder.klein.rpc.grpc.ext.HelloProcessor;
import com.ofcoder.klein.spi.ExtensionLoader;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;

/**
 * @author: 释慧利
 */
public class GrpcClientTest {
    private static final Logger LOG = LoggerFactory.getLogger(GrpcClientTest.class);
    private HelloProcessor processor = new HelloProcessor();

    private RpcClient rpcClient;
    private RpcServer rpcServer;

    @Before
    public void setup() {
        RpcProp prop = new RpcProp();
        rpcServer = ExtensionLoader.getExtensionLoader(RpcServer.class).getJoin("grpc");
        rpcClient = ExtensionLoader.getExtensionLoader(RpcClient.class).getJoin("grpc");
        rpcServer.init(prop);
        rpcClient.init(prop);
        rpcServer.registerProcessor(processor);
    }

    @After
    public void shutdown() {
        rpcClient.shutdown();
        rpcServer.shutdown();
    }

    @Test
    public void testSendRequest() {
        InvokeParam param = new InvokeParam();
        param.setData("I'm Klein");
        param.setService(processor.service());
        param.setMethod(processor.method());
        CountDownLatch latch = new CountDownLatch(1);
        rpcClient.sendRequest(new Endpoint("127.0.0.1", 1218), param, (result, err) -> {
            if (err != null) {
                LOG.error(err.getMessage(), err);
            }
            LOG.info("receive server message: {}", result);
            latch.countDown();
        }, 5000);

        try {
            latch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

    }

}