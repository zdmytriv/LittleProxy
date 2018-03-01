package org.littleshoot.proxy;

import org.littleshoot.proxy.impl.ProxyUtils;

import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

/**
 * No Op implementation of rate limiter. This is used by default in HttpProxyServer.
 */
public class NoOpRateLimiter implements RateLimiter {

  @Override
  public boolean isOverLimit(HttpRequest httpRequest) {
    return false;
  }

  @Override
  public boolean isAuthenticationOverLimit(HttpRequest httpRequest) {
    return false;
  }

  @Override
  public boolean isAuthenticationFailureOverLimit(HttpRequest httpRequest) {
    return false;
  }

  @Override
  public FullHttpResponse limitReachedHttpResponse(HttpRequest request) {
    return ProxyUtils.createFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.TOO_MANY_REQUESTS, "429 Too Many Requests");
  }
}
