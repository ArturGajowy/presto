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

import com.facebook.presto.operator.aggregation.state.RaggState;
import com.facebook.presto.operator.scalar.PageToRTranslator;
import com.facebook.presto.spi.Page;
import com.facebook.presto.spi.block.Block;
import com.facebook.presto.spi.block.BlockBuilder;
import com.facebook.presto.spi.function.AggregationFunction;
import com.facebook.presto.spi.function.CombineFunction;
import com.facebook.presto.spi.function.InputFunction;
import com.facebook.presto.spi.function.OutputFunction;
import com.facebook.presto.spi.function.SqlType;
import com.facebook.presto.spi.type.BigintType;
import com.facebook.presto.spi.type.DoubleType;
import com.facebook.presto.spi.type.StandardTypes;
import com.facebook.presto.spi.type.Type;
import com.facebook.presto.spi.type.VarcharType;
import io.airlift.slice.Slice;

import static java.lang.Double.longBitsToDouble;

@AggregationFunction(value = "ragg", decomposable = false)
public final class RAgg
{
    private RAgg() {}

    // int, string, string, int, string, string, int, int
    // Year, Carrier, TailNum, ActualElapsedTime, Origin, Dest, DepTime, ArrDelayMinutes

    private static final int YEAR_IDX = 0;
    private static final int CARRIER_IDX = 1;
    private static final int TAILNUM_IDX = 2;
    private static final int ELAPSEDTIME_IDX = 3;
    private static final int ORIGIN_IDX = 4;
    private static final int DESTINATION_IDX = 5;
    private static final int DEPARTURETIME_IDX = 6;
    private static final int DELAY_IDX = 7;

    @InputFunction
    public static void addInput(RaggState state,
            @SqlType(StandardTypes.VARCHAR) Slice rCode,
            @SqlType(StandardTypes.BIGINT) long year,
            @SqlType(StandardTypes.VARCHAR) Slice carrier,
            @SqlType(StandardTypes.VARCHAR) Slice tailNum,
            @SqlType(StandardTypes.BIGINT) long elapsedTime,
            @SqlType(StandardTypes.VARCHAR) Slice origin,
            @SqlType(StandardTypes.VARCHAR) Slice destination,
            @SqlType(StandardTypes.BIGINT) long depTime,
            @SqlType(StandardTypes.BIGINT) long delay)
    {
        if (state.retrieveCode().isEmpty()) {
            state.setCode(rCode.toStringUtf8());
        }
        state.retrieveBuilder(YEAR_IDX).writeLong(year).closeEntry();
        state.retrieveBuilder(CARRIER_IDX).writeBytes(carrier, 0, carrier.length()).closeEntry();
        state.retrieveBuilder(TAILNUM_IDX).writeBytes(tailNum, 0, tailNum.length()).closeEntry();
        state.retrieveBuilder(ELAPSEDTIME_IDX).writeLong(elapsedTime).closeEntry();
        state.retrieveBuilder(ORIGIN_IDX).writeBytes(origin, 0, origin.length()).closeEntry();
        state.retrieveBuilder(DESTINATION_IDX).writeBytes(destination, 0, destination.length()).closeEntry();
        state.retrieveBuilder(DEPARTURETIME_IDX).writeLong(depTime).closeEntry();
        state.retrieveBuilder(DELAY_IDX).writeLong(delay).closeEntry();
    }

    @CombineFunction
    public static void combine(RaggState state, RaggState otherState)
    {
        throw new IllegalStateException("unexpected combine call");
    }

    @OutputFunction(StandardTypes.DOUBLE)
    public static void output(RaggState state, BlockBuilder out)
    {
        PageToRTranslator rCaller = new PageToRTranslator();

        Page argumentsPage = buildArgumentsPage(state);
        Page resultPage = rCaller.RAGGREGATE(state.retrieveCode(), argumentsPage, DoubleType.DOUBLE, new Type[]{
                BigintType.BIGINT,
                VarcharType.createUnboundedVarcharType(),
                VarcharType.createUnboundedVarcharType(),
                BigintType.BIGINT,
                VarcharType.createUnboundedVarcharType(),
                VarcharType.createUnboundedVarcharType(),
                BigintType.BIGINT,
                BigintType.BIGINT});

        double result = longBitsToDouble(resultPage.getBlocks()[0].getLong(0, 0));

        DoubleType.DOUBLE.writeDouble(out, result);
    }

    private static Page buildArgumentsPage(RaggState state)
    {
        Block[] blocks = new Block[DELAY_IDX - YEAR_IDX + 1];
        for (int i = YEAR_IDX; i <= DELAY_IDX; ++i) {
            blocks[i] = state.retrieveBuilder(i).build();
        }
        return new Page(blocks);
    }
}
