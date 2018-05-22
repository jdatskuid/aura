({
    inspectMethodParams : function(cmp, event) {
        var args = event.getParam("arguments");
        var testUtils = args.testUtils;

        testUtils.assertEquals(true, args.booleanParam, "Access in non-locker: Failed to get expected value from Boolean.");
        testUtils.assertEquals("1888-08-08", args.dateParam, "Access in non-locker: Failed to get expected value from Date.");
        testUtils.assertEquals("1888-08-08T08:08:08.888Z", args.dateTimeParam, "Access in non-locker: Failed to get expected value from DateTime.");
        testUtils.assertEquals(88.88, args.decimalParam, "Access in non-locker: Failed to get expected value from Decimal.");
        testUtils.assertEquals(5/7, args.doubleParam, "Access in non-locker: Failed to get expected value from Double.");
        testUtils.assertEquals(8888, args.integerParam, "Access in non-locker: Failed to get expected value from Integer.");
        testUtils.assertEquals(Infinity, args.longParam, "Access in non-locker: Failed to get expected value from Long.");
        testUtils.assertEquals("GoldenState", args.stringParam, "Access in non-locker: Failed to get expected value from String.");

        var stringArrayParam = args.stringArrayParam;
        testUtils.assertEquals(3, stringArrayParam.length);
        testUtils.assertEquals("\"ONE\"", stringArrayParam[0], "#1: Access in same locker: Failed to get expected value from String[].");
        testUtils.assertEquals("!@#$%^&*()", stringArrayParam[1], "#2: Access in same locker: Failed to get expected value from String[].");
        testUtils.assertEquals("Äéįœû", stringArrayParam[2], "#3: Access in same locker: Failed to get expected value from String[].");

        var objectParam = args.objectParam;
        testUtils.assertEquals(2, Object.keys(objectParam).length);
        testUtils.assertEquals("Warriors", objectParam["GoldenState"], "#1: Access in same locker: Failed to get expected value from Object.");
        testUtils.assertEquals("2017", objectParam["arrayEntry"][1], "#2: Access in same locker: Failed to get expected value from Object.");

        var caller = args.cmpParam;
        var listParam = args.listParam;
        testUtils.assertEquals(5, listParam.length);
        testUtils.assertEquals(2, listParam[1], "#1: Access in non-locker: Failed to get expected value from List.");
        testUtils.assertEquals("foo", listParam[3][0], "#2: Access in non-locker: Failed to get expected value from List.");
        testUtils.assertEquals("bar", listParam[3][1], "#3: Access in non-locker: Failed to get expected value from List.");
        testUtils.assertEquals(caller, listParam[4], "#4: Access in non-locker: Failed to get expected value from List.");

        var mapParam = args.mapParam;
        testUtils.assertEquals(3, Object.keys(mapParam).length);
        testUtils.assertEquals(88, mapParam.b, "#1: Access in non-locker: Failed to get expected value from Map.");
        testUtils.assertEquals(caller, mapParam.c, "#2: Access in non-locker: Failed to get expected value from Map.");

        var setParam = args.setParam;
        testUtils.assertEquals(7, setParam.length);
        testUtils.assertEquals("red", setParam[0], "#1: Access in non-locker: Failed to get expected value from Set.");
        testUtils.assertEquals("green", setParam[4], "#2: Access in non-locker: Failed to get expected value from Set.");
        testUtils.assertEquals(caller, setParam[6], "#3: Access in same locker: Failed to get expected value from Set.");

        testUtils.assertTrue(caller.toString().indexOf("SecureComponent") === -1, "Expected raw component in non-lockerized component.");
        testUtils.assertTrue(objectParam.arrayEntry[2].toString().indexOf("SecureComponent") === -1, "Expected raw component in non-lockerized component.");
        testUtils.assertTrue(listParam[4].toString().indexOf("SecureComponent") === -1, "Expected raw component in non-lockerized component.");
        testUtils.assertTrue(mapParam.c.toString().indexOf("SecureComponent") === -1, "Expected raw component in non-lockerized component.");
        testUtils.assertTrue(setParam[6].toString().indexOf("SecureComponent") === -1, "Expected raw component in non-lockerized component.");

        cmp.set("v.methodCalled", true);

        // Just for visual verification
        var span = document.createElement("span");
        span.textContent = JSON.stringify(args.stringParam);
        args.resultHolder.appendChild(span);

        span = document.createElement("span");
        span.textContent = JSON.stringify(listParam);
        args.resultHolder.appendChild(span);

        span = document.createElement("span");
        span.textContent = JSON.stringify(objectParam);
        args.resultHolder.appendChild(span);
    }
})