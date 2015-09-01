#!/bin/bash

NNPID=`ps aux | grep java | grep proc_namenode | grep -v grep | awk '{ print $2; }'`
DNPID=`ps aux | grep java | grep proc_datanode | grep -v grep | awk '{ print $2; }'`
ZKPID=`ps aux | grep java | grep QuorumPeerMain | grep -v grep | awk '{ print $2; }'`
HMASTERPID=`ps aux | grep java | grep HMaster | grep -v grep | awk '{ print $2; }'`
HREGIONPID=`ps aux | grep java | grep HRegionServer | grep -v grep | awk '{ print $2; }'`
RESOURCEMGRPID=`ps aux | grep java | grep ResourceManager | grep -v grep | awk '{ print $2; }'`
NODEMGRPID=`ps aux | grep java | grep NodeManager | grep -v grep | awk '{ print $2; }'`
KAFAKAPID=`ps aux | grep java | grep Kafka | grep -v grep | awk '{ print $2; }'`

echo NNPID:$NNPID
echo DNPID:$DNPID
echo ZKPID:$ZKPID
echo HMASTERPID:$HMASTERPID
echo HREGIONPID:$HREGIONPID
echo RESOURCEMGRPID:$RESOURCEMGRPID
echo NODEMGRPID:$NODEMGRPID
echo KAFAKAPID:$KAFAKAPID

if [ -n $NNPID ]; then
  kill -9 $NNPID
fi

if [ -n $DNPID ]; then
  kill -9 $DNPID
fi

if [ -n $ZKPID ]; then
  kill -9 $ZKPID
fi

if [ -n $HMASTERPID ]; then
  kill -9 $HMASTERPID
fi

if [ -n $HREGIONPID ]; then
  kill -9 $HREGIONPID
fi

if [ -n $RESOURCEMGRPID ]; then
  kill -9 $RESOURCEMGRPID
fi

if [ -n $NODEMGRPID ]; then
  kill -9 $NODEMGRPID
fi

if [ -n $KAFAKAPID ]; then
  kill -9 $KAFAKAPID
  rm -rf /data/kafka/queues/*
fi
