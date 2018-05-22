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
package org.auraframework.integration.test.components.ui.listSorter;

import java.net.MalformedURLException;
import java.net.URISyntaxException;

import org.auraframework.integration.test.util.WebDriverTestCase;
import org.auraframework.integration.test.util.WebDriverTestCase.TargetBrowsers;
import org.auraframework.test.util.WebDriverUtil.BrowserType;
import org.auraframework.util.test.annotation.PerfTest;
import org.auraframework.util.test.annotation.UnAdaptableTest;
import org.junit.Ignore;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

/**
 * UI automation for ui:ListSorter.
 */
/*
 * UnAdaptable because issue with sfdc environments with sendkeys in iframes see W-1985839 and W-2009411
 */
@UnAdaptableTest
@TargetBrowsers({ BrowserType.GOOGLECHROME, BrowserType.FIREFOX, BrowserType.SAFARI })
public class ListSorterUITest extends WebDriverTestCase {

    public static final String APP = "/uitest/listSorter_Test.cmp";
    private final String ACTIVE_ELEMENT = "return $A.test.getActiveElement()";
    private final String SORT_TRIGGER = "defaultListSorterTrigger";
    private final String SORTER = "defaultListSorter";

    /**
     * Tab out should not close the sorter dialog The focus should remain in the Sorter Menu Test case for W-1985435
     * 
     * @throws MalformedURLException
     * @throws URISyntaxException
     */
    @PerfTest
    @Test
    public void testTabOutOfListSorter() throws Exception {
        verifyTabOutAndEscBehaviour(Keys.TAB, true);
    }

    /**
     * Verify pressing ESC while listSorter is opened should close the list sorter
     * 
     * @throws MalformedURLException
     * @throws URISyntaxException
     */
    @Ignore
    public void _testEscOfListSorter() throws Exception {
        verifyTabOutAndEscBehaviour(Keys.ESCAPE, false);
    }

    /**
     * If isOpen: true then listSorter should be open after pressing tab, isopen: false, list sorter should be closed
     * after pressing tab
     * 
     * @param keysToSend
     * @param isOpen
     * @throws URISyntaxException
     * @throws MalformedURLException
     */
    private void verifyTabOutAndEscBehaviour(Keys keysToSend, boolean isOpen) throws Exception {
        open(APP);
        WebDriver driver = this.getDriver();
        WebElement listSorter = driver.findElement(By.className(SORTER));
        // List Sorter dialog should be closed
        assertFalse("list Sorter Dialog should not be visible", listSorter.getAttribute("class").contains("open"));
        openListSorter();
        focusOnListSorter();//need to focus on the list sorter first, or ESC won't work
        WebElement activeElement = (WebElement) getAuraUITestingUtil().getEval(ACTIVE_ELEMENT);
        activeElement.sendKeys(keysToSend);
        if (isOpen) {
            assertTrue("list Sorter Dialog should still be visible after pressing tab", listSorter
                    .getAttribute("class").contains("open"));
        }
        else {
            assertFalse("list Sorter Dialog should not be visible after pressing ESC", listSorter.getAttribute("class")
                    .contains("open"));
        }
    }

    private void openListSorter() {
        WebDriver driver = this.getDriver();
        WebElement listTrigger = driver.findElement(By.className(SORT_TRIGGER));
        WebElement listSorter = driver.findElement(By.className(SORTER));
        listTrigger.click();
        assertTrue("list Sorter Dialog should be visible", listSorter.getAttribute("class").contains("open"));
    }
    
    private void focusOnListSorter() {
        WebElement input = findDomElement(By.cssSelector("li[class*='uiMenuItem']"));
        input.click(); 
    }
}
