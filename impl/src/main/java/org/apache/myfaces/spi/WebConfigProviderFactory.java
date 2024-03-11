/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.myfaces.spi;

import jakarta.faces.context.ExternalContext;

import org.apache.myfaces.spi.impl.DefaultWebConfigProviderFactory;
import org.apache.myfaces.spi.impl.SpiUtils;

/**
 * SPI to provide a WebConfigProviderFactory implementation and thus
 * a custom WebConfigProvider instance.
 *
 * @author Jakob Korherr
 * @author Leonardo Uribe
 * @since 2.0.3
 */
public abstract class WebConfigProviderFactory
{
    private static final String FACTORY_KEY = WebConfigProviderFactory.class.getName();

    public static WebConfigProviderFactory getWebConfigProviderFactory(ExternalContext ctx)
    {
        WebConfigProviderFactory instance = (WebConfigProviderFactory) ctx.getApplicationMap().get(FACTORY_KEY);

        if (instance != null)
        {
            // use cached instance
            return instance;
        }

        // create new instance from service entry
        instance = (WebConfigProviderFactory)
                SpiUtils.build(ctx, WebConfigProviderFactory.class,
                        DefaultWebConfigProviderFactory.class);

        if (instance != null)
        {
            // cache instance on ApplicationMap
            setWebConfigProviderFactory(ctx, instance);
        }

        return instance;
    }

    public static void setWebConfigProviderFactory(ExternalContext ctx, WebConfigProviderFactory instance)
    {
        ctx.getApplicationMap().put(FACTORY_KEY, instance);
    }

    public abstract WebConfigProvider getWebConfigProvider(ExternalContext externalContext);

}
