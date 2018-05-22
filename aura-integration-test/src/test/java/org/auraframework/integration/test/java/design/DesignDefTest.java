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
package org.auraframework.integration.test.java.design;

import java.util.Map;

import org.auraframework.def.ComponentDef;
import org.auraframework.def.DefDescriptor;
import org.auraframework.def.InterfaceDef;
import org.auraframework.def.design.DesignAttributeDef;
import org.auraframework.def.design.DesignDef;
import org.auraframework.def.design.DesignTemplateDef;
import org.auraframework.def.design.DesignTemplateRegionDef;
import org.auraframework.impl.AuraImplTestCase;
import org.auraframework.throwable.quickfix.DefinitionNotFoundException;
import org.junit.Test;

public class DesignDefTest extends AuraImplTestCase {
    @Test
    public void testLoadFakeDesignDefinition() throws Exception {
        DesignDef c = definitionService.getDefinition("test:fakeComponent", DesignDef.class);
        assertNotNull("DesignDef not found!", c);
        assertTrue("DesignDef not found!", c.getDescriptor().exists());
    }

    @Test
    public void testVerifyDesignDefLabel() throws Exception {
        DesignDef c = definitionService.getDefinition("test:fakeDesign", DesignDef.class);
        assertNotNull("DesignDef not found!", c);
        assertTrue("DesignDef not found!", c.getDescriptor().exists());
        assertEquals("DesignDef label is incorrect.", "some label", c.getLabel());
    }

    @Test
    public void testVerifyDesignDefDoesNotExist() throws Exception {
        try {
            definitionService.getDefinition("test:thisDesignDoesNotExist", DesignDef.class);
            fail("DesignDef for 'test:thisDesignDoesNotExist' should not exist.");
        } catch (DefinitionNotFoundException e) {
            DefDescriptor<DesignDef> desc = definitionService.getDefDescriptor("test:thisDesignDoesNotExist",
                    DesignDef.class);
            assertFalse("DesignDef for 'test:thisDesignDoesNotExist' should not exist.", desc.exists());
        }
    }

    @Test
    public void testLoadFakeDesignWithAttributes() throws Exception {
        DesignDef c = definitionService.getDefinition("test:fakeDesign", DesignDef.class);
        assertNotNull("DesignDef not found!", c);
        assertTrue("DesignDef not found!", c.getDescriptor().exists());
        Map<DefDescriptor<DesignAttributeDef>, DesignAttributeDef> attrs = c.getAttributeDesignDefs();
        assertFalse("Unable to parse AttributeDesignDefs on DesignDef!", attrs == null || attrs.size() == 0);
        DesignAttributeDef attr = c.getAttributeDesignDef("something");
        assertNotNull("AttributeDesignDef something not found!", attr);
    }

    @Test
    public void testAttributeDesignProperties() throws Exception {
        DesignDef c = definitionService.getDefinition("test:fakeDesign", DesignDef.class);
        assertNotNull("DesignDef not found!", c);
        assertTrue("DesignDef not found!", c.getDescriptor().exists());
        Map<DefDescriptor<DesignAttributeDef>, DesignAttributeDef> attrs = c.getAttributeDesignDefs();
        assertFalse("Unable to parse AttributeDesignDefs on DesignDef!", attrs == null || attrs.size() == 0);
        DesignAttributeDef attr = c.getAttributeDesignDef("something");
        assertNotNull("AttributeDesignDef 'something' not found!", attr);
        assertEquals("AttributeDesignDef 'something' name is incorrect.", "something", attr.getName());
        assertEquals("AttributeDesignDef 'something' label is incorrect.", "some label", attr.getLabel());
        assertEquals("AttributeDesignDef 'something' placeholder is incorrect.", "Leave blank for default value",
                attr.getPlaceholderText());
        assertEquals("AttributeDesignDef 'something' min is incorrect.", "-100", attr.getMin());
        assertEquals("AttributeDesignDef 'something' max is incorrect.", "100", attr.getMax());

        DesignAttributeDef attr2 = c.getAttributeDesignDef("entities");
        assertNotNull("AttributeDesignDef 'entities' not found!", attr2);
        assertEquals("AttributeDesignDef 'entities' type is incorrect.", "EntityName", attr2.getType());
        assertEquals("AttributeDesignDef 'entities' datasource is incorrect or has wrong format.", "Account,Contact",
                attr2.getDataSource());
        assertTrue("AttributeDesignDef 'entities' required is incorrect.", attr2.isRequired());
        assertFalse("AttributeDesignDef 'entities' readonly is incorrect.", attr2.isReadOnly());
    }

