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

import org.auraframework.integration.test.util.WebDriverTestCase.ExcludeBrowsers;
import org.auraframework.test.util.WebDriverUtil.BrowserType;

@ExcludeBrowsers({ BrowserType.IPAD, BrowserType.IPHONE, BrowserType.FIREFOX } )
public class InputMultiSelectNestedOptionsUITest extends BaseInputMultiSelect {

    /**
     * This URL runs tests against options added in through the body
     */
    public InputMultiSelectNestedOptionsUITest() {
        super("/uitest/inputMultiSelect_NestedOptionsTest.cmp");
    }
}
