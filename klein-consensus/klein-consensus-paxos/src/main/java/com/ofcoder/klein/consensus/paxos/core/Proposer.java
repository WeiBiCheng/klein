/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ofcoder.klein.consensus.paxos.core;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.EventTranslator;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.ofcoder.klein.common.Lifecycle;
import com.ofcoder.klein.common.disruptor.AbstractBatchEventHandler;
import com.ofcoder.klein.common.disruptor.DisruptorBuilder;
import com.ofcoder.klein.common.disruptor.DisruptorEvent;
import com.ofcoder.klein.common.disruptor.DisruptorExceptionHandler;
import com.ofcoder.klein.common.exception.ShutdownException;
import com.ofcoder.klein.common.util.KleinThreadFactory;
import com.ofcoder.klein.common.util.ThreadExecutor;
import com.ofcoder.klein.consensus.facade.AbstractInvokeCallback;
import com.ofcoder.klein.consensus.facade.MemberManager;
import com.ofcoder.klein.consensus.facade.Quorum;
import com.ofcoder.klein.consensus.facade.Result;
import com.ofcoder.klein.consensus.facade.config.ConsensusProp;
import com.ofcoder.klein.consensus.facade.exception.ConsensusException;
import com.ofcoder.klein.consensus.paxos.PaxosNode;
import com.ofcoder.klein.consensus.paxos.rpc.vo.AcceptReq;
import com.ofcoder.klein.consensus.paxos.rpc.vo.AcceptRes;
import com.ofcoder.klein.consensus.paxos.rpc.vo.ConfirmReq;
import com.ofcoder.klein.consensus.paxos.rpc.vo.PrepareReq;
import com.ofcoder.klein.consensus.paxos.rpc.vo.PrepareRes;
import com.ofcoder.klein.rpc.facade.Endpoint;
import com.ofcoder.klein.rpc.facade.InvokeParam;
import com.ofcoder.klein.rpc.facade.RpcClient;
import com.ofcoder.klein.rpc.facade.RpcEngine;
import com.ofcoder.klein.rpc.facade.RpcProcessor;
import com.ofcoder.klein.rpc.facade.serialization.Hessian2Util;
import com.ofcoder.klein.storage.facade.Instance;

/**
 * @author far.liu
 */
public class Proposer implements Lifecycle<ConsensusProp> {
    private static final Logger LOG = LoggerFactory.getLogger(Proposer.class);
    private static final int RUNNING_BUFFER_SIZE = 16384;
    private final AtomicReference<PrepareState> skipPrepare = new AtomicReference<>(PrepareState.NO_PREPARE);
    private RpcClient client;
    private ConsensusProp prop;
    private final PaxosNode self;
    private long prepareTimeout;
    private long acceptTimeout;
    /**
     * Disruptor to run propose.
     */
    private Disruptor<ProposeWithDone> proposeDisruptor;
    private RingBuffer<ProposeWithDone> proposeQueue;
    private CountDownLatch shutdownLatch;

    public Proposer(PaxosNode self) {
        this.self = self;
    }

    @Override
    public void init(ConsensusProp op) {
        this.prop = op;
        this.client = RpcEngine.getClient();
        this.prepareTimeout = (long) (op.getRoundTimeout() * 0.3);
        this.acceptTimeout = op.getRoundTimeout() - prepareTimeout;

        this.proposeDisruptor = DisruptorBuilder.<ProposeWithDone>newInstance()
                .setRingBufferSize(RUNNING_BUFFER_SIZE)
                .setEventFactory(ProposeWithDone::new)
                .setThreadFactory(KleinThreadFactory.create("klein-paxos-propose-disruptor-", true)) //
                .setProducerType(ProducerType.MULTI)
                .setWaitStrategy(new BlockingWaitStrategy())
                .build();
        this.proposeDisruptor.handleEventsWith(new ProposeEventHandler(this.prop.getBatchSize()));
        this.proposeDisruptor.setDefaultExceptionHandler(new DisruptorExceptionHandler<Object>(getClass().getSimpleName()));
        this.proposeQueue = this.proposeDisruptor.start();
    }

    @Override
    public void shutdown() {
        if (this.proposeQueue != null) {
            this.shutdownLatch = new CountDownLatch(1);
            this.proposeQueue.publishEvent((event, sequence) -> event.setShutdownLatch(this.shutdownLatch));
            try {
                shutdownLatch.await();
            } catch (InterruptedException e) {
                throw new ShutdownException(e.getMessage(), e);
            }
        }
    }

    public <E extends Serializable> void propose(final E data, final ProposeDone done) {
        if (this.shutdownLatch != null) {
            throw new ConsensusException("klein is shutting down.");
        }

        final EventTranslator<ProposeWithDone> translator = (event, sequence) -> {
            event.done = done;
            event.data = data;
        };
        this.proposeQueue.publishEvent(translator);
    }

