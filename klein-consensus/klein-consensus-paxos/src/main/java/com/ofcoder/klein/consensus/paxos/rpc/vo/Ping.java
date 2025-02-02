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
package com.ofcoder.klein.consensus.paxos.rpc.vo;

/**
 * @author 释慧利
 */
public class Ping extends BaseReq {

    private long maxAppliedInstanceId;
    private long maxInstanceId;
    private long lastCheckpoint;

    public long getMaxAppliedInstanceId() {
        return maxAppliedInstanceId;
    }

    public void setMaxAppliedInstanceId(long maxAppliedInstanceId) {
        this.maxAppliedInstanceId = maxAppliedInstanceId;
    }

    public long getMaxInstanceId() {
        return maxInstanceId;
    }

    public void setMaxInstanceId(long maxInstanceId) {
        this.maxInstanceId = maxInstanceId;
    }

    public long getLastCheckpoint() {
        return lastCheckpoint;
    }

    public void setLastCheckpoint(long lastCheckpoint) {
        this.lastCheckpoint = lastCheckpoint;
    }

    public static final class Builder {
        private String nodeId;
        private long proposalNo;
        private int memberConfigurationVersion;
        private long maxAppliedInstanceId;
        private long maxInstanceId;
        private long lastCheckpoint;

        private Builder() {
        }

        public static Builder aPing() {
            return new Builder();
        }

        public Builder nodeId(String nodeId) {
            this.nodeId = nodeId;
            return this;
        }

        public Builder proposalNo(long proposalNo) {
            this.proposalNo = proposalNo;
            return this;
        }

        public Builder memberConfigurationVersion(int memberConfigurationVersion) {
            this.memberConfigurationVersion = memberConfigurationVersion;
            return this;
        }

        public Builder maxAppliedInstanceId(long maxAppliedInstanceId) {
            this.maxAppliedInstanceId = maxAppliedInstanceId;
            return this;
        }

        public Builder maxInstanceId(long maxInstanceId) {
            this.maxInstanceId = maxInstanceId;
            return this;
        }

        public Builder lastCheckpoint(long lastCheckpoint) {
            this.lastCheckpoint = lastCheckpoint;
            return this;
        }

        public Ping build() {
            Ping ping = new Ping();
            ping.setNodeId(nodeId);
            ping.setProposalNo(proposalNo);
            ping.setMemberConfigurationVersion(memberConfigurationVersion);
            ping.maxInstanceId = this.maxInstanceId;
            ping.maxAppliedInstanceId = this.maxAppliedInstanceId;
            ping.lastCheckpoint = this.lastCheckpoint;
            return ping;
        }
    }
}
