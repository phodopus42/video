/* Copyright © 2022 Matthew Wilson
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0
 * which is available at:
 * http://www.eclipse.org/legal/epl-2.0
 * or the GNU General Public License v3.0 or later
 * which is available at:
 * https://www.gnu.org/licenses/gpl-3.0.en.html
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-3.0-or-later
 */
package phodopus.video;

import java.util.List;
import java.util.stream.IntStream;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public final class Util
{
    private Util()
    {
        throw new AssertionError();
    }

    public static boolean isSet( int value, int bit )
    {
        return ( value & ( 1 << bit ) ) != 0;
    }

    public static boolean isBetween( int value, int offset, int mask, int fromInclusive, int toExclusive )
    {
        int masked = ( value >>> offset ) & mask;
        return masked >= fromInclusive && masked < toExclusive;
    }

    public static boolean isEqual( int value, int offset, int mask, int desired )
    {
        int masked = ( value >>> offset ) & mask;
        return masked == desired;
    }

    public static List< String > bitRange( String name, int count )
    {
        return bitRange( name, 0, count );
    }

    public static List< String > bitRange( String name, int start, int count )
    {
        return IntStream.range( start, start + count ).mapToObj( i -> name + i ).toList();
    }

    public static List< Expression > optimise( List< TruthTable > tables )
    {
        return tables.stream().map( TruthTable::optimise ).toList();
    }

    @SafeVarargs
    public static < T > List< T > concat( List< T >... lists )
    {
        return ImmutableList.copyOf( Iterables.concat( lists ) );
    }

    public static < T > List< T > concat( List< T > list, T item )
    {
        return ImmutableList.copyOf( Iterables.concat( list, ImmutableList.of( item ) ) );
    }

    @SafeVarargs
    public static < T > List< T > concat( List< T > list, T... items )
    {
        return ImmutableList.copyOf( Iterables.concat( list, ImmutableList.copyOf( items ) ) );
    }

    public static void dumpAllExpressions( Iterable< Chip > chips )
    {
        for ( Chip chip : chips )
        {
            System.out.println( chip.name() );
            System.out.println();
            chip.dumpExpressions();
            System.out.println();
            System.out.println( "--------------" );
            System.out.println();
        }
    }
}
