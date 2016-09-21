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
package com.facebook.presto.operator.scalar;

import com.facebook.presto.spi.function.Description;
import com.facebook.presto.spi.function.ScalarFunction;
import com.facebook.presto.spi.function.SqlType;
import com.facebook.presto.spi.function.TypeParameter;
import com.facebook.presto.spi.type.StandardTypes;
import com.google.common.base.Throwables;
import io.airlift.slice.Slice;
import io.airlift.slice.Slices;
import org.rosuda.REngine.REXP;
import org.rosuda.REngine.REXPMismatchException;
import org.rosuda.REngine.REngineException;
import org.rosuda.REngine.Rserve.RConnection;
import org.rosuda.REngine.Rserve.RserveException;

@Description("Calls defined R function for given parameters")
@ScalarFunction("R")
public final class RFunctions
{
    private RFunctions()
    {}

    private static RConnection connection = getConnectionIgnoreException();
    private static boolean functionLoaded = false;

    @SqlType(StandardTypes.VARCHAR)
    @TypeParameter("T")
    public static Slice typeof(@SqlType("VARCHAR") Slice functionCode, @SqlType("T") Slice value)
    {
        try {
            //RConnection connection = new RConnection();
            REXP x = connection.eval("R.version.string");
            connection.close();
            return Slices.wrappedBuffer(x.asString().getBytes());
        }
        catch (RserveException | REXPMismatchException e) {
            throw Throwables.propagate(e);
        }
    }

    @SqlType(StandardTypes.VARCHAR)
    @TypeParameter("T")
    public static Slice typeof(@SqlType("VARCHAR") Slice functionCode, @SqlType("T") long value)
    {
        String result = "xxx";
        return Slices.wrappedBuffer(result.getBytes());
    }

    @SqlType(StandardTypes.VARCHAR)
    @TypeParameter("T")
    public static synchronized Slice typeof(@SqlType("VARCHAR") Slice functionCode, @SqlType("T") double value)
    {
        try {
            double[] input = { value };
            connection.assign("x", input);
            if (!functionLoaded) {
                connection.eval(functionCode.toStringUtf8());
                functionLoaded = true;
            }
            double[] result = connection.eval("fun(x)").asDoubles();
            return Slices.wrappedBuffer(Double.toString(result[0]).getBytes());
        }
        catch (REXPMismatchException | REngineException e) {
            throw Throwables.propagate(e);
        }
    }

    @SqlType(StandardTypes.VARCHAR)
    @TypeParameter("T")
    public static Slice typeof(@SqlType("VARCHAR") Slice functionCode, @SqlType("T") boolean value)
    {
        String result = "xxx";
        return Slices.wrappedBuffer(result.getBytes());
    }

    public static RConnection getConnectionIgnoreException()
    {
        try {
            return new RConnection();
        }
        catch (RserveException e) {
            throw Throwables.propagate(e);
        }
    }
}
