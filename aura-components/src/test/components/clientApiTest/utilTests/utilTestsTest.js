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
    testValidComponent : {
        test : function(cmp){
            var foo;
            $A.test.assertFalse( $A.util.isComponent(foo), "undefined: Should not be a component");

            foo = null;
            $A.test.assertFalse( $A.util.isComponent(foo), "null: Should not be a component");

            foo = {};
            $A.test.assertFalse( $A.util.isComponent(foo), "empty object: Should not be a component");

            $A.test.assertTrue( $A.util.isComponent(cmp), "Should be a component");
        }
    },


    testGetAndSetDataAttributes: {
        test : function(cmp){
            var div = cmp.find("aDiv").getElement();
            var data = $A.util.getDataAttribute(div, "testData");
            $A.test.assertEquals("divtestdata", data, "Could not retrieve data attribute hardcoded on div");

            $A.util.setDataAttribute(div, "testData", "newdata");
            data = $A.util.getDataAttribute(div, "testData");
            $A.test.assertEquals("newdata", data, "Could not set data attribute to new value");

            $A.util.setDataAttribute(div, "testMonkey", "bananas");
            var newAttrVal = $A.util.getDataAttribute(div, "testMonkey");
            $A.test.assertEquals("bananas", newAttrVal, "Setting data attribute with new key should create data attribute");
        }
    },

    /**
     * Verify setting data attribute to undefined removes it from dom element
     */
    testRemoveDataAttribute: {
        test : function(cmp){
            var div = cmp.find("aDiv").getElement();
            $A.test.assertFalse($A.util.isUndefinedOrNull(div.getAttribute("data-test-data")), "Data attribute not present on div");
            $A.util.setDataAttribute(div, "testData", undefined);
            $A.test.assertTrue($A.util.isUndefinedOrNull(div.getAttribute("data-test-data")), "Setting data attribute with undefined value " +
                "should remove the attribute");
        }
    },

    testDataAttributeInvalidNodeType: {
        test : function(cmp){
            var textNode = cmp.find("aDiv").getElement().childNodes[0];
            $A.util.setDataAttribute(textNode, "monkey", "shouldntWork");
            $A.test.assertNull($A.util.getDataAttribute(textNode, "monkey"), "Should not be able to set data " +
                "attributes on text nodes");
        }
    },

    /**
     * Verify $A.util.isComponent() API
     */
    testIsComponent: {
        test:[
            function(cmp){
                $A.test.assertTrue($A.util.isComponent(cmp));
                $A.test.assertTrue($A.util.isComponent(cmp.find("aDiv")));
                $A.test.assertFalse($A.util.isComponent(cmp.getDef()));
                $A.test.assertFalse($A.util.isComponent(cmp.getElement()));
                var valueObj = $A.expressionService.create(null, "literal");
                $A.test.assertFalse($A.util.isComponent(valueObj));
            },
            function(cmp){
                $A.test.assertFalse($A.util.isComponent(""));
                $A.test.assertFalse($A.util.isComponent(undefined));
                $A.test.assertFalse($A.util.isComponent(null));
                $A.test.assertFalse($A.util.isComponent());
            }
        ]
    },

    testGetCookieForNonExistedCookie: {
        test: function() {
            $A.test.assertUndefined($A.util.getCookie("nonexistentCookie"),
                    "'undefined' should be returned when cookie does not exist");
        }
    },

    testGetCookieForExpiredCookie: {
        test: function() {
            var cookieKey = "myCookieKey";
            var cookieValue = "myCookieValue";
            var expiredCookie = cookieKey + "=" + cookieValue + ";expires=Thu, 01 Jan 1970 00:00:00 GMT";
            document.cookie = expiredCookie;

            $A.test.assertUndefined($A.util.getCookie(cookieKey),
                    "'undefined' should be returned when cookie is expired");
        }
    },

    testGetCookieForExistedCookie: {
        test: function() {
            var cookieKey = "myCookieKey";
            var cookieValue = "myCookieValue";

            var expiration = new Date(new Date().getTime() + 1000*60*60); //1h
            var existedCookie = cookieKey + "=" + cookieValue + ";expires=" + expiration.toUTCString();
            document.cookie = existedCookie;

            $A.test.assertEquals(cookieValue, $A.util.getCookie(cookieKey),
                    "Failed to get right cookie value");
        }
    },

    testContainsForSVGElement: {
        test: function() {
            var svgElement = document.createElementNS("http://www.w3.org/2000/svg", "svg");
            var divElement = document.createElement("div");

            divElement.appendChild(svgElement);

            var actual = $A.util.contains(divElement, svgElement);
            $A.test.assertTrue(actual);
        }
    },

    testContainsForSVGElementInstance: {
        test: function() {
            var svgElement = document.createElementNS("http://www.w3.org/2000/svg", "svg");
            var divElement = document.createElement("div");
            //We don't create a real SVGElementInstance here because SVGElementInstance's are not
            //supported in all browsers.
            var svgElementInstance = document.createElementNS("http://www.w3.org/2000/svg", "svg");
            svgElementInstance["correspondingUseElement"] = svgElement;

            divElement.appendChild(svgElementInstance);
            var actual = $A.util.contains(divElement, svgElementInstance);
            $A.test.assertTrue(actual);
        }
    }
})