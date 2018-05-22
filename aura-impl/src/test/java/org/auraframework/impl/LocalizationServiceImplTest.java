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
package org.auraframework.impl;

import javax.inject.Inject;
import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.ParseException;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import org.auraframework.impl.service.testdata.LocalizationServiceTestData;
import org.auraframework.service.LocalizationService;
import org.auraframework.test.util.AuraTestCase;
import org.auraframework.util.number.AuraNumberFormat;
import org.auraframework.util.test.annotation.UnAdaptableTest;
import org.junit.Test;

import com.ibm.icu.text.NumberFormat;

/**
 * Tests for LocalizationServiceImpl.
 */
public class LocalizationServiceImplTest extends AuraTestCase {

    @Inject
    public LocalizationService localizationService;

    /**
     * Tests to verify Date parser across different Locale
     */
    @UnAdaptableTest("Date format on SFDC handles differently than standalone Aura, need to investigate")
    @Test
    public void testDateTimeParserChangeLocale() throws Exception {
        // API: parseDate(String Date, Locale locale, TimeZone timeZone, int
        // DateStyle)
        // Locale: English -> German
        {
            String expectedDE = "Dienstag, 31. Dezember 2222";
            String inputEN = "Tuesday, December 31, 2222";
            Date dateEN = localizationService.parseDate(inputEN, Locale.ENGLISH, TimeZone.getTimeZone("PST"),
                    DateFormat.FULL);
            String actualEN = localizationService.formatDate(dateEN, Locale.GERMAN, TimeZone.getTimeZone("CEST"),
                    DateFormat.FULL);

            assertEquals("Failed to convert date from English to German locale", expectedDE, actualEN);
        }
        // Locale: English -> Simplified Chinese
        {
            String expectedZH = DateFormat.getDateInstance(1, Locale.SIMPLIFIED_CHINESE).format(
                    new Date(16730265600000L));
            String inputEN = "February 28, 2500";
            Date dateEN = localizationService.parseDate(inputEN, Locale.ENGLISH, TimeZone.getTimeZone("PST"),
                    DateFormat.LONG);
            String actualZH = localizationService.formatDate(dateEN, Locale.SIMPLIFIED_CHINESE,
                    TimeZone.getTimeZone("PST"), DateFormat.LONG);
            assertEquals("Failed to convert date from English to Simplified Chinese locale", expectedZH, actualZH);
        }
        // Locale: English -> Arabic
        {
            String expectedSA = DateFormat.getDateInstance(DateFormat.FULL, new Locale("ar", "SA")).format(
                    new Date(1298880000000L));
            String inputEN = "Monday, February 28, 2011";
            Date dateEN = localizationService.parseDate(inputEN, Locale.ENGLISH, TimeZone.getTimeZone("PST"),
                    DateFormat.FULL);
            String actualSA = localizationService.formatDate(dateEN, new Locale("ar", "SA"),
                    TimeZone.getTimeZone("PST"), DateFormat.FULL);
            assertEquals("Failed to convert date from English to Arabic locale", expectedSA, actualSA);
        }
    }

    @Test
    public void testDateTimeParserBorderCases() {
        for (String dt : LocalizationServiceTestData.PASS_DATE_STRINGS) {
            try {
                localizationService.parseDate(dt, Locale.ENGLISH, TimeZone.getTimeZone("PST"), DateFormat.LONG);
            } catch (Exception e) {
                fail("Failed to parse valid date \'" + dt + "\', error: " + e);
            }
        }
    }

