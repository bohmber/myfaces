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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.faces.component.UIComponent;
import javax.faces.component.UniqueIdVendor;
import javax.faces.context.FacesContext;
import javax.faces.view.AttachedObjectHandler;
import javax.faces.view.EditableValueHolderAttachedObjectHandler;

import org.apache.myfaces.buildtools.maven2.plugin.builder.annotation.JSFWebConfigParam;
import org.apache.myfaces.shared.config.MyfacesConfig;
import org.apache.myfaces.shared.util.WebConfigParamUtils;
import org.apache.myfaces.view.facelets.ELExpressionCacheMode;
import org.apache.myfaces.view.facelets.FaceletCompositionContext;
import org.apache.myfaces.view.facelets.FaceletFactory;
import org.apache.myfaces.view.facelets.FaceletViewDeclarationLanguage;
import org.apache.myfaces.view.facelets.tag.jsf.ComponentSupport;

/**
 * @since 2.0.1
 * @author Leonardo Uribe (latest modification by $Author$)
 * @version $Revision$ $Date$
 */
public class FaceletCompositionContextImpl extends FaceletCompositionContext
{
    /**
     * Indicates if expressions generated by facelets should be cached or not.
     * Default is noCache. There there are four modes:
     * 
     * <ul>
     * <li>always: Only does not cache when expressions are inside user tags or the e
     * xpression contains a variable resolved using VariableMapper</li>
     * <li>allowCset: Like always, but does not allow cache when ui:param
     * was used on the current template context</li>
     * <li>strict: Like allowCset, but does not allow cache when c:set with
     * var and value properties only is used on the current page context</li>
     * <li>noCache: All expression are created each time the view is built</li>
     * </ul>
     * 
     */
    @JSFWebConfigParam(since="2.0.8", defaultValue="noCache", expectedValues="noCache, strict, allowCset, always",
                       group="EL", tags="performance")
    public static final String INIT_PARAM_CACHE_EL_EXPRESSIONS = "org.apache.myfaces.CACHE_EL_EXPRESSIONS";
    
    /**
     * Wrap exception caused by calls to EL expressions, so information like
     * the location, expression string and tag name can be retrieved by
     * the ExceptionHandler implementation and used to output meaningful information about itself.
     * 
     * <p>Note in some cases this will wrap the original javax.el.ELException,
     * so the information will not be on the stack trace unless ExceptionHandler
     * retrieve checking if the exception implements ContextAware interface and calling getWrapped() method.
     * </p>
     * 
     */
    @JSFWebConfigParam(since="2.0.9, 2.1.3" , defaultValue="true", expectedValues="true, false")
    public static final String INIT_PARAM_WRAP_TAG_EXCEPTIONS_AS_CONTEXT_AWARE
            = "org.apache.myfaces.WRAP_TAG_EXCEPTIONS_AS_CONTEXT_AWARE";
    
    private FacesContext _facesContext;
    
    private FaceletFactory _factory;

    private LinkedList<UIComponent> _compositeComponentStack;
    
    private LinkedList<UniqueIdVendor> _uniqueIdVendorStack;
    
    private LinkedList<String> _validationGroupsStack; 
    
    private LinkedList<String> _excludedValidatorIdsStack;
    
    private LinkedList<Map.Entry<String, EditableValueHolderAttachedObjectHandler>> _enclosingValidatorIdsStack;
    
    private Boolean _isRefreshingTransientBuild;
    
    private Boolean _isMarkInitialState;
    
    private Boolean _isBuildingViewMetadata;
    
    private Boolean _refreshTransientBuildOnPSS;
    
    private Boolean _refreshTransientBuildOnPSSPreserveState;
    
    private Boolean _usingPSSOnThisView;
    
    private ELExpressionCacheMode _elExpressionCacheMode;
    
    private Boolean _isWrapTagExceptionsAsContextAware;

    private List<Map<String, UIComponent>> _componentsMarkedForDeletion;
    
