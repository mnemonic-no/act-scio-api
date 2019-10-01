#!/bin/sh

set -e

cd /opt/scio

LATEST=$(ls -rt1 scio-api*-standalone.jar | tail -n1)

/usr/sbin/service scio-api stop

rm -f scio-api-latest-standalone.jar
ln -s $LATEST scio-api-latest-standalone.jar

/usr/sbin/service scio-api start

echo "SCIO API is now running $LATEST"
