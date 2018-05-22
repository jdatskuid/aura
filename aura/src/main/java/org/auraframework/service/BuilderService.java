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
package org.auraframework.service;

import org.auraframework.Aura;
import org.auraframework.builder.ApplicationDefBuilder;
import org.auraframework.builder.CacheBuilder;
import org.auraframework.builder.ComponentDefBuilder;
import org.auraframework.builder.ComponentDefRefBuilder;
import org.auraframework.builder.FlavoredStyleDefBuilder;
import org.auraframework.builder.ModuleDefRefBuilder;
import org.auraframework.builder.StyleDefBuilder;
import org.auraframework.builder.TokensDefBuilder;
import org.auraframework.def.ApplicationDef;
import org.auraframework.def.ComponentDef;
import org.auraframework.def.ComponentDefRef;
import org.auraframework.def.Definition;
import org.auraframework.def.FlavoredStyleDef;
import org.auraframework.def.StyleDef;
import org.auraframework.def.TokensDef;
import org.auraframework.throwable.quickfix.QuickFixException;

/**
 * <p>
 * Service for constructing your own {@link Definition}.
 * </p>
 * <p>
 * Instances of all AuraServices should be retrieved from {@link Aura}
 * </p>
 */
public interface BuilderService extends AuraService {

    /**
     * Retrieves a Builder suitable for defining an {@link ApplicationDef}.
     */
    ApplicationDefBuilder getApplicationDefBuilder();

    /**
     * Retrieves a Builder for defining a {@link ComponentDef}.
     */
    ComponentDefBuilder getComponentDefBuilder();

    /**
     * Retrieves a Builder for defining a {@link StyleDef}.
     */
    StyleDefBuilder getStyleDefBuilder();

    /**
     * Retrieves a Builder for defining a {@link FlavoredStyleDef}.
     */
    FlavoredStyleDefBuilder getFlavoredStyleDefBuilder();

    /**
     * Retrieves a Builder suitable for defining a {@link ComponentDefRef}.
     */
    ComponentDefRefBuilder getComponentDefRefBuilder();

    /**
     * Retrieves a Builder for {@link org.auraframework.def.module.ModuleDefRef}
     */
    ModuleDefRefBuilder getModuleDefRefBuilder();

    /**
     * Retrieves a Builder suitable for defining a {@link TokensDef}.
     */
    TokensDefBuilder getTokensDefBuilder();

    /**
     * Retrieves a Builder suitable for defining a {@link org.auraframework.cache.Cache}
     *
     * @param <K>
     *
     * @throws QuickFixException
     */
    <K, V> CacheBuilder<K, V> getCacheBuilder() throws QuickFixException;
}
