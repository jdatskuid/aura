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
     * Positive test case: Assign Negative value for attribute 'value'.
     */
    testNegativeValue:{
        attributes: {value : -123},
        test: function(component){
            $A.test.assertEquals('-123', $A.test.getText(component.find('span').getElement()), "Negative values not displayed correctly.");
        }
    },
    /**
     * Positive test case: Assign Negative value for attribute 'value' and special negative format
     */
    testNegativeValueWithNegativeFormat:{
        attributes: {value : -123.936, format:"#.0#;(#.0#)"},
        test: function(component){
            $A.test.assertEquals('(123.94)', $A.test.getText(component.find('span').getElement()), "Negative values not displayed correctly.");
        }
    },
    /**
     * Positive test case: Assign Positive value for attribute 'value'.
     */
    testValue: {
        attributes : {value : 123},
        test: function(component){
            $A.test.assertEquals('123', $A.test.getText(component.find('span').getElement()), "Positive value attribute not correct");
        }
    },
    /**
     * Negative test case: Assign blank value for attribute 'value'.
     * Expect it to show nothing.
     */
    //TODO W-984924 value must be set in markup. moved to js://uitest.outputCmps_EmptyStringValue for now.
    _testEmptyStringValue: {
        attributes : {value : ''},
        test: function(component){
            $A.test.assertEquals('', $A.test.getText(component.find('span').getElement()), "Expect to see an empty span.");
        }
    },
    /**
     * Negative test case:Verify providing non numeric value for Value attribute.
     */
    // W-4348694 handle non-numeric better
    _testNonNumericValue: {
        attributes : {value : 'NotANumber'},
        test: function(component){
            $A.test.assertEquals('', $A.test.getText(component.find('span').getElement()), "Should gracefully display nothing");
        }
    },
    /**
     * Positive test case: Assign nothing to format value and verify default precision used to display decimal.
     */
    testDefaultDecimalPrecision: {
        attributes : {value : 123.450},
        test: function(component){
            $A.test.assertEquals('123.45', $A.test.getText(component.find('span').getElement()), "Value not displayed as expected when format is not specified.");
        }
    },
    /**
     * Negative test case: Assign a blank value for attribute 'format'.
     */
    testEmptyStringFormat: {
        attributes : {value : 123.450, format : ''},
        test: function(component){
            $A.test.assertEquals('123.45', $A.test.getText(component.find('span').getElement()), "Value not displayed as expected when format is a empty string.");
        }
    },
    /**
     * Negative test case: Assign an invalid format value for attribute 'format'.
     */
    testInvalidFormat  : {
        attributes : {value : 123.450, format : ',,'},
        test: function(component){
            $A.test.assertEquals('Invalid format attribute', $A.test.getText(component.find('span').getElement()), "Should have displayed an error message.");
        }
    },
    /**
     * Verify Rounding up of lots of 9s
     */
    testRoundingLotsOfNines: {
        attributes : {value : 999999.9, format : '#,##0'},
        test: function(component){
            $A.test.assertEquals('1,000,000', $A.test.getText(component.find('span').getElement()), "Nines were not rounded up correctly");
        }
    },
    /**
     * Verify Rounding up of lots of 9s with extra zero padding
     */
    testRoundingLotsOfNinesAndZeroPadding: {
        attributes : {value : 999999.9, format : '00000000'},
        test: function(component){
            $A.test.assertEquals('01000000', $A.test.getText(component.find('span').getElement()), "Nines were not round up correctly");
        }
    },
    /**
     * Verify Rounding up of decimal part of value.
     */
    testFormat2DecimalPlaces_RoundUp: {
        attributes : {value : 3.1459, format : '.00'},
        test: function(component){
            $A.test.assertEquals('3.15', $A.test.getText(component.find('span').getElement()), "Decimal part of value was not rounded up based on format.");
        }
    },
    /**
     * Verify Rounding down of decimal part of value.
     */
    testFormat2DecimalPlaces_RoundDown: {
        attributes : {value : 3.14159, format : '.00'},
        test: function(component){
            $A.test.assertEquals('3.14', $A.test.getText(component.find('span').getElement()), "Decimal part of value was not rounded down based on format.");
        }
    },
    /**
     * Verify Rounding functionality when length of integer part is restricted by format.
     */
    testFormatDoesNotRestrictIntegerValue: {
        attributes : {value : 22.7, format : '0.0'},
        test: function(component){
            $A.test.assertEquals('22.7', $A.test.getText(component.find('span').getElement()), "Should have displayed full value but was probably truncated.");
        }
    },
    /**
     * Verify that zeros are appended to decimal value to match format.
     */
    testAppendingZeroToMatchFormat: {
        attributes : {value : 22.7, format : '.000'},
        test: function(component){
            $A.test.assertEquals('22.700', $A.test.getText(component.find('span').getElement()), "Should have appended two zeros to match format.");
        }
    },
    /**
     * Test big value that is too large for a js number and is represented instead by a string
     */
    testBigDecimal:{
        attributes : {value : '1234567890123456789012345678901234567890.12', format : '.00'},
        test: function(component){
            $A.test.assertEquals('1234567890123456789012345678901234567890.12', $A.test.getText(component.find('span').getElement()), "Unexpected value.");
        }
    },

    /**
     * Verify that when the value changes it is rerendered with the new value
     */
    testUpdateValue: {
        attributes : {value : 22.7, format : '##,#0,00.00#####'},
        test: function(component){
            $A.test.assertEquals('0,22.70', $A.test.getText(component.find('span').getElement()), "Value not formatted correctly");
            component.set("v.value", 49322);
            $A.rerender(component);
            $A.test.assertEquals('4,93,22.00', $A.test.getText(component.find('span').getElement()), "Value not updated after changed");
        }
    },

    /**
     * Verify that when the value doesn't change it is rerendered with the same value
     */
    testUpdateValueWithSame: {
        attributes : {value : 22.7, format : '##,#0,00.00#####'},
        test: function(component){
            $A.test.assertEquals('0,22.70', $A.test.getText(component.find('span').getElement()), "Value not formatted correctly");
            component.set("v.value", 22.7);
            $A.rerender(component);
            $A.test.assertEquals('0,22.70', $A.test.getText(component.find('span').getElement()), "Value not updated after changed");
        }
    },

    /**
     * Verify that when the format changes it is rerendered using the new format
     */
    testUpdateFormat: {
        attributes : {value : 22.7, format : '##,#0,00.00#####'},
        test: function(component){
            $A.test.assertEquals('0,22.70', $A.test.getText(component.find('span').getElement()), "Value not formatted correctly");
            component.set("v.format", "meh #.#");
            $A.rerender(component);
            $A.test.assertEquals('meh 22.7', $A.test.getText(component.find('span').getElement()), "Value not updated after format changed");
        }
    },

    /**
     * Verify that when the format doesn't change it is rerendered using the same format
     */
    testUpdateFormatWithSame: {
        attributes : {value : 22.7, format : '##,#0,00.00#####'},
        test: function(component){
            $A.test.assertEquals('0,22.70', $A.test.getText(component.find('span').getElement()), "Value not formatted correctly");
            component.set("v.format", "##,#0,00.00#####");
            $A.rerender(component);
            $A.test.assertEquals('0,22.70', $A.test.getText(component.find('span').getElement()), "Value not updated after format changed");
        }
    }
/*eslint-disable semi*/
})
/*eslint-enable semi*/
