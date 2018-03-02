package org.littleshoot.proxy.ratelimiter;

import org.junit.Test;
import org.littleshoot.proxy.ResponseInfo;

import io.netty.handler.codec.http.HttpResponseStatus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AuthenticationFailureRateLimitTest extends RateLimitTestBase {

  @Test
  public void testAuthenticationFailureLimits() throws Exception {
    this.proxyServer = bootstrapProxy()
        .withPort(0)
        .withProxyAuthenticator(new TestBasicProxyAuthenticator(USERNAME, "invalid password"))
        .withRateLimiter(new BaseRateLimiter(100, AUTHENTICATION_FAILURE_LIMIT))
        .start();

    int numRequests = 0;

    boolean rateLimited = false;
    int numValidRequests = 0;
    while (numRequests++ < AUTHENTICATION_FAILURE_LIMIT + 1) {
      ResponseInfo proxiedResponse = httpGetWithApacheClient(webHost, DEFAULT_RESOURCE, true, false);

      if (proxiedResponse.getStatusCode() == HttpResponseStatus.TOO_MANY_REQUESTS.code()) {
        rateLimited = true;
        break;
      }

      numValidRequests++;
    }

    assertTrue(rateLimited);
    assertEquals(AUTHENTICATION_FAILURE_LIMIT - 1, numValidRequests);
  }
}
