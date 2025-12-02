package com.waqiti.crypto.rpc;

import lombok.Data;

@Data
public class RpcResponse {
    private String jsonrpc;
    private Object result;
    private RpcError error;
    private Long id;

    @Data
    public static class RpcError {
        private int code;
        private String message;
    }
}