    // TODO(W-1482880): Change how Exceptions are handled after bug is fixed. We
    // should just be catching the Exception
    // type we expect and letting the others be thrown. This will either be
    // ParseExceptions from AuraNumberFormat.parse(...) or
    // IllegalArgumentExceptions from DateServiceImpl.getDateTimeStyleConverter(...)
    // or DateServiceImpl.JodaDateConverter.parse(...)
    // same thing apply to any call of LocalizationService.parseDate(...), parseTime(...),
    // parseDateTime(...), formatDate(...), formatTime(...), formatDateTime(...)
    //TODO: W-2603913
    @UnAdaptableTest("Date format on SFDC handles differently than standalone Aura, need to investigate")
    @Test
    public void testDateTimeParserExceptions() throws IllegalArgumentException {
        // Test invalid date strings in format DateFormat.MEDIUM
        {
            for (String dt : LocalizationServiceTestData.FAIL_DATE_STRINGS_MEDIUM_FORMAT) {
                try {
                    localizationService.parseDate(dt, Locale.ENGLISH, TimeZone.getTimeZone("PST"), DateFormat.MEDIUM);
                    fail("No Exception thrown when trying to parse \'" + dt + "\' into Date");
                } catch (Exception e) {
                    // Expected
                }
            }
        }

        // Test invalid date strings in format DateFormat.SHORT
        {
            for (String dt : LocalizationServiceTestData.FAIL_DATE_STRINGS_SHORT_FORMAT) {
                try {
                    localizationService.parseDate(dt, Locale.ENGLISH, TimeZone.getTimeZone("PST"), DateFormat.SHORT);
                    fail("No Exception thrown when trying to parse \'" + dt + "\' into Date");
                } catch (Exception e) {
                    // Expected
                }
            }
        }

        // Test date in English to parser with German locale
        {
            String EN_DATE_STRING = "Tuesday, December 31, 2222";
            try {
                localizationService.parseDate(EN_DATE_STRING, Locale.GERMAN, TimeZone.getTimeZone("PST"),
                        DateFormat.FULL);
                fail("# Exception not thrown for date:" + EN_DATE_STRING);
            } catch (Exception e) {
                assertTrue("# Incorrect exception type!", ((e instanceof DateTimeParseException)));
            }
        }

        // Test time in US to parser with UK locale
        {
            String US_TIME_STRING = "6:57:10 PM PDT";
            try {
                localizationService.parseTimeToCalendar(US_TIME_STRING, Locale.UK, TimeZone.getTimeZone("GMT"),
                        DateFormat.FULL);
                fail("# Exception not thrown for date:" + US_TIME_STRING);
            } catch (Exception e) {
                assertTrue("# Incorrect exception type!", ((e instanceof DateTimeParseException)));
            }
        }

        // Test date time in English to parser with French locale
        {
            String US_DATE_TIME_STRING = "Apr 1, 2012 4:27:52 PM PDT";
            try {
                localizationService.parseDateTimeToCalendar(US_DATE_TIME_STRING, Locale.FRENCH,
                        TimeZone.getTimeZone("UTC"), DateFormat.MEDIUM, DateFormat.FULL);
                fail("# Exception not thrown for date:" + US_DATE_TIME_STRING);
            } catch (Exception e) {
                assertTrue("# Incorrect exception type!", ((e instanceof DateTimeParseException)));
            }
        }
    }

    // TODO(W-1482880): same as testDateTimeParserExceptions()
    @Test
    public void testParseTimeParserExceptions() throws ParseException {
        // Test invalid time strings
        {
            for (String t : LocalizationServiceTestData.FAIL_TIME_STRINGS) {
                try {
                    localizationService.parseTime(t, Locale.ENGLISH, TimeZone.getTimeZone("PST"), DateFormat.FULL);
                    fail("No Exception thrown when trying to parse \'" + t + "\' into Time");
                } catch (Exception e) {
                    // Expected
                    assertTrue("# Incorrect exception type!", ((e instanceof DateTimeParseException)));
                }
            }
        }
    }

