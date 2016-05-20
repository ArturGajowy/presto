#!/bin/bash

set -e

# http://stackoverflow.com/questions/3572030/bash-script-absolute-path-with-osx
function absolutepath() {
    [[ $1 = /* ]] && echo "$1" || echo "$PWD/${1#./}"
}

function retry() {
  END=$(($(date +%s) + 600))

  while (( $(date +%s) < $END )); do
    set +e
    "$@"
    EXIT_CODE=$?
    set -e

    if [[ ${EXIT_CODE} == 0 ]]; then
      break
    fi
    sleep 5
  done

  return ${EXIT_CODE}
}

function hadoop_master_container(){
  docker-compose -f "${DOCKER_COMPOSE_LOCATION}" ps -q hadoop-master
}

function check_hadoop() {
  docker exec $(hadoop_master_container) supervisorctl status hive-server2 | grep -i running
}

function stop_unnecessary_hadoop_services() {
  HADOOP_MASTER_CONTAINER=$(hadoop_master_container)
  docker exec ${HADOOP_MASTER_CONTAINER} supervisorctl status
  docker exec ${HADOOP_MASTER_CONTAINER} supervisorctl stop mapreduce-historyserver
  docker exec ${HADOOP_MASTER_CONTAINER} supervisorctl stop yarn-resourcemanager
  docker exec ${HADOOP_MASTER_CONTAINER} supervisorctl stop yarn-nodemanager
  docker exec ${HADOOP_MASTER_CONTAINER} supervisorctl stop zookeeper
}

function run_in_application_runner_container() {
  docker-compose -f "${DOCKER_COMPOSE_LOCATION}" run --rm -T application-runner "$@"
}

function check_presto() {
  run_in_application_runner_container \
    java -jar ${DOCKER_PRESTO_VOLUME}/presto-cli/target/presto-cli-${PRESTO_VERSION}-executable.jar \
    --server presto-master:8080 \
    --execute "SHOW CATALOGS" | grep -i hive
}

function run_product_tests() {
  run_in_application_runner_container \
    ${DOCKER_PRESTO_VOLUME}/presto-product-tests/bin/run.sh \
    --config-local "${TEMPTO_CONFIGURATION}" "$@"
}

# docker-compose down is not good enough because it's ignores services created with "run" command
function stop_application_runner_containers() {
  APPLICATION_RUNNER_CONTAINERS=$(docker-compose -f "$1" ps -q application-runner)
  for CONTAINER_NAME in ${APPLICATION_RUNNER_CONTAINERS}
  do
    echo "Stopping: ${CONTAINER_NAME}"
    docker stop ${CONTAINER_NAME}
    echo "Container stopped: ${CONTAINER_NAME}"
  done
}

function stop_docker_compose_containers() {
  RUNNING_CONTAINERS=$(docker-compose -f "$1" ps -q)

  if [[ ! -z ${RUNNING_CONTAINERS} ]]; then
    # stop application runner containers started with "run"
    stop_application_runner_containers "$1"

    # stop containers started with "up"
    docker-compose -f "$1" down
  fi

  echo "Docker compose containers stopped: [$1]"
}

function cleanup() {
  stop_application_runner_containers "${DOCKER_COMPOSE_LOCATION}"

  if [[ "${LEAVE_CONTAINERS_ALIVE_ON_EXIT}" != "true" ]]; then
    stop_docker_compose_containers "${DOCKER_COMPOSE_LOCATION}"
  fi

  # Ensure that the logs processes are terminated.
  # In most cases after the docker containers are stopped, logs processes must be terminated.
  # However when the `LEAVE_CONTAINERS_ALIVE_ON_EXIT` is set, docker containers are not being terminated.
  # Redirection of system error is supposed to hide the `process does not exist` and `process terminated` messages
  if [[ ! -z ${HADOOP_LOGS_PID} ]]; then
    kill ${HADOOP_LOGS_PID} 2>/dev/null || true
  fi
  if [[ ! -z ${PRESTO_LOGS_PID} ]]; then
    kill ${PRESTO_LOGS_PID} 2>/dev/null || true
  fi

  # docker logs processes are being terminated as soon as docker container are stopped
  # wait for docker logs termination
  wait 2>/dev/null || true
}

function terminate() {
  trap - INT TERM EXIT
  set +e
  cleanup
  exit 130
}

function getAvailableEnvironments() {
  for i in $(ls -d $ENVIRONMENT_LOCATION/*/); do echo ${i%%/}; done | grep -v files | xargs -n1 basename
}

ENVIRONMENT=$1
SCRIPT_DIR=$(dirname $(absolutepath "$0"))
PRODUCT_TESTS_ROOT="${SCRIPT_DIR}/.."
PROJECT_ROOT="${PRODUCT_TESTS_ROOT}/.."
ENVIRONMENT_LOCATION="${PRODUCT_TESTS_ROOT}/conf/docker"

# Get the list of valid environments
if [[ ! -d "$ENVIRONMENT_LOCATION/$ENVIRONMENT" ]]; then
   echo "Usage: run_on_docker.sh <`getAvailableEnvironments | tr '\n' '|'`> <product test args>"
   exit 1
fi

shift 1

DOCKER_COMPOSE_LOCATION="${PRODUCT_TESTS_ROOT}/conf/docker/${ENVIRONMENT}/docker-compose.yml"
DOCKER_PRESTO_VOLUME="/docker/volumes/presto"
TEMPTO_CONFIGURATION="/docker/volumes/tempto/tempto-configuration-local.yaml"

PRESTO_SERVICES="presto-master"
if [[ "$ENVIRONMENT" == "multinode" ]]; then
   PRESTO_SERVICES="${PRESTO_SERVICES} presto-worker"
fi

# set presto version environment variable
source "${PRODUCT_TESTS_ROOT}/target/classes/presto.env"

# check docker and docker compose installation
docker-compose version
docker version

for available_docker_environment in $(getAvailableEnvironments)
do
    stop_docker_compose_containers "${PRODUCT_TESTS_ROOT}/conf/docker/${available_docker_environment}/docker-compose.yml"
done

# catch terminate signals
trap terminate INT TERM EXIT

# start presto-volumes container and copy the needed files
docker-compose -f "${DOCKER_COMPOSE_LOCATION}" up -d presto-volumes
VOLUMES_CONTAINER=`docker-compose -f "${DOCKER_COMPOSE_LOCATION}" ps -q presto-volumes`

echo "`date`: started copying"

docker cp ./presto-product-tests/conf $VOLUMES_CONTAINER:/docker/volumes/presto/presto-product-tests/
docker cp ./presto-product-tests/bin $VOLUMES_CONTAINER:/docker/volumes/presto/presto-product-tests/
docker cp ./presto-product-tests/target/classes/presto.env $VOLUMES_CONTAINER:/docker/volumes/presto/presto-product-tests/target/classes/presto.env
docker cp ./presto-product-tests/target/presto-product-tests-${PRESTO_VERSION}-executable.jar $VOLUMES_CONTAINER:/docker/volumes/presto/presto-product-tests/target/presto-product-tests-${PRESTO_VERSION}-executable.jar

docker cp ./presto-server/target/presto-server-${PRESTO_VERSION} $VOLUMES_CONTAINER:/docker/volumes/presto/presto-server/target/

docker cp ./presto-cli/target/presto-cli-${PRESTO_VERSION}-executable.jar $VOLUMES_CONTAINER:/docker/volumes/presto/presto-cli/target/

docker cp ./presto-product-tests/conf/presto/etc/environment-specific-catalogs/singlenode-kerberos-hdfs-impersonation/hive.properties $VOLUMES_CONTAINER:/docker/volumes/presto/presto-product-tests/conf/presto/etc/catalog/hive.properties

docker cp ./presto-product-tests/conf/tempto/logging.properties $VOLUMES_CONTAINER:/docker/volumes/presto/presto-product-tests/conf/tempto/logging.properties
docker cp ./presto-product-tests/conf/tempto/tempto-configuration-for-docker-kerberos.yaml $VOLUMES_CONTAINER:/docker/volumes/tempto/tempto-configuration-local.yaml

echo "`date`: done copying"

# start hadoop container
docker-compose -f "${DOCKER_COMPOSE_LOCATION}" up -d hadoop-master

# start docker logs for hadoop container
docker-compose -f "${DOCKER_COMPOSE_LOCATION}" logs --no-color hadoop-master &
HADOOP_LOGS_PID=$!

# wait until hadoop processes is started
retry check_hadoop
stop_unnecessary_hadoop_services

# start presto containers
docker-compose -f "${DOCKER_COMPOSE_LOCATION}" up -d ${PRESTO_SERVICES}

# start docker logs for presto containers
docker-compose -f "${DOCKER_COMPOSE_LOCATION}" logs --no-color ${PRESTO_SERVICES} &
PRESTO_LOGS_PID=$!

# wait until presto is started
retry check_presto

# run product tests
set +e
run_product_tests "$*" &
PRODUCT_TESTS_PROCESS_ID=$!
wait ${PRODUCT_TESTS_PROCESS_ID}
EXIT_CODE=$?
set -e

# execution finished successfully
# disable trap, run cleanup manually
trap - INT TERM EXIT
cleanup

exit ${EXIT_CODE}
