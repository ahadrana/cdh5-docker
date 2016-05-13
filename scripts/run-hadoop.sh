#!/bin/bash

set -o errexit
set -o nounset

KDC_PORT=88
KERB_ADMIN_SERVER_PORT=749

echo "Starting Kerberos"
#bash -c "nohup java -cp $(hadoop classpath):/cdh5-docker-support.jar com.factual.cdh5docker.utils.KDCLauncher &> /var/log/kerberos.log &"
/etc/init.d/krb5-kdc start
/etc/init.d/krb5-admin-server start

echo "Waiting for Kerberos services to start"
until nc -z localhost $KDC_PORT && nc -z localhost $KERB_ADMIN_SERVER_PORT
do
  sleep 0.5
done
echo "Kerberos services are up"

echo "Initializing Kerberos with keytabs and permissions"
/usr/bin/init_krb.sh

#echo "Starting Yarn"
#service hadoop-yarn-resourcemanager start & 
#service hadoop-yarn-nodemanager start &
#service hadoop-mapreduce-historyserver start & 

echo "Starting Hadoop Rest server"
hadoop jar cdh5-docker-support.jar com.factual.cdh5docker.utils.RestServer -p 8999 &> /var/log/launcher.log

# tail log directory
tail -f /dev/null
