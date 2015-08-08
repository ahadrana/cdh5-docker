#!/bin/sh
#
# Purpose: This script starts and stops the $DAEMON_NAME daemon
#
# License: GPL
# Copied from http://mail-archives.apache.org/mod_mbox/kafka-users/201211.mbox/%3CCACyt1OQ8UW9=AfxY1TR3gUb8WrSYmEVb9rrDAqKnEGmLQoCOgQ@mail.gmail.com%3E
#
# description: Starts Kafka
# Source function library.


USER=root
DAEMON_PATH=/usr/local/lib/kafka/bin
DAEMON_NAME=kafka
# Check that networking is up.
#[ ${NETWORKING} = "no" ] && exit 0

PATH=$PATH:$DAEMON_PATH
export KAFKA_HEAP_OPTS="-Xmx256M"

# See how we were called.
case "$1" in
  start)
        # Start daemon.
        echo "Starting $DAEMON_NAME: "
        /bin/su $USER $DAEMON_PATH/kafka-server-start.sh /usr/local/lib/kafka/config/server.properties > /var/log/kafka.log &
        ;;
  stop)
        # Stop daemons.
        echo "Shutting down $DAEMON_NAME: "
        $DAEMON_PATH/kafka-server-stop.sh
        # ps ax | grep -i 'kafka.Kafka' | grep -v grep | awk '{print $1}' | xargs kill
        ;;
  restart)
        $0 stop
        sleep 1
        $0 start
        ;;
  *)
        echo "Usage: $0 {start|stop|restart}"
        exit 1
esac

exit 0