    public void accept(final ProposeContext ctxt, PhaseCallback.AcceptPhaseCallback callback) {
        LOG.info("start accept phase, instanceId: {}", ctxt.getInstanceId());

        final AcceptReq req = AcceptReq.Builder.anAcceptReq()
                .nodeId(self.getSelf().getId())
                .instanceId(ctxt.getInstanceId())
                .proposalNo(self.getCurProposalNo())
                .datas(ctxt.getPrepareQuorum().getTempValue() != null ? ctxt.getPrepareQuorum().getTempValue() : ctxt.getDatas())
                .build();

        InvokeParam param = InvokeParam.Builder.anInvokeParam()
                .service(AcceptReq.class.getSimpleName())
                .method(RpcProcessor.KLEIN)
                .data(ByteBuffer.wrap(Hessian2Util.serialize(req))).build();

        MemberManager.getAllMembers().forEach(it -> {
            client.sendRequestAsync(it, param, new AbstractInvokeCallback<AcceptRes>() {
                @Override
                public void error(Throwable err) {
                    LOG.error(err.getMessage(), err);

                    ctxt.getPrepareQuorum().refuse(it);
                    if (ctxt.getPrepareQuorum().isGranted() == Quorum.GrantResult.REFUSE
                            && ctxt.getPrepareNexted().compareAndSet(false, true)) {
                        synchronized (skipPrepare) {
                            skipPrepare.compareAndSet(PrepareState.PREPARED, PrepareState.NO_PREPARE);
                        }
                        prepare(ctxt, new PrepareCallback());
                    }
                }

                @Override
                public void complete(AcceptRes result) {
                    handleAcceptRespose(ctxt, callback, result, it);
                }
            }, acceptTimeout);
        });

    }

    private void handleAcceptRespose(final ProposeContext ctxt, final PhaseCallback.AcceptPhaseCallback callback, final AcceptRes result
            , final Endpoint it) {
        LOG.info("handling node-{}'s accept response", result.getNodeId());

        if (result.getResult()) {
            ctxt.getAcceptQuorum().grant(it);
            if (ctxt.getAcceptQuorum().isGranted() == Quorum.GrantResult.PASS
                    && ctxt.getAcceptNexted().compareAndSet(false, true)) {
                // do learn phase and return client.
                callback.granted(ctxt);
            }
        } else {
            ctxt.getAcceptQuorum().refuse(it);

            final long selfProposalNo = self.getCurProposalNo();
            long diff = result.getProposalNo() - selfProposalNo;
            if (diff > 0) {
                self.addProposalNo(diff);
            }

            // do prepare phase
            if (ctxt.getAcceptQuorum().isGranted() == Quorum.GrantResult.REFUSE
                    && ctxt.getAcceptNexted().compareAndSet(false, true)) {
                synchronized (skipPrepare) {
                    skipPrepare.compareAndSet(PrepareState.PREPARED, PrepareState.NO_PREPARE);
                }
                prepare(ctxt, new PrepareCallback());
            }
        }
    }

    /**
     * 并行协商多个instance，不能跳过prepare
     * <p>
     * <instanceId, proposalNo>
     * A: pre<2, 1> → A, C
     * A: acc<2, 1> → A, C    reach consensus.
     * <p>
     * B-T1: pre<2, 1>           network failure.
     * B-T1: pre<2, 2>           network failure.
     * B-T2: pre<3, 3> → A, B, C
     * B-T1: notify, acc<2, 3>   once again reach consensus.
     *
     * @param ctxt
     * @param callback
     */
    public void prepare(final ProposeContext ctxt, final PhaseCallback.PreparePhaseCallback callback) {
        LOG.info("start prepare phase, instanceId: {}, the {} retry", ctxt.getInstanceId(), ctxt.getTimes());

        // limit the prepare phase to only one thread.
        if (!skipPrepare.compareAndSet(PrepareState.NO_PREPARE, PrepareState.PREPARING)) {
            synchronized (skipPrepare) {
                try {
                    skipPrepare.wait();
                } catch (InterruptedException e) {
                    throw new ConsensusException(e.getMessage(), e);
                }
            }
            if (skipPrepare.get() == PrepareState.PREPARED
                    && ctxt.getPrepareNexted().compareAndSet(false, true)) {
                callback.granted(ctxt);
                return;
            } else {
                prepare(ctxt, callback);
                return;
            }
        }

        // do prepare
        forcePrepare(ctxt, callback);
    }

    public void forcePrepare(ProposeContext ctxt, PhaseCallback.PreparePhaseCallback callback) {
        // check retry times, refused() is invoked only when the number of retry times reaches the threshold
        if (ctxt.getTimesAndIncrement() >= this.prop.getRetry()) {
            callback.refused(ctxt);
            return;
        }

        ctxt.reset();
        long proposalNo = self.incrementProposalNo();

        PrepareReq req = PrepareReq.Builder.aPrepareReq()
                .instanceId(ctxt.getInstanceId())
                .nodeId(self.getSelf().getId())
                .proposalNo(proposalNo)
                .build();

        InvokeParam param = InvokeParam.Builder.anInvokeParam()
                .service(PrepareReq.class.getSimpleName())
                .method(RpcProcessor.KLEIN)
                .data(ByteBuffer.wrap(Hessian2Util.serialize(req))).build();

        // fixme exclude self
        MemberManager.getAllMembers().forEach(it -> {
            client.sendRequestAsync(it, param, new AbstractInvokeCallback<PrepareRes>() {
                @Override
                public void error(Throwable err) {
                    LOG.error(err.getMessage(), err);
                    ctxt.getPrepareQuorum().refuse(it);
                    if (ctxt.getPrepareQuorum().isGranted() == Quorum.GrantResult.REFUSE
                            && ctxt.getPrepareNexted().compareAndSet(false, true)) {
                        forcePrepare(ctxt, callback);
                    }
                }

                @Override
                public void complete(PrepareRes result) {
                    handlePrepareResponse(ctxt, callback, result, it);
                }
            }, prepareTimeout);
        });
    }

