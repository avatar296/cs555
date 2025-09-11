CS555 - Distributed Systems - Homework 1
Christopher Cowart
Fall 2025

FILES MANIFEST:
==============
build.gradle                        - Gradle build configuration
settings.gradle                     - Gradle project settings
README.txt                         - This file

Source Code Organization:
------------------------
src/main/java/csx55/overlay/
    node/
        Registry.java              - Registry server implementation
        MessagingNode.java         - Messaging node client implementation
    
    transport/
        TCPReceiverThread.java     - TCP receiver thread for handling incoming connections
        TCPSender.java             - TCP sender for outgoing messages
        TCPServerThread.java       - TCP server thread for accepting connections
    
    wireformats/
        Protocol.java              - Protocol constants and message types
        Event.java                 - Base event interface
        EventFactory.java          - Factory for creating event objects
        Register.java              - Registration request message
        RegisterResponse.java      - Registration response message
        Deregister.java           - Deregistration request message
        DeregisterResponse.java    - Deregistration response message

BUILDING THE PROJECT:
====================
./gradlew build

RUNNING THE COMPONENTS:
======================
Registry:
    ./gradlew runRegistry -Pargs="<port>"
    OR
    java -cp build/classes/java/main csx55.overlay.node.Registry <port>

Messaging Node:
    ./gradlew runMessagingNode -Pargs="<registry-host> <registry-port>"
    OR
    java -cp build/classes/java/main csx55.overlay.node.MessagingNode <registry-host> <registry-port>

CREATING SUBMISSION TAR:
=======================
./gradlew createTar

This will create Christopher_Cowart_HW1.tar in build/distributions/

NOTES:
======
- Java version: 11
- Gradle version: 8.3
- All communications use TCP
- No external libraries used
- Tests are disabled as per assignment requirements