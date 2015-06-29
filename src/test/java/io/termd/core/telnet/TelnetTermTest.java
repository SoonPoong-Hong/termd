package io.termd.core.telnet;

import io.termd.core.util.Dimension;
import org.apache.commons.net.telnet.TelnetClient;
import org.apache.commons.net.telnet.WindowSizeOptionHandler;
import org.junit.Test;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public abstract class TelnetTermTest extends TelnetTestBase {

  @Test
  public void testSizeHandler() throws Exception {
    final CountDownLatch latch1 = new CountDownLatch(1);
    final CountDownLatch latch2 = new CountDownLatch(1);
    server(() -> {
      final AtomicInteger count = new AtomicInteger();
      final TelnetTtyConnection connection = new TelnetTtyConnection();
      connection.setResizeHandler(size -> {
        switch (count.getAndIncrement()) {
          case 0:
            assertEquals(20, size.getWidth());
            assertEquals(10, size.getHeight());
            latch1.countDown();
            break;
          case 1:
            assertEquals(80, size.getWidth());
            assertEquals(24, size.getHeight());
            latch2.countDown();
            break;
          case 2:
            assertEquals(180, size.getWidth());
            assertEquals(160, size.getHeight());
            connection.setResizeHandler(null);
            connection.setResizeHandler(size1 -> {
              assertEquals(180, size1.getWidth());
              assertEquals(160, size1.getHeight());
              testComplete();
            });
            break;
          default:
            fail("Was not expecting that");
        }
      });
      return connection;
    });
    WindowSizeOptionHandler optionHandler = new WindowSizeOptionHandler(20, 10, false, false, true, false);
    final AtomicReference<OutputStream> out = new AtomicReference<>();
    client = new TelnetClient() {
      @Override
      protected void _connectAction_() throws IOException {
        super._connectAction_();
        out.set(_output_);
      }
    };
    client.addOptionHandler(optionHandler);
    client.connect("localhost", 4000);
    latch1.await(30, TimeUnit.SECONDS);
    out.get().write(new byte[]{TelnetConnection.BYTE_IAC, TelnetConnection.BYTE_SB, 31, 0, 80, 0, 24, TelnetConnection.BYTE_IAC, TelnetConnection.BYTE_SE});
    out.get().flush();
    latch2.await(30, TimeUnit.SECONDS);
    out.get().write(new byte[]{TelnetConnection.BYTE_IAC, TelnetConnection.BYTE_SB, 31, 0, (byte) 180, 0, (byte) 160, TelnetConnection.BYTE_IAC, TelnetConnection.BYTE_SE});
    out.get().flush();
    await();
  }
}
