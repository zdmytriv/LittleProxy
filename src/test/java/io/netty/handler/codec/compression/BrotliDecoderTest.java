package io.netty.handler.codec.compression;

import static org.junit.Assert.assertEquals;

import com.nixxcode.jvmbrotli.common.BrotliLoader;
import com.nixxcode.jvmbrotli.enc.BrotliOutputStream;
import com.nixxcode.jvmbrotli.enc.Encoder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BrotliDecoderTest extends AbstractCompressionTest {
  public static final long FIVE_SECONDS_NANO = 5_000_000_000L;

  static final Logger log = LoggerFactory.getLogger(BrotliDecoderTest.class);

  protected static final ByteBuf WRAPPED_BYTES_SMALL;
  protected static final ByteBuf WRAPPED_BYTES_LARGE;

  static {
    WRAPPED_BYTES_SMALL = Unpooled.wrappedBuffer(BYTES_SMALL);
    WRAPPED_BYTES_LARGE = Unpooled.wrappedBuffer(BYTES_LARGE);
  }

  private byte[] compressedBytesSmall;
  private byte[] compressedBytesLarge;

  private EmbeddedChannel channel;

  @Rule
  public final ExpectedException expected = ExpectedException.none();

  @Before
  public void setUp() throws Exception {
    BrotliLoader.isBrotliAvailable();
    compressedBytesSmall = compress(BYTES_SMALL);
    compressedBytesLarge = compress(BYTES_LARGE);
  }

  @Before
  public void initChannel() {
    channel = new EmbeddedChannel(new BrotliDecoder());
  }

  @After
  public void destroyChannel() {
    if (channel != null) {
      channel.finishAndReleaseAll();
      channel = null;
    }
  }

  public BrotliDecoderTest() {
  }

  public ByteBuf[] smallData() {
    ByteBuf heap = Unpooled.wrappedBuffer(compressedBytesSmall);
    ByteBuf direct = Unpooled.directBuffer(compressedBytesSmall.length);
    direct.writeBytes(compressedBytesSmall);
    return new ByteBuf[]{heap, direct};
  }

  public ByteBuf[] largeData() {
    ByteBuf heap = Unpooled.wrappedBuffer(compressedBytesLarge);
    ByteBuf direct = Unpooled.directBuffer(compressedBytesLarge.length);
    direct.writeBytes(compressedBytesLarge);
    return new ByteBuf[]{heap, direct};
  }

  @Test
  public void testDecompressionOfSmallChunkOfData() throws Exception {
    for (ByteBuf byteBuf : this.smallData()) {
      testDecompression(WRAPPED_BYTES_SMALL, byteBuf);
    }
  }

  @Test
  public void testDecompressionOfLargeChunkOfData() throws Exception {
    for (ByteBuf byteBuf : this.largeData()) {
      testDecompression(WRAPPED_BYTES_LARGE, byteBuf);
    }
  }

  @Test
  public void testDecompressionOfRandomlyChunkedData() {
    ByteBuf[] data = randomChunks(compressedBytesLarge);

    Assert.assertTrue(channel.writeInbound(data));
    ByteBuf decompressed = readDecompressed(channel);
    assertEquals(0, ByteBufUtil.compare(WRAPPED_BYTES_LARGE, decompressed));
    decompressed.release();
  }

  private static ByteBuf[] randomChunks(byte[] source) {
    List<byte[]> chunks = new ArrayList<>();
    int start = 0;
    Random rng = new Random(42);
    while (start < source.length) {
      int chunkSize = rng.nextInt(source.length / 12) + source.length / 4;
      chunkSize = Math.min(chunkSize, source.length - start);
      byte[] chunk = Arrays.copyOfRange(source, start, start + chunkSize);
      chunks.add(chunk);
      start += chunkSize;
    }
    return chunks.stream().map(Unpooled::wrappedBuffer).toArray(ByteBuf[]::new);
  }

  protected void testDecompression(final ByteBuf expected, final ByteBuf data) throws Exception {
    Assert.assertTrue(channel.writeInbound(data));

    ByteBuf decompressed = readDecompressed(channel);
    assertEquals(0, ByteBufUtil.compare(expected, decompressed));

    decompressed.release();
  }

  @Test
  public void testCorruptedStreamNotBlocking() {
    byte[] fakeData = "xyz".getBytes();
    ByteBuf input = Unpooled.wrappedBuffer(fakeData);
    long start = System.nanoTime();
    Assert.assertFalse(channel.writeInbound(input));
    Assert.assertTrue(System.nanoTime() - start < FIVE_SECONDS_NANO);
  }

  @Test
  @Ignore("this test seems wrong") // todo remove?
  public void testDecompressionOfBatchedFlowOfData() throws Exception {
    final byte[] data = BYTES_LARGE;
    byte[] compressedArray = compress(data);
    int written = 0, length = rand.nextInt(100);
    while (written + length < compressedArray.length) {
      ByteBuf compressed = Unpooled.wrappedBuffer(compressedArray, written, length);
      if(channel.writeInbound(compressed)) {
        written += length;
        length = rand.nextInt(100);
      }
      else {
        length = length + rand.nextInt(100);
      }
    }
    ByteBuf compressed = Unpooled.wrappedBuffer(compressedArray, written, compressedArray.length - written);
    channel.writeInbound(compressed);

    ByteBuf expectedByteBuf = Unpooled.wrappedBuffer(data);
    ByteBuf uncompressed = readDecompressed(channel);

    assertEquals(0, ByteBufUtil.compare(expectedByteBuf, uncompressed));

    uncompressed.release();
    expectedByteBuf.release();
  }

  protected static ByteBuf readDecompressed(final EmbeddedChannel channel) {
    CompositeByteBuf decompressed = ByteBufAllocator.DEFAULT.compositeBuffer();
    ByteBuf msg;
    while ((msg = channel.readInbound()) != null) {
      decompressed.addComponent(true, msg);
    }
    return decompressed;
  }

  protected byte[] compress(byte[] data) throws Exception {
    ByteArrayOutputStream dst = new ByteArrayOutputStream();
    // If being used to compress streams in real-time,
    //   I do not advise a quality setting above 4 due to performance
    Encoder.Parameters params = new Encoder.Parameters().setQuality(4);
        /*
         Initialize compressor by binding it to our file output stream

         It's important to close the BrotliOutputStream object. This also closes the
         underlying FileOutputStream
        */
    try (BrotliOutputStream brotliOutputStream = new BrotliOutputStream(dst, params)) {
      brotliOutputStream.write(data);
    } catch (IOException e) {
      log.error("Could not brotli compress data ", e);
    }
    return dst.toByteArray();
  }
}