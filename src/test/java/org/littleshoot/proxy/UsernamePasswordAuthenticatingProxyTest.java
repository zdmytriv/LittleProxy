package org.littleshoot.proxy;

import org.littleshoot.proxy.authenticator.BasicProxyAuthenticator;

import io.netty.handler.codec.http.HttpRequest;

/**
 * Tests a single proxy that requires username/password authentication.
 */
public class UsernamePasswordAuthenticatingProxyTest extends BaseProxyTest {
    public final static String USERNAME = "user1";
    public final static String PASSWORD = "password";

    @Override
    protected void setUp() {
        this.proxyServer = bootstrapProxy()
                .withPort(0)
                .withProxyAuthenticator(new TestBasicProxyAuthenticator(USERNAME, PASSWORD))
                .start();
    }

    @Override
    protected String getUsername() {
        return USERNAME;
    }

    @Override
    protected String getPassword() {
        return PASSWORD;
    }

    @Override
    protected boolean isAuthenticating() {
        return true;
    }

    public static class TestBasicProxyAuthenticator extends BasicProxyAuthenticator {

        private final String username;
        private final String password;

        public TestBasicProxyAuthenticator(String username, String password) {
            this.username = username;
            this.password = password;
        }

        @Override
        public String getRealm() {
          return null;
        }

        @Override
        public boolean authenticate(String username, String password, HttpRequest request) {
          return this.username.equals(username) && this.password.equals(password);
        }
    }
}
