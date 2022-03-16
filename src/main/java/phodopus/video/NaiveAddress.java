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

import static phodopus.video.Util.bitRange;
import static phodopus.video.Util.concat;
import static phodopus.video.Util.dumpAllExpressions;
import static phodopus.video.Util.isSet;
import static phodopus.video.Util.optimise;

import java.util.List;

import com.google.common.collect.ImmutableList;

import phodopus.video.Chip.BitFormatter;

public final class NaiveAddress
{
    public static void main( String[] args )
    {
        Chip addr0 = addr0();
        Chip addr1 = addr1();

        dumpAllExpressions( ImmutableList.of( addr0, addr1 ) );
    }

    private static Chip addr0()
    {
        List< String > outputNames = concat( bitRange( "A", 8 ), ImmutableList.of( "AC", "AB" ) );
        List< String > inputs = concat( bitRange( "AS", 8 ), ImmutableList.of( "INCADDR", "REPLINE", "SETADDR" ) );
        int as0 = outputNames.size();
        int incaddr = as0 + 8;
        int repline = incaddr + 1;
        int setaddr = repline + 1;

        int mask = 255;

        List< TruthTable > tables = TruthTable.createMany( inputs, outputNames, i ->
        {
            int ab = ( i & mask ) < 160 ? 1 << 9 : 0;
            int ac = ( i & mask ) == mask ? 1 << 8 : 0;
            if ( isSet( i, setaddr ) )
            {
                // SETADDR
                return ( i >>> as0 ) & mask;
            }
            if ( isSet( i, repline ) )
            {
                // REPLINE
                return ( ( i & mask ) - 160 ) & mask | ac | ab;
            }

            if ( isSet( i, incaddr ) )
            {
                // INCADDR
                return ( ( i & mask ) + 1 ) & mask | ac | ab;
            }
            // Don't change.
            return i & mask | ac | ab;
        } );

        List< Expression > outputs = optimise( tables );
        return new Chip( "addr0",
                         inputs,
                         outputs,
                         new BitFormatter( "[x0-7] [8,AC] [9,AB]" ) );
    }

    private static Chip addr1()
    {
        List< String > outputNames = concat( bitRange( "A", 8, 7 ) );
        List< String > inputs = concat( bitRange( "AS", 8, 7 ), ImmutableList.of( "INCADDR", "REPLINE", "SETADDR", "AC", "AB" ) );
        int as8 = outputNames.size();
        int incaddr = as8 + 7;
        int repline = incaddr + 1;
        int setaddr = repline + 1;
        int ac = setaddr + 1;
        int ab = ac + 1;

        int mask = 127;

        List< TruthTable > tables = TruthTable.createMany( inputs, outputNames, i ->
        {
            if ( isSet( i, setaddr ) )
            {
                // SETADDR
                return ( i >>> as8 ) & mask;
            }
            if ( isSet( i, repline ) && isSet( i, ab ) )
            {
                return ( i & mask ) - 1;
            }
            if ( isSet( i, incaddr ) && isSet( i, ac ) )
            {
                return ( i & mask ) + 1;
            }
            // Don't change.
            return i & mask;
        } );

        List< Expression > outputs = optimise( tables );
        return new Chip( "addr1",
                         inputs,
                         outputs,
                         new BitFormatter( "[x0-6]" ) );
    }

}
