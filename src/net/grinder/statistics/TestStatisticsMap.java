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

package net.grinder.statistics;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Map;
import java.util.TreeMap;

import net.grinder.common.GrinderException;
import net.grinder.common.GrinderProperties;
import net.grinder.common.Test;
import net.grinder.util.Serialiser;


/**
 * A map of test numbers to {@link StatisticsImplementation}s.
 *
 * Unsynchronised.
 *
 * @author Philip Aston
 * @version $Revision$
 */
public class TestStatisticsMap implements java.io.Externalizable
{
    private static final long serialVersionUID = -8058790429635766121L;

    /** Use a TreeMap so we store in test number order. */
    private final Map m_data = new TreeMap();

    public final void put(Test test, StatisticsImplementation statistics)
    {
	m_data.put(test, statistics);
    }

    public final TestStatisticsMap getDelta(boolean updateSnapshot)
    {
	final TestStatisticsMap result = new TestStatisticsMap();

	final Iterator iterator = new Iterator();

	while (iterator.hasNext()) {

	    final Pair pair = iterator.next();

	    result.put(pair.getTest(),
		       pair.getStatistics().getDelta(updateSnapshot));
	}

	return result;
    }

    public final StatisticsImplementation getTotal()
    {
	final StatisticsImplementation result = new StatisticsImplementation();

	final java.util.Iterator iterator = m_data.values().iterator();

	while (iterator.hasNext()) {
	    result.add((StatisticsImplementation)iterator.next());
	}

	return result;
    }

    public final int getSize()
    {
	return m_data.size();
    }

    public final void add(TestStatisticsMap operand)
    {
	final Iterator iterator = operand.new Iterator();

	while (iterator.hasNext()) {

	    final Pair pair = iterator.next();

	    final Test test = pair.getTest();
	    final StatisticsImplementation statistics =
		(StatisticsImplementation)m_data.get(pair.getTest());

	    if (statistics == null) {
		put(test, pair.getStatistics().getClone());
	    }
	    else {
		statistics.add(pair.getStatistics());
	    }
	}
    }


    /**
     * A type safe iterator.
     */
    public final class Iterator
    {
	private final java.util.Iterator m_iterator;

	public Iterator()
	{
	    m_iterator = m_data.entrySet().iterator();
	}

	public final boolean hasNext()
	{
	    return m_iterator.hasNext();
	}

	public final Pair next()
	{
	    final Map.Entry entry = (Map.Entry)m_iterator.next();
	    final Test test = (Test)entry.getKey();
	    final StatisticsImplementation statistics =
		(StatisticsImplementation)entry.getValue();

	    return new Pair(test, statistics);
	}
    }

    public final class Pair
    {
	private final Test m_test;
	private final StatisticsImplementation m_statistics;

	private Pair(Test test, StatisticsImplementation statistics)
	{
	    m_test = test;
	    m_statistics = statistics;
	}

	public final Test getTest()
	{
	    return m_test;
	}

	public final StatisticsImplementation getStatistics()
	{
	    return m_statistics;
	}
    }

    public void writeExternal(ObjectOutput out)
	throws IOException
    {
	out.writeInt(m_data.size());

	final Serialiser serialiser = new Serialiser();

	final Iterator iterator = new Iterator();

	while (iterator.hasNext()) {
	    final Pair pair = iterator.next();

	    out.writeInt(pair.getTest().getNumber());
	    pair.getStatistics().myWriteExternal(out, serialiser);
	    //	    out.writeObject(pair.getStatistics());
	}
    }

    public void readExternal(ObjectInput in)
	throws IOException, ClassNotFoundException
    {
	final int n = in.readInt();

	m_data.clear();

	final Serialiser serialiser = new Serialiser();

	for (int i=0; i<n; i++) {
	    m_data.put(new LightweightTest(in.readInt()),
		       //		       (Statistics)in.readObject());
		       new StatisticsImplementation(in, serialiser));
	}
    }

    private final static class LightweightTest implements Test
    {
	private final int m_number;

	public LightweightTest(int number)
	{
	    m_number = number;
	}

	public final int getNumber()
	{
	    return m_number;
	}

	public final String getDescription()
	{
	    throw new RuntimeException(
		getClass().getName() +
		".LightweightTest.getDescription() should never be called");	    
	}

	public final GrinderProperties getParameters()
	{
	    throw new RuntimeException(
		getClass().getName() + ".LightweightTest.getParameters()");
	}

	public final int compareTo(Object o) 
	{
	    final int other = ((Test)o).getNumber();
	    return m_number<other ? -1 : (m_number==other ? 0 : 1);
	}

	/**
	 * The test number is used as the hash code. Wondered whether
	 * it was worth distributing the hash codes more evenly across
	 * the range of an int, but using the value is good enough for
	 * <code>java.lang.Integer</code> so its good enough for us.
	 **/
	public final int hashCode()
	{
	    return m_number;
	}

	public final boolean equals(Object o)
	{
	    if (o instanceof Test) {
		return m_number == ((Test)o).getNumber();
	    }

	    return false;
	}

	public final String toString()
	{
	    return "Test " + getNumber();
	}
    }
}
