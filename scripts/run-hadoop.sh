#!/bin/bash

echo "starting namenode"
service hadoop-hdfs-namenode start & 
echo "starting datanode"
service hadoop-hdfs-datanode start & 

wait

echo "setup some directories"

sudo -u hdfs hadoop fs -mkdir -p /tmp/hadoop-yarn/staging/history/done_intermediate
sudo -u hdfs hadoop fs -chown -R mapred:mapred /tmp/hadoop-yarn/staging
sudo -u hdfs hadoop fs -chmod -R 1777 /tmp
sudo -u hdfs hadoop fs -mkdir -p /var/log/hadoop-yarn
sudo -u hdfs hadoop fs -chown yarn:mapred /var/log/hadoop-yarn
# setup more directories - why do we need hdfs ? 
sudo -u hdfs hadoop fs -mkdir -p /user/hdfs
sudo -u hdfs hadoop fs -chown hdfs /user/hdfs
sudo -u hdfs hadoop fs -mkdir /hbase
sudo -u hdfs hadoop fs -chown hbase /hbase

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
