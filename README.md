SEAD 2.0
========

Source repository for SEAD 2.0 C3P-R services. 

Steps to build:
---------------

Move the to root directory and execute following command.

mvn clean install -DskipTests
(Skipping tests will help as there's an intermittent test failure in sead-doi-service)

This should build all micro services.

Steps to deploy on Tomcat:
--------------------------

* Copy the following .war files from relevant target directories into TOMCAT_HOME/webapps.

sead-api.war
sead-mm.war
sead-pdt.war
va-workflow.war
metadata-gen.war

* Fix the endpoint URL's in following configuration files under webapps.

sead-mm/WEB-INF/classes/org/sead/matchmaker/default.properties
sead-pdt/WEB-INF/classes/org/seadpdt/util/default.properties
sead-api/WEB-INF/classes/org/sead/api/util/default.properties

* Start the server.

* Now the API should be accessible through the following URL.

http://host:port/sead-api/..

For more details on how to use the API, please refer to:
https://opensource.ncsa.illinois.edu/confluence/pages/viewpage.action?pageId=71139377