    @Test
    public void testParsersWithNullArgs() throws ParseException {
        {
            assertNull("parseDate(null) did not return 'null'", localizationService.parseDate(null));
            assertNull("parseTime(null) did not return 'null'", localizationService.parseTime(null));
            assertNull("parseDate(null, null) did not return 'null'", localizationService.parseDate(null, null));
            assertNull("parseTimeToCalendar(null, null, null -1) did not return 'null'",
                    localizationService.parseTimeToCalendar(null, null, null, -1));
            assertNull("parseDateTime(null) did not return 'null'", localizationService.parseDateTime(null));
            assertNull("parseDateTimeToCalendar(null, null, null, -1, -1) did not return 'null'",
                    localizationService.parseDateTimeToCalendar(null, null, null, -1, -1));
            assertNull("parseCurrency(null) did not return 'null'", localizationService.parseCurrency(null));

            try {
                localizationService.parseInt(null);
                fail("parseInt(null) did not throw an exception");
            } catch (Exception e) {
                checkExceptionContains(e, ParseException.class, "Parameter 'number' was null");
            }

            try {
                localizationService.parseLong(null);
                fail("parseLong(null) did not throw an exception");
            } catch (Exception e) {
                checkExceptionContains(e, ParseException.class, "Parameter 'number' was null");
            }

            try {
                localizationService.parseFloat(null);
                fail("parseFloat(null) did not throw an exception");
            } catch (Exception e) {
                checkExceptionContains(e, ParseException.class, "Parameter 'number' was null");
            }

            try {
                localizationService.parseDouble(null);
                fail("parseDouble(null) did not throw an exception");
            } catch (Exception e) {
                checkExceptionContains(e, ParseException.class, "Parameter 'number' was null");
            }

            try {
                localizationService.parsePercent(null);
                fail("parsePercent(null) did not throw an exception");
            } catch (Exception e) {
                checkExceptionContains(e, ParseException.class, "Parameter 'percent' was null");
            }
        }
    }

    @Test
    public void testParsersWithEmptyString() throws ParseException {
        {
            try {
                localizationService.parseCurrency("");
                fail("parseCurrency(\"\") did not throw an exception");
            } catch (Exception e) {
                checkExceptionContains(e, ParseException.class, "Unparseable number: \"\"");
            }

            try {
                localizationService.parseDateTimeToCalendar("", null, null, -1, -1);
                fail("parseDateTimeToCalendar(\"\", null, null, -1, -1) did not throw an exception");
            } catch (Exception e) {
                // TODO(W-1482880): same as testDateTimeParserExceptions()
                checkExceptionContains(e, IllegalArgumentException.class, "Both date style and time style cannot be none");
            }

            try {
                localizationService.parseDateTime("");
                fail("parseDateTime(\"\") did not throw an exception");
            } catch (Exception e) {
                // TODO(W-1482880): same as testDateTimeParserExceptions()
                checkExceptionContains(e, DateTimeParseException.class, "Text '' could not be parsed at index 0");
            }

            try {
                localizationService.parseTimeToCalendar("", null, null, -1);
                fail("parseTimeToCalendar(\"\", null, null, -1) did not throw an exception");
            } catch (Exception e) {
                // TODO(W-1482880): same as testDateTimeParserExceptions()
                checkExceptionContains(e, IllegalArgumentException.class, "Both date style and time style cannot be none");
            }

            try {
                localizationService.parseTime("");
                fail("parseTime(\"\") did not throw an exception");
            } catch (Exception e) {
                // TODO(W-1482880): same as testDateTimeParserExceptions()
                checkExceptionContains(e, DateTimeParseException.class, "Text '' could not be parsed at index 0");
            }

            try {
                localizationService.parseDate("");
                fail("parseDate(\"\") did not throw an exception");
            } catch (Exception e) {
                // TODO(W-1482880): same as testDateTimeParserExceptions()
                checkExceptionContains(e, DateTimeParseException.class, "Text '' could not be parsed at index 0");
            }

            try {
                localizationService.parseInt("");
                fail("parseInt(\"\") did not throw an exception");
            } catch (Exception e) {
                checkExceptionContains(e, ParseException.class, "Unparseable number: \"\"");
            }

            try {
                localizationService.parseLong("");
                fail("parseLong(\"\") did not throw an exception");
            } catch (Exception e) {
                checkExceptionContains(e, ParseException.class, "Unparseable number: \"\"");
            }

            try {
                localizationService.parseFloat("");
                fail("parseFloat(\"\") did not throw an exception");
            } catch (Exception e) {
                checkExceptionContains(e, ParseException.class, "Unparseable number: \"\"");
            }

            try {
                localizationService.parseDouble("");
                fail("parseDouble(\"\") did not throw an exception");
            } catch (Exception e) {
                checkExceptionContains(e, ParseException.class, "Unparseable number: \"\"");
            }

            try {
                localizationService.parsePercent("");
                fail("parsePercent(\"\") did not throw an exception");
            } catch (Exception e) {
                checkExceptionContains(e, ParseException.class, "Unparseable number: \"\"");
            }
        }
    }

