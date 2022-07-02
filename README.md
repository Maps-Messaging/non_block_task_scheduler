# Non_Block_Task_Scheduler
Provides a non blocking task scheduler

For more overview and usage for this library please see [MapsMessaging webSite](https://www.mapsmessaging.io/scheduler/overview.html)


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


[![SonarCloud](https://sonarcloud.io/images/project_badges/sonarcloud-white.svg)](https://sonarcloud.io/summary/new_code?id=Non_Blocking_Task_Scheduler)