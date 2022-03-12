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

public record TruthTable( String name, BitSet table, int bitCount )
{
    public static TruthTable create( int bitCount, IntPredicate bitTester )
    {
        return create( "bit", bitCount, bitTester );
    }

    public static TruthTable create( String name, int bitCount, IntPredicate bitTester )
    {
        BitSet table = new BitSet( 1 << bitCount );
        for ( int i = 0; i < 1 << bitCount; i++ )
        {
            table.set( i, bitTester.test( i ) );
        }
        return new TruthTable( name, table, bitCount );
    }

    public static TruthTable create( int bitCount, IntUnaryOperator operator, int bitOfInterest )
    {
        return create( "bit", bitCount, operator, bitOfInterest );
    }

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

    public static List< TruthTable > createMany( int bitCount, IntUnaryOperator operator )
    {
        return IntStream.range( 0, bitCount ).mapToObj( bitOfInterest -> TruthTable.create( bitCount, operator, bitOfInterest ) ).toList();
    }

    public static List< TruthTable > createMany( int inputBitCount, int outputBitCount, IntUnaryOperator operator )
    {
        return IntStream.range( 0, outputBitCount )
                        .mapToObj( bitOfInterest -> TruthTable.create( inputBitCount, operator, bitOfInterest ) ).toList();
    }

    /**
     * @param inputs
     *            unique inputs, forming bits [outputs.size(), outputs.size()+inputs.size()-1]
     * @param outputs
     *            outputs, forming bits 0..[outputs.size()-1]
     * @param operator
     *            the operator thing
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

    public Expression optimise()
    {
        List< And > results = new ArrayList<>();

        BitSet todo = (BitSet) this.table.clone();

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
                iterator.set( BitState.DONT_CARE );
                if ( !And.of( allBits ).testAll( this.table, this.bitCount ) )
                {
                    // Ah, can't remove this one.
                    iterator.set( oldState );
                }
            }
            And and = And.of( allBits );
            and.clearAll( todo, this.bitCount );
            results.add( and );
        }

        assert todo.isEmpty();

        results.sort( null );

        return new Expression( this.name, results );
    }

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

    public TruthTable inverse()
    {
        BitSet clone = (BitSet) this.table.clone();
        clone.flip( 0, this.bitCount );
        return new TruthTable( "/" + this.name, clone, this.bitCount );
    }
}
