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
   * @param username
   * @return <code>true</code> if user hit rate limit
   * <code>false</code>.
   */
  boolean isAuthenticationOverLimit(String username);

  /**
   * Rate Limiting user authentication failures requests
   *
   * @param username
   * @return <code>true</code> if user's authentication failures hit rate limit
   * <code>false</code>.
   */
  boolean isAuthenticationFailureOverLimit(String username);
}
