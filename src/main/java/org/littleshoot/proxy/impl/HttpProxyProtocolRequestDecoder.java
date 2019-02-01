package org.littleshoot.proxy.impl;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.AttributeKey;

public class HttpProxyProtocolRequestDecoder extends ChannelInboundHandlerAdapter {

  public static final AttributeKey<String> SOURCE_IP_ATTRIBUTE = AttributeKey.valueOf("sourceIp");

  // PROXY_STRING + single space + INET_PROTOCOL + single space + CLIENT_IP + single space + PROXY_IP + single space + CLIENT_PORT + single space + PROXY_PORT + "\r\n"
  // Example: PROXY TCP4 198.51.100.22 203.0.113.7 35646 80\r\n
  // Source: https://docs.aws.amazon.com/elasticloadbalancing/latest/classic/enable-proxy-protocol.html
  private static final Pattern PROXY_PROTOCOL_HEADER_PATTERN =
      Pattern.compile("PROXY TCP4 (((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)(\\.)?){4}) (((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)(\\.)?){4}) \\d+ \\d+");

  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
    ByteBuf buf = ((ByteBuf) msg);

    String body = bufToString(buf);

    Matcher matcher = PROXY_PROTOCOL_HEADER_PATTERN.matcher(body);

    if (!matcher.find()) {
      ctx.fireChannelRead(msg);
      return; // no proxy protocol header found, proceed
    }

    String sourceIp = matcher.group(1);
    ctx.channel().attr(SOURCE_IP_ATTRIBUTE).set(sourceIp);

    int proxyProtocolHeaderIndex = body.indexOf("\r\n");
    String stripped = body.substring(proxyProtocolHeaderIndex + 2); // +2 for \r\n

    buf.clear().writeBytes(stripped.getBytes());

    ctx.fireChannelRead(msg);
  }

  private String bufToString(ByteBuf content) {
    final byte[] bodyBytes = new byte[content.readableBytes()];

    content.getBytes(content.readerIndex(), bodyBytes);

    return new String(bodyBytes);
  }
}
