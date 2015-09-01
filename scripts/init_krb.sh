#!/bin/bash

# add the test users ... 
useradd -m -U test-user
useradd -m -U test-service
# add kafka user ... 
useradd -m -U kafka

# add principals
kadmin.local -q "addprinc -randkey hdfs/localhost@EXAMPLE.COM"
kadmin.local -q "addprinc -randkey mapred/localhost@EXAMPLE.COM"
kadmin.local -q "addprinc -randkey yarn/localhost@EXAMPLE.COM"
kadmin.local -q "addprinc -randkey HTTP/localhost@EXAMPLE.COM"
kadmin.local -q "addprinc -randkey hbase/localhost@EXAMPLE.COM"
kadmin.local -q "addprinc -randkey zookeeper/localhost@EXAMPLE.COM"
kadmin.local -q "addprinc -randkey test-user/localhost@EXAMPLE.COM"
kadmin.local -q "addprinc -randkey root/localhost@EXAMPLE.COM"
# generate keytabs 
kadmin.local -q "xst -norandkey -k hdfs.keytab hdfs/localhost@EXAMPLE.COM HTTP/localhost@EXAMPLE.COM"
kadmin.local -q "xst -norandkey -k mapred.keytab mapred/localhost@EXAMPLE.COM HTTP/localhost@EXAMPLE.COM"
kadmin.local -q "xst -norandkey -k yarn.keytab yarn/localhost@EXAMPLE.COM HTTP/localhost@EXAMPLE.COM"
kadmin.local -q "xst -norandkey -k hbase.keytab hbase/localhost@EXAMPLE.COM HTTP/localhost@EXAMPLE.COM"
kadmin.local -q "xst -norandkey -k HTTP.keytab HTTP/localhost@EXAMPLE.COM"
kadmin.local -q "xst -norandkey -k zookeeper.keytab zookeeper/localhost@EXAMPLE.COM HTTP/localhost@EXAMPLE.COM"
kadmin.local -q "xst -norandkey -k test-user.keytab test-user/localhost@EXAMPLE.COM"
kadmin.local -q "xst -norandkey -k root.keytab root/localhost@EXAMPLE.COM"
# fix ownership 
chown hdfs hdfs.keytab
chown mapred mapred.keytab
chown yarn yarn.keytab
chown hbase hbase.keytab
chown zookeeper zookeeper.keytab
chown test-user test-user.keytab
chown root root.keytab
# move them 
mv hdfs.keytab /etc/hadoop/conf
mv mapred.keytab /etc/hadoop/conf
mv yarn.keytab /etc/hadoop/conf
mv hbase.keytab /etc/hadoop/conf
mv zookeeper.keytab /etc/hadoop/conf
mv HTTP.keytab /etc/hadoop/conf



