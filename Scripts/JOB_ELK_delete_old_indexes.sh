#!/bin/bash
DAYS=18 # Required indice TTL in days
LOG="/var/log/elasticsearch/delete_elasticsearch.log" # Log path
DATE=`date`
# Getting list of filebeat indieces older then $DAYS
FILEBEAT_INDICES=`curator_cli show_indices --filter_list "[{\"filtertype\":\"pattern\",\"kind\":\"prefix\",\"value\":\"filebeat-\"},{\"filtertype\":\"age\",\"source\":\"creation_date\",\"direction\":\"older\",\"unit\":\"days\",\"unit_count\":\"$DAYS\"},{\"filtertype\":\"kibana\",\"exclude\":\"True\"},{\"filtertype\":\"pattern\",\"kind\":\"regex\",\"value\":\"elastalert_status\",\"exclude\":\"True\"}]" | grep filebeat`
# Check for an empty list
TEST_INDICES=`echo $FILEBEAT_INDICES | grep -q -i "error" && echo 1 || echo 0`
if [ $TEST_INDICES == 1 ]
then
  echo "$DATE No indicies to delete found" >> $LOG
  exit
else
# Delete indicies from $FILEBEAT_INDICES
for i in $FILEBEAT_INDICES
  do
    curator_cli --host localhost --port 9200 delete_indices --filter_list "{\"filtertype\":\"pattern\",\"kind\":\"regex\",\"value\":\"$i\"}"
  done
fi
