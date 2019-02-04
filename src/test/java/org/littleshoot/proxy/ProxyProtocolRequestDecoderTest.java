package org.littleshoot.proxy;

import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;
import com.tngtech.java.junit.dataprovider.UseDataProvider;

import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.littleshoot.proxy.impl.DefaultHttpProxyServer;
import org.littleshoot.proxy.impl.ThreadPoolConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicReference;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;

import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.littleshoot.proxy.impl.HttpProxyProtocolRequestDecoder.SOURCE_IP_ATTRIBUTE;

@RunWith(DataProviderRunner.class)
public class ProxyProtocolRequestDecoderTest {

  private Server webServer;
  private HttpProxyServer proxyServer;
  private int webServerPort;

  @Before
  public void setUp() throws Exception {
    webServer = new Server(0);
    webServer.setHandler(new AbstractHandler() {
      public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        response.setStatus(HttpServletResponse.SC_OK);
        baseRequest.setHandled(true);
        byte[] content = ("OK1\n").getBytes();
        response.addHeader("Content-Length", Integer.toString(content.length));
        response.getOutputStream().write(content);
      }
    });
    webServer.start();
    webServerPort = TestUtils.findLocalHttpPort(webServer);
  }

  @After
  public void tearDown() throws Exception {
    try {
      if (webServer != null) {
        webServer.stop();
      }
    } finally {
      if (proxyServer != null) {
        proxyServer.abort();
      }
    }
  }

  private void setUpHttpProxyServer(HttpFiltersSource filtersSource) {
    proxyServer = DefaultHttpProxyServer.bootstrap()
        .withPort(0)
        .withFiltersSource(filtersSource)
        .withThreadPoolConfiguration(new ThreadPoolConfiguration()
            .withAcceptorThreads(1)
            .withClientToProxyWorkerThreads(1)
            .withProxyToServerWorkerThreads(1))
        .start();
  }

  @DataProvider
  public static Object[][] validCases() {
    return new Object[][]{
        {"", nullValue()}, // No Proxy Header, typical case
        {"PROXY TCP4 11.22.33.44 99.88.77.66 5555 6666\r\n", equalTo("11.22.33.44")}, // Valid TCP4
        {"PROXY TCP6 FE80:0000:0000:0000:0202:B3FF:FE1E:8329 1200:0000:AB00:1234:0000:2552:7777:1313 5555 6666\r\n", equalTo("FE80:0000:0000:0000:0202:B3FF:FE1E:8329")}, // Valid TCP6
    };
  }

  @DataProvider
  public static Object[][] invalidCases() {
    return new Object[][]{
        {"PROXY TCP4 555.22.33.44 99.88.77.66 5555 6666\r\n"}, // Invalid IP
        {"PROXY TCP6 FE80::0202:B3FF:FE1E:8329 1200::AB00:1234::2552:7777:1313 5555 6666\r\n"}, // Invalid collapsed TCP6, should be 39 characters
        {"PROXY TCP4 555.22.33.44 99.88.77.66 5555 6666"}, // Missing \r\n
        {"PROXY UNKNOWN 11.22.33.44 99.88.77.66 5555 6666\n"}, // We do not support UNKNOWN
    };
  }

  @Test
  @UseDataProvider("validCases")
  public void testValid(String proxyProtocolHeader, Matcher sourceIpMatcher) throws Exception {
    ChannelHandlerContext context = runTest(proxyProtocolHeader);

    assertThat(context.channel().attr(SOURCE_IP_ATTRIBUTE).get(), sourceIpMatcher);
  }

  @Test
  @UseDataProvider("invalidCases")
  public void testInvalid(String proxyProtocolHeader) throws Exception {
    ChannelHandlerContext context = runTest(proxyProtocolHeader);
    assertThat(context, nullValue());
  }

  private ChannelHandlerContext runTest(String proxyProtocolHeader) throws Exception {
    final AtomicReference<ChannelHandlerContext> serverCtxReference = new AtomicReference<>();

    setUpHttpProxyServer(new HttpFiltersSourceAdapter() {
      @Override
      public HttpFilters filterRequest(HttpRequest originalRequest, ChannelHandlerContext ctx) {
        return new HttpFiltersAdapter(originalRequest, ctx) {
          @Override
          public io.netty.handler.codec.http.HttpResponse clientToProxyRequest(HttpObject httpObject) {
            serverCtxReference.set(ctx);
            return null;
          }
        };
      }
    });

    StringBuilder headers = new StringBuilder()
        .append(proxyProtocolHeader)
        .append("CONNECT ").append("localhost").append(':').append(webServerPort).append(" HTTP/1.1\r\n")
        .append("\r\n");

    InetSocketAddress inetSocketAddress = new InetSocketAddress("127.0.0.1", proxyServer.getListenAddress().getPort());

    Socket httpProxySocket = new Socket();
    httpProxySocket.connect(inetSocketAddress);
    httpProxySocket.getOutputStream().write(headers.toString().getBytes());

    InputStream proxyInputStream = httpProxySocket.getInputStream();
    String response = IOUtils.toString(proxyInputStream);

    System.out.println(response);

    return serverCtxReference.get();
  }
}
