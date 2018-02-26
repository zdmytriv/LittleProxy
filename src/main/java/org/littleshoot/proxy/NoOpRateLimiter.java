package org.littleshoot.proxy;

import io.netty.handler.codec.http.HttpRequest;

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
}
