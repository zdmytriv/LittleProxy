package org.littleshoot.proxy;

import io.netty.handler.codec.http.HttpRequest;

/**
 * Interface for rate limiting requests
 */
public interface RateLimiter {

  /**
   * Rate Limiting general http requests
   *
   * @param httpRequest
   * @return <code>true</code> if http request hit rate limit
   * <code>false</code>.
   */
  boolean isOverLimit(HttpRequest httpRequest);

  /**
   * Rate Limiting user authentication requests
   *
   * @param httpRequest
   * @return <code>true</code> if user hit rate limit
   * <code>false</code>.
   */
  boolean isAuthenticationOverLimit(HttpRequest httpRequest);

  /**
   * Rate Limiting user authentication failures requests
   *
   * @param httpRequest
   * @return <code>true</code> if user's authentication failures hit rate limit
   * <code>false</code>.
   */
  boolean isAuthenticationFailureOverLimit(HttpRequest httpRequest);
}
