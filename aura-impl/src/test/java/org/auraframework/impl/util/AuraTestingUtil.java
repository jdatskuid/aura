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
package org.auraframework.impl.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nullable;

import org.auraframework.adapter.ConfigAdapter;
import org.auraframework.def.BaseComponentDef;
import org.auraframework.def.DefDescriptor;
import org.auraframework.def.DefDescriptor.DefType;
import org.auraframework.def.Definition;
import org.auraframework.impl.source.BundleSourceImpl;
import org.auraframework.impl.source.StringSource;
import org.auraframework.impl.source.StringSourceLoaderImpl.DescriptorInfo;
import org.auraframework.impl.system.DefDescriptorImpl;
import org.auraframework.service.ContextService;
import org.auraframework.service.DefinitionService;
import org.auraframework.system.AuraContext;
import org.auraframework.system.AuraContext.Authentication;
import org.auraframework.system.AuraContext.Format;
import org.auraframework.system.AuraContext.Mode;
import org.auraframework.system.BundleSource;
import org.auraframework.system.DefRegistry;
import org.auraframework.system.Parser;
import org.auraframework.system.Source;
import org.auraframework.system.SourceListener;
import org.auraframework.system.TextSource;
import org.auraframework.test.source.StringSourceLoader;
import org.auraframework.test.source.StringSourceLoader.NamespaceAccess;
import org.auraframework.throwable.AuraRuntimeException;
import org.auraframework.throwable.quickfix.QuickFixException;
import org.auraframework.util.AuraTextUtil;
import org.auraframework.util.FileMonitor;
import org.auraframework.util.json.JsonEncoder;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class AuraTestingUtil {
    public static final long CACHE_CLEARING_TIMEOUT_SECS = 60;
    private static AtomicLong nonce = new AtomicLong(System.currentTimeMillis());

    private Set<DefDescriptor<?>> cleanUpDds;
    private FileMonitor fileMonitor;
    private StringSourceLoader stringSourceLoader;
    private DefinitionService definitionService;
    private ConfigAdapter configAdapter;
    private ContextService contextService;

    private final SecureRandom random = new SecureRandom();
    private final String internalNamespace;
    private final String privilegedNamespace;
    private final String customNamespace;

    public AuraTestingUtil(FileMonitor fileMonitor, StringSourceLoader stringSourceLoader,
                           DefinitionService definitionService, ConfigAdapter configAdapter,
                           ContextService contextService) {
        this.fileMonitor = fileMonitor;
        this.stringSourceLoader = stringSourceLoader;
        this.definitionService = definitionService;
        this.configAdapter = configAdapter;
        this.contextService = contextService;
        //
        // Setup namespaces
        //
        internalNamespace = "ns_internal_"+Math.abs(random.nextLong());
        configAdapter.addInternalNamespace(internalNamespace);
        privilegedNamespace = "ns_privileged_"+Math.abs(random.nextLong());
        configAdapter.addPrivilegedNamespace(privilegedNamespace);
        customNamespace = "ns_custom_"+Math.abs(random.nextLong());
    }

    public AuraTestingUtil() {
        internalNamespace = null;
        privilegedNamespace = null;
        customNamespace = null;
    }

    public void tearDown() {
        if (cleanUpDds != null) {
            for (DefDescriptor<?> dd : cleanUpDds) {
                stringSourceLoader.removeSource(dd);
            }
            cleanUpDds.clear();
        }
    }

    /**
     * Get a unique value for use in tests
     */
    public String getNonce() {
        return Long.toString(nonce.incrementAndGet());
    }

    /**
     * @return the internalNamespace
     */
    public String getInternalNamespace() {
        return internalNamespace;
    }

    /**
     * @return the privilegedNamespace
     */
    public String getPrivilegedNamespace() {
        return privilegedNamespace;
    }

    /**
     * @return the customNamespace
     */
    public String getCustomNamespace() {
        return customNamespace;
    }

    /**
     * Get a unique value and append it to a provided string
     */
    public String getNonce(String prefix) {
        return (prefix == null ? "" : prefix) + getNonce();
    }

    /**
     * Retrieves the source of a component resource. Note: Works only for markup://string:XXXXX components and not for
     * any other namespace. By default, test util is aware of StringSourceLoader only.
     *
     * @param descriptor Descriptor of the resource you want to see the source of
     * @return
     */
    public <T extends Definition> Source<T> getSource(DefDescriptor<T> descriptor) {
        // Look up in the registry if a context is available. Otherwise, we're
        // probably running a context-less unit test
        // and better be using StringSourceLoader
        AuraContext context = contextService.getCurrentContext();
        if (context != null) {
            Source<T> res = definitionService.getSource(descriptor);
            if (res != null) {
                return res;
            }
        }
        Source<T> source = stringSourceLoader.getSource(descriptor);
        if (source instanceof BundleSource) {
            @SuppressWarnings("unchecked")
            Source<T> inBundle = (Source<T>)((BundleSource<T>)source).getBundledParts().get(descriptor);
            return inBundle;
        }
        return source;
    }

    /**
     * update source for a resource
     * 
     * @param desc definition descriptor of the resource
     * @param content new content for the descriptor
     */
    public void updateSource(DefDescriptor<?> desc, String content) {
        Source<?> src = getSource(desc);
        final String expectedName = desc.getQualifiedName();

        if (src == null) {
            throw new RuntimeException("unable to find "+desc);
        }
        final Semaphore updated = new Semaphore(0);
        SourceListener changeListener = new SourceListener() {
            @Override
            public void onSourceChanged(SourceMonitorEvent event, String filePath) {
                if (expectedName.equals(filePath)) {
                    updated.release();
                }
            }
        };
        fileMonitor.subscribeToChangeNotification(changeListener);
        try {
            if (StringSource.class.isAssignableFrom(src.getClass())) {
                stringSourceLoader.putSource(src.getDescriptor(), content, true);
            } else {
                throw new RuntimeException("We can only update StringSource, but we got " + src.getClass());
            }
            updated.acquire();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while waiting for updated source event", e);
        } finally {
            fileMonitor.unsubscribeToChangeNotification(changeListener);
        }
    }

    /**
     * Generate a {@link DefDescriptor} with a unique name. If namePrefix does not contain a namespace, the descriptor
     * will be created in the 'string' namespace. If namePrefix does not contain the name portion (i.e. it is null,
     * empty, or just a namespace with the trailing delimiter), 'thing' will be used as the base name.
     *
     * @param namePrefix if non-null, then generate some name with the given prefix for the descriptor.
     * @param defClass the interface of the type definition
     * @param bundle the bundle for this descriptor
     * @return a {@link DefDescriptor} with name that is guaranteed to be unique in the string: namespace.
     */
    public final <D extends Definition, B extends Definition> DefDescriptor<D> createStringSourceDescriptor(
            @Nullable String namePrefix, Class<D> defClass, DefDescriptor<B> bundle) {
        return stringSourceLoader.createStringSourceDescriptor(namePrefix, defClass, bundle);
    }

    /**
     * Get a descriptor for a bundle part.
     *   
     * @param defClass the interface of the type definition
     * @param bundle the bundle for this descriptor
     * @return a {@link DefDescriptor} with name that is guaranteed to be unique in the string: namespace.
     */
    public <D extends Definition, B extends Definition> DefDescriptor<D> getBundlePartDescriptor(Class<D> defClass,
            DefDescriptor<B> bundle) {
        DescriptorInfo descriptorInfo = DescriptorInfo.get(defClass);
        return descriptorInfo.getDescriptor(definitionService, bundle);
    }
    
    /**
     * Convenience method to create a description and load a source in one shot.
     *
     * @param defClass interface of the definition represented by this source
     * @param contents source contents
     * @return the {@link DefDescriptor} for the created definition
     */
    public <T extends Definition> DefDescriptor<T> addSourceAutoCleanup(Class<T> defClass, String contents) {
        return addSourceAutoCleanup(defClass, contents, null, NamespaceAccess.INTERNAL);
    }

    /**
     * Convenience method to create a description and load a source in one shot.
     *
     * @param defClass interface of the definition represented by this source
     * @param contents source contents
     * @param namePrefix package name prefix
     * @return the {@link DefDescriptor} for the created definition
     */
    public <T extends Definition> DefDescriptor<T> addSourceAutoCleanup(Class<T> defClass, String contents,
            String namePrefix) {
        return addSourceAutoCleanup(defClass, contents, namePrefix, NamespaceAccess.INTERNAL);
    }

    /**
     * Convenience method to create a description and load a source in one shot.
     *
     * @param defClass interface of the definition represented by this source
     * @param contents source contents
     * @param namePrefix package name prefix
     * @param access the namespace access type.
     * @return the {@link DefDescriptor} for the created definition
     */
    public <T extends Definition> DefDescriptor<T> addSourceAutoCleanup(Class<T> defClass, String contents,
            String namePrefix, NamespaceAccess access) {
        DefDescriptor<T> descriptor = stringSourceLoader.addSource(defClass, contents, namePrefix,
                access).getDescriptor();
        markForCleanup(descriptor);
        return descriptor;
    }

    /**
     * Convenience method to create a description and load a source in one shot.
     *
     * @param descriptor descriptor for the source to be created
     * @param contents source contents
     * @return the {@link DefDescriptor} for the created definition
     */
    public <T extends Definition> DefDescriptor<T> addSourceAutoCleanup(DefDescriptor<T> descriptor, String contents) {
        return addSourceAutoCleanup(descriptor, contents, NamespaceAccess.INTERNAL);
    }

    /**
     * Convenience method to create a description and load a source in one shot.
     *
     * @param descriptor descriptor for the source to be created
     * @param contents source contents
     * @param access namespace access type.
     * @return the {@link DefDescriptor} for the created definition
     */
    public <T extends Definition> DefDescriptor<T> addSourceAutoCleanup(DefDescriptor<T> descriptor, String contents,
            NamespaceAccess access) {
        return addSourceAutoCleanup(descriptor, contents, access, false);
    }

    public <T extends Definition> DefDescriptor<T> addSourceAutoCleanup(DefDescriptor<T> descriptor, String contents,
                NamespaceAccess access, Boolean overwrite) {
        stringSourceLoader.putSource(descriptor, contents, overwrite, access);
        markForCleanup(descriptor);
        return descriptor;
    }

    /**
     * Remove a definition from the source loader.
     *
     * @param descriptor the descriptor identifying the loaded definition to remove.
     */
    public <T extends Definition> void removeSource(DefDescriptor<T> descriptor) {
        stringSourceLoader.removeSource(descriptor);
        if (cleanUpDds != null) {
            cleanUpDds.remove(descriptor);
        }
    }

    private void markForCleanup(DefDescriptor<?> desc) {
        if (cleanUpDds == null) {
            cleanUpDds = Sets.newHashSet();
        }
        cleanUpDds.add(desc);
    }

    /**
     * Start a context and set up default values.
     */
    protected AuraContext setupContext(Mode mode, Format format, DefDescriptor<? extends BaseComponentDef> desc)
            throws QuickFixException {
        AuraContext ctxt = contextService.startContext(mode, format, Authentication.AUTHENTICATED, desc);
        ctxt.setFrameworkUID(configAdapter.getAuraFrameworkNonce());
        ctxt.setActionPublicCacheKey(configAdapter.getActionPublicCacheKey());

        // start debug block
        DefRegistry registry = ctxt.getRegistries().getRegistryFor(desc);
        BaseComponentDef def = null;
        Throwable exception = null;
        try {
            def = registry.getDef(desc);
        } catch (Throwable t) {
            exception = t;
        }
        if (def == null) {
            throw new AuraRuntimeException(String.format("Failed to find %s@%s in %s%s%s%s among %s", desc.getQualifiedName(),
                    desc.getDefType(), registry.getClass(), registry.getDefTypes(), registry.getPrefixes(),
                    registry.getNamespaces(), ctxt.getRegistries().getAllRegistries()), exception);
        }
        // end debug block

        String uid = definitionService.getUid(null, desc);
        ctxt.addLoaded(desc, uid);
        return ctxt;
    }

    /**
     * Get a context for use with a get/post.
     *
     * @param mode the Aura mode to use.
     * @param format the format (HTML vs JSON) to use
     * @param desc the descriptor name to set as the primary object.
     * @param type the type of descriptor.
     * @param modified break the context uid.
     */
    public String getContextURL(Mode mode, Format format, String desc, Class<? extends BaseComponentDef> type,
            boolean modified) throws QuickFixException {
        return getContextURL(mode, format, definitionService.getDefDescriptor(desc, type), modified);
    }

    @Deprecated
    public String getContext(Mode mode, Format format, String desc, Class<? extends BaseComponentDef> type,
            boolean modified) throws QuickFixException {
        return getContextURL(mode, format, desc, type, modified);
    }

    /**
     * Get a context as a string.
     *
     * @param mode the Aura mode to use.
     * @param format the format (HTML vs JSON) to use
     * @param desc the descriptor to set as the primary object.
     * @param modified break the context uid.
     */
    public String getContextURL(Mode mode, Format format, DefDescriptor<? extends BaseComponentDef> desc,
            boolean modified) throws QuickFixException {
        AuraContext ctxt = setupContext(mode, format, desc);
        String ctxtString;
        if (modified) {
            String uid = modifyUID(ctxt.getLoaded().get(desc));
            ctxt.addLoaded(desc, uid);
        }
        ctxtString = ctxt.getEncodedURL(AuraContext.EncodingStyle.Normal);
        contextService.endContext();
        return ctxtString;
    }

    public String buildContextForPost(Mode mode, DefDescriptor<? extends BaseComponentDef> app)
            throws QuickFixException {
        return buildContentForXHR(mode, app, null, null, null, null, null);
    }

    public String buildContextForPost(Mode mode, DefDescriptor<? extends BaseComponentDef> app,
            Map<DefDescriptor<?>, String> extraLoaded, List<String> dn) throws QuickFixException {
        return buildContentForXHR(mode, app, null, null, extraLoaded, dn, null);
    }

    public String buildContextForPost(Mode mode, DefDescriptor<? extends BaseComponentDef> app, String appUid,
            String fwuid, Map<DefDescriptor<?>, String> extraLoaded, List<String> dn) throws QuickFixException {
        return buildContentForXHR(mode, app, appUid, fwuid, extraLoaded, dn, null);
    }
    
    public String buildContextForPublicCacheableXHR(Mode mode, DefDescriptor<? extends BaseComponentDef> app, 
    		String actionPublicCacheKey) throws QuickFixException{
    	return AuraTextUtil.urlencode(buildContentForXHR(mode, app, null, null, null, null, actionPublicCacheKey));
    }

    /**
     * Serialize a context for an XHR.
     *
     * This must remain in sync with AuraContext.js
     *
     * <code>
     * return aura.util.json.encode({
     *     "mode" : this.mode,
     *     "loaded" : this.loaded,
     *     "dn" : $A.services.component.getDynamicNamespaces(),
     *     "app" : this.app,
     *     "cmp" : this.cmp,
     *     "fwuid" : this.fwuid,
     *     "test" : this.test
     * });
     * </code>
     */
    private String buildContentForXHR(Mode mode, DefDescriptor<? extends BaseComponentDef> app, String appUid,
            String fwuid, Map<DefDescriptor<?>, String> extraLoaded, List<String> dn, 
            String actionPublicCacheKey) throws QuickFixException {
        StringBuffer sb = new StringBuffer();
        JsonEncoder json = new JsonEncoder(sb, false);
        Map<String, String> loaded = Maps.newHashMap();

        if (appUid == null) {
            AuraContext ctx = null;
            if (!contextService.isEstablished()) {
                ctx = contextService.startContext(mode, Format.JSON, Authentication.AUTHENTICATED, app);
            }
            appUid = definitionService.getUid(null, app);
            if (ctx != null) {
                contextService.endContext();
            }
        }
        if (fwuid == null) {
            fwuid = configAdapter.getAuraFrameworkNonce();
        }
        if (dn == null) {
            dn = Lists.newArrayList();
        }
        if (extraLoaded != null) {
            for (Map.Entry<DefDescriptor<?>, String> entry : extraLoaded.entrySet()) {
                loaded.put(String.format("%s@%s", entry.getKey().getDefType().toString(),
                        entry.getKey().getQualifiedName()), entry.getValue());
            }
        }
        loaded.put(String.format("%s@%s", app.getDefType().toString(), app.getQualifiedName()), appUid);

        try {
            json.writeMapBegin();
            json.writeMapEntry("mode", mode.toString());
            json.writeMapEntry("loaded", loaded);
            if (app.getDefType() == DefType.APPLICATION) {
                json.writeMapEntry("app", app.getQualifiedName());
            } else {
                json.writeMapEntry("cmp", app.getQualifiedName());
            }
            json.writeMapEntry("dn", dn);
            json.writeMapEntry("fwuid", fwuid);
            json.writeMapEntry("test", "undefined");
            if (actionPublicCacheKey != null) {
                json.writeMapEntry("apck", actionPublicCacheKey);
            }
            json.writeMapEnd();
        } catch (IOException ioe) {
            // you can't get an io exception writing to a stringbuffer.....
            throw new RuntimeException(ioe);
        }
        return sb.toString();
    }

    /**
     * Make a UID be incorrect.
     */
    public String modifyUID(String old) {
        StringBuilder sb = new StringBuilder(old);
        char flip = sb.charAt(3);

        // change the character.
        if (flip == 'a') {
            flip = 'b';
        } else {
            flip = 'a';
        }
        sb.setCharAt(3, flip);
        return sb.toString();
    }

    private Map<DefType, String> prefixMap = new ImmutableMap.Builder<DefType, String>()
            .put(DefType.COMPONENT, "markup")
            .put(DefType.APPLICATION, "markup")
            .put(DefType.EVENT, "markup")
            .put(DefType.INTERFACE, "markup")
            .put(DefType.LIBRARY, "markup")
            .put(DefType.TOKENS, "markup")
            .put(DefType.MODULE, "markup")

            .put(DefType.DESIGN, "markup")
            .put(DefType.RENDERER, "js")
            .put(DefType.PROVIDER, "js")
            .put(DefType.CONTROLLER, "js")
            .put(DefType.HELPER, "js")
            .put(DefType.MODEL, "js")
            .put(DefType.INCLUDE, "js")
            .put(DefType.TESTSUITE, "js")

            .put(DefType.STYLE, "css")
            .put(DefType.FLAVORED_STYLE, "css")

            .put(DefType.DOCUMENTATION, "markup")
            .put(DefType.EXAMPLE, "markup")

            .put(DefType.SVG, "svg")
            .build();

    private Map<DefType, Boolean> nameableMap = new ImmutableMap.Builder<DefType, Boolean>()
            .put(DefType.INCLUDE, Boolean.TRUE)
            .build();

    private Map<DefType, Parser.Format> formatMap = new ImmutableMap.Builder<DefType, Parser.Format>()
            .put(DefType.COMPONENT, Parser.Format.XML)
            .put(DefType.APPLICATION, Parser.Format.XML)
            .put(DefType.EVENT, Parser.Format.XML)
            .put(DefType.INTERFACE, Parser.Format.XML)
            .put(DefType.LIBRARY, Parser.Format.XML)
            .put(DefType.TOKENS, Parser.Format.XML)
            .put(DefType.MODULE, Parser.Format.XML)

            .put(DefType.DESIGN, Parser.Format.XML)
            .put(DefType.RENDERER, Parser.Format.JS)
            .put(DefType.PROVIDER, Parser.Format.JS)
            .put(DefType.CONTROLLER, Parser.Format.JS)
            .put(DefType.HELPER, Parser.Format.JS)
            .put(DefType.MODEL, Parser.Format.JS)
            .put(DefType.INCLUDE, Parser.Format.JS)
            .put(DefType.TESTSUITE, Parser.Format.JS)

            .put(DefType.STYLE, Parser.Format.CSS)
            .put(DefType.FLAVORED_STYLE, Parser.Format.CSS)

            .put(DefType.DOCUMENTATION, Parser.Format.XML)
            .put(DefType.EXAMPLE, Parser.Format.XML)

            .put(DefType.SVG, Parser.Format.SVG)
            .build();

    public static class BundleEntryInfo {
        private final DefType type;
        private String name;
        private final String contents;
        private DefDescriptor<?> descriptor;

        public BundleEntryInfo(DefType type, String name, String contents) {
            this.type = type;
            this.name = name;
            this.contents = contents;
        }

        public BundleEntryInfo(DefType type, String contents) {
            this.type = type;
            this.contents = contents;
        }

        /**
         * @return the type
         */
        public DefType getType() {
            return type;
        }

        /**
         * @return the name
         */
        public String getName() {
            return name;
        }

        /**
         * @param name the name to set
         */
        public void setName(String name) {
            this.name = name;
        }

        /**
         * @return the contents
         */
        public String getContents() {
            return contents;
        }

        /**
         * @return the descriptor
         */
        public DefDescriptor<?> getDescriptor() {
            return descriptor;
        }

        /**
         * @param descriptor the descriptor to set
         */
        public void setDescriptor(DefDescriptor<?> descriptor) {
            this.descriptor = descriptor;
        }
    }

    /**
     * Build a single text source with a static name and static prefix (only for templateCss?).
     *
     * This is intended for unit testing, or submodule testing. The source provided here is not registered
     * anywhere and cannot be looked up by aura. This means that it can only be used for sub-unit testing.
     *
     * Preferably use the version without the name.
     *
     * @param namespace the namespace (should be one of the provided ones here) - specifies internal/priveleged/custom
     * @param namespace the name
     * @param defClass the class of the source.
     * @param prefixOverride the prefix override
     * @param contents the contents for the source.
     */
    public <D extends Definition> TextSource<D> buildTextSource(String namespace, String name, Class<D> defClass,
            String prefixOverride, String contents, Parser.Format fmt) {
        DefDescriptor<D> descriptor = new DefDescriptorImpl<>(prefixOverride, namespace, name, defClass);
        return new StringSource<D>(descriptor, contents, descriptor.getQualifiedName(), fmt);
    }

    /**
     * Build a single text source with a static name and bundle.
     *
     * This is intended for unit testing, or submodule testing. The source provided here is not registered
     * anywhere and cannot be looked up by aura. This means that it can only be used for sub-unit testing.
     *
     * Preferably use the version without the name.
     *
     * @param namespace the namespace (should be one of the provided ones here) - specifies internal/priveleged/custom
     * @param namespace the name
     * @param defClass the class of the source.
     * @param contents the contents for the source.
     * @param bundle the bundle for the descriptor.
     */
    public <D extends Definition> TextSource<D> buildTextSource(String namespace, String name, Class<D> defClass,
            String contents, DefDescriptor<?> bundle) {
        DefType type = DefType.getDefType(defClass);
        DefDescriptor<D> descriptor = new DefDescriptorImpl<>(prefixMap.get(type), namespace, name, defClass, bundle);
        return new StringSource<>(descriptor, contents, descriptor.getQualifiedName(), formatMap.get(type));
    }



    /**
     * Build a single text source with a static name.
     *
     * This is intended for unit testing, or submodule testing. The source provided here is not registered
     * anywhere and cannot be looked up by aura. This means that it can only be used for sub-unit testing.
     *
     * Preferably use the version without the name.
     *
     * @param namespace the namespace (should be one of the provided ones here) - specifies internal/priveleged/custom
     * @param namespace the name
     * @param defClass the class of the source.
     * @param contents the contents for the source.
     */
    public <D extends Definition> TextSource<D> buildTextSource(String namespace, String name, Class<D> defClass,
            String contents) {
        return buildTextSource(namespace, name, defClass, contents, null);
    }

    /**
     * Build a single text source.
     *
     * This is intended for unit testing, or submodule testing. The source provided here is not registered
     * anywhere and cannot be looked up by aura. This means that it can only be used for sub-unit testing.
     *
     * @param namespace the namespace (should be one of the provided ones here) - specifies internal/priveleged/custom
     * @param defClass the class of the source.
     * @param contents the contents for the source.
     */
    public <D extends Definition> TextSource<D> buildTextSource(String namespace, Class<D> defClass,
            String contents) {
        return buildTextSource(namespace, "name"+random.nextLong(), defClass, contents, null);
    }

    /**
     * Build a single text source with a bundle.
     *
     * This is intended for unit testing, or submodule testing. The source provided here is not registered
     * anywhere and cannot be looked up by aura. This means that it can only be used for sub-unit testing.
     *
     * @param namespace the namespace (should be one of the provided ones here) - specifies internal/priveleged/custom
     * @param defClass the class of the source.
     * @param contents the contents for the source.
     * @param bundle the bundle for the descriptor.
     */
    public <D extends Definition> TextSource<D> buildTextSource(String namespace, Class<D> defClass,
            String contents, DefDescriptor<?> bundle) {
        return buildTextSource(namespace, "name"+random.nextLong(), defClass, contents, bundle);
    }

    /**
     * Build a bundle from a map of type to contents, including a static name.
     *
     * This builds a simple bundle for testing compilers and def builders. The resulting tests are really
     * integration tests of everything below the bundle compiler.
     *
     * Note that the DefTypes map to a descriptor for a file base bundle part. This means that there are only
     * JS functional declarations (CONTROLLER, HELPER, RENDERER, PROVIDER, MODEL)
     *
     * @param namespace the namespace for building (should be #getInternalNamespace() #getPrivilegedNamespace() or #getCustomNamespace()
     * @param name the names for building
     * @param defClass the class of the def to be provided.
     * @param contents the map of DefType to string contents.
     */
    public <D extends Definition> BundleSource<D> buildBundleSource(String namespace, String name,
            Class<D> defClass, Collection<BundleEntryInfo> contents) {
        DefDescriptor<D> bundleDescriptor = new DefDescriptorImpl<>("markup", namespace, name, defClass);
        Map<DefDescriptor<?>, Source<?>> map = Maps.newHashMap();

        for (BundleEntryInfo entry : contents) {
            // Sort out our name
            String entryName = name;
            DefType type = entry.getType();
            DefDescriptor<D> parent = null;
            if (Boolean.TRUE.equals(nameableMap.get(type))) {
                entryName = entry.getName();
                if (entryName == null) {
                    entryName = "name"+random.nextLong();
                }
                parent = bundleDescriptor;
            }
            entry.setName(entryName);
            DefDescriptor<?> descriptor = new DefDescriptorImpl<>(prefixMap.get(type),
                    namespace, entryName, type.getPrimaryInterface(), parent);
            entry.setDescriptor(descriptor);
            Source<?> source = new StringSource<>(descriptor, entry.getContents(), descriptor.getQualifiedName(),
                    formatMap.get(type));
            map.put(descriptor, source);
        }
        return new BundleSourceImpl<D>(bundleDescriptor, map, true);
    }

    /**
     * Build a bundle from a map of type to contents.
     *
     * This builds a simple bundle for testing compilers and def builders. The resulting tests are really
     * integration tests of everything below the bundle compiler.
     *
     * Note that the DefTypes map to a descriptor for a file base bundle part. This means that there are only
     * JS functional declarations (CONTROLLER, HELPER, RENDERER, PROVIDER, MODEL)
     *
     * @param namespace the namespace for building (should be #getInternalNamespace() #getPrivilegedNamespace() or #getCustomNamespace()
     * @param defClass the class of the def to be provided.
     * @param contents the map of DefType to string contents.
     */
    public <D extends Definition> BundleSource<D> buildBundleSource(String namespace, Class<D> defClass,
            Collection<BundleEntryInfo> contents) {
        return buildBundleSource(namespace, "name"+random.nextLong(), defClass, contents);
    }

    /**
     * Creates a bundle-like folder structure with file as specified
     *
     * will create a file and make directories in the format:
     *   <directory>/<name>/<name><extension>
     *
     * @param directory directory to create the folder / file
     * @param name name of folder and file
     * @param extension file extension of the file
     * @param contents stuff for the file
     * @throws Exception
     */
    public void makeFile(File directory, String name, String extension, String contents) throws Exception {
        File dir = new File(directory, name);
        File file = new File(dir, name+extension);
        dir.mkdirs();
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(contents.getBytes("UTF-8"));
            fos.close();
        }
    }
}
