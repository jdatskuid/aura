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
     * Calling destroy on a facet which has its own facet.
     * Each level of facet evaluating v.body in its markup.
     */
    testDestroyOnChainedFacet:{
        test:[ 
            function(cmp){
                //Before Destroy
                $A.test.assertDefined(cmp.find("outerFacet"));
                $A.test.assertDefined(cmp.find("innerFacet1"));
                $A.test.assertDefined(cmp.find("innerFacet2"));
                $A.test.assertEquals(5, cmp.find("team").getElement().childNodes.length);
            },
            function(cmp){
                //Destroy
                var outerFacet = cmp.find("outerFacet");
                try{
                    outerFacet.destroy(false);
                }catch(e){
                    $A.test.fail("Component destroy() failed destroying chained facets with body:"+e)
                }
            },
            function(cmp){
                //After Destroy
                this.verifyComponentDestroyed(cmp);
            }
        ]
    },

    /**
     * Verify that destroy can handle reference loops in facets.
     * Cause a reference loop by making a facet aware of its parent using an attribute on the facet.
     * W-1584816
     */
    testDestroyOnParentAwareFacet:{
        test:[
            function(cmp){
	            $A.test.assertDefined(cmp.find("informFacet"));
	            $A.test.assertDefined(cmp.find("knowParent"));
	        },
	        function(cmp){
	            var facet = cmp.find("informFacet");
	            try{
	                facet.destroy(false);
	            }catch(e){
	                $A.test.fail("Component destroy() failed to handle reference loops in facet:"+e)
	            }
	        },
	        function(cmp){
	            $A.test.assertUndefinedOrNull(cmp.find("informFacet"));
	            $A.test.assertUndefinedOrNull(cmp.find("knowParent"));
	        }
	    ]
    },

    /**
     * Verify Component.isValid() returns false after a component has been synchronously destroyed.
     */
    testIsValidSynchronousDestroy: {
        test: function(cmp) {
            var facet = cmp.find("knowParent");
            $A.test.assertTrue(facet.isValid());
            facet.destroy(false);
            $A.test.assertFalse(facet.isValid());
        }
    },

    /**
     * Verify Component.isValid() returns false after a component has been asynchronously destroyed.
     * note that we force destroy to be sync under test mode : W-1927159. this test is the same as above one now
     */
    testIsValidAsynchronousDestroy: {
        test: function(cmp) {
            var facet = cmp.find("knowParent");
            $A.test.assertTrue(facet.isValid());
            facet.destroy(true);
            $A.test.assertFalse(facet.isValid());
        }
    },

    // TODO: Re-enable when we stop forcing destroy to be synchronous in test modes. This is done in the destroy
    //       functions of Component.js and ArrayValue.js.
    // W-1928349: When we destroy asynchronously we leave a reference node behind, but not when we destroy
    //            synchronously. If the reference node only for async is expected then logic in 
    //            verifyComponentDestroyed will need to change.
    _testDestroyAsync: {
        test: function(cmp) {
            $A.test.assertDefined(cmp.find("outerFacet"));
            $A.test.assertDefined(cmp.find("players"));
            // on load the 'players' div should not have it's display property set
            var playersDisplay = cmp.find("players").getElement().style.display;
            $A.test.assertEquals("", playersDisplay);

            try{
                cmp.find("outerFacet").destroy(true);
            }catch(e){
                $A.test.fail("Component.destroy(true) failed: " + e);
            }

            // 'players' div should still exist, but the style.display will be set to 'none' while we destroy
            $A.test.assertDefined(cmp.find("players"));
            playersDisplay = cmp.find("players").getElement().style.display;
            $A.test.assertEquals("none", playersDisplay);

            var component = cmp;
            $A.test.addWaitFor(
                true,
                function() { return $A.util.isUndefinedOrNull(component.find("outerFacet")); },
                function() { this.verifyComponentDestroyed(cmp); }
            );
        }
    },

    testDestroySameComponentTwice: {
        test: function(cmp) {
            var outerFacet = cmp.find("outerFacet");
            outerFacet.destroy();
            this.verifyComponentDestroyed(cmp);

            // Already destroyed components will call destroy on InvalidComponent, which is a no-op
            $A.test.assertTrue(outerFacet.toString().indexOf("InvalidComponent") === 0);
            outerFacet.destroy();
            this.verifyComponentDestroyed(cmp);
        }
    },

    /**
     * Verify customized destroy handler gets called when component gets destroyed.
     */
    testCustomizedDestroyHanlder : {
        test : function(cmp) {
                component = cmp.find("cmpWithDestroyHandlerWrapper");
                component.destroy(false);

                $A.test.assertTrue(cmp.get("v.cmpDestroyed"),
                        "Destroy handler didn't get called when component gets destroyed");
                $A.test.assertTrue(cmp.get("v.childCmpDestroyed"),
                        "Child component's Destroy handler didn't get called when parent component gets destroyed");
            }
     },

    /**
     * After a component is destroyed it's prototype is swapped with InvalidComponent to display error messages if
     * the user tries to execute furthur operations. Verify we get that error with the correct info.
     */
    testInvalidComponentError: {
        test: function(cmp) {
            $A.test.expectAuraError("Invalid component");
            var textCmp = cmp.find("textInOuterFacet");
            var globalId = textCmp.getGlobalId();
            $A.test.assertTrue(textCmp.toString().indexOf("InvalidComponent") === -1,
                    "Component should not be an InvalidComponent before being destroyed.");
            textCmp.destroy();
            $A.test.assertTrue(textCmp.toString().indexOf("InvalidComponent") === 0,
                    "Component prototype was not swapped with InvalidComponent after being destroyed.");
            textCmp.set("v.value", "New value");
            var errorMsg = $A.test.getAuraErrorMessage();
            this.verifyInvalidComponentErrorMessage(errorMsg, "set", "v.value,New value", "markup://aura:text", globalId);
        }
    },

    verifyInvalidComponentErrorMessage: function(msg, func, params, cmpFQN, globalId) {
        var index = 0;

        var opening = "Invalid component tried calling function [" + func + "]";
        var chunk = msg.substr(index, opening.length);
        $A.test.assertEquals(opening, chunk, "InvalidComponent error message did not display expected function info.");

        var paramsMsg = " with arguments [" + params + "]";
        index += opening.length;
        chunk = msg.substr(index, paramsMsg.length);
        $A.test.assertEquals(paramsMsg, chunk, "InvalidComponent error message did not display expected parameter info.");

        var cmpMsg = ", " + cmpFQN;
        index += paramsMsg.length;
        chunk = msg.substr(index, cmpMsg.length);
        $A.test.assertEquals(cmpMsg, chunk, "InvalidComponent error message did not display expected component name.");

        var globalIdMsg = " [" + globalId + "]";
        index += cmpMsg.length;
        chunk = msg.substr(index, globalIdMsg.length);
        $A.test.assertEquals(globalIdMsg, chunk, "InvalidComponent error message did not display expected globalId.");
    },

    verifyComponentDestroyed : function(cmp) {
        $A.test.assertUndefinedOrNull(cmp.find("outerFacet"));
        $A.test.assertUndefinedOrNull(cmp.find("textInOuterFacet"));
        $A.test.assertUndefinedOrNull(cmp.find("bullPen"));

        $A.test.assertUndefinedOrNull(cmp.find("innerFacet1"));
        $A.test.assertUndefinedOrNull(cmp.find("textInInnerFacet"));
        $A.test.assertUndefinedOrNull(cmp.find("players"));

        $A.test.assertUndefinedOrNull(cmp.find("innerFacet2"));
        $A.test.assertUndefinedOrNull(cmp.find("coach"));

        $A.test.assertEquals(0, cmp.find("team").getElement().childNodes.length);
    }
})