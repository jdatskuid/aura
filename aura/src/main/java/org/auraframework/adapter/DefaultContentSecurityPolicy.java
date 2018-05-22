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
package org.auraframework.adapter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.auraframework.Aura;
import org.auraframework.http.CSP;
import org.auraframework.http.CSPReporterServlet;
import org.auraframework.service.CSPInliningService;

/**
 * A default, fairly strict security policy, allowing no framing and only same-origin script.
 *
 * @since 194
 */
public class DefaultContentSecurityPolicy implements ContentSecurityPolicy {

    private static final String[] ARRAY_NONE = { CSP.NONE };
    private static final String[] ARRAY_ANY = { CSP.ALL };
    private static final String[] ARRAY_SELF_ONLY = { CSP.SELF };

    private static String inlineHeader = null;
    private static String defaultHeader = null;

    private final CSPInliningService cspInliningService;

    private boolean nonCspInlineEnabled;

    private ConfigAdapter configAdapter;

    /**
     * Returns the content security report URL.
     */
    @Override
    public String getReportUrl() {
        return configAdapter.getCSPReportUri();
    }

    /**
     * Creates a default policy.
     *
     * @param inline whether to allow inline script and style. It's better not to, but legacy is what legacy is.
     * @deprecated use injection to get csp inlining service
     */
    @Deprecated
    public DefaultContentSecurityPolicy(boolean inline) {
        this(inline, Aura.getCspInliningService(), Aura.getConfigAdapter());
    }

    /**
     * Creates a default policy.
     *
     * @param inline whether to allow inline script and style. It's better not to, but legacy is what legacy is.
     * @param scriptService inline csp service
     * @param auraConfigAdapter static reference to config adapter
     */
    public DefaultContentSecurityPolicy(boolean inline, CSPInliningService scriptService, ConfigAdapter auraConfigAdapter) {
        nonCspInlineEnabled = inline;
        cspInliningService = scriptService;
        configAdapter = auraConfigAdapter;
    }

    /**
     * We don't like framing, by default. Not even same-origin. Except that our tests require same-origin framing, so
     * we'll allow that anyway.
     */
    @Override
    public Collection<String> getFrameAncestors() {
        return getSameOrigin();
    }

    /**
     * Default to allow same-origin child framing.
     */
    @Override
    public Collection<String> getFrameSources() {
        return getSameOrigin();
    }

    /**
     * We allow script from same origin and chrome extensions. And sometimes, sadly, inline and eval.
     */
    @Override
    public Collection<String> getScriptSources() {
        List<String> list = new ArrayList<>(nonCspInlineEnabled ? 4 : 3);
        list.add(null); // Same origin allowed

        if (!nonCspInlineEnabled && cspInliningService != null){
            list.addAll(cspInliningService.getCurrentScriptDirectives());
        }

        list.add("chrome-extension:");

        boolean strictCSPEnforced = Aura.getConfigAdapter().isStrictCSPEnforced();

        if (nonCspInlineEnabled || !strictCSPEnforced) {
            list.add(CSP.UNSAFE_INLINE);
        }

        list.add(CSP.UNSAFE_EVAL);

        return list;
    }

    /**
     * We allow style from same origin and chrome extensions. And sometimes, sadly, inline.
     */
    @Override
    public Collection<String> getStyleSources() {
        List<String> list = new ArrayList<>(3);
        list.add(null); // Same origin allowed
        list.add("chrome-extension:");
        list.add(CSP.UNSAFE_INLINE);
        return list;
    }

    /** Gets default-src terms, initially same-origin. */
    @Override
    public Collection<String> getDefaultSources() {
        return getSameOrigin();
    }

    @Override
    public Collection<String> getFontSources() {
        return null;
    }

    @Override
    public Collection<String> getImageSources() {
        return null;
    }

    @Override
    public Collection<String> getObjectSources() {
        return getSameOrigin();
    }

    @Override
    public Collection<String> getMediaSources() {
        return null;
    }

    @Override
    public boolean isNonCspInlineEnabled() {
        return nonCspInlineEnabled;
    }

    /**
     * We can connect only to the same origin. Which should be default by the browser anyway.
     */
    @Override
    public Collection<String> getConnectSources() {
        return getSameOrigin();
    }

    /** Creates a shared, immutable list for same-origin-only */
    private List<String> getSameOrigin() {
    	List<String> sameOrigin = new ArrayList<String>(1);
    	sameOrigin.add(null);
        
    	return sameOrigin;
    }

    @Override
    public String getCspHeaderValue() {
        String header = nonCspInlineEnabled ? inlineHeader : defaultHeader;
        if (header == null) {
            header = buildHeaderNormally(this);
            if (nonCspInlineEnabled) {
                inlineHeader = header;
            } else {
                defaultHeader = header;
            }
        }
        return header;
    }

    public static final String buildHeaderNormally(ContentSecurityPolicy csp) {
        CSP.PolicyBuilder builder = new CSP.PolicyBuilder();

        builder.frame_ancestor(getTerms(csp.getFrameAncestors()));
        builder.frame_src(getTerms(csp.getFrameSources()));
        builder.script_src(getTerms(csp.getScriptSources()));
        builder.style_src(getTerms(csp.getStyleSources()));
        builder.connect_src(getTerms(csp.getConnectSources()));
        builder.default_src(getTerms(csp.getDefaultSources()));
        builder.font_src(getTerms(csp.getFontSources()));
        builder.img_src(getTerms(csp.getImageSources()));
        builder.media_src(getTerms(csp.getMediaSources()));
        builder.object_src(getTerms(csp.getObjectSources()));
        builder.report_uri(csp.getReportUrl());

        return builder.build();
    }

    private static String[] getTerms(Collection<String> terms) {
        if (terms == null) {
            return ARRAY_ANY;
        } else if (terms.size() == 0) {
            return ARRAY_NONE;
        } else if (terms.size() == 1 && terms.contains(null)) {
            return ARRAY_SELF_ONLY;
        }
        String[] result = new String[terms.size()];
        int i = 0;
        for (String site : terms) {
            if (site == null) {
                result[i] = CSP.SELF;
            } else {
                result[i] = site.toLowerCase();
            }
            i++;
        }
        return result;
    }

}