    private int _deletionLevel;
    
    private Map<UIComponent, List<AttachedObjectHandler>> _attachedObjectHandlers;
    
    private Map<UIComponent, Map<String, Object> > _methodExpressionsTargeted;
    
    private Map<UIComponent, Map<String, Boolean> > _compositeComponentAttributesMarked;

    private static final String VIEWROOT_FACELET_ID = "oam.VIEW_ROOT";
    
    private SectionUniqueIdCounter _sectionUniqueIdCounter;
    
    private SectionUniqueIdCounter _sectionUniqueComponentIdCounter;
    
    private List<String> _uniqueIdsList;
    private Iterator<String> _uniqueIdsIterator;
    private int _level;
    
    private int _isInMetadataSection;
    private SectionUniqueIdCounter _sectionUniqueMetadataIdCounter;
    private SectionUniqueIdCounter _sectionUniqueNormalIdCounter;
    private SectionUniqueIdCounter _sectionUniqueComponentMetadataIdCounter;
    private SectionUniqueIdCounter _sectionUniqueComponentNormalIdCounter;
    
    private StringBuilder _sharedStringBuilder;
    
    public FaceletCompositionContextImpl(FaceletFactory factory, FacesContext facesContext)
    {
        super();
        _factory = factory;
        _facesContext = facesContext;
        _componentsMarkedForDeletion = new ArrayList<Map<String,UIComponent>>();
        _deletionLevel = -1;
        _sectionUniqueIdCounter = new SectionUniqueIdCounter();
        //Cached at facelet view
        MyfacesConfig myfacesConfig = MyfacesConfig.getCurrentInstance(
                facesContext.getExternalContext());
        if (myfacesConfig.getComponentUniqueIdsCacheSize() > 0)
        {
            String[] componentIdsCache = (String [])facesContext.getExternalContext().
                    getApplicationMap().get(FaceletViewDeclarationLanguage.CACHED_COMPONENT_IDS);
            if (componentIdsCache != null)
            {
                _sectionUniqueComponentIdCounter = new SectionUniqueIdCounter("_", 
                        componentIdsCache);
            }
            else
            {
                _sectionUniqueComponentIdCounter = new SectionUniqueIdCounter("_");
            }
        }
        else
        {
            _sectionUniqueComponentIdCounter = new SectionUniqueIdCounter("_");
        }
        _sectionUniqueNormalIdCounter = _sectionUniqueIdCounter;
        _sectionUniqueComponentNormalIdCounter = _sectionUniqueComponentIdCounter;
        _uniqueIdsList = null;
        _uniqueIdsIterator = null;
        _level = 0;
        _isInMetadataSection = 0;
        _sharedStringBuilder = null;
    }
    
    @Override
    public void setUniqueIdsIterator(Iterator<String> uniqueIdsIterator)
    {
        _uniqueIdsList = null;
        _uniqueIdsIterator = uniqueIdsIterator;
    }
    
    @Override
    public void initUniqueIdRecording()
    {
        _uniqueIdsList = new LinkedList<String>();
        _uniqueIdsIterator = null;
    }
    
    @Override
    public void addUniqueId(String uniqueId)
    {
        if (_uniqueIdsList != null && _level == 0 && !(_isInMetadataSection > 0))
        {
            _uniqueIdsList.add(uniqueId);
        }
    }
    
    @Override
    public String getUniqueIdFromIterator()
    {
        if (_uniqueIdsIterator != null && _uniqueIdsIterator.hasNext() && 
                _level == 0 && !(_isInMetadataSection > 0))
        {
            return _uniqueIdsIterator.next();
        }
        return null;
    }
    
    @Override
    public List<String> getUniqueIdList()
    {
        return _uniqueIdsList;
    }

    public FaceletFactory getFaceletFactory()
    {
        return _factory;
    }
    
