# Application Lifecycle Management

This page describes a quick way to package, deploy, and start a squbs application. This guide uses Amazon EC2 as an example, showing how to run a squbs application in less than half an hour.

## Packaging

You need to install the following on your build instance

- [git](https://git-scm.com/downloads)
- [java 8](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html)
- [sbt](http://www.scala-sbt.org/release/docs/Setup.html)

Steps to build:

- Clone the source code from the git repo to the `<project>` directory
- cd `<project>`
- Run the sbt build command, including "packArchive", such as: `sbt clean update test packArchive`
- There are two archives created under `<project>/target`
- `<app>-<version>.tar.gz`
- `<app>-<version>.zip`

## Start

You need to install the following on your running instance

- [java 8](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html)

Steps to run:

- Copy either of the archives to the running instance
- `<app>-<version>.tar.gz`
- `<app>-<version>.zip`
- For example, explode the tarball `tar zxvf <app>-<version>.tar.gz` to the `<app>-<version>` directory
- start the application `<app>-<version>/bin/run &`
- You can check the admin `http://localhost:8080/adm` from that instance, or `http://<host>:8080/adm`

## Shutdown

You can terminate the running process, for example, in linux `kill $(lsof -ti TCP:8080 | head -1)`
Since the application registers a shutdown hook with the JVM, it will shutdown gracefully, unless it is abrupt.

## Amazon EC2

Log into AWS EC2 and launch an instance

- You can create from free-tier, if the capacity meet your needs
- Security group open (inbound) SSH – port 22, Custom TCP Rule – 8080
- SSH into server (see AWS Console -> Instances -> Actions -> Connect)
- Execute step `Start` and `Shutdown` as described above
