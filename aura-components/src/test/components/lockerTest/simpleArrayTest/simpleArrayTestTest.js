({
    /**
     * Note that this test file operates in system mode (objects are not Lockerized) so the tests delegate logic and
     * verification to the controller and helper files, which operate in user mode.
     */

    // LockerService not supported on IE
    // TODO(W-3674741, W-4446969): FF and LockerService disabled for iOS browser in 212
    browsers: ["-IE8", "-IE9", "-IE10", "-IE11", "-SAFARI", "-IPHONE", "-IPAD"],

    setUp: function(cmp) {
        cmp.set("v.testUtils", $A.test);
    },

    testFilteringOfArrayOfObjects: {
        test: function(cmp) {
            cmp.testFilteringOfArrayOfObjects();
        }
    },

    testArrayProperties: {
        test:function(cmp) {
            cmp.testArrayProperties();
        }
    },
    testArrayPop : {
        test: function(cmp) {
            cmp.testArrayPop();
        }
    },
    testArrayPush : {
        test: function(cmp) {
            cmp.testArrayPush();
        }
    },
    testArrayReverse : {
        test: function(cmp) {
            cmp.testArrayReverse();
        }
    },
    testArrayShift : {
        test: function(cmp) {
            cmp.testArrayShift();
        }
    },
    testArraySort : {
        test: function(cmp) {
            cmp.testArraySort();
        }
    },
    testArraySplice : {
        test: function(cmp) {
            cmp.testArraySplice();
        }
    },
    testArrayConcat : {
        test: function(cmp) {
            cmp.testArrayConcat();
        }
    },
    testArrayUnshift : {
        test: function(cmp) {
            cmp.testArrayUnshift();
        }
    },
    testArrayAccessorMethods : {
        test: function(cmp) {
            cmp.testArrayAccessorMethods();
        }
    },
    testArrayForEach : {
        test: function(cmp) {
            cmp.testArrayForEach();
        }
    },
    testArrayForIn : {
        test: function(cmp) {
            cmp.testArrayForIn();
        }
    },
    testArrayAssociativeArray: {
        test: function(cmp) {
            cmp.testArrayAssociativeArray();
        }
    },
    testArrayProxyTraps: {
        test: function(cmp) {
            cmp.testArrayProxyTraps();
        }
    }

})