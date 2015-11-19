/*
 *
 * Copyright 2015 The Trustees of Indiana University
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *
 * @author charmadu@umail.iu.edu
 */

package org.sead.va.dataone.util;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;

public class Constants {

    public static String mongoHost;
    public static int mongoPort;

    public static String dataonDbName;

    static {
        try {
            loadConfigurations();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void loadConfigurations() throws IOException {
        InputStream inputStream =
                Constants.class.getResourceAsStream("./default.properties");

        StringWriter writer = new StringWriter();
        IOUtils.copy(inputStream, writer);

        String result = writer.toString();
        String[] pairs = result.trim().split(
                "\n|\\=");


        for (int i = 0; i + 1 < pairs.length;) {
            String name = pairs[i++].trim();
            String value = pairs[i++].trim();

            if(name.equals("mongo.host")){
                mongoHost = value;
            }
            if(name.equals("mongo.port")){
                mongoPort = Integer.parseInt(value);
            }
            if(name.equals("dataone.db.name")){
                dataonDbName = value;
            }
        }
    }
}