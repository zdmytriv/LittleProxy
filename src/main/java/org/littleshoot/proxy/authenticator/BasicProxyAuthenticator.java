package org.littleshoot.proxy.authenticator;

import org.littleshoot.proxy.impl.ProxyUtils;

import io.netty.handler.codec.http.HttpRequest;

/**
 * Basic Auth user/password authenticator
 */
public abstract class BasicProxyAuthenticator extends AbstractProxyAuthenticator {

  @Override
  public boolean authenticate(HttpRequest request) {
    BasicCredentials credentials = ProxyUtils.getBasicCredentials(request);

    if (credentials != null) {
      return authenticate(credentials.getUsername(), credentials.getPassword(), request);
    }

    return false;
  }

  public abstract boolean authenticate(String username, String password, HttpRequest request);
}