    @Test
    public void testLeniencyCurrencyParser() throws Exception {
        // API: parseCurrency(String currency, Locale l)
        // Locale: US $ -> UK £
        {
            String expectedGB = "£100,000.0000000009";
            String inputEN = "$100,000.0000000009";
            BigDecimal moneyEN = localizationService.parseCurrency(inputEN, Locale.US);
            String actualGB = localizationService.formatCurrency(moneyEN, Locale.UK, 10, 10);
            assertEquals(expectedGB, actualGB);
        }
        // Locale US -> France
        {
            String expectedFR = NumberFormat.getCurrencyInstance(Locale.FRANCE).format(100000000);
            String inputEN = "$100,000,000.000";
            BigDecimal moneyEN = localizationService.parseCurrency(inputEN, Locale.US);
            String actualFR = localizationService.formatCurrency(moneyEN, Locale.FRANCE);
            assertEquals(expectedFR, actualFR);
        }
        // Locale US -> China
        {
            String expectedCN = NumberFormat.getCurrencyInstance(Locale.CHINA).format(0.09);
            String inputEN = "$0.09";
            BigDecimal moneyEN = localizationService.parseCurrency(inputEN, Locale.US);
            String actualCN = localizationService.formatCurrency(moneyEN, Locale.CHINA, 2, 2);
            assertEquals(expectedCN, actualCN);
        }
        // Locale US -> Japan
        {
            String expectedJP = NumberFormat.getCurrencyInstance(Locale.JAPAN).format(9.0d);
            String inputEN = "$9.00";
            BigDecimal moneyEN = localizationService.parseCurrency(inputEN, Locale.US);
            String actualJP = localizationService.formatCurrency(moneyEN, Locale.JAPAN);
            assertEquals(expectedJP, actualJP);
        }
        // Locale US -> Arabic
        {
            String expectedSA = NumberFormat.getCurrencyInstance(new Locale("ar", "SA")).format(100001100.212d);
            String inputEN = "$100,001,100.212";
            BigDecimal moneyEN = localizationService.parseCurrency(inputEN, Locale.US);
            String actualSA = localizationService.formatCurrency(moneyEN, new Locale("ar", "SA"));
            assertEquals(expectedSA, actualSA);
        }
    }

    @Test
    public void testPercentParserExceptions() throws ParseException {
        // Test invalid Percent strings
        {
            for (String per : LocalizationServiceTestData.FAIL_PERCENT_STRINGS) {
                try {
                    localizationService.parsePercent(per, Locale.FRENCH);
                    fail("No Exception thrown when trying to parse \'" + per + "\' into Percentage");
                } catch (Exception e) {
                    // Expected error from AuraNumberformat.parse(...)
                    checkExceptionFull(e, ParseException.class, getErrorMsg(per));
                }
            }
        }
    }

