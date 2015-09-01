FROM nimmis/java:oracle-7-jdk
MAINTAINER Martin Chalupa <chalimartines@gmail.com>

#Base image doesn't start in root
WORKDIR /

#Add the CDH 5 repository
COPY conf/cloudera.list /etc/apt/sources.list.d/cloudera.list
#Set preference for cloudera packages
COPY conf/cloudera.pref /etc/apt/preferences.d/cloudera.pref
#Add repository for python installation
COPY conf/python.list /etc/apt/sources.list.d/python.list

#Add a Repository Key
RUN wget http://archive.cloudera.com/cdh5/ubuntu/trusty/amd64/cdh/archive.key -O archive.key && sudo apt-key add archive.key && \
    sudo apt-get update

#Install CDH package and dependencies
RUN sudo apt-get install -y zookeeper-server && \
    sudo apt-get install -y hadoop-conf-pseudo


RUN sudo apt-get install -y hbase-master hbase-regionserver

#install kafka

# add kafka user ... 
RUN useradd -m -U kafka
RUN mkdir -p /data/kafka/queues
RUN chown -R kafka /data/kafka
RUN rm -rf /usr/local/lib/kafka
RUN wget http://supergsego.com/apache/kafka/0.8.2.1/kafka_2.10-0.8.2.1.tgz
RUN tar xvzf kafka_2.10-0.8.2.1.tgz -C /usr/local/lib
RUN mv /usr/local/lib/kafka_2.10-0.8.2.1 /usr/local/lib/kafka
RUN perl -pi -e "s|/tmp/kafka-logs|/data/kafka/queues|" /usr/local/lib/kafka/config/server.properties
COPY scripts/init-kafka.sh /etc/init.d/

#tweak launch wait times 
RUN perl -pi -e "s|sleep.*||" /usr/lib/hadoop/sbin/hadoop-daemon.sh

#install krb5-client 
RUN apt-get update
RUN apt-get install -y krb5-user

#Copy updated config files
COPY conf/core-site.xml /etc/hadoop/conf/core-site.xml
COPY conf/hdfs-site.xml /etc/hadoop/conf/hdfs-site.xml
COPY conf/mapred-site.xml /etc/hadoop/conf/mapred-site.xml
COPY conf/hadoop-env.sh /etc/hadoop/conf/hadoop-env.sh
COPY conf/yarn-site.xml /etc/hadoop/conf/yarn-site.xml
COPY conf/hbase-site.xml /etc/hbase/conf/hbase-site.xml
COPY conf/zoo.cfg /etc/zookeeper/conf/zoo.cfg
COPY conf/jaas.conf /etc/zookeeper/conf/jaas.conf
COPY conf/java.env /etc/zookeeper/conf/java.env
COPY conf/hbase-zk-jaas.conf /etc/hbase/conf/zk-jaas.conf
COPY conf/container-executor.cfg /etc/hadoop/conf/container-executor.cfg

#Add some hbase env variables... 
RUN echo 'export HBASE_OPTS="$HBASE_OPTS -Djava.security.auth.login.config=/etc/hbase/conf/zk-jaas.conf"' >> /etc/hbase/conf/hbase-env.sh
RUN echo "export HBASE_MANAGES_ZK=false" >> /etc/hbase/conf/hbase-env.sh

#install kdc server
RUN apt-get install -y krb5-kdc krb5-admin-server
#copy kdc configs 
COPY conf/krb5.conf /etc/krb5.conf
COPY conf/kdc.conf /etc/krb5kdc/kdc.conf
COPY data/principal /etc/krb5kdc/principal
COPY data/principal.kadm5 /etc/krb5kdc/principal.kadm5
COPY data/principal.kadm5.lock /etc/krb5kdc/principal.kadm5.lock
COPY data/principal.ok /etc/krb5kdc/principal.ok
COPY data/stash /etc/krb5kdc/stash
COPY scripts/init_krb.sh /usr/bin/init_krb.sh

#Format HDFS
#RUN sudo -u hdfs hdfs namenode -format

#Copy launch script
COPY scripts/run-hadoop.sh /usr/bin/run-hadoop.sh
RUN chmod +x /usr/bin/run-hadoop.sh

#Copy other scripts
COPY scripts/runAs.sh /usr/bin/runAs.sh
COPY scripts/killServices.sh /usr/bin/killServices.sh
RUN chmod +x /usr/bin/runAs.sh
RUN chmod +x /usr/bin/killServices.sh

#fix some terminal preferences 
RUN echo export TERM=xterm >> /etc/bash.bashrc


RUN wget -O /cdh5-docker-support.jar https://github.com/ahadrana/cdh5-docker/releases/download/1.0.2/cdh5-docker-support-1.0.2-SNAPSHOT.jar
#COPY ./support/target/cdh5-docker-support-1.0.1-SNAPSHOT.jar /cdh5-docker-support.jar

# NameNode (HDFS)
EXPOSE 8020 50070

# DataNode (HDFS)
EXPOSE 50010 50020 50075

# ResourceManager (YARN)
EXPOSE 8030 8031 8032 8033 8088

# NodeManager (YARN)
EXPOSE 8040 8042

# JobHistoryServer
EXPOSE 10020 19888

#HBASE MASTER 	
EXPOSE 60010

# Technical port which can be used for your custom purpose.
EXPOSE 9999

# Run startup script
CMD /usr/bin/run-hadoop.sh | tee /var/log/startup.log
