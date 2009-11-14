// Copyright (C) 2009 Philip Aston
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
// COPYRIGHT HOLDERS OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
// INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
// (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
// SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
// HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
// STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
// ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
// OF THE POSSIBILITY OF SUCH DAMAGE.

package net.grinder.engine.process.instrumenter;

import java.util.ArrayList;
import java.util.List;

import net.grinder.common.Test;
import net.grinder.engine.process.Instrumenter;
import net.grinder.engine.process.ScriptEngine.Recorder;
import net.grinder.engine.process.instrumenter.dcr.DCRInstrumenterFactory;
import net.grinder.engine.process.instrumenter.traditionaljython.JythonInstrumenterFactory;
import net.grinder.script.NotWrappableTypeException;


/**
 * Master instrumenter.
 *
 * @author Philip Aston
 * @version $Revision:$
 */
public final class MasterInstrumenter implements Instrumenter {

  private final List<Instrumenter> m_instrumenters =
    new ArrayList<Instrumenter>();

  /**
   * Constructor for MasterInstrumenter.
   */
  public MasterInstrumenter() {
    m_instrumenters.add(new RejectNullInstrumenter());

    final DCRInstrumenterFactory dcrInstrumenterFactory =
      DCRInstrumenterFactory.createFactory();

    final boolean addedTraditionalJythonInstrumenter =
      JythonInstrumenterFactory.addJythonInstrumenter(m_instrumenters);

    if (dcrInstrumenterFactory != null) {
      if (!addedTraditionalJythonInstrumenter) {
        dcrInstrumenterFactory.addJythonInstrumenter(m_instrumenters);
      }

      dcrInstrumenterFactory.addJavaInstrumenter(m_instrumenters);
    }
  }

  /**
   * {@inheritDoc}
   */
  public Object createInstrumentedProxy(Test test,
                                        Recorder recorder,
                                        Object target)
    throws NotWrappableTypeException {

    for (Instrumenter instrumenter : m_instrumenters) {
      final Object result =
        instrumenter.createInstrumentedProxy(test, recorder, target);

      if (result != null) {
        return result;
      }
    }

    throw new NotWrappableTypeException("Failed to wrap " + target);
  }

  /**
   * {@inheritDoc}
   */
  public String getDescription() {
    final StringBuilder result = new StringBuilder();

    for (Instrumenter instrumenter : m_instrumenters) {
      final String description = instrumenter.getDescription();

      if (description != null) {
        if (result.length() > 0) {
          result.append("; ");
        }

        result.append(description);
      }
    }

    if (result.length() == 0) {
      result.append("NO INSTRUMENTER COULD BE LOADED");
    }

    return result.toString();
  }

  private static final class RejectNullInstrumenter implements Instrumenter {

    public Object createInstrumentedProxy(Test test,
                                          Recorder recorder,
                                          Object target)
      throws NotWrappableTypeException {

      if (target == null) {
        throw new NotWrappableTypeException("Can't wrap null/None");
      }

      return null;
    }

    public String getDescription() {
      return null;
    }
  }
}
