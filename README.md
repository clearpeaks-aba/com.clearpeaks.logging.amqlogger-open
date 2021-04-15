# com.clearpeaks.logging.amqlogger-open

This plugin adds a custom log4j appender that, for certain KNIME log messages, sends an XML message into an AMQ queue (using Apache Qpid JMS libraries and AMQP protocol). 
It is intended to be used for auditing purposes because events are sent:
  
1. when a KNIME node is starting its execution (“EXECUTING” event).

2. when a KNIME node has finished execution. Actually when a KNIME node finished execution 3 events are sent:

    2a. the first event is related to whether the node executed successfully or not. Therefore there are 2 options:

        2a1. “EXECUTED” event – indicates node executed successfully

        2a2. ”ERROR” event – indicate node execution had an error (the error message is also included in the event)

    2b. the second event is related to lineage of the executed node. This is called an “INPUTPORTS” event and it indicates the ids of the nodes that the current nodes has dependencies on.

    2c. the last event is related to the relevant parameters used to execute the node. This is called a “PARAMETERS” event; which parameters are considered relevant can be controlled by a configuration file. This event also contains flow variables.
	
## Using the plugin

Note this plugin has a dependancy on another plugin - com.knime.logging.extended (this other plugin is in charge of adding the KNIME logs for the INPUTPORTS and PARAMETERS events, so if the com.knime.logging.extended plugin is not used, the INPUTPORTS and PARAMETERS events will not be generated).

For convenience the jars folder in this repo contains the jars of both the com.clearpeaks.logging.amqlogger and the com.knime.logging.extended plugins.

Therefore, if you only want to use the plugins (and not modify the source code), you just need to download these 2 jars and do the following simple steps:

1. Add the com.clearpeaks.logging.amqlogger[version].jar and the com.knime.logging.extended[version].jar) in the dropins folder of the KNIME installation

2. Create a jndi.properties which contains the AMQ URL and queue name. The content of the file looks like:
```
java.naming.factory.initial = org.apache.qpid.jms.jndi.JmsInitialContextFactory
connectionfactory.qpidConnectionFactory = amqp://localhost:5672
queue.amqQueue = knimeaudit
```

3. Create a log4j3.xml (you can use as base the log4j3.xml you will find inside the .metadata/knime folder in any KNIME workspace). Make sure you add the definition of the amqAppender and modify accordingly the path to the jndi.properties and, if needed, the interesting keys (there are the names of the parameters that would be included in the "PARAMETERS" event)
```
    <appender name="amqAppender" class="com.clearpeaks.logging.amqlogger.AMQLogger">
        <param name="propertiesFilePath" value="/home/omartinez/knime_4.2.1/jndi.properties"/>
        <param name="interestingKeys" value="username,parameterName,DataURL,url,filename,file_location,user,host,port,sql_statement,credentials,selectedType,schema,table,path_or_url,authenticationmethod,useworkflowcredentials,workflowcredentials,json.location,outputLocation,fileUrl,folder,server-address,database_name,location,principal,location,path,FileURL,filesystem,knime_filesystem,knime_mountpoint_filesystem,pmml.reader.file,FileLocationURL,PMMLWriterFile,ARFFoutput,URIDataValue,Schema,TableName,Constant URI,Use constant URI,URI column"/>
        <param name="timeZone" value="CET"/>
        <layout class="com.clearpeaks.logging.amqlogger.CustomLogLayout">	
        </layout>
    </appender>
```

4. Also on log4j3.xml add the reference to the amqAppender at the bottom of the file in the "root" section.
```
    <appender-ref ref="amqAppender" />
```

5.Add the following lines at the bottom of the knime.ini to configure the plugins and modify the path to the log4f3.xml file:
```
-Dlog4j.configuration=/home/omartinez/knime_4.2.1/log4j3.xml
-Dcom.knime.logging.extended.enabled=true
```

6. Run the KNIME process and enjoy of an audited KNIME

Note auditing makes sense when the KNIME application is NOT started by the users, ie in KNIME Executors. If the users are the ones starting the KNIME application they may opt for not specifying the required parameters on the knime.ini to activate the plugins, which then kills the purpose of auditing. Therefore the scope of this auditing is restricted to KNIME Executors; unless some mechanism is used that would ensure that KNIME AP is started for sure with fixed configuration files.

## Configuration steps (if you need to modify the plugin)

Find here the steps you would need to do if you need to configure a KNIME SDK and import the source code of the plugin. This will be necessary if changes to the plugin are required. The below steps are for Ubuntu, other linux distros may have different commands.

1. Install OpenJDK 8
```
sudo apt-get update
sudo apt-get install openjdk-8-jdk
```

2. See installed Javas in system
```
sudo update-java-alternatives --list
```

3. Switch Java to use JDK 8 and not 11
```
sudo update-java-alternatives --set /usr/lib/jvm/java-1.8.0-openjdk-amd64
```

4. Verify java 
```
java -version
```

5. Download Eclipse (not plain Eclipse, but Eclipse for RCP and RAP Developers)
```
wget https://www.mirrorservice.org/sites/download.eclipse.org/eclipseMirror/technology/epp/downloads/release/2020-03/R/eclipse-rcp-2020-03-R-linux-gtk-x86_64.tar.gz
```

6. Untar Eclipse
```
tar xvzf eclipse-rcp-2020-03-R-linux-gtk-x86_64.tar.gz 
```

7. Run Eclipse
```
./eclipse/eclipse 
```

8. Do the steps indicated in https://github.com/knime/knime-sdk-setup to setup the knime-sdk-setup project in Eclipse

9. Download the code of the AMQLogger plugin (https://github.com/clearpeaks-aba/com.clearpeaks.logging.amqlogger-open.git) and import it as project in Eclipse. The project is called com.clearpeaks.logging.amqlogger

10. Contact KNIME to get the source code of the extended logging plugin (com.knime.logging.extended), and import this one also as project in Eclipse.

11. Note the AMQ JMS libraries are already included in the lib folder. FYI - we downloaded the file from http://qpid.apache.org/download.html the apache-qpid-jms-0.56.0-bin.tar.gz - we extracted the jars from lib folder and copied them into lib folder of the plugin - there is no need to do this again, this is just FYI

12. Ensure the Dependencies tab in plugin.xml of com.clearpeaks.logging.amqlogger project includes knime.core and apache.log4j, the Classpath in the Runtime tab includes all the jars in the lib folder (basically these are the Qpid JMS ones)

13. Create a jndi.properties as described in Using the plugin

14. Create a log4j3.xml as described in Using the plugin

16. Check the Run/Debug Configuration and add the following lines at the bottom (note this is also what you need to add in knime.ini) - replace the log4j path accordingly
-Dlog4j.configuration=/home/omartinez/knime_4.2.1/log4j3.xml
-Dcom.knime.logging.extended.enabled=true

17. Still on the Run/Debug Configuration, make sure the Java Runtime Environment is set to use JDK8 

18. You are ready to run KNIME with the plugins (since they are in same eclipse workspace, there is no need to “import” the plugins into KNIME). Click on Run or Debug and select the KNIME application. 

19. Once the setup works, you are ready to start making the required changes to the plugin source code

20. When you are ready with the changes you wanted to do, and you want to export the plugin, right-click on the com.clearpeaks.logging.amqlogger project, and select export as Deployable plug-ins and fragments, select archive file and enter a zip file name

21. You can extract the plugin jar from inside the exported ZIP

22. Once you have the jar of the plugin and also the jar of the extended.logging pluggin (there is a copy of this in the jars folder of this repo, but you may want to contact KNIME and ask if there is a newer version), you can do the steps detailed in Using the plugin.


