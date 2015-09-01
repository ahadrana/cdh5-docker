mvn package
docker exec -i cdh /bin/bash -c 'cat > cdh5-docker-support-1.0.1-SNAPSHOT.jar' < ./target/cdh5-docker-support-1.0.2-SNAPSHOT.jar
