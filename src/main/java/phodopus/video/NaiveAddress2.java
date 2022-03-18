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
import java.util.function.Consumer;

import com.google.common.collect.ImmutableList;

import phodopus.video.Chip.BitFormatter;
import phodopus.video.Simulation.State;

public final class NaiveAddress2
{
    public static void main( String[] args )
    {
        Chip addr0 = addr0();
        Chip addr1 = addr1();

        List< Chip > chips = ImmutableList.of( addr0, addr1 );
        dumpAllExpressions( chips );

        Consumer< State > print =
            state -> System.out.printf( "%02x%02x%n", state.number( "A8", 7 ), state.number( "A0", 8 ) );

        Simulation simulation = Simulation.create( chips );

        State state = simulation.zeroState();
        print.accept( state );
        System.out.println();

        // Start at 0x4321.
        System.out.println( "** Setting" );
        state = state.withNumber( "AS", 15, 0x4321 ).with( "SETADDR", true ).next();
        print.accept( state );
        System.out.println();

        // Test incrementing the address a few times.
        System.out.println( "** Incrementing" );
        state = state.with( "INCADDR", true ).next();
        print.accept( state );
        state = state.with( "INCADDR", true ).next();
        print.accept( state );
        System.out.println();

        System.out.println( "** Subtracting 160 (0xA0)" );
        state = state.with( "REPLINE", true ).next();
        print.accept( state );
        state = state.with( "REPLINE2", true ).next();
        print.accept( state );
        state = state.with( "REPLINE", true ).next();
        print.accept( state );
        state = state.with( "REPLINE2", true ).next();
        print.accept( state );
    }

    private static Chip addr0()
    {
        List< String > outputNames = concat( bitRange( "A", 8 ), "AC" );
        // [9..16] [17] [18] [19]
        List< String > inputs = concat( bitRange( "AS", 8 ), "INCADDR", "REPLINE", "SETADDR" );
        int as0 = outputNames.size();
        int incaddr = as0 + 8;
        int repline = incaddr + 1;
        int setaddr = repline + 1;

        int mask = 255;

        List< TruthTable > tables = TruthTable.createMany( inputs, outputNames, i ->
        {
            if ( isSet( i, setaddr ) )
            {
                // SETADDR
                return ( i >>> as0 ) & mask;
            }
            if ( isSet( i, repline ) )
            {
                // REPLINE
                int ac = ( i & mask ) < 160 ? 1 << 8 : 0;
                return ( ( i & mask ) - 160 ) & mask | ac;
            }

            int ac = ( i & mask ) == mask ? 1 << 8 : 0;
            if ( isSet( i, incaddr ) )
            {
                // INCADDR
                return ( ( i & mask ) + 1 ) & mask | ac;
            }
            // Don't change.
            return i & mask | ac;
        } );

        List< Expression > outputs = optimise( tables );
        return new Chip( "addr0",
                         inputs,
                         outputs,
                         new BitFormatter( "[x0-7] [8,AC]" ) );
    }

    private static Chip addr1()
    {
        List< String > outputNames = concat( bitRange( "A", 8, 7 ), "A14INC", "A14REP" );
        List< String > inputs = concat( bitRange( "AS", 8, 7 ), "INCADDR", "REPLINE2", "SETADDR", "AC" );
        int as8 = outputNames.size();
        int incaddr = as8 + 7;
        int repline2 = incaddr + 1;
        int setaddr = repline2 + 1;
        int ac = setaddr + 1;

        int mask = 127;

        List< TruthTable > tables = TruthTable.createMany( inputs, outputNames, i ->
        {
            int inc = ( i & mask ) + 1;
            int dec = ( i & mask ) - 1;

            if ( isSet( i, setaddr ) )
            {
                // SETADDR
                return ( i >>> as8 ) & mask;
            }
            if ( isSet( i, repline2 ) && isSet( i, ac ) )
            {
                // was REPLINE -- had to borrow 256
                return dec & 127;
            }
            if ( isSet( i, incaddr ) && isSet( i, ac ) )
            {
                // INCADDR -- had to carry 256
                return inc & 127;
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
