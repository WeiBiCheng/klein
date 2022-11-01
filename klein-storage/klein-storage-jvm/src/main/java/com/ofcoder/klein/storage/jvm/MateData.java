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
package com.ofcoder.klein.storage.jvm;/**
 * @author far.liu
 */

import java.io.Serializable;

/**
 * @author 释慧利
 */
public class MateData implements Serializable {
    private long maxInstanceId;
    private long maxAppliedInstanceId;
    private long maxProposalNo;
    private SnapMate lastSnap;

    public long getMaxInstanceId() {
        return maxInstanceId;
    }

    public void setMaxInstanceId(long maxInstanceId) {
        this.maxInstanceId = maxInstanceId;
    }

    public long getMaxAppliedInstanceId() {
        return maxAppliedInstanceId;
    }

    public void setMaxAppliedInstanceId(long maxAppliedInstanceId) {
        this.maxAppliedInstanceId = maxAppliedInstanceId;
    }

    public long getMaxProposalNo() {
        return maxProposalNo;
    }

    public void setMaxProposalNo(long maxProposalNo) {
        this.maxProposalNo = maxProposalNo;
    }

    public SnapMate getLastSnap() {
        return lastSnap;
    }

    public void setLastSnap(SnapMate lastSnap) {
        this.lastSnap = lastSnap;
    }


    public static final class SnapMate implements Serializable {
        private long checkpoint;
        private String path;

        public SnapMate() {
        }

        public SnapMate(long checkpoint, String path) {
            this.checkpoint = checkpoint;
            this.path = path;
        }

        public long getCheckpoint() {
            return checkpoint;
        }

        public void setCheckpoint(long checkpoint) {
            this.checkpoint = checkpoint;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }
    }

    public static final class Builder {
        private long maxInstanceId;
        private long maxAppliedInstanceId;
        private long maxProposalNo;
        private SnapMate lastSnap;

        private Builder() {
        }

        public static Builder aMateData() {
            return new Builder();
        }

        public Builder maxInstanceId(long maxInstanceId) {
            this.maxInstanceId = maxInstanceId;
            return this;
        }

        public Builder maxAppliedInstanceId(long maxAppliedInstanceId) {
            this.maxAppliedInstanceId = maxAppliedInstanceId;
            return this;
        }

        public Builder maxProposalNo(long maxProposalNo) {
            this.maxProposalNo = maxProposalNo;
            return this;
        }

        public Builder lastSnap(SnapMate lastSnap) {
            this.lastSnap = lastSnap;
            return this;
        }

        public MateData build() {
            MateData mateData = new MateData();
            mateData.setMaxInstanceId(maxInstanceId);
            mateData.setMaxAppliedInstanceId(maxAppliedInstanceId);
            mateData.setMaxProposalNo(maxProposalNo);
            mateData.setLastSnap(lastSnap);
            return mateData;
        }
    }
}