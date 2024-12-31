package com.inksetter.twist;

public interface MethodInterceptor {
    public Object invokeMethod(String methodName, Object[] args);
}