    @Override
    public void release(FacesContext facesContext)
    {
        super.release(facesContext);
        _factory = null;
        _facesContext = null;
        _compositeComponentStack = null;
        _enclosingValidatorIdsStack = null;
        _excludedValidatorIdsStack = null;
        _uniqueIdVendorStack = null;
        _validationGroupsStack = null;
        _componentsMarkedForDeletion = null;
        _sectionUniqueIdCounter = null;
        _sectionUniqueNormalIdCounter = null;
        _sectionUniqueMetadataIdCounter = null;
        _sectionUniqueComponentIdCounter = null;
        _sectionUniqueComponentNormalIdCounter = null;
        _sectionUniqueComponentMetadataIdCounter = null;
        _sharedStringBuilder = null;
    }
   
    @Override
    public UIComponent getCompositeComponentFromStack()
    {
        if (_compositeComponentStack != null && !_compositeComponentStack.isEmpty())
        {
            return _compositeComponentStack.peek();
        }
        return null;
    }

    @Override
    public void pushCompositeComponentToStack(UIComponent parent)
    {
        if (_compositeComponentStack == null)
        {
            _compositeComponentStack = new LinkedList<UIComponent>();
        }
        _compositeComponentStack.addFirst(parent);
    }

    @Override
    public void popCompositeComponentToStack()
    {
        if (_compositeComponentStack != null && !_compositeComponentStack.isEmpty())
        {
            _compositeComponentStack.removeFirst();
        }
    }

    @Override
    public UniqueIdVendor getUniqueIdVendorFromStack()
    {
        if (_uniqueIdVendorStack != null && !_uniqueIdVendorStack.isEmpty())
        {
            return _uniqueIdVendorStack.peek();
        }
        return null;
    }

    @Override
    public void popUniqueIdVendorToStack()
    {
        if (_uniqueIdVendorStack != null && !_uniqueIdVendorStack.isEmpty())
        {
            _uniqueIdVendorStack.removeFirst();
        }
    }

    @Override
    public void pushUniqueIdVendorToStack(UniqueIdVendor parent)
    {
        if (_uniqueIdVendorStack == null)
        {
            _uniqueIdVendorStack = new LinkedList<UniqueIdVendor>();
        }
        _uniqueIdVendorStack.addFirst(parent);
    }
    
    /**
     * Gets the top of the validationGroups stack.
     * @return
     * @since 2.0
     */
    @Override
    public String getFirstValidationGroupFromStack()
    {
        if (_validationGroupsStack != null && !_validationGroupsStack.isEmpty())
        {
            return _validationGroupsStack.getFirst(); // top-of-stack
        }
        return null;
    }
    
    /**
     * Removes top of stack.
     * @since 2.0
     */
    @Override
    public void popValidationGroupsToStack()
    {
        if (_validationGroupsStack != null && !_validationGroupsStack.isEmpty())
        {
            _validationGroupsStack.removeFirst();
        }
    }
    
    /**
     * Pushes validationGroups to the stack.
     * @param validationGroups
     * @since 2.0
     */
    @Override
    public void pushValidationGroupsToStack(String validationGroups)
    {
        if (_validationGroupsStack == null)
        {
            _validationGroupsStack = new LinkedList<String>();
        }

        _validationGroupsStack.addFirst(validationGroups);
    }
    
    /**
     * Gets all validationIds on the stack.
     * @return
     * @since 2.0
     */
    @Override
    public Iterator<String> getExcludedValidatorIds()
    {
        if (_excludedValidatorIdsStack != null && !_excludedValidatorIdsStack.isEmpty())
        {
            return _excludedValidatorIdsStack.iterator();
        }
        return null;
    }
    
    /**
     * Removes top of stack.
     * @since 2.0
     */
    @Override
    public void popExcludedValidatorIdToStack()
    {
        if (_excludedValidatorIdsStack != null && !_excludedValidatorIdsStack.isEmpty())
        {
            _excludedValidatorIdsStack.removeFirst();
        }
    }
    
