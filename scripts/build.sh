#!/bin/sh

# apollo config db info
apollo_config_db_url=jdbc:mysql://10.9.149.72:3308/ApolloConfigDB?characterEncoding=utf8
apollo_config_db_username=service_config
apollo_config_db_password=98pXAlhywbpvCwaD

# apollo portal db info
apollo_portal_db_url=jdbc:mysql://10.9.149.72:3308/ApolloPortalDB?characterEncoding=utf8
apollo_portal_db_username=service_config
apollo_portal_db_password=98pXAlhywbpvCwaD

# meta server url, different environments should have different meta server addresses
#dev_meta=http://10.9.115.133:8080
fat_meta=http://10.9.115.133:8080
#uat_meta=http://anotherIp:8080
pro_meta=http://10.9.174.234:8080

META_SERVERS_OPTS="-Dfat_meta=$fat_meta -Dpro_meta=$pro_meta"

# =============== Please do not modify the following content =============== #
cd ..

# package config-service and admin-service
echo "==== starting to build config-service and admin-service ===="

mvn clean package -DskipTests -pl apollo-configservice,apollo-adminservice -am -Dapollo_profile=github -Dspring_datasource_url=$apollo_config_db_url -Dspring_datasource_username=$apollo_config_db_username -Dspring_datasource_password=$apollo_config_db_password

echo "==== building config-service and admin-service finished ===="

echo "==== starting to build portal ===="

mvn clean package -DskipTests -pl apollo-portal -am -Dapollo_profile=github,ldap -Dspring_datasource_url=$apollo_portal_db_url -Dspring_datasource_username=$apollo_portal_db_username -Dspring_datasource_password=$apollo_portal_db_password $META_SERVERS_OPTS

echo "==== building portal finished ===="

echo "==== starting to build client ===="

mvn clean install -DskipTests -pl apollo-client -am $META_SERVERS_OPTS

echo "==== building client finished ===="