    private void handlePrepareResponse(final ProposeContext ctxt, final PhaseCallback.PreparePhaseCallback callback, final PrepareRes result
            , final Endpoint it) {
        LOG.info("handling node-{}'s prepare response", result.getNodeId());

        if (result.getGrantValue() != null && result.getProposalNo() > ctxt.getPrepareQuorum().getMaxRefuseProposalNo()) {
            // confirmed instance must have been included.
            // this is because an instance in state CONFIRMED must be copied to the majority,
            // and the instance in maxProposalNo must be the same as that in state CONFIRMED
            ctxt.getPrepareQuorum().setTempValue(result.getProposalNo(), result.getGrantValue());
        }

        if (result.getResult()) {
            ctxt.getPrepareQuorum().grant(it);
            if (ctxt.getPrepareQuorum().isGranted() == Quorum.GrantResult.PASS
                    && ctxt.getPrepareNexted().compareAndSet(false, true)) {
                // do accept phase.
                callback.granted(ctxt);
            }
        } else {
            ctxt.getPrepareQuorum().refuse(it);
            final long selfProposalNo = self.getCurProposalNo();
            long diff = result.getProposalNo() - selfProposalNo;
            if (diff > 0) {
                self.addProposalNo(diff);
            }

            if (result.getState() == Instance.State.CONFIRMED
                    && ctxt.getPrepareNexted().compareAndSet(false, true)) {
                callback.confirmed(ctxt);
            } else {
                // do prepare phase
                if (ctxt.getPrepareQuorum().isGranted() == Quorum.GrantResult.REFUSE
                        && ctxt.getPrepareNexted().compareAndSet(false, true)) {
                    forcePrepare(ctxt, callback);
                }
            }
        }
    }


    public static class ProposeWithDone extends DisruptorEvent {
        private Object data;
        private ProposeDone done;

        public Object getData() {
            return data;
        }

        public ProposeDone getDone() {
            return done;
        }
    }

    public class ProposeEventHandler extends AbstractBatchEventHandler<ProposeWithDone> {

        public ProposeEventHandler(int batchSize) {
            super(batchSize);
        }

        @Override
        protected void handle(List<ProposeWithDone> events) {
            LOG.info("start negotiations, proposal size: {}", events.size());

            final List<ProposeWithDone> finalEvents = ImmutableList.copyOf(events);
            //
            ProposeContext ctxt = new ProposeContext(self.incrementInstanceId(), finalEvents);
            if (Proposer.this.skipPrepare.get() != PrepareState.PREPARED) {
                prepare(ctxt, new PrepareCallback());
            }
        }
    }

    public class PrepareCallback implements PhaseCallback.PreparePhaseCallback {

        @Override
        public void granted(ProposeContext context) {
            synchronized (skipPrepare) {
                skipPrepare.compareAndSet(PrepareState.PREPARING, PrepareState.PREPARED);
                skipPrepare.notifyAll();
            }
            accept(context, new AcceptCallback());
        }

        @Override
        public void confirmed(ProposeContext context) {
            synchronized (skipPrepare) {
                skipPrepare.compareAndSet(PrepareState.PREPARING, PrepareState.NO_PREPARE);
                skipPrepare.notifyAll();
            }

            ThreadExecutor.submit(() -> {
                // confirm
                RoleAccessor.getLearner().handleConfirmRequest(
                        ConfirmReq.Builder.aConfirmReq().nodeId(self.getSelf().getId())
                                .datas(context.getPrepareQuorum().getTempValue())
                                .instanceId(context.getInstanceId())
                                .build()
                );

                for (ProposeDone event : context.getDones()) {
                    event.done(Result.UNKNOWN);
                }
            });
        }

        @Override
        public void refused(ProposeContext context) {
            synchronized (skipPrepare) {
                skipPrepare.compareAndSet(PrepareState.PREPARING, PrepareState.NO_PREPARE);
                skipPrepare.notifyAll();
            }

            ThreadExecutor.submit(() -> {
                for (ProposeDone event : context.getDones()) {
                    event.done(Result.FAILURE);
                }
            });
        }
    }

    public class AcceptCallback implements PhaseCallback.AcceptPhaseCallback {

        @Override
        public void granted(ProposeContext context) {

            ThreadExecutor.submit(() -> {
                // do confirm
                RoleAccessor.getLearner().confirm(context.getInstanceId(), context.getDatas());

                for (ProposeDone event : context.getDones()) {
                    event.done(Result.SUCCESS);
                }
            });

        }
    }


}
