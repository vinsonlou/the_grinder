// Copyright (C) 2003, 2004, 2005 Philip Aston
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

package net.grinder.tools.tcpproxy;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


/**
 * Composite TCPProxyFilter.
 *
 * @author Philip Aston
 * @version $Revision$
 */
public final class CompositeFilter implements TCPProxyFilter {

  private final List m_filters = new ArrayList();

  /**
   * Handle a message fragment from the stream.
   *
   * @param connectionDetails Describes the connection.
   * @param originalBuffer Contains the data.
   * @param bytesRead How many bytes of data in <code>buffer</code>.
   * @return Filters can optionally return a <code>byte[]</code>
   * which will be transmitted to the server instead of
   * <code>buffer</code>.
   * @throws FilterException If an error occurs.
   */
  public byte[] handle(ConnectionDetails connectionDetails,
                       byte[] originalBuffer, int bytesRead)
    throws FilterException {

    final Iterator iterator = m_filters.iterator();

    byte[] nextBuffer = originalBuffer;
    int nextBytesRead = bytesRead;

    while (iterator.hasNext()) {
      final TCPProxyFilter filter = (TCPProxyFilter) iterator.next();

      final byte[] buffer =
        filter.handle(connectionDetails, nextBuffer, nextBytesRead);

      if (buffer != null) {
        nextBuffer = buffer;
        nextBytesRead = buffer.length;
      }
    }

    return nextBuffer != originalBuffer ? nextBuffer : null;
  }

  /**
   * A new connection has been opened.
   *
   * @param connectionDetails Describes the connection.
   * @throws FilterException If an error occurs.
   */
  public void connectionOpened(final ConnectionDetails connectionDetails)
    throws FilterException {

    final Iterator iterator = m_filters.iterator();

    while (iterator.hasNext()) {
      final TCPProxyFilter filter = (TCPProxyFilter) iterator.next();
      filter.connectionOpened(connectionDetails);
    }
  }

  /**
   * A connection has been closed.
   *
   * @param connectionDetails Describes the connection.
   * @throws FilterException If an error occurs.x
   */
  public void connectionClosed(final ConnectionDetails connectionDetails)
    throws FilterException {

    final Iterator iterator = m_filters.iterator();

    while (iterator.hasNext()) {
      final TCPProxyFilter filter = (TCPProxyFilter) iterator.next();
      filter.connectionClosed(connectionDetails);
    }
  }

  /**
   * Called just before stop.
   */
  public void stop() {

    final Iterator iterator = m_filters.iterator();

    while (iterator.hasNext()) {
      final TCPProxyFilter filter = (TCPProxyFilter) iterator.next();
      filter.stop();
    }
  }

  /**
   * Add a filter to the composite.
   *
   * @param filter The filter.
   */
  public void add(TCPProxyFilter filter) {
    m_filters.add(filter);
  }

  /**
   * Access to composed filters.
   *
   * @return The filters.
   */
  public List getFilters() {
    return m_filters;
  }
}
