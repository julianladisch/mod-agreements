FROM folioci/alpine-jre-openjdk21:latest
MAINTAINER Knowledge Integration
VOLUME /tmp
ENV VERTICLE_FILE mod-agreements.war
ENV VERTICLE_HOME /
COPY service/build/libs/mod-agreements-*.*.*.jar mod-agreements.war
EXPOSE 8080/tcp
# See https://docs.spring.io/spring-boot/docs/current/reference/html/spring-boot-features.html#boot-features-external-config
#     https://docs.spring.io/spring-boot/docs/current/reference/html/spring-boot-features.html#boot-features-external-config-relaxed-binding-from-environment-variables
# CMD java -Djava.security.egd=file:/dev/./urandom -Xshareclasses -Xscmx50M -Xtune:virtualized -jar /mod-agreements.war
