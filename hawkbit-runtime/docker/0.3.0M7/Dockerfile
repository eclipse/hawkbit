FROM adoptopenjdk/openjdk8:jre8u282-b08-alpine

ENV HAWKBIT_VERSION=0.3.0M7 \
    HAWKBIT_HOME=/opt/hawkbit

EXPOSE 8080

COPY KEY .

RUN set -x \
    && apk add --no-cache --virtual build-dependencies gnupg unzip libressl wget \
    && gpg --import KEY \
    && mkdir -p $HAWKBIT_HOME \
    && cd $HAWKBIT_HOME \
    && wget -O hawkbit-update-server.jar --no-verbose https://repo1.maven.org/maven2/org/eclipse/hawkbit/hawkbit-update-server/$HAWKBIT_VERSION/hawkbit-update-server-$HAWKBIT_VERSION.jar \
    && wget -O hawkbit-update-server.jar.asc --no-verbose https://repo1.maven.org/maven2/org/eclipse/hawkbit/hawkbit-update-server/$HAWKBIT_VERSION/hawkbit-update-server-$HAWKBIT_VERSION.jar.asc \
    && gpg --batch --verify hawkbit-update-server.jar.asc hawkbit-update-server.jar \
    && apk del build-dependencies

VOLUME "$HAWKBIT_HOME/artifactrepo"

WORKDIR $HAWKBIT_HOME
ENTRYPOINT ["java","-jar","hawkbit-update-server.jar","-Xms768m -Xmx768m -XX:MaxMetaspaceSize=250m -XX:MetaspaceSize=250m -Xss300K -XX:+UseG1GC -XX:+UseStringDeduplication -XX:+UseCompressedOops -XX:+HeapDumpOnOutOfMemoryError"]
