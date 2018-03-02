package org.littleshoot.proxy.ratelimiter;

import org.junit.Ignore;
import org.littleshoot.proxy.UsernamePasswordAuthenticatingProxyTest;
import org.littleshoot.proxy.authenticator.BasicCredentials;
import org.littleshoot.proxy.impl.ProxyUtils;
import org.littleshoot.proxy.ratelimit.RateLimiter;

import java.util.HashMap;
import java.util.Map;

import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

@Ignore
public class RateLimitTestBase extends UsernamePasswordAuthenticatingProxyTest {

  static final int AUTHENTICATION_LIMIT = 10;
  static final int AUTHENTICATION_FAILURE_LIMIT = 20;

  @Override
  protected void setUp() {
    this.proxyServer = bootstrapProxy()
        .withPort(0)
        .withProxyAuthenticator(new TestBasicProxyAuthenticator(USERNAME, PASSWORD))
        .withRateLimiter(new BaseRateLimiter(AUTHENTICATION_LIMIT, 100))
        .start();
  }

  static class BaseRateLimiter implements RateLimiter {

    private Map<String, Integer> attemptsPerUser = new HashMap<>();
    private Map<String, Integer> failureAttemptsPerUser = new HashMap<>();
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
    public boolean isAuthenticationOverLimit(HttpRequest request) {
      BasicCredentials credentials = ProxyUtils.getBasicCredentials(request);

      if (credentials == null) {
        return false;
      }

      attemptsPerUser.put(credentials.getUsername(),
          attemptsPerUser.containsKey(credentials.getUsername()) ? attemptsPerUser.get(credentials.getUsername()) + 1 : 1);
      return attemptsPerUser.get(credentials.getUsername()) >= authenticationLimit;
    }

    @Override
    public boolean isAuthenticationFailureOverLimit(HttpRequest request) {
      BasicCredentials credentials = ProxyUtils.getBasicCredentials(request);

      if (credentials == null) {
        return false;
      }

      failureAttemptsPerUser.put(credentials.getUsername(),
          failureAttemptsPerUser.containsKey(credentials.getUsername()) ? failureAttemptsPerUser.get(credentials.getUsername()) + 1 : 1);
      return failureAttemptsPerUser.get(credentials.getUsername()) >= authenticationFailureLimit;
    }

    @Override
    public FullHttpResponse limitReachedResponse(HttpRequest request) {
      return ProxyUtils.createFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.TOO_MANY_REQUESTS, "429 Too Many Requests");
    }
  }
}
