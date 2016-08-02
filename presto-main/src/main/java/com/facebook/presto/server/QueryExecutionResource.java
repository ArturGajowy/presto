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
package com.facebook.presto.server;

import com.google.common.io.Resources;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static com.google.common.io.Resources.getResource;

@Path("/")
public class QueryExecutionResource
{
    @GET
    @Path("/ui/plan")
    @Produces(MediaType.TEXT_HTML)
    public String getPlanUi()
            throws IOException
    {
        return Resources.toString(getResource(getClass(), "plan.html"), StandardCharsets.UTF_8);
    }
}
