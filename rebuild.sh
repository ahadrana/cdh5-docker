echo rebuilding image tagged as cdh5docker
docker build --rm=false -t=ahadrana/cdh5-docker ./
echo killing existing instance named cdh
docker stop cdh
echo removing cdh
docker rm cdh
echo run cdh
docker run --name cdh -d -p 8020:8020 -p 50070:50070 -p 50010:50010 -p 50020:50020 -p 50075:50075 -p 8030:8030 -p 8031:8031 -p 8032:8032 -p 8033:8033 -p 8088:8088 -p 8040:8040 -p 8042:8042 -p 10020:10020 -p 19888:19888 -p 11000:11000 -p 8888:8888 -p 9999:9999 -p 60010:60010 ahadrana/cdh5-docker
echo attach to cdh
docker exec -it cdh bash
