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

import org.auraframework.util.AuraTextUtil;

/**
 * Gets files from the relevant file paths
 */
public enum AuraImplFiles {

    /**
     * Aura-Impl Module Root dir
     */
    AuraImplModuleDirectory(AuraUtil.getAuraHome(), "aura-impl"),
    /**
     * Aura-Impl-Test Module Root dir
     */
    AuraImplTestModuleDirectory(AuraImplModuleDirectory.getPath(), "src", "test"),
    /**
     * javascript source directory
     */
    AuraJavascriptSourceDirectory(AuraImplModuleDirectory.getPath(), "src", "main" , "resources"),

    /**
     * aura-resources Module Root dir
     */
    AuraResourcesModuleDirectory(AuraUtil.getAuraHome(), "aura-resources"),

    AuraResourcesSrcGenDirectory(AuraResourcesModuleDirectory.getPath(), "target", "src-gen", "main",
            "resources", "aura"),

    /**
     * javascript destination directory to generate into, in the resources
     * module
     */
    AuraResourceJavascriptDirectory(AuraResourcesSrcGenDirectory.getPath(), "javascript"),

    /**
     * javascript destination directory to generate into, in the resources
     * module
     */
    AuraResourcesSrcGenResourcesDirectory(AuraResourcesSrcGenDirectory.getPath(), "resources"),

    /**
     * the other javascript destination directory that we have to regenerate
     * into, also in the resources module
     */
    AuraResourceJavascriptClassDirectory(AuraResourcesModuleDirectory.getPath(), "target", "classes", "aura",
            "javascript"),
    /**
     * javascript source file directory for xUnit tests 
     */
    AuraJavascriptTestSourceDirectory(AuraImplTestModuleDirectory.getPath(), "javascript"),
    /**
     * javascript tests destination, xUnit test files are generated into this directory
     */
    AuraResourceJavascriptTestDirectory(AuraImplModuleDirectory.getPath(), "target", "xunitjs"),

    AuraResourcesClassDirectory(AuraResourcesModuleDirectory.getPath(), "target", "classes", "aura", "resources"),

    /**
     * aura-resources source directory
     */
    AuraResourcesSourceDirectory(AuraResourcesModuleDirectory.getPath(), "src", "main", "resources",
            "aura", "resources"),

    EngineSourceDirectory(AuraResourcesSourceDirectory.getPath(), "engine"),

    AuraLockerSourceDirectory(AuraResourcesSourceDirectory.getPath(), "lockerservice");

    private final String path;

    private AuraImplFiles(String... path) {
        this.path = AuraTextUtil.arrayToString(path, File.separator, -1, false);
    }

    /**
     * @return the path to this File.
     */
    public String getPath() {
        return this.path;
    }

    /**
     * @return A java.util.File for this file's path
     */
    public File asFile() {
        return new File(path);
    }

}
