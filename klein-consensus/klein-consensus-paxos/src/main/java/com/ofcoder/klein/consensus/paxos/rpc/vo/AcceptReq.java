package com.ofcoder.klein.consensus.paxos.rpc.vo;

import java.io.Serializable;
import java.util.List;

import com.ofcoder.klein.consensus.paxos.Proposal;

/**
 * @author far.liu
 */
public class AcceptReq implements Serializable {
    private String nodeId;
    private long instanceId;
    private long proposalNo;
    private List<Proposal> data;

    public String getNodeId() {
        return nodeId;
    }

    public long getInstanceId() {
        return instanceId;
    }

    public long getProposalNo() {
        return proposalNo;
    }

    public List<Proposal> getData() {
        return data;
    }

    public static final class Builder {
        private String nodeId;
        private long instanceId;
        private long proposalNo;
        private List<Proposal> data;

        private Builder() {
        }

        public static Builder anAcceptReq() {
            return new Builder();
        }

        public Builder nodeId(String nodeId) {
            this.nodeId = nodeId;
            return this;
        }

        public Builder instanceId(long instanceId) {
            this.instanceId = instanceId;
            return this;
        }

        public Builder proposalNo(long proposalNo) {
            this.proposalNo = proposalNo;
            return this;
        }

        public Builder data(List<Proposal> data) {
            this.data = data;
            return this;
        }

        public AcceptReq build() {
            AcceptReq acceptReq = new AcceptReq();
            acceptReq.data = this.data;
            acceptReq.nodeId = this.nodeId;
            acceptReq.proposalNo = this.proposalNo;
            acceptReq.instanceId = this.instanceId;
            return acceptReq;
        }
    }
}
