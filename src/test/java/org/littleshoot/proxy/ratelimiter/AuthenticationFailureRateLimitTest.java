package org.littleshoot.proxy.ratelimiter;

import org.junit.Test;
import org.littleshoot.proxy.ResponseInfo;

import io.netty.handler.codec.http.HttpResponseStatus;

import static org.junit.Assert.assertTrue;

public class AuthenticationFailureRateLimitTest extends RateLimitTestBase {

  @Override
  protected String getPassword() {
    return "this is wrong password";
  }

  @Test
  public void testAuthenticationFailureLimits() throws Exception {
    int numRequests = 0;

    boolean rateLimited = false;
    while (numRequests++ < AUTHENTICATION_FAILURE_LIMIT + 1) {
      ResponseInfo proxiedResponse = httpGetWithApacheClient(webHost, DEFAULT_RESOURCE, true, false);

      if (proxiedResponse.getStatusCode() == HttpResponseStatus.TOO_MANY_REQUESTS.code()) {
        rateLimited = true;
        break;
      }
    }

    assertTrue(rateLimited);
  }
}
