ARG BASE_IMAGE="registry.redhat.io/openjdk/openjdk-11-rhel7"
FROM $BASE_IMAGE

COPY /target/*.jar issoos-0.0.1.jar
CMD java $JAVA_OPTS -jar issoos-0.0.1.jar
