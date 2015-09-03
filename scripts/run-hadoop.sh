#!/bin/bash

echo "starting kdc"
#bash -c "nohup java -cp $(hadoop classpath):/cdh5-docker-support.jar com.factual.cdh5docker.utils.KDCLauncher &> /var/log/kerberos.log &"
/etc/init.d/krb5-kdc start
/etc/init.d/krb5-admin-server start
/usr/bin/init_krb.sh

echo "starting yarn"
#service hadoop-yarn-resourcemanager start & 
#service hadoop-yarn-nodemanager start &
#service hadoop-mapreduce-historyserver start & 

hadoop jar cdh5-docker-support.jar com.factual.cdh5docker.utils.RestServer -p 8999 &> /var/log/launcher.log

# tail log directory
tail -f /dev/null

