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

import com.google.inject.Binder;
import com.google.inject.Module;
import io.airlift.http.server.HttpServerBinder;

import static io.airlift.http.server.HttpServerBinder.httpServerBinder;

/**
 * See http://www.webjars.org/
 */
public class WebjarsResourceModule implements Module
{
    @Override
    public void configure(Binder binder)
    {
        HttpServerBinder httpServerBinder = httpServerBinder(binder);
        httpServerBinder.bindResource("webjars_overrides", "webapp/webjars_overrides");
        httpServerBinder.bindResource("webjars", "META-INF/resources/webjars");
    }
}
