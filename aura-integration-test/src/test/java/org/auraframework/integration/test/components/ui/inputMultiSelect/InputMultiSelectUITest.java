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
package org.auraframework.integration.test.components.ui.inputMultiSelect;

import java.util.List;

import org.auraframework.integration.test.util.WebDriverTestCase;
import org.auraframework.test.util.WebDriverUtil.BrowserType;
import org.auraframework.util.test.annotation.PerfTest;
import org.auraframework.integration.test.util.WebDriverTestCase.ExcludeBrowsers;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.Select;

@ExcludeBrowsers({ BrowserType.IPAD, BrowserType.IPHONE, BrowserType.FIREFOX } )
public class InputMultiSelectUITest extends WebDriverTestCase {
    private final String[] URL = new String[] { "/uitest/inputMultiSelect_Test.cmp",
            "/uitest/inputMultiSelect_NestedOptionsTest.cmp" };
    private final By outputLocator = By.xpath("//span[@class='uiOutputText']");
    private final By selectLocator = By.xpath("//select[1]");
    private final By submitLocator = By.cssSelector("button.uiButton");
    private final String optionLocatorString = "//select[1]/option[text()='%s']";

    private void openTestPage(int i) throws Exception {
        open(URL[i]);
    }

    private Select getInputSelect() {
        return new Select(findDomElement(selectLocator));
    }

    private void selectOption(String optionLabel) {
        selectDeselectOption(optionLabel, true);
    }

    private void deselectOption(String optionLabel) {
        selectDeselectOption(optionLabel, false);
    }

    private void selectDeselectOption(String optionLabel, boolean isSelect) {
        if (isSelect) {
            getInputSelect().selectByVisibleText(optionLabel);
            verifyOptionSelected(optionLabel);
        } else {
            getInputSelect().deselectByVisibleText(optionLabel);
            verifyOptionDeselected(optionLabel);
        }
    }

    private void verifyOptionSelected(String optionLabel) {
        verifyOptionSelectDeselct(optionLabel, true);
    }

    private void verifyOptionDeselected(String optionLabel) {
        verifyOptionSelectDeselct(optionLabel, false);
    }

    private void verifyOptionSelectDeselct(String optionLabel, boolean isSelected) {
        WebElement option = findDomElement(By.xpath(String.format(optionLocatorString, optionLabel)));
        if (isSelected) {
            assertTrue("Option '" + optionLabel + "' should be selected", option.isSelected());
        } else {
            assertFalse("Option '" + optionLabel + "' should be deselected", option.isSelected());
        }
    }

    /**
     * Select one. Choose one option. Deselect one. Deselect one option.
     */
    @PerfTest
    @Test
    public void testInputSelectSingle() throws Exception {
        for (int i = 0; i < URL.length; i++) {
            openTestPage(i);

            // select
            focusSelectElement();
            selectOption("Option1");
            verifyOptionDeselected("Option2");
            verifyOptionDeselected("Option3");

            findDomElement(submitLocator).click();
            getAuraUITestingUtil().waitForElementText(outputLocator, "option1", true);
            verifyOptionSelected("Option1");
            verifyOptionDeselected("Option2");
            verifyOptionDeselected("Option3");

            // deselect
            focusSelectElement();
            deselectOption("Option1");
            selectOption("Option3");
            verifyOptionDeselected("Option2");

            findDomElement(submitLocator).click();
            getAuraUITestingUtil().waitForElementText(outputLocator, "option3", true);
            verifyOptionSelected("Option3");
            verifyOptionDeselected("Option1");
            verifyOptionDeselected("Option2");
        }
    }
    /**
     * Only for IE10 we need to explicitly bring focus on to select input. selectBy() does not do it. But clicking on
     * select element corrupts selected/unselected options so we need to preserve the state
     */
    private void focusSelectElement() {
        if (BrowserType.IE10.equals(getBrowserType())) {
            List<WebElement> selectedOptions = getInputSelect().getAllSelectedOptions();
            findDomElement(selectLocator).click();

            getInputSelect().deselectAll();
            for (int i = 0; i < selectedOptions.size(); i++) {
                getInputSelect().selectByVisibleText(selectedOptions.get(i).getText());
            }
        }
    }
}
