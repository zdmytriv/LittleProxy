/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.compression;

import com.nixxcode.jvmbrotli.common.BrotliLoader;
import com.nixxcode.jvmbrotli.dec.BrotliInputStream;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decompress a {@link ByteBuf} using the inflate algorithm.
 */
public class BrotliDecoder extends ByteToMessageDecoder {

  private static final Logger log = LoggerFactory.getLogger(BrotliDecoder.class);

  private static final int EOF = -1;

  public BrotliDecoder() {
    // https://github.com/nixxcode/jvm-brotli#loading-jvm-brotli
    if (!BrotliLoader.isBrotliAvailable()) {
      throw new DecompressionException("Brotli decoding is not supported");
    }
  }

  @Override
  protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
    ByteBuf result = in.alloc().buffer();
    boolean success = true;
    try (
        ByteBufOutputStream bbout = new ByteBufOutputStream(result);
        ByteBufInputStream bbin = new ByteBufInputStream(in);
    ) {
      decompress(bbin, bbout);
      if (!result.isReadable()) {
        success = false;
        return;
      }
      out.add(result);
    } catch (IOException ioe) {
      // stream is corrupted or not ready (retriable)
      // exit condition will be reached when Netty has nothing more to add to the buffer
      // and this function is not able to decode any messages to "out"
      success = false;
    } catch (Exception e) {
      success = false;
      throw e;
    } finally {
      if (!success) {
        in.resetReaderIndex();
        result.release();
      }
    }
  }

  public static void decompress(InputStream in, OutputStream out) throws IOException {
    try (BrotliInputStream brotliInputStream = new BrotliInputStream(in)) {
      int bytesRead;
      byte[] decompressBuffer = new byte[8192];
      while ((bytesRead = brotliInputStream.read(decompressBuffer)) != EOF) {
        out.write(decompressBuffer, 0, bytesRead);
      }
    }
  }
}