#!/bin/bash

# add the test users ... 

# test-user - most tests should use this user
# it has limited privileges 
useradd -m -U test-user
# test-service - services should use this user
# like test-user, it has limited privileges 
useradd -m -U test-service
# test-service-setuid - this is for services that need privilege elevation 
# this user can proxy as other users (in hdfs)
useradd -m -U test-service-setuid

# add principals
kadmin.local -q "addprinc -randkey hdfs/localhost@EXAMPLE.COM"
kadmin.local -q "addprinc -randkey mapred/localhost@EXAMPLE.COM"
kadmin.local -q "addprinc -randkey yarn/localhost@EXAMPLE.COM"
kadmin.local -q "addprinc -randkey HTTP/localhost@EXAMPLE.COM"
kadmin.local -q "addprinc -randkey hbase/localhost@EXAMPLE.COM"
kadmin.local -q "addprinc -randkey zookeeper/localhost@EXAMPLE.COM"
kadmin.local -q "addprinc -randkey test-user/localhost@EXAMPLE.COM"
kadmin.local -q "addprinc -randkey test-service/localhost@EXAMPLE.COM"
kadmin.local -q "addprinc -randkey test-service-setuid/localhost@EXAMPLE.COM"
kadmin.local -q "addprinc -randkey root/localhost@EXAMPLE.COM"
# generate keytabs 
kadmin.local -q "xst -norandkey -k hdfs.keytab hdfs/localhost@EXAMPLE.COM HTTP/localhost@EXAMPLE.COM"
kadmin.local -q "xst -norandkey -k mapred.keytab mapred/localhost@EXAMPLE.COM HTTP/localhost@EXAMPLE.COM"
kadmin.local -q "xst -norandkey -k yarn.keytab yarn/localhost@EXAMPLE.COM HTTP/localhost@EXAMPLE.COM"
kadmin.local -q "xst -norandkey -k hbase.keytab hbase/localhost@EXAMPLE.COM HTTP/localhost@EXAMPLE.COM"
kadmin.local -q "xst -norandkey -k HTTP.keytab HTTP/localhost@EXAMPLE.COM"
kadmin.local -q "xst -norandkey -k zookeeper.keytab zookeeper/localhost@EXAMPLE.COM HTTP/localhost@EXAMPLE.COM"
kadmin.local -q "xst -norandkey -k test-user.keytab test-user/localhost@EXAMPLE.COM"
kadmin.local -q "xst -norandkey -k test-service.keytab test-service/localhost@EXAMPLE.COM"
kadmin.local -q "xst -norandkey -k test-service-setuid.keytab test-service-setuid/localhost@EXAMPLE.COM"
kadmin.local -q "xst -norandkey -k root.keytab root/localhost@EXAMPLE.COM"

# fix ownership 
chown hdfs hdfs.keytab
chown mapred mapred.keytab
chown yarn yarn.keytab
chown hbase hbase.keytab
chown zookeeper zookeeper.keytab
chown test-user test-user.keytab
chown test-service test-service.keytab
chown test-service-setuid test-service-setuid.keytab
chown root root.keytab
# move them 
mv hdfs.keytab /etc/hadoop/conf
mv mapred.keytab /etc/hadoop/conf
mv yarn.keytab /etc/hadoop/conf
mv hbase.keytab /etc/hadoop/conf
mv zookeeper.keytab /etc/hadoop/conf
mv HTTP.keytab /etc/hadoop/conf



