// The Grinder
// Copyright (C) 2000, 2001  Paco Gomez
// Copyright (C) 2000, 2001  Philip Aston

// This program is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License
// as published by the Free Software Foundation; either version 2
// of the License, or (at your option) any later version.

// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.

// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.

package net.grinder.engine.process;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

import net.grinder.communication.CommunicationException;
import net.grinder.communication.Message;
import net.grinder.communication.Receiver;
import net.grinder.communication.ReportStatisticsMessage;
import net.grinder.communication.Sender;
import net.grinder.communication.StartGrinderMessage;
import net.grinder.communication.StopGrinderMessage;
import net.grinder.engine.EngineException;
import net.grinder.plugininterface.GrinderPlugin;
import net.grinder.plugininterface.PluginProcessContext;
import net.grinder.plugininterface.Test;
import net.grinder.plugininterface.ThreadCallbacks;
import net.grinder.statistics.Statistics;
import net.grinder.statistics.StatisticsTable;
import net.grinder.statistics.TestStatisticsMap;
import net.grinder.util.FilenameFactory;
import net.grinder.util.GrinderException;
import net.grinder.util.GrinderProperties;
import net.grinder.util.ProcessContextImplementation;
import net.grinder.util.PropertiesHelper;


/**
 * The class executed by the main thread of each JVM.
 * The total number of JVM is specified in the property "grinder.jvms".
 * This class is responsible for creating as many threads as configured in the
 * property "grinder.threads". Each thread is an object of class "CycleRunnable".
 * It is responsible for storing the statistical information from the threads
 * and also for send it to the console and print it at the end.
 * 
 * @author Paco Gomez
 * @author Philip Aston
 * @version $Revision$
 * @see net.grinder.engine.process.GrinderThread
 */
public class GrinderProcess
{
    /**
     * The application's entry point.
     * 
     */    
    public static void main(String args[])
    {
	if (args.length < 2 || args.length > 3) {
	    System.err.println("Usage: java " +
			       GrinderProcess.class.getName() +
			       " <hostID> <processID>");
	    System.exit(1);
	}

	if (args.length == 3) {
	    GrinderProperties.setPropertiesFileName(args[2]);
	}

	try {
	    final GrinderProcess grinderProcess = 
		new GrinderProcess(args[0], args[1]);

	    grinderProcess.run();
	}
	catch (GrinderException e) {
	    System.err.println("Error initialising grinder process: " + e);
	    e.printStackTrace();
	    System.exit(2);
	}
    }

    private final ProcessContextImplementation m_context;
    private final int m_numberOfThreads;
    private final String m_logDirectory;
    private final boolean m_appendToLog;

    private final ConsoleListener m_consoleListener;
    private final Sender m_consoleSender;
    private int m_reportToConsoleInterval = 0;

    private final GrinderPlugin m_plugin;

    /** A map of Tests to TestData. (TestData is the class this
     * package uses to store information about Tests). */
    private final Map m_testSet;

    /** A map of Tests to Statistics for passing elsewhere. */
    private final TestStatisticsMap m_testStatisticsMap;

    public GrinderProcess(String hostID, String processID)
	throws GrinderException
    {
	final GrinderProperties properties = GrinderProperties.getProperties();
	final PropertiesHelper propertiesHelper = new PropertiesHelper();

	m_context = new ProcessContextImplementation(hostID, processID);

	m_numberOfThreads = properties.getInt("grinder.threads", 1);
	m_logDirectory = properties.getProperty("grinder.logDirectory", ".");
	m_appendToLog = properties.getBoolean("grinder.appendLog", false);

	// Parse console configuration.
	final String multicastAddress = 
	    properties.getProperty("grinder.multicastAddress");

	final boolean receiveConsoleSignals =
		properties.getBoolean("grinder.receiveConsoleSignals", false);

	if (receiveConsoleSignals) {
	    final int grinderPort = properties.getInt("grinder.multicastPort",
						      0);

	    if (multicastAddress != null && grinderPort > 0) {
		m_consoleListener = new ConsoleListener(m_context,
							multicastAddress,
							grinderPort);
		final Thread t = new Thread(m_consoleListener,
					    "Console Listener");
		t.setDaemon(true);
		t.start();
	    }
	    else {
		throw new EngineException(
		    "Unable to receive console signals: " +
		    "multicast address or port not specified");
	    }
	}
	else {
	    m_consoleListener = null;
	}

	final boolean reportToConsole =
	    properties.getBoolean("grinder.reportToConsole", false);

	if (reportToConsole) {
	    final int consolePort =
		properties.getInt("grinder.console.multicastPort", 0);

	    if (multicastAddress != null && consolePort > 0) {
		m_consoleSender = new Sender(multicastAddress, consolePort);

		m_reportToConsoleInterval =
		    properties.getInt("grinder.reportToConsole.interval", 500);
	    }
	    else {
		throw new EngineException(
		    "Unable to report to console: " +
		    "multicast address or console port not specified");
	    }
	}
	else {
	    m_consoleSender = null;
	}

	// Parse plugin class.
	m_plugin = propertiesHelper.instantiatePlugin(m_context);

	// Get defined tests.
	final Set tests = propertiesHelper.getTestSet(m_plugin);

	// Wrap tests with our information.
	m_testSet = new TreeMap();
	m_testStatisticsMap = new TestStatisticsMap();
	
	final Iterator testSetIterator = tests.iterator();

	while (testSetIterator.hasNext())
	{
	    final Test test = (Test)testSetIterator.next();

	    final String sleepTimePropertyName =
		propertiesHelper.getTestPropertyName(test.getTestNumber(),
						     "sleepTime");

	    final long sleepTime =
		properties.getInt(sleepTimePropertyName, -1);

	    final Statistics statistics = new Statistics();
	    m_testSet.put(test, new TestData(test, sleepTime, statistics));
	    m_testStatisticsMap.put(test, statistics);
	}
    }    

