// Copyright (C) 2003 Philip Aston
// All rights reserved.
//
// This file is part of The Grinder software distribution. Refer to
// the file LICENSE which is part of The Grinder distribution for
// licensing details. The Grinder distribution is available on the
// Internet at http://grinder.sourceforge.net/
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
// "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
// LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
// FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
// REGENTS OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
// INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
// (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
// SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
// HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
// STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
// ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
// OF THE POSSIBILITY OF SUCH DAMAGE.

package net.grinder.communication;

import java.io.IOException;
import java.io.OutputStream;

import net.grinder.util.thread.Kernel;
import net.grinder.util.thread.UncheckedInterruptedException;


/**
 * Manages the sending of messages to many streams.
 *
 * @author Philip Aston
 * @version $Revision$
 */
public final class FanOutStreamSender extends AbstractFanOutSender {

  /**
   * Constructor.
   *
   * @param numberOfThreads Number of sender threads to use.
   */
  public FanOutStreamSender(int numberOfThreads) {
    this(new Kernel(numberOfThreads));
  }

  /**
   * Constructor.
   *
   * @param kernel Kernel to use.
   */
  private FanOutStreamSender(Kernel kernel) {
    super(kernel, new ResourcePool());
  }

  /**
   * Add a stream.
   *
   * @param stream The stream.
   */
  public void add(OutputStream stream) {
    getResourcePool().add(new OutputStreamResource(stream));
  }

  /**
   * Shut down this sender.
   */
  public void shutdown() {
    super.shutdown();
    getResourcePool().close();
  }

  /**
   * Return an output stream from a resource.
   *
   * @param resource The resource.
   * @return The output stream.
   */
  protected OutputStream resourceToOutputStream(
    ResourcePool.Resource resource) {

    return ((OutputStreamResource)resource).getOutputStream();
  }

  private static final class OutputStreamResource
          implements ResourcePool.Resource {

    private final OutputStream m_outputStream;

    public OutputStreamResource(OutputStream outputStream) {
      m_outputStream = outputStream;
    }

    public OutputStream getOutputStream() {
      return m_outputStream;
    }

    public void close() {
      try {
        m_outputStream.close();
      }
      catch (IOException e) {
        // Ignore.
        UncheckedInterruptedException.ioException(e);
      }
    }
  }
}
