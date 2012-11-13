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
package org.apache.myfaces.renderkit;

import javax.faces.context.FacesContext;

/**
 *
 */
class SessionViewStorageFactory<T extends KeyFactory<K,V>, K, V >
{
    private KeyFactory<K, V> keyFactory;

    public SessionViewStorageFactory( KeyFactory<K, V> keyFactory )
    {
        this.keyFactory = keyFactory;
    }

    public KeyFactory<K, V> getKeyFactory()
    {
        return keyFactory;
    }

    public ServerSideStateCacheImpl.SerializedViewCollection createSerializedViewCollection(
            FacesContext context)
    {
        return new ServerSideStateCacheImpl.SerializedViewCollection();
    }

    public SerializedViewKey createSerializedViewKey(
            FacesContext context, String viewId, Object key)
    {
        return new SerializedViewKey(viewId, key);
    }

}
