/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.facebook.presto.operator.aggregation;

import com.facebook.presto.operator.aggregation.state.LongDecimalWithOverflowState;
import com.facebook.presto.operator.aggregation.state.LongDecimalWithOverflowStateFactory;
import com.facebook.presto.spi.PrestoException;
import com.facebook.presto.spi.block.BlockBuilder;
import com.facebook.presto.spi.block.BlockBuilderStatus;
import com.facebook.presto.spi.block.VariableWidthBlockBuilder;
import com.facebook.presto.spi.type.DecimalType;
import com.facebook.presto.spi.type.UnscaledDecimal128Arithmetic;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.math.BigInteger;

import static com.facebook.presto.spi.StandardErrorCode.NUMERIC_VALUE_OUT_OF_RANGE;
import static com.facebook.presto.spi.type.DecimalType.createDecimalType;
import static com.facebook.presto.spi.type.UnscaledDecimal128Arithmetic.unscaledDecimal;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

@Test(singleThreaded = true)
public class TestDecimalSumAggregation
{
    private static final BigInteger TWO = new BigInteger("2");
    private static final DecimalType TYPE = createDecimalType(38, 0);

    private LongDecimalWithOverflowState state;

    @BeforeMethod
    public void setUp()
    {
        state = new LongDecimalWithOverflowStateFactory().createSingleState();
    }

    @Test
    public void testOverflow()
    {
        addToState(state, TWO.pow(126));

        assertEquals(state.getOverflow(), 0);
        assertEquals(state.getLongDecimal(), unscaledDecimal(TWO.pow(126)));

        addToState(state, TWO.pow(126));

        assertEquals(state.getOverflow(), 1);
        assertEquals(state.getLongDecimal(), unscaledDecimal(0));
    }

    @Test
    public void testUnderflow()
    {
        addToState(state, TWO.pow(126).negate());

        assertEquals(state.getOverflow(), 0);
        assertEquals(state.getLongDecimal(), unscaledDecimal(TWO.pow(126).negate()));

        addToState(state, TWO.pow(126).negate());

        assertEquals(state.getOverflow(), -1);
        assertEquals(UnscaledDecimal128Arithmetic.compare(state.getLongDecimal(), unscaledDecimal(0)), 0);
    }

    @Test
    public void testUnderflowAfterOverflow()
    {
        addToState(state, TWO.pow(126));
        addToState(state, TWO.pow(126));
        addToState(state, TWO.pow(125));

        assertEquals(state.getOverflow(), 1);
        assertEquals(state.getLongDecimal(), unscaledDecimal(TWO.pow(125)));

        addToState(state, TWO.pow(126).negate());
        addToState(state, TWO.pow(126).negate());
        addToState(state, TWO.pow(126).negate());

        assertEquals(state.getOverflow(), 0);
        assertEquals(state.getLongDecimal(), unscaledDecimal(TWO.pow(125).negate()));
    }

    @Test
    public void testCombineOverflow()
    {
        addToState(state, TWO.pow(125));
        addToState(state, TWO.pow(126));

        LongDecimalWithOverflowState otherState = new LongDecimalWithOverflowStateFactory().createSingleState();

        addToState(otherState, TWO.pow(125));
        addToState(otherState, TWO.pow(126));

        DecimalSumAggregation.combine(state, otherState);
        assertEquals(state.getOverflow(), 1);
        assertEquals(state.getLongDecimal(), unscaledDecimal(TWO.pow(126)));
    }

    @Test
    public void testCombineUnderflow()
    {
        addToState(state, TWO.pow(125).negate());
        addToState(state, TWO.pow(126).negate());

        LongDecimalWithOverflowState otherState = new LongDecimalWithOverflowStateFactory().createSingleState();

        addToState(otherState, TWO.pow(125).negate());
        addToState(otherState, TWO.pow(126).negate());

        DecimalSumAggregation.combine(state, otherState);
        assertEquals(state.getOverflow(), -1);
        assertEquals(state.getLongDecimal(), unscaledDecimal(TWO.pow(126).negate()));
    }

    @Test
    public void testOverflowOnOutput()
    {
        addToState(state, TWO.pow(126));
        addToState(state, TWO.pow(126));

        assertEquals(state.getOverflow(), 1);
        try {
            DecimalSumAggregation.outputLongDecimal(TYPE, state, new VariableWidthBlockBuilder(new BlockBuilderStatus(), 10, 10));
            fail("failure expected");
        }
        catch (PrestoException e) {
            assertEquals(e.getErrorCode(), NUMERIC_VALUE_OUT_OF_RANGE.toErrorCode());
        }
    }

    private static void addToState(LongDecimalWithOverflowState state, BigInteger value)
    {
        BlockBuilder blockBuilder = TYPE.createFixedSizeBlockBuilder(1);
        TYPE.writeSlice(blockBuilder, unscaledDecimal(value));

        DecimalSumAggregation.inputLongDecimal(TYPE, state, blockBuilder.build(), 0);
    }
}