    /**
     * Pushes validatorId to the stack of excluded validatorIds.
     * @param validatorId
     * @since 2.0
     */
    @Override
    public void pushExcludedValidatorIdToStack(String validatorId)
    {
        if (_excludedValidatorIdsStack == null)
        {
            _excludedValidatorIdsStack = new LinkedList<String>();
        }

        _excludedValidatorIdsStack.addFirst(validatorId);
    }
    
    /**
     * Gets all validationIds on the stack.
     * @return
     * @since 2.0
     */
    @Override
    public Iterator<String> getEnclosingValidatorIds()
    {
        if (_enclosingValidatorIdsStack != null && !_enclosingValidatorIdsStack.isEmpty())
        {
            return new KeyEntryIterator<String, EditableValueHolderAttachedObjectHandler>
                (_enclosingValidatorIdsStack.iterator()); 
        }
        return null;
    }
    
    /**
     * Removes top of stack.
     * @since 2.0
     */
    @Override
    public void popEnclosingValidatorIdToStack()
    {
        if (_enclosingValidatorIdsStack != null && !_enclosingValidatorIdsStack.isEmpty())
        {
            _enclosingValidatorIdsStack.removeFirst();
        }
    }
    
    /**
     * Pushes validatorId to the stack of all enclosing validatorIds.
     * @param validatorId
     * @since 2.0
     */
    @Override
    public void pushEnclosingValidatorIdToStack(String validatorId)
    {
        pushEnclosingValidatorIdToStack(validatorId, null);
    }
    
    @Override
    public void pushEnclosingValidatorIdToStack(String validatorId, 
            EditableValueHolderAttachedObjectHandler attachedObjectHandler)
    {
        if (_enclosingValidatorIdsStack == null)
        {
            _enclosingValidatorIdsStack = 
                new LinkedList<Map.Entry<String, EditableValueHolderAttachedObjectHandler>>();
        }

        _enclosingValidatorIdsStack.addFirst(
                new SimpleEntry<String, EditableValueHolderAttachedObjectHandler>
                    (validatorId, attachedObjectHandler));
    }

    public Iterator<Map.Entry<String, EditableValueHolderAttachedObjectHandler>> getEnclosingValidatorIdsAndHandlers()
    {
        if (_enclosingValidatorIdsStack != null && !_enclosingValidatorIdsStack.isEmpty())
        {
            return _enclosingValidatorIdsStack.iterator(); 
        }
        return null;
    }
    
