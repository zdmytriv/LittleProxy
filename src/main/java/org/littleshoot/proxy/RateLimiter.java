package org.littleshoot.proxy;

import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpRequest;

/**
 * Interface for rate limiting requests
 */
public interface RateLimiter {

  /**
   * Rate Limiting general http requests
   *
   * @param request
   * @return <code>true</code> if http request hit rate limit
   * <code>false</code>.
   */
  boolean isOverLimit(HttpRequest request);

  /**
   * Rate Limiting user authentication requests
   *
   * @param request
   * @return <code>true</code> if user hit rate limit
   * <code>false</code>.
   */
  boolean isAuthenticationOverLimit(HttpRequest request);

  /**
   * Rate Limiting user authentication failures requests
   *
   * @param request
   * @return <code>true</code> if user's authentication failures hit rate limit
   * <code>false</code>.
   */
  boolean isAuthenticationFailureOverLimit(HttpRequest request);

  /**
   * Http response that is going to be returned to user when limit is reached
   */
  FullHttpResponse limitReachedHttpResponse(HttpRequest request);
}
