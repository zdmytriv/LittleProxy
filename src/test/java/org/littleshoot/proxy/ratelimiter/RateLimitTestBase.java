package org.littleshoot.proxy.ratelimiter;

import org.littleshoot.proxy.BaseProxyTest;
import org.littleshoot.proxy.ProxyAuthenticator;
import org.littleshoot.proxy.RateLimiter;

import java.util.HashMap;
import java.util.Map;

import io.netty.handler.codec.http.HttpRequest;

public class RateLimitTestBase extends BaseProxyTest implements ProxyAuthenticator {

  static final int AUTHENTICATION_LIMIT = 10;
  static final int AUTHENTICATION_FAILURE_LIMIT = 20;

  @Override
  protected void setUp() {
    this.proxyServer = bootstrapProxy()
        .withPort(0)
        .withProxyAuthenticator(this)
        .withRateLimiter(new BaseRateLimiter(AUTHENTICATION_LIMIT, AUTHENTICATION_FAILURE_LIMIT))
        .start();
  }

  @Override
  protected String getUsername() {
    return "user1";
  }

  @Override
  protected String getPassword() {
    return "user2";
  }

  @Override
  public boolean authenticate(String userName, String password) {
    return getUsername().equals(userName) && getPassword().equals(password);
  }

  @Override
  protected boolean isAuthenticating() {
    return true;
  }

  @Override
  public String getRealm() {
    return null;
  }

  private static class BaseRateLimiter implements RateLimiter {

    private Map<String, Integer> attemptsPerUser = new HashMap<>();
    private int authenticationLimit;
    private int authenticationFailureLimit;

    public BaseRateLimiter(int authenticationLimit, int authenticationFailureLimit) {
      this.authenticationLimit = authenticationLimit;
      this.authenticationFailureLimit = authenticationFailureLimit;
    }

    @Override
    public boolean isOverLimit(HttpRequest httpRequest) {
      return false;
    }

    @Override
    public boolean isAuthenticationOverLimit(String username) {
      addUser(username);
      return attemptsPerUser.get(username) >= authenticationLimit;
    }

    @Override
    public boolean isAuthenticationFailureOverLimit(String username) {
      addUser(username);
      return attemptsPerUser.get(username) >= authenticationFailureLimit;
    }

    private void addUser(String username) {
      attemptsPerUser.put(username, attemptsPerUser.containsKey(username) ? attemptsPerUser.get(username) + 1 : 1);
    }
  }
}
