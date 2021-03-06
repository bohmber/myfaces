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
package org.apache.myfaces.view.facelets.impl;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.faces.view.facelets.FaceletContext;
import jakarta.faces.view.facelets.FaceletException;

import org.apache.myfaces.resource.ResourceLoaderUtils;
import org.apache.myfaces.util.lang.Assert;
import org.apache.myfaces.view.facelets.AbstractFaceletCache;
import org.apache.myfaces.view.facelets.AbstractFaceletContext;

/**
 * Extended MyFaces specific FaceletCache implementation that recompile
 * facelet instance when a template context param is found.
 * 
 * @author Leonardo Uribe
 * @since 2.1.0
 *
 */
class CacheELFaceletCacheImpl extends AbstractFaceletCache<DefaultFacelet>
{

    private static final long INFINITE_DELAY = -1;
    private static final long NO_CACHE_DELAY = 0;
    
    /**
     * FaceletNode is necessary only here, because view metadata and 
     * composite component metadata are special and does not allow use nested
     * template tags. View metadata facelet trims everything outside f:metadata
     * and composite component metadata only takes into account composite:xxx tags,
     * ignoring ui:xxx tags.
     */
    private Map<String, FaceletNode> _facelets;
    
    private Map<String, DefaultFacelet> _viewMetadataFacelets;
    
    private Map<String, DefaultFacelet> _compositeComponentMetadataFacelets;
    
    private long _refreshPeriod;
    
    CacheELFaceletCacheImpl(long refreshPeriod)
    {
        _refreshPeriod = refreshPeriod < 0 ? INFINITE_DELAY : refreshPeriod * 1000;

        _facelets = new ConcurrentHashMap<>();
        _viewMetadataFacelets = new ConcurrentHashMap<>();
        _compositeComponentMetadataFacelets = new ConcurrentHashMap<>();
    }

    @Override
    public DefaultFacelet getFacelet(URL url) throws IOException
    {
        Assert.notNull(url, "url");
        
        String key = url.toString();
        
        FaceletNode node = _facelets.get(key);
        DefaultFacelet f = node != null ? node.getFacelet() : null;
        
        if (f == null || this.needsToBeRefreshed(f))
        {
            Set<String> paramsSet = null;
            if (node != null)
            {
                paramsSet = node.getParams();
            }
            f = getMemberFactory().newInstance(url);
            if (_refreshPeriod != NO_CACHE_DELAY)
            {
                _facelets.put(key, (paramsSet != null && !paramsSet.isEmpty()) ? 
                        new FaceletNode(f, paramsSet) : new FaceletNode(f) );
            }
        }
        
        return f;
    }

    @Override
    public DefaultFacelet getFacelet(FaceletContext ctx, URL url) throws IOException
    {
        String key = url.toString();
        
        //1. Check that the current parameters on the template are known
        //   for the template.
        //2. If all current parameters are known return the template
        //2. If some current parameter is not known, add the param(s) to the
        //   template, register the known params in the template context and
        //   recompile the facelet, to clean up al EL expressions at once.

        FaceletNode node = _facelets.get(key);
        DefaultFacelet f = (node != null) ? node.getFacelet() : null;
        
        Set<String> paramsSet = Collections.emptySet();
        paramsSet = (node != null) ? node.getParams() : paramsSet;

        AbstractFaceletContext actx = (AbstractFaceletContext) ctx;
        Set<String> knownParameters = actx.getTemplateContext().isKnownParametersEmpty() ?
            (Set) Collections.emptySet() : actx.getTemplateContext().getKnownParameters();
        
        boolean create = false;
        for (String paramKey : knownParameters)
        {
            if (!paramsSet.contains(paramKey))
            {
                create = true;
                break;
            }
        }
        
        if (f == null || this.needsToBeRefreshed(f) || create)
        {
            f = getMemberFactory().newInstance(url);
            if (_refreshPeriod != NO_CACHE_DELAY)
            {
                if (!paramsSet.isEmpty()|| !knownParameters.isEmpty() )
                {
                    paramsSet = new HashSet(paramsSet);
                    paramsSet.addAll(knownParameters);
                    _facelets.put(key, new FaceletNode(f, paramsSet));
                }
                else
                {
                    _facelets.put(key, new FaceletNode(f));
                }
            }
        }

        if (!paramsSet.isEmpty())
        {
            for (String param : paramsSet)
            {
                if (!actx.getTemplateContext().containsKnownParameter(param))
                {
                    actx.getTemplateContext().addKnownParameters(param);
                }
            }
        }
        
        return f;
    }
    
