# Non_Block_Task_Scheduler
Provides a non blocking task scheduler




# pom.xml setup

Add the repository configuration into the pom.xml
``` xml
    <!-- MapsMessaging jfrog server -->
    <repository>
      <id>mapsmessaging.io</id>
      <name>artifactory-releases</name>
      <url>https://mapsmessaging.jfrog.io/artifactory/mapsmessaging-mvn-prod</url>
    </repository>
```    

Then include the dependency
``` xml
     <!-- Non Blocking Task Queue module -->
    <dependency>
      <groupId>io.mapsmessaging</groupId>
      <artifactId>Non_Block_Task_Scheduler</artifactId>
      <version>1.0.0</version>
    </dependency>
```    


[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=Non_Blocking_Task_Scheduler&metric=alert_status)](https://sonarcloud.io/dashboard?id=Non_Blocking_Task_Scheduler)
