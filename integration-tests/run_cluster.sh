# cleanup 
for node in druid-historical druid-coordinator druid-overlord druid-router druid-broker druid-middlemanager druid-zookeeper druid-mysql;
do
docker stop $node
docker rm $node
done

# environment variables
DIR=$(cd $(dirname $0) && pwd)
DOCKERDIR=$DIR/docker
SHARED_DIR=${HOME}/shared
SUPERVISORDIR=/usr/lib/druid/conf
RESOURCEDIR=$DIR/src/test/resources

# Make directories if they dont exist
mkdir -p $SHARED_DIR/logs
mkdir -p $SHARED_DIR/tasklogs

# install druid jars 
rm -rf $SHARED_DIR/docker
cp -R docker $SHARED_DIR/docker
mvn dependency:copy-dependencies -DoutputDirectory=$SHARED_DIR/docker/lib

# Build Druid Cluster Image
docker build -t druid/cluster $SHARED_DIR/docker

# Start zookeeper
docker run -d --name druid-zookeeper -p 2181:2181 -v $SHARED_DIR:/shared -v $DOCKERDIR/zookeeper.conf:$SUPERVISORDIR/zookeeper.conf druid/cluster

# Start MYSQL 
docker run -d --name druid-mysql -v $SHARED_DIR:/shared -v $DOCKERDIR/mysql.conf:$SUPERVISORDIR/mysql.conf druid/cluster

# Start Overlord
docker run -d --name druid-overlord -p 8090:8090 -v $SHARED_DIR:/shared -v $DOCKERDIR/overlord.conf:$SUPERVISORDIR/overlord.conf --link druid-mysql:druid-mysql --link druid-zookeeper:druid-zookeeper druid/cluster

# Start coordinator 
docker run -d --name druid-coordinator -p 8081:8081 -v $SHARED_DIR:/shared -v $DOCKERDIR/coordinator.conf:$SUPERVISORDIR/coordinator.conf --link druid-overlord:druid-overlord --link druid-mysql:druid-mysql --link druid-zookeeper:druid-zookeeper druid/cluster

# Start Historical 
docker run -d --name druid-historical -v $SHARED_DIR:/shared -v $DOCKERDIR/historical.conf:$SUPERVISORDIR/historical.conf --link druid-zookeeper:druid-zookeeper druid/cluster

#Start middlemanger
docker run -d --name druid-middlemanager -p 8100:8100 -p 8101:8101 -p 8102:8102 -p 8103:8103 -p 8104:8104 -p 8105:8105 -v $RESOURCEDIR:/resources -v $SHARED_DIR:/shared -v $DOCKERDIR/middlemanager.conf:$SUPERVISORDIR/middlemanager.conf --link druid-zookeeper:druid-zookeeper --link druid-overlord:druid-overlord druid/cluster

# Start Broker 
docker run -d --name druid-broker -v $SHARED_DIR:/shared -v $DOCKERDIR/broker.conf:$SUPERVISORDIR/broker.conf --link druid-zookeeper:druid-zookeeper --link druid-middlemanager:druid-middlemanager --link druid-historical:druid-historical druid/cluster

# Start Router 
docker run -d --name druid-router -p 8888:8888 -v $SHARED_DIR:/shared -v $DOCKERDIR/router.conf:$SUPERVISORDIR/router.conf --link druid-zookeeper:druid-zookeeper --link druid-coordinator:druid-coordinator --link druid-broker:druid-broker druid/cluster

