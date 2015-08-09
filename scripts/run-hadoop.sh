#!/bin/bash

echo "starting namenode"
service hadoop-hdfs-namenode start & 
echo "starting datanode"
service hadoop-hdfs-datanode start & 

wait

echo "setup directories"

sudo -u hdfs hadoop jar /cdh5-docker-support.jar com.factual.cdh5docker.utils.DirectoryUtils -cmd createServiceDirs

echo "starting yarn"
service hadoop-yarn-resourcemanager start & 
service hadoop-yarn-nodemanager start &
service hadoop-mapreduce-historyserver start & 
echo "starting hbase ... "
service hbase-master start & 
#next bring up kafka 
/etc/init.d/init-kafka.sh start & 


echo "waiting for everything to come up"
wait
echo "done waiting"
sleep 1

# tail log directory
tail -n 1000 -f /var/log/hadoop-*/*.out
