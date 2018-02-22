package org.littleshoot.proxy;

/**
 * Interface for rate limiting user's authentication requests
 */
public interface AuthenticationRateLimiter {

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
