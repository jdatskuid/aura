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
({
    /**
     * Verify the initial property values
     */
    testInitialPropertyValues: {
        test:[
            function valueSetInInitHandlerIsNotDisplayed(component){
                var expected = component.get("v.initValue");

                var element = component.find("PV_initContainer").getElement();
                var actual = $A.test.getText(element);

                aura.test.assertNotEquals(expected,actual,"Value set in init was displayed.");
            },
            function booleanValueMatchesAttribute(component){
                var expected = component.get("v.booleanValue")+'';

                var element = component.find("PV_booleanContainer").getElement();
                var actual = $A.test.getText(element);

                aura.test.assertEquals(expected,actual,"Boolean value did not match attribute.");
            },
            function numberValueMatchesAttribute(component){
                var expected = component.get("v.numberValue")+'';

                var element = component.find("PV_numberContainer").getElement();
                var actual = $A.test.getText(element);

                aura.test.assertEquals(expected,actual,"Number value did not match attribute.");
            },
            function stringValueMatchesAttribute(component){
                var expected = component.get("v.stringValue");

                var element = component.find("PV_stringContainer").getElement();
                var actual = $A.test.getText(element);

                aura.test.assertEquals(expected,actual,"String value did not match attribute.");
            },
            function fcvValueMatchesAttribute(component){
                var expected = component.get("v.stringValue")+" FCV CONCAT";

                var element = component.find("PV_fcvContainer").getElement();
                var actual = $A.test.getText(element);

                aura.test.assertEquals(expected,actual,"FunctionCallValue did not match attribute.");
            }
        ]
    },
    /**
     * Verify the initial property reference values
     */
    testInitialPropertyReferenceValues: {
        test:[
            function referenceSetInInitHandlerIsDisplayed(component){
                var expected = component.get("v.initValue");

                var element = component.find("PRV_initContainer").getElement();
                var actual = $A.test.getText(element);

                aura.test.assertEquals(expected,actual,"Value set in init was not displayed.");
            },
            function booleanReferenceMatchesAttribute(component){
                var expected = component.get("v.booleanValue")+'';

                var element = component.find("PRV_booleanContainer").getElement();
                var actual = $A.test.getText(element);

                aura.test.assertEquals(expected,actual,"Boolean value did not match attribute.");
            },
            function numberReferenceMatchesAttribute(component){
                var expected=component.get("v.numberValue")+'';

                var element = component.find("PRV_numberContainer").getElement();
                var actual = $A.test.getText(element);

                aura.test.assertEquals(expected,actual,"Number value did not match attribute.");
            },
            function stringReferenceMatchesAttribute(component){
                var expected = component.get("v.stringValue");

                var element = component.find("PRV_stringContainer").getElement();
                var actual = $A.test.getText(element);

                aura.test.assertEquals(expected,actual,"String value did not match attribute.");
            },
            function fcvReferenceMatchesAttribute(component){
                var expected=component.get("v.stringValue")+" FCV CONCAT";

                var element=component.find("PRV_fcvContainer").getElement();
                var actual = $A.test.getText(element);

                aura.test.assertEquals(expected,actual,"FunctionCallValue did not match attribute.");
            }
        ]
    },
    /**
     * Verify the initial property values
     */
    testPropertyValuesAfterChange: {
        test:[
            function changeValuesForSubsequentTests(component){
                component.find("changeButton").getElement().click();
            },
            function valueSetInInitHandlerIsStillEmpty(component){
                var expected='';

                var element = component.find("PV_initContainer").getElement();
                var actual = $A.test.getText(element);

                aura.test.assertEquals(expected,actual,"Value set in init was not empty.");
            },
            function booleanValueMatchesAttribute(component){
                var expected="true";

                var element = component.find("PV_booleanContainer").getElement();
                var actual = $A.test.getText(element);

                aura.test.assertEquals(expected,actual,"Boolean value did not match attribute.");
            },
            function numberValueMatchesAttribute(component){
                var expected="7357";

                var element = component.find("PV_numberContainer").getElement();
                var actual = $A.test.getText(element);

                aura.test.assertEquals(expected,actual,"Number value did not match attribute.");
            },
            function stringValueMatchesAttribute(component){
                var expected = "default string value";

                var element = component.find("PV_stringContainer").getElement();
                var actual = $A.test.getText(element);

                aura.test.assertEquals(expected,actual,"String value did not match attribute.");
            },
            function fcvValueMatchesAttribute(component){
                var expected = "default string value FCV CONCAT";

                var element = component.find("PV_fcvContainer").getElement();
                var actual = $A.test.getText(element);

                aura.test.assertEquals(expected,actual,"FunctionCallValue did not match attribute.");
            }
        ]
    },
    /**
     * Verify the initial property values
     */
    testPropertyReferenceValuesAfterChange: {
        test:[
            function changeValuesForSubsequentTests(component){
                component.find("changeButton").getElement().click();
            },
            function valueSetInInitHandlerIsStillEmpty(component){
                var expected='CHANGED init value';

                var element = component.find("PRV_initContainer").getElement();
                var actual = $A.test.getText(element);

                aura.test.assertEquals(expected,actual,"Value set in init was not empty.");
            },
            function booleanValueMatchesAttribute(component){
                var expected="false";

                var element = component.find("PRV_booleanContainer").getElement();
                var actual = $A.test.getText(element);

                aura.test.assertEquals(expected,actual,"Boolean value did not match attribute.");
            },
            function numberValueMatchesAttribute(component){
                var expected="8335";

                var element = component.find("PRV_numberContainer").getElement();
                var actual = $A.test.getText(element);

                aura.test.assertEquals(expected,actual,"Number value did not match attribute.");
            },
            function stringValueMatchesAttribute(component){
                var expected="CHANGED string value";

                var element = component.find("PRV_stringContainer").getElement();
                var actual = $A.test.getText(element);

                aura.test.assertEquals(expected,actual,"String value did not match attribute.");
            },
            function fcvValueMatchesAttribute(component){
                var expected="CHANGED string value FCV CONCAT";

                var element = component.find("PRV_fcvContainer").getElement();
                var actual = $A.test.getText(element);

                aura.test.assertEquals(expected,actual,"FunctionCallValue did not match attribute.");
            }
        ]
    }

})
