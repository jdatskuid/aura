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
package org.auraframework.components.test.java.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.auraframework.system.Annotations.AuraEnabled;
import org.auraframework.system.Annotations.Model;

@Model
public class test_InfiniteListCsccModel {

    private List<Map<String, Object>> items;

    private static Map<String, Object> fields = new HashMap<String, Object>() {
    	private static final long serialVersionUID = 4937664306513003195L;
	{
        put("isClosed", false);
    }};

    private static Map<String, Object> newRow(Integer index) {
    	@SuppressWarnings("unchecked")
		HashMap<String, Object> items =  (HashMap<String,Object>)((HashMap<String,Object>)fields).clone();
    	items.put("Id", "Server " + (index).toString());
    	return items;
    }
    
    // create a set of X items that adds a sequentially numbered id in the form: Server N
    public test_InfiniteListCsccModel() {
        items = new ArrayList<>();

        for (int i = 0; i < 3; i++) {
            items.add(newRow(i));
        }
    }

    @AuraEnabled
    public List<Map<String, Object>> getItems() {
        return items;
    }
}
