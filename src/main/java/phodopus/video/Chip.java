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

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;
import java.util.function.ObjIntConsumer;
import java.util.stream.Stream;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

public record Chip( String name, List< String > inputs, List< Expression > outputs, IntFunction< String > formatter )
{
    public Chip
    {
        assert outputs.size() <= 10 : "Too many outputs";
        assert ( inputs.size() + outputs.size() ) <= 21 : "Too many pins in total";
    }

    public String state( int value )
    {
        return this.formatter.apply( value );
    }

    public void dumpExpressions()
    {
        PrintWriter writer = new PrintWriter( System.out );
        dumpExpressions( writer );
        writer.flush();
    }

    public void dumpExpressions( PrintWriter writer )
    {
        List< String > names = Stream.concat( this.outputs.stream().map( Expression::name ), this.inputs.stream() ).toList();
        for ( Expression output : this.outputs )
        {
            List< And > ands = output.ands();
            int terms = ands.size();
            if ( terms == 1 )
            {
                writer.println( "; 1 term" );
            }
            else
            {
                writer.println( "; " + terms + " terms" );
            }

            if ( terms > 16 )
            {
                writer.println( "; ***** cannot achieve *****" );
            }
            else if ( terms > 14 )
            {
                writer.println( "; 22V10 pins 18-19 only" );
            }
            else if ( terms > 12 )
            {
                writer.println( "; 22V10 pins 17-20 only" );
            }
            else if ( terms > 10 )
            {
                writer.println( "; 22V10 pins 16-21 only" );
            }
            else if ( terms > 8 )
            {
                writer.println( "; 22V10 pins 15-22 only" );
            }

            writer.println( output.name() + ".R =" );
            String prefix = "      ";

            for ( And and : ands )
            {
                writer.println( prefix + and.toString( names ) );
                prefix = prefix.substring( 0, prefix.length() - 2 ) + "+ ";

            }
            writer.println();
        }
    }

    public static final class BitFormatter implements IntFunction< String >
    {
        private final String format;

        private final List< ObjIntConsumer< StringBuilder > > parts = new ArrayList<>();

        public BitFormatter( String format )
        {
            // TODO this code is awful

            // e.g. "[0,MYFLAG] some string [1-4] [x1-4]"
            this.format = format;

            int i = 0;
            while ( i < format.length() )
            {
                int bracket = format.indexOf( '[', i );
                String literal = this.format.substring( i, bracket < 0 ? format.length() : bracket );
                this.parts.add( ( output, value ) -> output.append( literal ) );
                if ( bracket < 0 )
                {
                    break;
                }

                int end = format.indexOf( ']', bracket + 1 );
                Preconditions.checkArgument( end >= 0 );

                int comma = format.indexOf( ',', bracket + 1 );
                int dash = format.indexOf( '-', bracket + 1 );
                if ( comma > 0 && comma < end )
                {
                    // It's a single-bit flag.
                    int bit = 1 << Integer.parseInt( this.format.substring( bracket + 1, comma ) );
                    String name = this.format.substring( comma + 1, end );
                    this.parts.add( ( output, value ) -> output.append( ( value & bit ) != 0 ? ' ': '/' ).append( name ) );
                }
                else if ( dash > 0 && dash < end )
                {
                    // It's a number.
                    boolean hex = this.format.charAt( bracket + 1 ) == 'x';
                    int from = Integer.parseInt( this.format.substring( bracket + 1 + ( hex ? 1 : 0 ), dash ) );
                    int to = Integer.parseInt( this.format.substring( dash + 1, end ) );
                    int mask = ( 1 << ( to - from + 1 ) ) - 1;
                    if ( hex )
                    {
                        int digits = ( to - from + 1 + 3 ) / 4;
                        this.parts.add( ( output, value ) -> output.append( Strings.padStart( Integer.toHexString( ( value >>> from ) & mask ), digits, '0' ) ) );
                    }
                    else
                    {
                        int max = ( 1 << ( to - from + 1 ) ) - 1;
                        int digits = Integer.toString( max ).length();
                        this.parts.add( ( output, value ) -> output.append( Strings.padStart( Integer.toString( ( value >>> from ) & mask ), digits, '0' ) ) );
                    }
                }
                else
                {
                    throw new IllegalArgumentException();
                }

                i = end + 1;
            }
        }

        @Override
        public String apply( int value )
        {
            StringBuilder output = new StringBuilder();
            for ( ObjIntConsumer< StringBuilder > part : this.parts )
            {
                part.accept( output, value );
            }

            return output.toString();
        }

        @Override
        public String toString()
        {
            return this.format;
        }
    }
}