    public boolean containsEnclosingValidatorId(String id)
    {
        if (_enclosingValidatorIdsStack != null && !_enclosingValidatorIdsStack.isEmpty())
        {
            for (Map.Entry<String, EditableValueHolderAttachedObjectHandler> entry : _enclosingValidatorIdsStack)
            {
                if (entry.getKey().equals(id))
                {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean isRefreshingTransientBuild()
    {
        if (_isRefreshingTransientBuild == null)
        {
            _isRefreshingTransientBuild = FaceletViewDeclarationLanguage.
                isRefreshingTransientBuild(_facesContext);
        }
        return _isRefreshingTransientBuild;
    }

    @Override
    public boolean isMarkInitialState()
    {
        if (_isMarkInitialState == null)
        {
            _isMarkInitialState = FaceletViewDeclarationLanguage.
                isMarkInitialState(_facesContext);
        }
        return _isMarkInitialState;
    }

    @Override
    public void setMarkInitialState(boolean value)
    {
        _isMarkInitialState = value;
    }

    @Override
    public boolean isRefreshTransientBuildOnPSS()
    {
        if (_refreshTransientBuildOnPSS == null)
        {
            _refreshTransientBuildOnPSS = FaceletViewDeclarationLanguage.
                isRefreshTransientBuildOnPSS(_facesContext);
        }
        return _refreshTransientBuildOnPSS;
    }
    
    public boolean isRefreshTransientBuildOnPSSPreserveState()
    {
        if (_refreshTransientBuildOnPSSPreserveState == null)
        {
            _refreshTransientBuildOnPSSPreserveState = MyfacesConfig.getCurrentInstance(
                    _facesContext.getExternalContext()).isRefreshTransientBuildOnPSSPreserveState();
        }
        return _refreshTransientBuildOnPSSPreserveState;
    }
    
    @Override
    public boolean isBuildingViewMetadata()
    {
        if (_isBuildingViewMetadata == null)
        {
            _isBuildingViewMetadata = FaceletViewDeclarationLanguage.
                    isBuildingViewMetadata(_facesContext);
        }
        return _isBuildingViewMetadata;
    }

    @Override
    public boolean isUsingPSSOnThisView()
    {
        if (_usingPSSOnThisView == null)
        {
            _usingPSSOnThisView = FaceletViewDeclarationLanguage.
                isUsingPSSOnThisView(_facesContext);
        }
        return _usingPSSOnThisView;
    }
    
    public boolean isMarkInitialStateAndIsRefreshTransientBuildOnPSS()
    {
        return isMarkInitialState() && isRefreshTransientBuildOnPSS();
    }

    @Override
    public ELExpressionCacheMode getELExpressionCacheMode()
    {
        if (_elExpressionCacheMode == null)
        {
            String value = WebConfigParamUtils.getStringInitParameter(
                    _facesContext.getExternalContext(),
                    INIT_PARAM_CACHE_EL_EXPRESSIONS, ELExpressionCacheMode.noCache.name());
            
            _elExpressionCacheMode = Enum.valueOf(ELExpressionCacheMode.class, value); 
        }
        return _elExpressionCacheMode;
    }

    @Override
    public boolean isWrapTagExceptionsAsContextAware()
    {
        if (_isWrapTagExceptionsAsContextAware == null)
        {
            _isWrapTagExceptionsAsContextAware
                    = WebConfigParamUtils.getBooleanInitParameter(_facesContext.getExternalContext(),
                    INIT_PARAM_WRAP_TAG_EXCEPTIONS_AS_CONTEXT_AWARE, true);
        }
        return _isWrapTagExceptionsAsContextAware;
    }

    @Override
    public void addAttachedObjectHandler(UIComponent compositeComponentParent, AttachedObjectHandler handler)
    {
        List<AttachedObjectHandler> list = null;
        if (_attachedObjectHandlers == null)
        {
            _attachedObjectHandlers = new HashMap<UIComponent, List<AttachedObjectHandler>>();
        }
        else
        {
            list = _attachedObjectHandlers.get(compositeComponentParent);
        }

        if (list == null)
        {
            list = new ArrayList<AttachedObjectHandler>();
            _attachedObjectHandlers.put(compositeComponentParent, list);
        }

        list.add(handler);
    }

    @Override
    public void removeAttachedObjectHandlers(UIComponent compositeComponentParent)
    {
        if (_attachedObjectHandlers == null)
        {
            return;
        }
        _attachedObjectHandlers.remove(compositeComponentParent);
    }

    @Override
    public List<AttachedObjectHandler> getAttachedObjectHandlers(UIComponent compositeComponentParent)
    {
        if (_attachedObjectHandlers == null)
        {
            return null;
        }
        return _attachedObjectHandlers.get(compositeComponentParent);
    }
    
    @Override
    public void addMethodExpressionTargeted(UIComponent targetedComponent, String attributeName, Object backingValue)
    {
        Map<String, Object> map = null;
        if (_methodExpressionsTargeted == null)
        {
            _methodExpressionsTargeted = new HashMap<UIComponent, Map<String, Object>>();
        }
        else
        {
            map = _methodExpressionsTargeted.get(targetedComponent);
        }

        if (map == null)
        {
            map = new HashMap<String, Object>(8);
            _methodExpressionsTargeted.put(targetedComponent, map);
        }

        map.put(attributeName, backingValue);
    }

    public boolean isMethodExpressionAttributeApplied(UIComponent compositeComponentParent, String attributeName)
    {
        if (_compositeComponentAttributesMarked == null)
        {
            return false;
        }
        Map<String, Boolean> map = _compositeComponentAttributesMarked.get(compositeComponentParent);
        if (map == null)
        {
            return false;
        }
        Boolean v = map.get(attributeName);
        return v == null ? false : v.booleanValue();
    }
    
    public void markMethodExpressionAttribute(UIComponent compositeComponentParent, String attributeName)
    {
        Map<String, Boolean> map = null;
        if (_compositeComponentAttributesMarked == null)
        {
            _compositeComponentAttributesMarked = new HashMap<UIComponent, Map<String, Boolean>>(); 
        }
        else
        {
            map = _compositeComponentAttributesMarked.get(compositeComponentParent);
        }
        
        if (map == null)
        {
            map = new HashMap<String, Boolean>(8);
            _compositeComponentAttributesMarked.put(compositeComponentParent, map);
        }
        map.put(attributeName, Boolean.TRUE);
        
    }
    
    public void clearMethodExpressionAttribute(UIComponent compositeComponentParent, String attributeName)
    {
        if (_compositeComponentAttributesMarked == null)
        {
            return;
        }
        Map<String, Boolean> map = _compositeComponentAttributesMarked.get(compositeComponentParent);
        if (map == null)
        {
            //No map, so just return
            return;
        }
        map.put(attributeName, Boolean.FALSE);
    }
    
    
    @Override
    public Object removeMethodExpressionTargeted(UIComponent targetedComponent, String attributeName)
    {
        if (_methodExpressionsTargeted == null)
        {
            return null;
        }
        Map<String, Object> map = _methodExpressionsTargeted.get(targetedComponent);
        if (map != null)
        {
            return map.remove(attributeName);
        }
        return null;
    }

    /**
     * Add a level of components marked for deletion.
     */
    private void increaseComponentLevelMarkedForDeletion()
    {
        _deletionLevel++;
        if (_componentsMarkedForDeletion.size() <= _deletionLevel)
        {
            _componentsMarkedForDeletion.add(new HashMap<String, UIComponent>());
            
        }
    }

    /**
     * Remove the last component level from the components marked to be deleted. The components are removed
     * from this list because they are deleted from the tree. This is done in ComponentSupport.finalizeForDeletion.
     *
     * @return the array of components that are removed.
     */
    private void decreaseComponentLevelMarkedForDeletion()
    {
        //The common case is this co
        if (!_componentsMarkedForDeletion.get(_deletionLevel).isEmpty())
        {
            _componentsMarkedForDeletion.get(_deletionLevel).clear();
        }
        _deletionLevel--;
    }

    /** Mark a component to be deleted from the tree. The component to be deleted is addded on the
     * current level. This is done from ComponentSupport.markForDeletion
     *
     * @param id
     * @param component the component marked for deletion.
     */
    private void markComponentForDeletion(String id , UIComponent component)
    {
        _componentsMarkedForDeletion.get(_deletionLevel).put(id, component);
    }

    /**
     * Remove a component from the last level of components marked to be deleted.
     *
     * @param id
     */
    private UIComponent removeComponentForDeletion(String id)
    {
        UIComponent removedComponent = _componentsMarkedForDeletion.get(_deletionLevel).remove(id); 
        if (removedComponent != null && _deletionLevel > 0)
        {
            _componentsMarkedForDeletion.get(_deletionLevel-1).remove(id);
        }
        return removedComponent;
    }
    
    public void markForDeletion(UIComponent component)
    {
        increaseComponentLevelMarkedForDeletion();
        
        String id = (String) component.getAttributes().get(ComponentSupport.MARK_CREATED);
        id = (id == null) ? VIEWROOT_FACELET_ID : id;
        markComponentForDeletion(id, component);
        
        
        if (component.getFacetCount() > 0)
        {
            for (UIComponent fc: component.getFacets().values())
            {
                id = (String) fc.getAttributes().get(ComponentSupport.MARK_CREATED);
                if (id != null)
                {
                    markComponentForDeletion(id, fc);
                }
                else if (Boolean.TRUE.equals(fc.getAttributes().get(ComponentSupport.FACET_CREATED_UIPANEL_MARKER)))
                {
                    //Mark its children, but do not mark itself.
                    int childCount = fc.getChildCount();
                    if (childCount > 0)
                    {
                        for (int i = 0; i < childCount; i++)
                        {
                            UIComponent child = fc.getChildren().get(i);
                            id = (String) child.getAttributes().get(ComponentSupport.MARK_CREATED);
                            if (id != null)
                            {
                                markComponentForDeletion(id, child);
                            }
                        }
                    }
                }
            }
        }
                
        int childCount = component.getChildCount();
        if (childCount > 0)
        {
            for (int i = 0; i < childCount; i++)
            {
                UIComponent child = component.getChildren().get(i);
                id = (String) child.getAttributes().get(ComponentSupport.MARK_CREATED);
                if (id != null)
                {
                    markComponentForDeletion(id, child);
                }
            }
        }
    }
    
    public void finalizeForDeletion(UIComponent component)
    {
        String id = (String) component.getAttributes().get(ComponentSupport.MARK_CREATED);
        id = (id == null) ? VIEWROOT_FACELET_ID : id;
        // remove any existing marks of deletion
        removeComponentForDeletion(id);
        
        // finally remove any children marked as deleted
        int childCount = component.getChildCount();
        if (childCount > 0)
        {
            for (int i = 0; i < childCount; i ++)
            {
                UIComponent child = component.getChildren().get(i);
                id = (String) child.getAttributes().get(ComponentSupport.MARK_CREATED); 
                if (id != null && removeComponentForDeletion(id) != null)
                {
                    component.getChildren().remove(i);
                    i--;
                    childCount--;
                }
            }
        }

        // remove any facets marked as deleted
        
        if (component.getFacetCount() > 0)
        {
            Map<String, UIComponent> facets = component.getFacets();
            for (Iterator<UIComponent> itr = facets.values().iterator(); itr.hasNext();)
            {
                UIComponent fc = itr.next();
                id = (String) fc.getAttributes().get(ComponentSupport.MARK_CREATED);
                if (id != null && removeComponentForDeletion(id) != null)
                {
                    itr.remove();
                }
                else if (id == null
                         && Boolean.TRUE.equals(fc.getAttributes().get(ComponentSupport.FACET_CREATED_UIPANEL_MARKER)))
                {
                    if (fc.getChildCount() > 0)
                    {
                        for (int i = 0, size = fc.getChildCount(); i < size; i++)
                        {
                            UIComponent child = fc.getChildren().get(i);
                            id = (String) child.getAttributes().get(ComponentSupport.MARK_CREATED);
                            if (id != null && removeComponentForDeletion(id) != null)
                            {
                                fc.getChildren().remove(i);
                                i--;
                                size--;
                            }
                        }
                    }
                    if (fc.getChildCount() == 0)
                    {
                        itr.remove();
                    }
                }
            }
        }
        
        decreaseComponentLevelMarkedForDeletion();
    }
    
    public String startComponentUniqueIdSection()
    {
        _level++;
        _sectionUniqueComponentIdCounter.startUniqueIdSection();
        return _sectionUniqueIdCounter.startUniqueIdSection();
    }

    @Override
    public void incrementUniqueId()
    {
        _sectionUniqueIdCounter.incrementUniqueId();
    }
    
    public String generateUniqueId()
    {
        return _sectionUniqueIdCounter.generateUniqueId();
    }
    
    public void generateUniqueId(StringBuilder builderToAdd)
    {
        _sectionUniqueIdCounter.generateUniqueId(builderToAdd);
    }

    public String generateUniqueComponentId()
    {
        return _sectionUniqueComponentIdCounter.generateUniqueId();
    }
    
    public void endComponentUniqueIdSection()
    {
        _level--;
        _sectionUniqueIdCounter.endUniqueIdSection();
        _sectionUniqueComponentIdCounter.endUniqueIdSection();
    }
    
    @Override
    public void startMetadataSection()
    {
        if (_isInMetadataSection == 0)
        {
            if (_sectionUniqueMetadataIdCounter == null)
            {
                _sectionUniqueMetadataIdCounter = new SectionUniqueIdCounter("__md_");
            }
            if (_sectionUniqueComponentMetadataIdCounter == null)
            {
                _sectionUniqueComponentMetadataIdCounter = new SectionUniqueIdCounter("__md_");
            }
            //Replace the counter with metadata counter
            _sectionUniqueIdCounter = _sectionUniqueMetadataIdCounter;
            _sectionUniqueComponentIdCounter = _sectionUniqueComponentMetadataIdCounter;
        }
        _isInMetadataSection++;
    }
    
    @Override
    public void endMetadataSection()
    {
        _isInMetadataSection--;
        if (_isInMetadataSection == 0)
        {
            //Use normal id counter again
            _sectionUniqueIdCounter = _sectionUniqueNormalIdCounter;
            _sectionUniqueComponentIdCounter = _sectionUniqueComponentNormalIdCounter;
        }
    }
    
    @Override
    public boolean isInMetadataSection()
    {
       return _isInMetadataSection > 0;
    }
    
    @Override
    public boolean isRefreshingSection()
    {
       return isRefreshingTransientBuild() ||  (!isBuildingViewMetadata() && isInMetadataSection());
    }
    
    @Override
    public StringBuilder getSharedStringBuilder()
    {
        if (_sharedStringBuilder == null)
        {
            _sharedStringBuilder = new StringBuilder();
        }
        else
        {
            _sharedStringBuilder.setLength(0);
        }
        return _sharedStringBuilder;
    }
    
    private static class KeyEntryIterator<K, V> implements Iterator<K>
    {
        private Iterator<Map.Entry<K, V>> _delegateIterator;
        
        public KeyEntryIterator(Iterator<Map.Entry<K, V>> delegate)
        {
            _delegateIterator = delegate;
        }
        
        public boolean hasNext()
        {
            if (_delegateIterator != null)
            {
                return _delegateIterator.hasNext();
            }
            return false;
        }

        public K next()
        {
            if (_delegateIterator != null)
            {
                return _delegateIterator.next().getKey();
            }
            return null;
        }

        public void remove()
        {
            if (_delegateIterator != null)
            {
                _delegateIterator.remove();
            }
        }
        
    }
    
    private static class SimpleEntry<K, V> implements Map.Entry<K, V>
    {
        private final K _key;
        private final V _value;

        public SimpleEntry(K key, V value)
        {
            _key = key;
            _value = value;
        }
        
        public K getKey()
        {
            return _key;
        }

        public V getValue()
        {
            return _value;
        }

        @Override
        public int hashCode()
        {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((_key == null) ? 0 : _key.hashCode());
            result = prime * result + ((_value == null) ? 0 : _value.hashCode());
            return result;
        }

        @SuppressWarnings("unchecked")
        @Override
        public boolean equals(Object obj)
        {
            if (this == obj)
            {
                return true;
            }
            if (obj == null)
            {
                return false;
            }
            if (getClass() != obj.getClass())
            {
                return false;
            }
            SimpleEntry other = (SimpleEntry) obj;
            if (_key == null)
            {
                if (other._key != null)
                {
                    return false;
                }
            }
            else if (!_key.equals(other._key))
            {
                return false;
            }
            
            if (_value == null)
            {
                if (other._value != null)
                {
                    return false;
                }
            }
            else if (!_value.equals(other._value))
            {
                return false;
            }
            return true;
        }

        public V setValue(V value)
        {
            throw new UnsupportedOperationException();
        }
    }
}