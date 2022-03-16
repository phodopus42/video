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

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.ListIterator;
import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.google.common.base.Strings;

import phodopus.video.And.BitState;

/**
 * Table of 'n' input bits giving one result bit for each combination.
 *
 * @param name
 *            the table name
 * @param table
 *            bit set containing results, sized according to the number of
 *            inputs
 * @param bitCount
 *            count of input bits
 * @author matthew
 */
public record TruthTable( String name, BitSet table, int bitCount )
{
    /**
     * Creates a table based on a number of inputs behaving as an integer.
     *
     * @param name
     *            the table name
     * @param bitCount
     *            count of input bits
     * @param bitTester
     *            tester that will be called for each input value
     * @return a new table
     */
    public static TruthTable create( String name, int bitCount, IntPredicate bitTester )
    {
        BitSet table = new BitSet( 1 << bitCount );
        for ( int i = 0; i < 1 << bitCount; i++ )
        {
            table.set( i, bitTester.test( i ) );
        }
        return new TruthTable( name, table, bitCount );
    }

    /**
     * Creates a table based on a number of inputs behaving as an integer, giving an
     * output as an integer, and sampling just one bit from that output. This is
     * useful for things like counters.
     *
     * @param name
     *            the table name
     * @param bitCount
     *            count of input bits
     * @param operator
     *            operator that will be called for each input value
     * @param bitOfInterest
     *            the bit number of interest to sample from the output values
     * @return a new table
     */
    public static TruthTable create( String name, int bitCount, IntUnaryOperator operator, int bitOfInterest )
    {
        int mask = 1 << bitOfInterest;
        BitSet table = new BitSet( 1 << bitCount );
        for ( int i = 0; i < 1 << bitCount; i++ )
        {
            table.set( i, ( operator.applyAsInt( i ) & mask ) != 0 );
        }
        return new TruthTable( name, table, bitCount );
    }

    /**
     * Creates a table based on a number of inputs behaving as an integer, giving an
     * output as an integer, and sampling all bits from that output. This is
     * useful for things like counters.
     *
     * @param inputs
     *            unique inputs, forming bits [outputs.size(), outputs.size()+inputs.size()-1]
     * @param outputs
     *            outputs, forming bits 0..[outputs.size()-1]
     * @param operator
     *            operator that will be called for each input value
     * @return tables
     */
    public static List< TruthTable > createMany( List< String > inputs,
                                                 List< String > outputs,
                                                 IntUnaryOperator operator )
    {
        int inputBitCount = outputs.size() + inputs.size();
        return IntStream.range( 0, outputs.size() )
                        .mapToObj( bitOfInterest -> TruthTable.create( outputs.get( bitOfInterest ),
                                                                       inputBitCount,
                                                                       operator,
                                                                       bitOfInterest ) )
                        .toList();
    }

    /**
     * Optimises the table into an expression formed of an OR of ANDs.
     *
     * @return an expression
     */
    public Expression optimise()
    {
        List< And > results = new ArrayList<>();

        BitSet todo = (BitSet) this.table.clone();

        // Pick a bit that is set but is not part of an existing expression (yet).
        for ( int i = todo.nextSetBit( 0 ); i >= 0; i = todo.nextSetBit( i + 1 ) )
        {
            int output = i;

            // Make an expression to match this output combination alone.
            List< BitState > allBits =
                IntStream.range( 0, this.bitCount )
                         .mapToObj( bit -> ( ( output & ( 1 << bit ) ) != 0 ? BitState.TRUE : BitState.FALSE ) )
                         .collect( Collectors.toCollection( ArrayList::new ) );

            // Try dropping each of the bits to see if it still works.
            for ( ListIterator< BitState > iterator = allBits.listIterator(); iterator.hasNext(); )
            {
                BitState oldState = iterator.next();

                // Speculatively try dropping.
                iterator.set( BitState.DONT_CARE );

                if ( !And.of( allBits ).testAll( this.table, this.bitCount ) )
                {
                    // Ah, can't remove this one.  Revert back.
                    iterator.set( oldState );
                }
            }

            And and = And.of( allBits );
            results.add( and );

            // Mark these bits as done.
            and.clearAll( todo, this.bitCount );
        }

        assert todo.isEmpty();

        results.sort( null );

        return new Expression( this.name, results );
    }

    /**
     * Dumps the Karnaugh map to the console.
     */
    public void printKarnaughMap()
    {
        int rowBits = this.bitCount / 2;
        int colBits = this.bitCount - rowBits;

        int rs = 1 << rowBits;
        int cs = 1 << colBits;

        System.out.print( "; " + " ".repeat( rowBits ) + " |" );
        for ( int c = 0; c < cs; c++ )
        {
            String g = Strings.padStart( Integer.toBinaryString( GreyCode.code( c ) ), colBits, '0' );
            System.out.print( " " + g );
        }
        System.out.println( );
        System.out.println( "; " + "-".repeat( rowBits + 2 + colBits * cs + cs ) );

        for ( int r = 0; r < rs; r++ )
        {
            String g;
            if ( rs > 1 )
            {
                g = Strings.padStart( Integer.toBinaryString( GreyCode.code( r ) ), rowBits, '0' );
            }
            else
            {
                g = "x";
            }
            System.out.print( "; " + g.substring( 0, rowBits ) + " |" );

            for ( int c = 0; c < cs; c++ )
            {
                int input = ( GreyCode.code( r ) << colBits ) | GreyCode.code( c );
                int value = this.table.get( input ) ? 1 : 0;
                System.out.print( " ".repeat( colBits ) + value );
            }
            System.out.println();
        }

        System.out.println();
    }

    /**
     * @return an inverse
     */
    public TruthTable inverse()
    {
        BitSet clone = (BitSet) this.table.clone();
        clone.flip( 0, this.bitCount );
        return new TruthTable( "/" + this.name, clone, this.bitCount );
    }

    @Override
    public String toString()
    {
        return this.name;
    }
}
