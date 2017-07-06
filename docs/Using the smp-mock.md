# Documentation of smp-mock server
### Introduction
The purpose of the program is to provide an easy way 
to test the NCP (national contact point application). 
This is done by POSTing a returnUrl to the NCP application. 
The returnUrl is used by the NCP application to post back 
the exam results. 

### The application.properties file
The application.properties file is the default configuration and 
it will be used unless otherwise specified.
The smp-mock application is a spring boot application, therefore
you can setup the application in the application.properties file, 
which is read from the classpath of the application. 

An example of the application.properties file: 

- server.port = 8080
- environment.url = https://dans-emrexws.kmd.dk
- environment.port = 8000
- return.url = http://localhost

The assumption is that the ncp is running on port 8000.

##### Short description of the fields.
- server.port - The port where the smp-mock application is supposed to start on.
- environment.url  - The URL of the NCP application.
- environment.port - The port of the NCP application. 
- return.url - The URL of the smp-mock application.

### How to use
Start the war file (java -jar &lt;file_name&gt;.war), and go to the /smp 
page and press the submit button. This should bring you to the 
NCP site. On the NCP select the courses, and proceed through the 
flow and POST the results back. This will bring you to the /onReturn 
site on the smp-mock application. Here you should be able to view the
received PDF og XML information.´

### Testing against prod and test environments
For testing against prod the following command can be used:

> java -jar -Dspring.profiles.active=prod &lt;file_name&gt;.war

For testing against the test environment use the following command

> java -jar -Dspring.profiles.active=test &lt;file_name&gt;.war

### Url
https://localhost:8080/smp