    @Test
    public void testAttributeDesignDataSource() throws Exception {
        DesignDef c = definitionService.getDefinition("test:fakeDesign", DesignDef.class);
        assertNotNull("DesignDef not found!", c);
        assertTrue("DesignDef not found!", c.getDescriptor().exists());
        Map<DefDescriptor<DesignAttributeDef>, DesignAttributeDef> attrs = c.getAttributeDesignDefs();
        assertFalse("Unable to parse AttributeDesignDefs on DesignDef!", attrs == null || attrs.size() == 0);
        DesignAttributeDef attr = c.getAttributeDesignDef("else");
        assertNotNull("AttributeDesignDef 'else' not found!", attr);
        assertEquals("AttributeDesignDef 'else' datasource is incorrect or has wrong format", "one,two,three",
                attr.getDataSource());
    }

    @Test
    public void testDesignTemplateWithRegions() throws Exception {
        ComponentDef cmp = definitionService.getDefinition("test:fakeDesign", ComponentDef.class);
        DesignDef c = cmp.getDesignDef();
        assertNotNull("DesignDef not found!", c);
        DesignTemplateDef template = c.getDesignTemplateDef();
        assertNotNull("DesignTemplateDef not found!", template);

        DesignTemplateRegionDef regionOne = template.getDesignTemplateRegionDef("regionOne");
        assertNotNull("DesignTemplateRegionDef regionOne not found!", regionOne);
        assertTrue("DesignTemplateRegionDef regionOne should have one allowed interface.", regionOne
                .getAllowedInterfaces().size() == 1);
        for (DefDescriptor<InterfaceDef> intf : regionOne.getAllowedInterfaces()) {
            assertTrue("InterfaceDef not found!", intf.exists());
        }

        DesignTemplateRegionDef regionTwo = template.getDesignTemplateRegionDef("regionTwo");
        assertNotNull("DesignTemplateRegionDef regionTwo not found!", regionTwo);
        assertTrue("DesignTemplateREgionDef regionTwo should not have any allowed interfaces.", regionTwo
                .getAllowedInterfaces().isEmpty());

        DesignTemplateRegionDef regionThree = template.getDesignTemplateRegionDef("regionThree");
        assertNotNull("DesignTemplateRegionDef regionThree not found!", regionThree);
        assertTrue("DesignTemplateRegionDef regionThree should have two allowed interfaces.", regionThree
                .getAllowedInterfaces().size() == 2);
        for (DefDescriptor<InterfaceDef> intf : regionThree.getAllowedInterfaces()) {
            assertTrue("InterfaceDef not found!", intf.exists());
        }
    }

    @Test
    public void testDesignOption() throws Exception {
        ComponentDef cmp = definitionService.getDefinition("test:fakeDesign", ComponentDef.class);
        DesignDef c = cmp.getDesignDef();

        assertNotNull("Expected to receive a value with option", c.getOption("filter").get(0).getValue());
        assertNull("Expected option to return null value", c.getOption("desktopEnabled").get(0).getValue());
    }

    @Test
    public void testDesignTemplateWithNonExistentInterface() throws Exception {
        try {
            definitionService.getDefinition("test:fakeDesignNonExistentInterface", ComponentDef.class);
            fail("InterfaceDef should not exist and throw validation error");
        } catch (Exception t) {
            assertExceptionMessageStartsWith(t, DefinitionNotFoundException.class,
                    "No INTERFACE named markup://this:doesNotExist found");
        }
    }

}
