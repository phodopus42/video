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
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

public final class Simulation
{
    private final List< Chip > chips;
    private final Table< String, Integer, Integer > inputMasks;

    private Simulation( List< Chip > chips, Table< String, Integer, Integer > inputMasks )
    {
        this.chips = chips;
        this.inputMasks = inputMasks;
    }

    public static Simulation create( List< Chip > chips )
    {
        Table< String, Integer, Integer > pinMasks = HashBasedTable.create();
        for ( int i = 0; i < chips.size(); i++ )
        {
            Chip chip = chips.get( i );

            // Outputs are first, then inputs.
            int mask = 1;
            for ( Expression output : chip.outputs() )
            {
                pinMasks.put( output.name(), i, mask );

                mask <<= 1;
            }
            for ( String input : chip.inputs() )
            {
                // Assume that inputs aren't duplicated on a single chip.
                pinMasks.put( input, i, mask );

                mask <<= 1;
            }
        }

        return new Simulation( chips, pinMasks );
    }

    public List< Chip > chips()
    {
        return this.chips;
    }

    public State zeroState()
    {
        return new State( new int[ this.chips.size() ] );
    }

    public State randomState()
    {
        int[] values = new int[ this.chips.size() ];
        for ( int i = 0; i < this.chips.size(); i++ )
        {
            Chip chip = this.chips.get( i );
            int mask = ( 1 << chip.outputs().size() + chip.inputs().size() ) - 1;
            values[ i ] = ThreadLocalRandom.current().nextInt() & mask;
        }

        // Ensure they are consistent.
        return new State( values ).wireInputs();
    }

    public final class State
    {
        private final int[] values;

        private State( int[] values )
        {
            this.values = values;
        }

        public State with( String input, boolean flag )
        {
            int[] newValues = this.values.clone();
            setImpl( newValues, input, flag );
            return new State( newValues );
        }

        public State next()
        {
            int[] newValues = new int[ this.values.length ];

            // Evaluate output expressions.
            for ( int i = 0; i < Simulation.this.chips.size(); i++ )
            {
                Chip chip = Simulation.this.chips.get( i );
                int outputValue = 0;
                for ( int j = 0; j < chip.outputs().size(); j++ )
                {
                    if ( chip.outputs().get( j ).evaluate( this.values[ i ] ) )
                    {
                        outputValue |= 1 << j;
                    }
                }

                // Leave inputs uninitialised for now.
                newValues[ i ] = outputValue;
            }

            // Wire up inputs for next round.
            wireInputsImpl( newValues );

            return new State( newValues );
        }

        private State wireInputs()
        {
            int[] newValues = this.values.clone();
            wireInputsImpl( newValues );
            return new State( newValues );
        }

        private void wireInputsImpl( int[] newValues )
        {
            for ( int i = 0; i < Simulation.this.chips.size(); i++ )
            {
                Chip chip = Simulation.this.chips.get( i );
                for ( int j = 0; j < chip.outputs().size(); j++ )
                {
                    setImpl( newValues,
                             chip.outputs().get( j ).name(),
                             ( ( newValues[ i ] >>> j ) & 1 ) != 0 );
                }
            }
        }

        private void setImpl( int[] newValues, String input, boolean flag )
        {
            for ( Map.Entry< Integer, Integer > entry : Simulation.this.inputMasks.row( input ).entrySet() )
            {
                int chip = entry.getKey();
                int mask = entry.getValue();
                newValues[ chip ] = flag ? newValues[ chip ] | mask : newValues[ chip ] & ~mask;
            }

//            // TODO build an index
//            for ( int i = 0; i < Simulation.this.chips.size(); i++ )
//            {
//                Chip chip = Simulation.this.chips.get( i );
//                int index = chip.inputs().indexOf( input );
//                if ( index >= 0 )
//                {
//                    // Outputs are first, then inputs.
//                    int fullIndex = index + chip.outputs().size();
//                    int mask = 1 << fullIndex;
//                    newValues[ i ] = flag ? newValues[ i ] | mask : newValues[ i ] & ~mask;
//                }
//            }
        }

        public void dump()
        {
            for ( int i = 0; i < Simulation.this.chips.size(); i++ )
            {
                Chip chip = Simulation.this.chips.get( i );
                System.out.println( chip.name() + " " + chip.state( this.values[ i ] ) );
            }
        }

        public boolean flag( String pin )
        {
            // Any match will do.
            Map.Entry< Integer, Integer > entry = Simulation.this.inputMasks.row( pin ).entrySet().iterator().next();
            int value = this.values[ entry.getKey() ];
            int mask = entry.getValue();
            return ( value & mask ) != 0;
        }

        public String formattedFlag( String pin )
        {
            return ( flag( pin ) ? ' ' : '/' ) + pin;
        }

        public int number( String pin, int count )
        {
            // Any match will do, as long as it's an output.
            Map.Entry< Integer, Integer > entry = null;
            for ( Map.Entry< Integer, Integer > e : Simulation.this.inputMasks.row( pin ).entrySet() )
            {
                Chip chip = Simulation.this.chips.get( e.getKey() );
                if ( e.getValue() < chip.outputs().size() )
                {
                    entry = e;
                    break;
                }
            }
            int value = this.values[ entry.getKey() ];
            int start = Integer.numberOfTrailingZeros( entry.getValue() );
            int mask = ( 1 << count ) - 1;
            return ( value >>> start ) & mask;
        }
    }
}