    /**
     * The application's main loop. This is split from the constructor
     * as theoretically it might be called multiple times. The
     * constructor sets up the static configuration, this does a
     * single execution.
     */        
    protected void run() throws GrinderException
    {
	final String dataFilename =
	    m_context.getFilenameFactory().createFilename("data");

	final PrintWriter dataPrintWriter;

	try {
	    dataPrintWriter =
		new PrintWriter(
		    new BufferedWriter(
			new FileWriter(dataFilename, m_appendToLog)));

	    if (!m_appendToLog) {
		dataPrintWriter.println("Thread, Cycle, Method, Time");
	    }
	}
	catch(Exception e){
	    throw new EngineException("Cannot open process data file '" +
				      dataFilename + "'", e);
	}

	final GrinderThread runnable[] = new GrinderThread[m_numberOfThreads];

	for (int i=0; i<m_numberOfThreads; i++) {
	    final ThreadContextImplementation pluginThreadContext =
		new ThreadContextImplementation(m_context, i);

	    final ThreadCallbacks threadCallbackHandler =
		m_plugin.createThreadCallbackHandler();

	    runnable[i] = new GrinderThread(this, threadCallbackHandler,
					    pluginThreadContext,
					    dataPrintWriter, m_testSet);
	}
        
	if (m_consoleListener != null) {
	    m_consoleListener.reset();

	    m_context.logMessage("waiting for console signal");

	    do {
		try {
		    synchronized (this) {
			wait();
		    }
		}
		catch (InterruptedException e) {
		    continue;
		}
	    }
	    while (!m_consoleListener.startReceived() &&
		   !m_consoleListener.stopReceived());
	}

	if (!m_consoleListener.stopReceived()) {
	    m_context.logMessage("starting threads");

	    //   Start the threads
	    for (int i=0; i<m_numberOfThreads; i++) {
		final Thread t = new Thread(runnable[i],
					    "Grinder thread " + i);
		t.setDaemon(true);
		t.start();
	    }
	}

	do			// We want at least one report.
	{
	    try {
		synchronized (this) {
		    wait(m_reportToConsoleInterval);
		}
	    }
	    catch (InterruptedException e) {
		continue;
	    }

	    if (m_consoleSender != null) {
		m_consoleSender.send(
		    new ReportStatisticsMessage(
			m_context.getHostIDString(),
			m_context.getProcessIDString(),
			m_testStatisticsMap.getDelta(true)));
	    }
	}
	while (GrinderThread.numberOfUncompletedThreads() > 0 &&
	       !m_consoleListener.stopReceived());

        if (dataPrintWriter != null) {
	    dataPrintWriter.close();
	}

	m_context.logMessage("finished");

 	System.out.println("Final statistics for this process:");

	final StatisticsTable statisticsTable =
	    new StatisticsTable(m_testStatisticsMap);

	statisticsTable.print(System.out);
    }

    /**
     * Runnable that receives event messages from the Console.
     * Currently no point in synchronising access.
     */
    private class ConsoleListener implements Runnable
    {
	private final PluginProcessContext m_context;
	private final Receiver m_receiver;
	private boolean m_startReceived = false;
	private boolean m_stopReceived = false;

	public ConsoleListener(PluginProcessContext context, String address,
			       int port)
	    throws CommunicationException
	{
	    m_context = context;
	    m_receiver = new Receiver(address, port);
	}
	
	public void run()
	{
	    while (true) {
		final Message message;
		
		try {
		    message = m_receiver.waitForMessage();
		}
		catch (CommunicationException e) {
		    m_context.logError("Error receiving console signal: " + e);
		    continue;
		}

		if (message instanceof StartGrinderMessage) {
		    m_context.logMessage("Got a start message from console");
		    m_startReceived = true;
		}
		else if (message instanceof StopGrinderMessage) {
		    m_context.logMessage("Got a stop message from console");
		    m_stopReceived = true;
		}
		else {
		    m_context.logMessage(
			"Got an unknown message from console");
		}

		m_context.logMessage("notifying " + GrinderProcess.this);
		synchronized(GrinderProcess.this) {
		    GrinderProcess.this.notifyAll();
		}
	    }
	}

	public void reset()
	{
	    m_startReceived = false;
	    m_stopReceived = false;
	}

	public boolean startReceived()
	{
	    return m_startReceived;
	}

	public boolean stopReceived()
	{
	    return m_stopReceived;
	}
    }
}


