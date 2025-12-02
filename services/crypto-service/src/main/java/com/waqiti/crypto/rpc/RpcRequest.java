package com.waqiti.crypto.rpc;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class RpcRequest {
    private String jsonrpc;
    private String method;
    private List<Object> params;
    private Long id;
}