    @Test
    public void testCurrencyParserExceptions() throws ParseException {
        // Test invalid Currency strings
        {
            for (String curr : LocalizationServiceTestData.FAIL_CURRENCY_STRINGS) {
                try {
                    localizationService.parseCurrency(curr, Locale.US);
                    fail("No Exception thrown when trying to parse \'" + curr + "\' into Currency");
                } catch (Exception e) {
                    // Expected error from AuraNumberformat.parse(...)
                    checkExceptionFull(e, ParseException.class, getErrorMsg(curr));
                }
            }
        }

        // Currency in China Yuan to parser with US locale
        {
            String inputCN = NumberFormat.getCurrencyInstance(Locale.CHINA).format(0);
            try {
                localizationService.parseCurrency(inputCN, Locale.UK);
                fail("# Exception not thrown for currency:" + inputCN);
            } catch (Exception e) {
                // Expected error from AuraNumberformat.parse(...)
                checkExceptionFull(e, ParseException.class, getErrorMsg(inputCN));
            }
        }

        // Currency in China Yuan to parser with Brazil locale
        {
            String inputCN = NumberFormat.getCurrencyInstance(Locale.CHINA).format(-1);
            try {
                localizationService.parseCurrency(inputCN, new Locale("pt", "BR"));
                fail("# Exception not thrown for currency:" + inputCN);
            } catch (Exception e) {
                // Expected error from AuraNumberformat.parse(...)
                checkExceptionFull(e, ParseException.class, getErrorMsg(inputCN));
            }
        }
    }

    @Test
    public void testIntParserExceptions() throws ParseException {
        // Test invalid Int strings
        {
            for (String num : LocalizationServiceTestData.FAIL_INT_STRINGS) {
                try {
                    localizationService.parseInt(num);
                    fail("No Exception thrown when trying to parse \'" + num + "\' into int");
                } catch (Exception e) {
                    // Expected error from AuraNumberformat.parse(...)
                    checkExceptionFull(e, ParseException.class, getErrorMsg(num));
                }
            }
        }
    }

    @Test
    public void testLongParserExceptions() throws ParseException {
        // Test invalid Long strings
        {
            for (String num : LocalizationServiceTestData.FAIL_LONG_STRINGS) {
                try {
                    localizationService.parseLong(num);
                    fail("No Exception thrown when trying to parse \'" + num + "\' into long");
                } catch (Exception e) {
                    // Expected error from AuraNumberformat.parse(...)
                    checkExceptionFull(e, ParseException.class, getErrorMsg(num));
                }
            }
        }
    }

    @Test
    public void testFloatParserExceptions() throws ParseException {
        // Test invalid Float strings
        {
            for (String num : LocalizationServiceTestData.FAIL_FLOAT_STRINGS) {
                try {
                    localizationService.parseFloat(num);
                    fail("No Exception thrown when trying to parse \'" + num + "\' into float");
                } catch (Exception e) {
                    // Expected error from AuraNumberformat.parse(...)
                    checkExceptionFull(e, ParseException.class, getErrorMsg(num));
                }
            }
        }
    }

    @Test
    public void testDoubleParserExceptions() throws ParseException {
        // Test invalid Double strings
        {
            for (String num : LocalizationServiceTestData.FAIL_DOUBLE_STRINGS) {
                try {
                    localizationService.parseDouble(num);
                    fail("No Exception thrown when trying to parse \'" + num + "\' into double");
                } catch (Exception e) {
                    // Expected error from AuraNumberformat.parse(...)
                    checkExceptionFull(e, ParseException.class, getErrorMsg(num));
                }
            }
        }
    }

