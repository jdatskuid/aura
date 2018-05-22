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
    render: function outputNumberRender(cmp) {
        var span = this.superRender()[0];
        var f = cmp.get("v.format");
        var num = cmp.get("v.value");
        var currencyCode = cmp.get("v.currencyCode");
        var currencySymbol = cmp.get("v.currencySymbol") || currencyCode;
        var formatted;
        if (($A.util.isNumber(num) || $A.util.isString(num)) && !$A.util.isEmpty(num) && !isNaN(num)) {
            var hasFormat = !$A.util.isEmpty(f);
            if (hasFormat || currencySymbol) {
                var nf;
                try {
                    var symbols;
                    if (currencySymbol) {
                        symbols = {
                            currencyCode: currencyCode,
                            currency: currencySymbol,
                            decimalSeparator: $A.get("$Locale.decimal"),
                            groupingSeparator: $A.get("$Locale.grouping"),
                            zeroDigit: $A.get("$Locale.zero")
                        };
                    }
                    if (!hasFormat) {
                        f = $A.get("$Locale.currencyFormat");
                    }
                    nf = $A.localizationService.getNumberFormat(f, symbols);
                } catch (e) {
                    formatted = "Invalid format attribute";
                    $A.log(e);
                }
                if (nf) {
                    formatted = nf.format(num);
                }
            } else {
                formatted = $A.localizationService.formatCurrency(num);
            }
            span.textContent = span.innerText = formatted;
        }
        return span;
    },

    rerender: function outputNumberRerenderer(cmp) {
        if (cmp.isDirty("v.value") || cmp.isDirty("v.format") || cmp.isDirty("v.currencyCode") || cmp.isDirty("v.currencySymbol")) {
        	var formatted = '';
            var f = cmp.get("v.format");
            var val = cmp.get("v.value");
            var currencyCode = cmp.get("v.currencyCode");
            var currencySymbol = cmp.get("v.currencySymbol") || currencyCode;
            if (($A.util.isNumber(val) || $A.util.isString(val)) && !$A.util.isEmpty(val) && !isNaN(val)) {
                var hasFormat = !$A.util.isEmpty(f);
                if (hasFormat || currencySymbol) {
                    var nf;
                    try {
                        var symbols;
                        if (currencySymbol) {
                            symbols = {
                                currencyCode: currencyCode,
                                currency: currencySymbol,
                                decimalSeparator: $A.get("$Locale.decimal"),
                                groupingSeparator: $A.get("$Locale.grouping"),
                                zeroDigit: $A.get("$Locale.zero")
                            };
                        }
                        if (!hasFormat) {
                            f = $A.get("$Locale.currencyFormat");
                        }
                        nf = $A.localizationService.getNumberFormat(f, symbols);
                    } catch (e) {
                        formatted = "Invalid format attribute";
                        $A.log(e);
                    }
                    if (nf) {
                        formatted = nf.format(val);
                    }
                } else {
                    formatted = $A.localizationService.formatCurrency(val);
                }
            }
            var span = cmp.find("span");
            span.getElement().textContent = span.getElement().innerText = formatted;
        }
    }
 })// eslint-disable-line semi
