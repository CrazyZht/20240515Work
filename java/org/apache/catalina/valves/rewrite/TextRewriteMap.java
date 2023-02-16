/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.catalina.valves.rewrite;

import org.apache.tomcat.util.file.ConfigFileLoader;
import org.apache.tomcat.util.res.StringManager;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

public class TextRewriteMap implements RewriteMap{

    protected static final StringManager sm = StringManager.getManager(TextRewriteMap.class);
    private final Map<String,String> map = new HashMap<>();

    public TextRewriteMap(String txtFilePath) {
        String line;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(ConfigFileLoader.getSource().getResource(txtFilePath).getInputStream()))) {
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#") || line.isEmpty()) {
                    //Ignore comment or empty lines
                    continue;
                }
                String[] keyValuePair = line.split(" ", 2);
                if (keyValuePair.length > 1) {
                    String key = keyValuePair[0];
                    String value = keyValuePair[1];
                    map.put(key, value);
                } else {
                    throw new IllegalArgumentException(sm.getString("rewriteValve.invalidLine", line));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("I/O exception: " + e.getMessage());
        }
    }

    @Override
    public String setParameters(String params) {
        throw new IllegalStateException();
    }

    @Override
    public void setParameters(String... params) {
        throw new IllegalStateException();
    }

    @Override
    public String lookup(String key) {
        return map.get(key);
    }
}
