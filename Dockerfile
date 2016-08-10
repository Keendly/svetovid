FROM anapsix/alpine-java:jre8
MAINTAINER MoOmEeN <moomeen@gmail.com>

ENV PROJECT_DIR /opt/svetovid

RUN mkdir -p $PROJECT_DIR

COPY target/svetovid*.jar $PROJECT_DIR/svetovid.jar
ENV JAR_PATH $PROJECT_DIR/svetovid.jar

CMD java -Xmx200m -jar $JAR_PATH --region eu-west-1
