/*
 * Copyright (C) 2013 salesforce.com, inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.auraframework.impl.java.controller;

import org.auraframework.def.ActionDef;
import org.auraframework.def.DefDescriptor;
import org.auraframework.def.TypeDef;
import org.auraframework.def.ValueDef;
import org.auraframework.impl.system.DefinitionImpl;
import org.auraframework.impl.util.AuraUtil;
import org.auraframework.throwable.quickfix.InvalidDefinitionException;
import org.auraframework.throwable.quickfix.QuickFixException;
import org.auraframework.util.json.Json;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;

/**
 * action in java, aka static method.
 */
public class JavaActionDef extends DefinitionImpl<ActionDef> implements ActionDef {
    private static final long serialVersionUID = -9179014361802437154L;
    private final DefDescriptor<TypeDef> returnTypeDescriptor;
    private final List<ValueDef> params;
    private final List<String> loggableParams;
    private final Class<?>[] javaParams;
    private final Method method;
    private final boolean background;
    private final boolean caboose;
    private String actionGroup;
    private final boolean publicCachingEnabled;
    private final int publicCachingExpiration;

    protected JavaActionDef(Builder builder) {
        super(builder);
        this.returnTypeDescriptor = builder.returnTypeDescriptor;
        this.params = AuraUtil.immutableList(builder.params);
        this.loggableParams = builder.loggableParams;
        this.javaParams = builder.javaParams;
        this.method = builder.method;
        this.background = builder.background;
        this.caboose = builder.caboose;
        this.actionGroup = builder.actionGroup;
        this.publicCachingEnabled = builder.publicCachingEnabled;
        this.publicCachingExpiration = builder.publicCachingExpiration;
    }

    @Override
    public ActionType getActionType() {
        return ActionType.SERVER;
    }

    @Override
    public List<ValueDef> getParameters() {
        return params;
    }

    @Override
    public List<String> getLoggableParams() {
        return loggableParams;
    }

    Class<?>[] getJavaParams() {
        return this.javaParams;
    }

    /**
     * Gets the method for this instance.
     * 
     * @return The method.
     */
    public Method getMethod() {
        return this.method;
    }

    @Override
    public DefDescriptor<TypeDef> getReturnType() {
        return returnTypeDescriptor;
    }
    
    public boolean isBackground() {
        return background;
    }

    public boolean isCaboose() {
        return caboose;
    }
    
    public String getActionGroup() {
        return this.actionGroup;
    }

    public boolean isPublicCachingEnabled() {
        return publicCachingEnabled;
    }

    public int getPublicCachingExpiration() {
        return publicCachingExpiration;
    }

    @Override
    public void serialize(Json json) throws IOException {
        json.writeMapBegin();
        json.writeMapEntry(Json.ApplicationKey.NAME, getName());
        json.writeMapEntry(Json.ApplicationKey.DESCRIPTOR, getDescriptor());
        json.writeMapEntry(Json.ApplicationKey.ACTIONTYPE, getActionType());
        json.writeMapEntry(Json.ApplicationKey.RETURNTYPE, getReturnType());
        json.writeMapEntry(Json.ApplicationKey.BACKGROUND, isBackground());
        json.writeMapEntry(Json.ApplicationKey.CABOOSE, isCaboose());
        String ag = getActionGroup();
        if (ag != null) {
            json.writeMapEntry(Json.ApplicationKey.ACTIONGROUP, ag);
        }
        json.writeMapEntry(Json.ApplicationKey.PARAMS, params);
        if (isPublicCachingEnabled()) {
            json.writeMapEntry(Json.ApplicationKey.PUBLICCACHINGENABLED, true);
            json.writeMapEntry(Json.ApplicationKey.PUBLICCACHINGEXPIRATION, getPublicCachingExpiration());
        }
        json.writeMapEnd();
    }
    
    @Override
    public void validateDefinition() throws QuickFixException {
    	super.validateDefinition();
    	
        if (this.isPublicCachingEnabled() && this.getPublicCachingExpiration() <= 0) {
            throw new InvalidDefinitionException("When public caching is enabled, public caching expiration time must be greater than 0.", location);
    	}
    }

    public static class Builder extends DefinitionImpl.BuilderImpl<ActionDef> {

        public Builder() {
            super(ActionDef.class);
        }

        private DefDescriptor<TypeDef> returnTypeDescriptor;
        private List<ValueDef> params;
        private List<String> loggableParams;
        private Class<?>[] javaParams;
        private Method method;
        private boolean background = false;
        private boolean caboose = false;
        private String actionGroup;
        private boolean publicCachingEnabled = false;
        private int publicCachingExpiration = -1;

        @Override
        public JavaActionDef build() {
            return new JavaActionDef(this);
        }

        /**
         * Sets the returnTypeDescriptor for this instance.
         * 
         * @param returnTypeDescriptor The returnTypeDescriptor.
         */
        public void setReturnTypeDescriptor(DefDescriptor<TypeDef> returnTypeDescriptor) {
            this.returnTypeDescriptor = returnTypeDescriptor;
        }

        /**
         * Sets the params for this instance.
         * 
         * @param params The params.
         */
        public void setParams(List<ValueDef> params) {
            this.params = params;
        }

        /**
         * Sets the loggable param names for this instance.
         *
         * @param loggableParams The loggableParams.
         */
        public void setLoggableParams(List<String> loggableParams) {
            this.loggableParams = loggableParams;
        }

        /**
         * Sets the javaParams for this instance.
         * 
         * @param javaParams The javaParams.
         */
        public void setJavaParams(Class<?>[] javaParams) {
            this.javaParams = javaParams;
        }

        public void setMethod(Method method) {
            this.method = method;
        }

        public void setBackground(boolean background) {
            this.background = background;
        }

        public void setCaboose(boolean caboose) {
            this.caboose = caboose;
        }
        
        public void setActionGroup(String actionGroup) {
            this.actionGroup = actionGroup;
        }

        public void setPublicCachingEnabled(boolean publicCachingEnabled) {
            this.publicCachingEnabled = publicCachingEnabled;
        }

        public void setPublicCachingExpiration(int publicCachingExpiration) {
            this.publicCachingExpiration = publicCachingExpiration;
        }
    }
}
