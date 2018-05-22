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
package org.auraframework.integration.test.util;

import org.auraframework.def.ComponentDef;
import org.auraframework.def.DefDescriptor;
import org.auraframework.test.util.WebDriverUtil.BrowserType;
import org.junit.Test;
import org.openqa.selenium.By;

public class UtilUITest extends WebDriverTestCase {

	@Test
	@ExcludeBrowsers({BrowserType.IPAD, BrowserType.IPHONE}) 
	public void testRemoveOn() throws Exception {
		DefDescriptor<ComponentDef> cmpDesc = addSourceAutoCleanup(
				ComponentDef.class, String.format(baseComponentTag,
						"render='client'",
						"<div aura:id='div' class='target'>handled</div>"));
		By target = By.cssSelector(".target");
		open(cmpDesc);

		// check that test property is unset, then add a handler
		getDriver().findElement(target).click();
		assertEquals(
				true,
				getAuraUITestingUtil()
						.getBooleanEval("return window['handledByTest'] === undefined"));
		getAuraUITestingUtil()
				.getEval("var handler=function(){window['handledByTest']=true;};window['testHandler']=handler;var elem=$A.getRoot().find('div').getElement();$A.util.on(elem, 'click', handler, false);");

		// trigger handler, then check that test property is now set
		// reset the test property after checking
		getDriver().findElement(target).click();
		assertEquals(
				true,
				getAuraUITestingUtil()
						.getBooleanEval("var res = window['handledByTest'];window['handledByTest'] = undefined;return res===true;"));
		assertEquals(
				true,
				getAuraUITestingUtil()
						.getBooleanEval("return window['handledByTest'] === undefined"));

		// remove the handler, then check that test property is not set on next event
		getAuraUITestingUtil()
				.getEval("$A.util.removeOn($A.getRoot().find('div').getElement(), 'click', window['testHandler'], false);");
		getDriver().findElement(target).click();
		assertEquals(
				true,
				getAuraUITestingUtil()
						.getBooleanEval("return window['handledByTest'] === undefined"));
	}
}
