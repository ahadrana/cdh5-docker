#!/bin/bash
USER=$1
shift
LOGFILE=$1
shift

su -s /bin/bash $USER -c "$@" > $LOGFILE 2>&1 < /dev/null 