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

import java.util.BitSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.google.common.collect.ImmutableList;

public final class And implements Comparable< And >
{
    private final ImmutableList< BitState > bits;

    private final int interestMask;

    private final int setMask;

    private And( ImmutableList< BitState > bits )
    {
        this.bits = Objects.requireNonNull( bits );

        int im = 0;
        int sm = 0;
        for ( int i = 0; i < bits.size(); i++ )
        {
            int bit = 1 << i;
            switch ( bits.get( i ) )
            {
                case TRUE ->
                {
                    im |= bit;
                    sm |= bit;
                }
                case FALSE -> im |= bit;
                default ->
                {
                    // nothing
                }
            }
        }
        this.interestMask = im;
        this.setMask = sm;

        assert ( ~this.interestMask & this.setMask ) == 0;
    }

    public static And of( Iterable< BitState > bits )
    {
        ImmutableList< BitState > list = ImmutableList.copyOf( bits );
        while ( !list.isEmpty() && list.get( list.size() - 1 ) == BitState.DONT_CARE )
        {
            list = list.subList( 0, list.size() - 1 );
        }
        return new And( list );
    }

    public boolean contains( And other )
    {
        for ( int i = 0; i < other.bits.size(); i++ )
        {
            BitState otherBit = other.bits.get( i );
            BitState thisBit = i < this.bits.size() ? this.bits.get( i ) : BitState.DONT_CARE;
            if ( thisBit != BitState.DONT_CARE && thisBit != otherBit )
            {
                return false;
            }
        }

        return true;
    }

    public boolean test( int input )
    {
        return ( input & this.interestMask ) == this.setMask;
    }

    /**
     * Determines if all matching combinations (that is, inputs that evaluate to 1)
     * have a 1 in the given table.
     * <p>
     * It does not check any combinations that do not match (that is, inputs that
     * evaluate to 0).
     *
     * @param table
     *            the truth table
     * @param bitCount
     *            the bit count of the table
     * @return whether all match
     */
    public boolean testAll( BitSet table, int bitCount )
    {
        // The fewer bits used, the more combinations there are to check.
        int usedBits = Integer.bitCount( this.interestMask );
        int unusedBits = bitCount - usedBits;
        int combinations = 1 << unusedBits;

        for ( int combination = 0; combination < combinations; combination++ )
        {
            // Turn a combination into an input that yields 1 for this expression.
            // Firstly, it must have all bits in setMask.
            // Secondly, any bit not in interestMask must be set according to the
            // combination.
            int input = this.setMask;
            int data = combination;
            for ( int i = 0; i < bitCount; i++ )
            {
                int bit = 1 << i;

                // Is this a DONT_CARE bit?
                if ( ( this.interestMask & bit ) == 0 )
                {
                    input |= ( 1 << i ) * ( data & 1 );
                    data >>>= 1;
                }
            }

            // This must be a case that is covered.
            assert ( input & this.interestMask ) == this.setMask;

            if ( !table.get( input ) )
            {
                // It's a 0, so it's not good.
                return false;
            }
        }

        return true;
    }

    public boolean clearAll( BitSet table, int bitCount )
    {
        int end = 1 << bitCount;

        for ( int input = 0; input < end; input++ )
        {
            if ( ( input & this.interestMask ) == this.setMask )
            {
                table.clear( input );
            }
        }

        return true;
    }

    @Override
    public boolean equals( Object obj )
    {
        return obj instanceof And and && and.bits.equals( this.bits );
    }

    @Override
    public int hashCode()
    {
        return this.bits.hashCode();
    }

    @Override
    public int compareTo( And o )
    {
        // If they aren't of the same size, earlier one is the one that has a higher bit.
        int result = Integer.compare( o.bits.size(), this.bits.size() );
        if ( result != 0 )
        {
            return result;
        }

        for ( int i = this.bits.size() - 1; i >= 0; i-- )
        {
            boolean tb = this.bits.get( i ) != BitState.DONT_CARE;
            boolean to = o.bits.get( i ) != BitState.DONT_CARE;
            if ( tb != to )
            {
                return tb ? -1 : 1;
            }
        }

        return 0;
    }

    @Override
    public String toString()
    {
        return toString( IntStream.range( 0, this.bits.size() )
                                  .mapToObj( i -> "B" + i )
                                  .toList() );
    }

    public String toString( List< String > bitNames )
    {
        if ( this.bits.isEmpty() )
        {
            return "[true]";
        }

        return IntStream.range( 0, this.bits.size() )
                        // reverse order
                        .map( i -> this.bits.size() - i - 1 )
                        .mapToObj( i -> this.bits.get( i ).format( bitNames.get( i ) ) )
                        .filter( string -> !string.isEmpty() )
                        .collect( Collectors.joining( " * " ) );
    }

    public enum BitState
    {
        DONT_CARE( "" ),
        TRUE( " %s" ),
        FALSE( "/%s" );

        private final String formatString;

        BitState( String formatString )
        {
            this.formatString = formatString;
        }


        String format( String name )
        {
            return String.format( Locale.ROOT, this.formatString, name );
        }
    }
}