    @Test
    public void testStrictNumberParsing() throws ParseException {
        NumberFormat nf = null;
        Map<Locale, String[]> strictParserTestNumberStrings = new HashMap<>();
        strictParserTestNumberStrings.put(Locale.ENGLISH, new String[] { "100.200,300", "1 1", "1.1.1.1" });
        strictParserTestNumberStrings.put(Locale.FRANCE, new String[] { "1 1 1 1", "1.1.1", "00. 000 000",
        "100,200.300" });
        strictParserTestNumberStrings.put(Locale.CHINESE, new String[] { "1, 0,0", "100'2" });
        strictParserTestNumberStrings.put(Locale.GERMAN, new String[] { "100,200,300.456", "0.123,456,789",
        "111.111,111.111" });

        for (Locale locale : strictParserTestNumberStrings.keySet()) {
            for (String num : strictParserTestNumberStrings.get(locale)) {
                try {
                    nf = NumberFormat.getInstance(locale);
                    AuraNumberFormat.parseStrict(num, nf);
                    fail("No Exception thrown for value:" + num + " and locale:" + locale.getDisplayName());
                } catch (Exception e) {
                    // Expected error from AuraNumberformat.parse(...)
                    checkExceptionFull(e, ParseException.class, getErrorMsg(num));
                }
            }
        }
    }

    /**
     * Test decimal parsing with delimiters in incorrect places.
     */
    @Test
    public void testBigNumberParsing() {
        Map<Locale, String[]> testNumbers = new HashMap<>();
        Map<Locale, String[]> testNumbersExpected = new HashMap<>();

        testNumbers.put(Locale.ENGLISH, new String[] { "1,23", "12,34", "1,23.45", "1,23,45.67", "1,234" });
        testNumbersExpected.put(Locale.ENGLISH, new String[] { "123", "1234", "123.45", "12345.67", "1234" });


        testNumbers.put(Locale.FRANCE, new String[] { "1.23", "12.34", "1.23,45", "1.23.45,67", "1.234" });
        testNumbersExpected.put(Locale.FRANCE, new String[] { "123", "1234", "123.45", "12345.67", "1234" });

        doBigNumberParsingTest(testNumbers, testNumbersExpected, false);
    }

    /**
     * Test decimal parsing with delimiters and passing the strict flag. Should get exceptions thrown because
     * delimiters are in the wrong place.
     */
    @Test
    public void testStrictBigNumberParsing() {
        Map<Locale, String[]> testNumbers = new HashMap<>();

        testNumbers.put(Locale.ENGLISH, new String[] { "1,23", "12,34", "1,23.45", "1,23,45.67" });
        testNumbers.put(Locale.FRANCE, new String[] { "1.23", "12.34", "1.23,45", "1.23.45,67", });

        // expect errors to be thrown.
        doBigNumberParsingTest(testNumbers, null, true);
    }

    private void doBigNumberParsingTest(Map<Locale, String[]> testNumbers,
            Map<Locale, String[]> testNumbersExpected, boolean isStrict) {
        LocalizationServiceImpl ls = new LocalizationServiceImpl();
        String[] testNums = {};
        String[] testNumsExpected = {};

        for (Locale locale : testNumbers.keySet()) {
            testNums = testNumbers.get(locale);
            if (testNumbersExpected != null) {
                testNumsExpected = testNumbersExpected.get(locale);
            } else {
                testNumsExpected = new String[testNums.length];
            }

            for (int i=0; i<testNums.length; i++) {
                String num = testNums[i];
                String expected = testNumsExpected[i];

                try {
                    String actual = ls.parseBigDecimal(num, locale, isStrict).toString();
                    if (isStrict) {
                        fail("Should have gotten an exception for: " + locale + " - " + num + " - " + isStrict);
                    } else {
                        assertEquals("Number is not formated correctly for " + locale, expected, actual);
                    }
                } catch (Exception e) {
                    if (isStrict) {
                        checkExceptionFull(e, ParseException.class, getErrorMsg(num));
                    } else {
                        fail("Should NOT have gotten an exception for: " + locale + " - " + num + " - " + isStrict);
                    }
                }
            }
        }
    }

    /*
     * return error message we are expecting, throw by parse() in AuraNumberFormat.java
     */
    public String getErrorMsg(String num) {
        return "Unparseable number: \""+num+"\"";
    }
}
