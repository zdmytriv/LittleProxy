package org.littleshoot.proxy;

import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.littleshoot.proxy.impl.ProtocolHeadersRequestDecoder.CONNECTION_ID_ATTRIBUTE;
import static org.littleshoot.proxy.impl.ProtocolHeadersRequestDecoder.SOURCE_IP_ATTRIBUTE;

import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;
import com.tngtech.java.junit.dataprovider.UseDataProvider;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicReference;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
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

@RunWith(DataProviderRunner.class)
public class ProtocolHeadersRequestDecoderTest {

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
        {"PROXY TCP6 FE80:0000:0000:0000:0202:B3FF:FE1E:8329 1200:0000:AB00:1234:0000:2552:7777:1313 5555 6666\r\n", equalTo("FE80:0000:0000:0000:0202:B3FF:FE1E:8329")} // Valid TCP6
    };
  }

  @DataProvider
  public static Object[][] invalidCases() {
    return new Object[][]{
        {"PROXY TCP4 555.22.33.44 99.88.77.66 5555 6666\r\n"}, // Invalid IP
        {"PROXY TCP6 FE80::0202:B3FF:FE1E:8329 1200::AB00:1234::2552:7777:1313 5555 6666\r\n"}, // Invalid collapsed TCP6, should be 39 characters
        {"PROXY TCP4 555.22.33.44 99.88.77.66 5555 6666"}, // Missing \r\n
        {"PROXY UNKNOWN 11.22.33.44 99.88.77.66 5555 6666\n"}, // We do not support UNKNOWN
        {"testPROXY TCP4 555.22.33.44 99.88.77.66 5555 6666\r\n"}, // PROXY is not the first string in request
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

  @DataProvider
  public static Object[][] validCasesTraceHeader() {
    return new Object[][]{
        {"PROXY TCP4 11.22.33.44 99.88.77.66 5555 6666\r\nTRACE 0123456789abcdef0123456789abcdef\r\n", equalTo("0123456789abcdef0123456789abcdef")}, // PROXY_TCP4 + TRACE
        {"PROXY TCP6 FE80:0000:0000:0000:0202:B3FF:FE1E:8329 1200:0000:AB00:1234:0000:2552:7777:1313 5555 6666\r\nTRACE 0123456789abcdef0123456789abcdef\r\n",
            equalTo("0123456789abcdef0123456789abcdef")} // PROXY_TCP6 + TRACE
    };
  }

  @DataProvider
  public static Object[][] invalidCasesTraceHeader() {
    return new Object[][]{
        {"TRACE 0123456789abcdef0123456789abcdef\r\n"}, // NO PROXY header in front of trace
        {"PROXY TCP4 55.22.33.44 99.88.77.66 5555 6666\r\nTRACE 0123456789abcdef0123456789abcdef\n\n"}, // Invalid ending
        {"PROXY TCP4 55.22.33.44 99.88.77.66 5555 6666\r\nTRACE 01234567890abcdef01234567890abcde\r\n"}, // Too short
        {"PROXY TCP4 55.22.33.44 99.88.77.66 5555 6666\r\nTRACE 0123456789abcdef0123456789abcdeff\r\n"}, // Too long
        {"PROXY TCP4 55.22.33.44 99.88.77.66 5555 6666\r\nTRACE 0123456789ABCDEF0123456789ABCDEF\r\n"}, // Uppercase characters
        {"PROXY TCP4 55.22.33.44 99.88.77.66 5555 6666\r\nTRACE asdfasdfasdfasdfasdfasdfasdfasdfa\r\n"}, // Invalid characters
        {"PROXY TCP4 555.22.33.44 99.88.77.66 5555 6666\r\nTRACE 0123456789abcdef0123456789abcdef\r\n"}, // Invalid IP in PROXY header in front
        {"PROXY TCP4 55.22.33.44 99.88.77.66 5555 6666\rTRACE 0123456789abcdef0123456789abcdef\r\n"}, // Invalid delimiter in between PROXY and TRACE headers
        {"PROXY TCP4 55.22.33.44 99.88.77.66 5555 6666\r\ntestTRACE 0123456789abcdef0123456789abcdef\r\n"}, // Invalid characters in front of TRACE header
    };
  }

  @Test
  @UseDataProvider("validCasesTraceHeader")
  public void testValidTraceHeader(String traceHeader, Matcher traceMatcher) throws Exception {
    ChannelHandlerContext context = runTest(traceHeader);

    assertThat(context.channel().attr(CONNECTION_ID_ATTRIBUTE).get(), traceMatcher);
  }

  @Test
  @UseDataProvider("invalidCasesTraceHeader")
  public void testInvalidTraceHeader(String traceHeader) throws Exception {
    ChannelHandlerContext context = runTest(traceHeader);
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