    @Override
    public boolean isFaceletCached(URL url)
    {
        return _facelets.containsKey(url.toString());
    }

    @Override
    public DefaultFacelet getViewMetadataFacelet(URL url) throws IOException
    {
        Assert.notNull(url, "url");
        
        String key = url.toString();
        
        DefaultFacelet f = _viewMetadataFacelets.get(key);
        
        if (f == null || this.needsToBeRefreshed(f))
        {
            f = getMetadataMemberFactory().newInstance(url);
            if (_refreshPeriod != NO_CACHE_DELAY)
            {
                _viewMetadataFacelets.put(key, f);
            }
        }
        
        return f;
    }

    @Override
    public boolean isViewMetadataFaceletCached(URL url)
    {
        return _viewMetadataFacelets.containsKey(url.toString());
    }

    /**
     * Template method for determining if the Facelet needs to be refreshed.
     * 
     * @param facelet
     *            Facelet that could have expired
     * @return true if it needs to be refreshed
     */
    protected boolean needsToBeRefreshed(DefaultFacelet facelet)
    {
        // if set to 0, constantly reload-- nocache
        if (_refreshPeriod == NO_CACHE_DELAY)
        {
            return true;
        }

        // if set to -1, never reload
        if (_refreshPeriod == INFINITE_DELAY)
        {
            return false;
        }

        long target = facelet.getCreateTime() + _refreshPeriod;
        if (System.currentTimeMillis() > target)
        {
            // Should check for file modification
            try
            {
                long lastModified = ResourceLoaderUtils.getResourceLastModified(facelet.getSource());

                return lastModified == 0 || lastModified > target;
            }
            catch (IOException e)
            {
                throw new FaceletException("Error Checking Last Modified for " + facelet.getAlias(), e);
            }
        }

        return false;
    }

    @Override
    public DefaultFacelet getCompositeComponentMetadataFacelet(URL url) throws IOException
    {
        Assert.notNull(url, "url");

        String key = url.toString();

        DefaultFacelet f = _compositeComponentMetadataFacelets.get(key);

        if (f == null || this.needsToBeRefreshed(f))
        {
            f = getCompositeComponentMetadataMemberFactory().newInstance(url);
            if (_refreshPeriod != NO_CACHE_DELAY)
            {
                _compositeComponentMetadataFacelets.put(key, f);
            }
        }
        return f;
    }

    @Override
    public boolean isCompositeComponentMetadataFaceletCached(URL url)
    {
        return _compositeComponentMetadataFacelets.containsKey(url.toString());
    }
    
    private static class FaceletNode
    {
        private DefaultFacelet facelet;
        private Set<String> params;

        public FaceletNode(DefaultFacelet facelet)
        {
            this.facelet = facelet;
            this.params = Collections.emptySet();
        }
        
        public FaceletNode(DefaultFacelet facelet, Set<String> params)
        {
            this.facelet = facelet;
            this.params = params;
        }

        public DefaultFacelet getFacelet()
        {
            return facelet;
        }

        public void setFacelet(DefaultFacelet facelet)
        {
            this.facelet = facelet;
        }

        public Set<String> getParams()
        {
            return params;
        }

        public void setParams(Set<String> params)
        {
            this.params = params;
        }
    }
}
