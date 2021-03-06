package org.seadva.metadatagen.metagen.impl;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.seadva.metadatagen.metagen.BaseMetadataGen;
import org.seadva.metadatagen.util.Constants;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Properties;


public class OREMetadataGen extends BaseMetadataGen {

    private static final Logger log = Logger.getLogger(OREMetadataGen.class);

    public static String CREATOR = "Creator";
    public static String ABSTRACT = "Abstract";
    public static String TITLE = "Title";
    public static String DESCRIBES = "describes";
    public static String AGGREGATES = "aggregates";
    public static String SIMILAR_TO = "similarTo";
    public static String HAS_PART = "Has Part";
    public static String IDENTIFIER = "Identifier";
    public static String SIZE = "Size";

    private String errorMsg = null;
    private boolean skipValidation;

    public OREMetadataGen() {
        Properties properties = new Properties();
        try {
            properties.load(OREMetadataGen.class.getResourceAsStream("./../../log4j.properties"));
        } catch (IOException e) {
            log.error("Could not load properties file");
        }
        PropertyConfigurator.configure(properties);
        errorMsg = "";
        skipValidation = false;
    }

    public boolean getSkipValidation() {
        return skipValidation;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    @Override
    public String generateMetadata(String id){

        String result = "";
        return result;

    }

    public boolean hasValidOREMetadata(String oreString) {

        // Checking whether OREMap request contains metadata needed - check for independent submissions
        try {
            if(!hasMinimalMetadata(oreString) || !hasValidDownloadLinks(oreString)){
                return false;
            }
        } catch (JSONException e) {
            e.printStackTrace();
            log.error("Error while validating ORE");
            this.errorMsg = "Error while validating ORE";
            return  false;
        }

        return true;
    }

    private boolean hasMinimalMetadata(String oreString) throws JSONException{
        boolean validated = true;

        // Checking whether ORE metadata includes Creator, Title and Abstract
        // These are the metadata required for SDA-Agent
        JSONObject oreObject = null;
        try {
            oreObject = new JSONObject(oreString);
        } catch (JSONException e) {
            log.error("ORE is not a valid JSON object");
            this.errorMsg = "ORE is not a valid JSON object";
            return false;
        }

        if(!oreObject.has(DESCRIBES)) {
            log.error("ORE Does not have '" + DESCRIBES + "'");
            this.errorMsg = "ORE Does not have '" + DESCRIBES + "'";
            return false;
        }

        if(!(oreObject.get(DESCRIBES) instanceof JSONObject)) {
            log.error("ORE Does not have '" + DESCRIBES + "'");
            this.errorMsg = "ORE Does not have a valid JSON object for '" + DESCRIBES + "'";
            return false;
        }

        JSONObject object = (JSONObject)oreObject.get(DESCRIBES);

        if(!object.has(CREATOR) || !nullCheck(object.get(CREATOR)) ) {
            log.error("ORE request does not contain value for '" + CREATOR + "' field.");
            this.errorMsg = "ORE request does not contain value for '" + CREATOR + "' field.";
            return false;
        }
        if(!object.has(TITLE) || !nullCheck(object.get(TITLE)) ) {
            log.error("ORE request does not contain value for '" + TITLE + "' field.");
            this.errorMsg = "ORE request does not contain value for '" + TITLE + "' field.";
            return false;
        }
        if(!object.has(ABSTRACT) || !nullCheck(object.get(ABSTRACT)) ) {
            log.error("ORE request does not contain value for '" + ABSTRACT + "' field.");
            this.errorMsg = "ORE request does not contain value for '" + ABSTRACT + "' field.";
            return false;
        }

        return validated;
    }

    private boolean hasValidDownloadLinks(String oreString) throws JSONException {
        boolean validated = true;
        JSONObject oreObject = new JSONObject(oreString);
        JSONObject describes = (JSONObject)oreObject.get(DESCRIBES);

        if(!describes.has(AGGREGATES)) {
            log.error("ORE Does not have '" + AGGREGATES + "'");
            this.errorMsg = "ORE Does not have '" + AGGREGATES + "'";
            return false;
        }

        if(!(describes.get(AGGREGATES) instanceof JSONArray)) {
            log.error("ORE Does not have a valid JSON Array object for '" + AGGREGATES + "'");
            this.errorMsg = "ORE Does not have a valid JSON Array object for '" + AGGREGATES + "'";
            return false;
        }

        JSONArray aggregates = (JSONArray)describes.get(AGGREGATES);

        for(int i=0 ; i < aggregates.length() ; i++) {
            Object part = aggregates.get(i);

            if(!(part instanceof JSONObject)) {
                log.error("ORE '" + AGGREGATES + "' has invalid JSON Object");
                this.errorMsg = "ORE '" + AGGREGATES + "' has invalid JSON Object";
                return false;
            }

            JSONObject partObject = (JSONObject)part;

            if(!partObject.has(IDENTIFIER) || !(partObject.get(IDENTIFIER) instanceof String)) {
                log.error("ORE '" + AGGREGATES + "' has Object with invalid '" + IDENTIFIER + "'");
                this.errorMsg = "ORE '" + AGGREGATES + "' has Object with invalid '" + IDENTIFIER + "'";
                return false;
            }

            String identifier = (String)partObject.get(IDENTIFIER);

            double size = -1;

            if(partObject.has(SIZE) ) {
                if(partObject.get(SIZE) instanceof String) {
                    size = Double.parseDouble((String) partObject.get(SIZE));
                } else if(partObject.get(SIZE) instanceof Integer) {
                    size = (double)partObject.getInt(SIZE);
                } else if(partObject.get(SIZE) instanceof Double) {
                    size = partObject.getDouble(SIZE);
                } else if(partObject.get(SIZE) instanceof Long) {
                    size = (double)partObject.getLong(SIZE);
                }
            }

            if(size > 0 && Constants.validateDownloadLinks && partObject.has(SIMILAR_TO)) {
                if(!validateDownloadLink(partObject.get(SIMILAR_TO), size)) {
                    log.error("ORE has invalid download link to file with identifier : '" + identifier + "' ");
                    this.errorMsg = "ORE has invalid download link to file with identifier : '" + identifier + "' ";
                    return false;
                } else if(this.skipValidation == true) {
                    log.error("ORE has a file download link that is not working at the moment : '" + partObject.get(SIMILAR_TO) + "' ");
                    this.errorMsg = "ORE has a file download link that is not working at the moment : '" + partObject.get(SIMILAR_TO) + "' ";
                    break;
                }
            }
        }

        return validated;
    }

    private boolean validateDownloadLink(Object downloadLink, double size) {
        boolean validated = true;

        if(!(downloadLink instanceof String))
            return false;

        if(!head((String)downloadLink, size))
            return false;

        return validated;
    }

    private boolean nullCheck(Object object) throws JSONException {
        boolean isNotNull = false;

        if(object instanceof String ){
            if(object != null || !object.equals("")) {
                isNotNull = true;
            }
        } else if(object instanceof JSONArray){
            JSONArray list = (JSONArray)object;
            for(int i = 0 ; i < list.length() ; i++){
                Object arrayItem = list.get(i);
                if(arrayItem != null && !arrayItem.equals("")){
                    isNotNull = true;
                    break;
                }
            }
        } else {
            log.error("Unable to validate ORE since " + object + " is not either a String or an Array");
        }

        return isNotNull;
    }

    private boolean head(String url, double fileSize) {

        long startTime = 0;
        long endTime = 0;
        long duration = 0;

        HttpURLConnection connection = null;
        try {
            startTime = System.currentTimeMillis();
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(60000);
            connection.setReadTimeout(60000);
            connection.setRequestMethod("HEAD");
            int responseCode = connection.getResponseCode();
            endTime = System.currentTimeMillis();
            duration = endTime - startTime;
            if (responseCode == 200) {
                //log.info("SUCCESS HEAD " + fileSize + " " + duration + " " + url);
                return true;
            }
            //log.error("FAIL HEAD " + fileSize + " " + duration + " " + url + " !200");
        } catch (IOException e) {
            //log.error("FAIL HEAD " + fileSize + " " + duration + " " + url + " Exception:" + e.getClass());
        } finally {
            if (connection != null)
                connection.disconnect();
        }

        InputStream inputStream = null;
        try {
            startTime = System.currentTimeMillis();
            URL urlCon = new URL(url);
            URLConnection con = urlCon.openConnection();
            con.setConnectTimeout(60000);
            inputStream = con.getInputStream();

            // Read in the first byte from the url.
            int size = 1;
            byte[] data = new byte[size];
            int length = inputStream.read(data);
            endTime = System.currentTimeMillis();
            duration = endTime - startTime;
            if (length == 1) {
                //log.info("SUCCESS GET " + fileSize + " " + duration + " " + url);
                return true;
            } else {
                //log.error("FAIL GET " + fileSize + " " + duration + " " + url + " size!=1");
                return false;
            }
        } catch (IOException e) {
            if (e instanceof SocketTimeoutException) {
                this.skipValidation = true;
                //log.error("FAIL GET " + fileSize + " " + duration + " " + url + " Trivial_Exception" +  e.getMessage());
                return true;
            }
            //log.error("FAIL GET " + fileSize + " " + duration + " " + url + " Exception" +  e.getMessage());
            return false;
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }
}
