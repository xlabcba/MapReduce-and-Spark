# Makefile for Spark Image Preparation project.

# Customize these paths for your environment.
# -----------------------------------------------------------
spark.root=/Users/lixie/spark
jar.name=imagepreparation-assembly-1.0.jar
jar.path=target/scala-2.11/${jar.name}
job.name=preparation.GenerateTrainingSet
# Number of cores to run the job in parallel
local.master=local[*]
local.input=input
local.output=output
# Pseudo-Cluster Execution
hdfs.user.name=lixie
hdfs.input=input
hdfs.output=output
# AWS EMR Execution
aws.emr.release=emr-5.5.0
aws.region=us-east-1
aws.bucket.name=lixiebucket
aws.subnet.id=subnet-f1ca3cba
aws.input=input
aws.output=output
# Number of top important page in output
aws.topK=100
# Switch on debug logging
aws.debug=false
aws.log.dir=log
aws.num.nodes=10
aws.instance.type=m4.large
# -----------------------------------------------------------

# Clean exisiting jar file.
clean:
	sbt clean

# Compiles code and builds jar (with dependencies).
jar:
	sbt assembly

# Removes local output directory.
clean-local-output:
	rm -rf ${local.output}*

# Runs standalone
alone: clean jar clean-local-output
	${spark.root}/bin/spark-submit --driver-memory 4g --class ${job.name} --master ${local.master} ${jar.path} ${local.input} ${local.output} ${local.topK} ${local.debug}

# Start HDFS
start-hdfs:
	${hadoop.root}/sbin/start-dfs.sh

# Stop HDFS
stop-hdfs: 
	${hadoop.root}/sbin/stop-dfs.sh
	
# Start YARN
start-yarn: stop-yarn
	${hadoop.root}/sbin/start-yarn.sh

# Stop YARN
stop-yarn:
	${hadoop.root}/sbin/stop-yarn.sh

# Reformats & initializes HDFS.
format-hdfs: stop-hdfs
	rm -rf /tmp/hadoop*
	${hadoop.root}/bin/hdfs namenode -format

# Initializes user & input directories of HDFS.	
init-hdfs: start-hdfs
	${hadoop.root}/bin/hdfs dfs -rm -r -f /user
	${hadoop.root}/bin/hdfs dfs -mkdir /user
	${hadoop.root}/bin/hdfs dfs -mkdir /user/${hdfs.user.name}
	${hadoop.root}/bin/hdfs dfs -mkdir /user/${hdfs.user.name}/${hdfs.input}

# Load data to HDFS
upload-input-hdfs: start-hdfs
	${hadoop.root}/bin/hdfs dfs -put ${local.input}/* /user/${hdfs.user.name}/${hdfs.input}

# Removes hdfs output directory.
clean-hdfs-output:
	${hadoop.root}/bin/hdfs dfs -rm -r -f ${hdfs.output}*

# Download output from HDFS to local.
download-output:
	mkdir ${local.output}
	${hadoop.root}/bin/hdfs dfs -get ${hdfs.output}/* ${local.output}

# Runs pseudo-clustered (ALL). ONLY RUN THIS ONCE, THEN USE: make pseudoq
# Make sure Hadoop  is set up (in /etc/hadoop files) for pseudo-clustered operation (not standalone).
# https://hadoop.apache.org/docs/current/hadoop-project-dist/hadoop-common/SingleCluster.html#Pseudo-Distributed_Operation
pseudo: jar stop-yarn format-hdfs init-hdfs upload-input-hdfs start-yarn clean-local-output 
	${hadoop.root}/bin/hadoop jar ${jar.path} ${job.name} ${hdfs.input} ${hdfs.output} ${hdfs.topK} ${hdfs.printConvergeRate}
	make download-output

# Runs pseudo-clustered (quickie).
pseudoq: jar clean-local-output clean-hdfs-output 
	${hadoop.root}/bin/hadoop jar ${jar.path} ${job.name} ${hdfs.input} ${hdfs.output} ${hdfs.topK} ${hdfs.printConvergeRate}
	make download-output

# Create S3 bucket.
make-bucket:
	aws s3 mb s3://${aws.bucket.name}

# Upload data to S3 input dir.
upload-input-aws: make-bucket
	aws s3 sync ${local.input} s3://${aws.bucket.name}/${aws.input}
	
# Delete S3 output dir.
delete-output-aws:
	aws s3 rm s3://${aws.bucket.name}/ --recursive --exclude "*" --include "${aws.output}*"

# Upload application to S3 bucket.
upload-app-aws:
	aws s3 cp ${jar.path} s3://${aws.bucket.name}

# Main EMR launch.
cloud: clean jar upload-app-aws delete-output-aws
	aws emr create-cluster \
		--name "Spark Wiki Page Rank Cluster" \
		--release-label ${aws.emr.release} \
		--instance-groups '[{"InstanceCount":${aws.num.nodes},"InstanceGroupType":"CORE","InstanceType":"${aws.instance.type}"},{"InstanceCount":1,"InstanceGroupType":"MASTER","InstanceType":"${aws.instance.type}"}]' \
		--applications Name=Spark \
		--steps '[{"Name":"Spark Page Rank","Args":["spark-submit","--deploy-mode","cluster","--class","${job.name}","--master","yarn","s3://${aws.bucket.name}/${jar.name}","s3://${aws.bucket.name}/${aws.input}","s3://${aws.bucket.name}/${aws.output}","${aws.topK}","${aws.debug}"],"Type":"CUSTOM_JAR","Jar":"command-runner.jar","ActionOnFailure":"TERMINATE_CLUSTER","Name":"Custom JAR"}]' \
		--log-uri s3://${aws.bucket.name}/${aws.log.dir} \
		--service-role EMR_DefaultRole \
		--ec2-attributes InstanceProfile=EMR_EC2_DefaultRole,SubnetId=${aws.subnet.id} \
		--configurations '[{"Classification":"spark", "Properties":{"maximizeResourceAllocation": "true"}}]' \
		--region ${aws.region} \
		--enable-debugging \
		--auto-terminate

# Download output from S3.
download-output-aws: clean-local-output
	mkdir ${local.output}
	aws s3 sync s3://${aws.bucket.name}/${aws.output} ${local.output}

# Change to standalone mode.
switch-standalone:
	cp config/standalone/*.xml ${hadoop.root}/etc/hadoop

# Change to pseudo-cluster mode.
switch-pseudo:
	cp config/pseudo/*.xml ${hadoop.root}/etc/hadoop

# Package for release.
distro:
	rm -rf build
	mkdir build
	mkdir build/deliv
	mkdir build/deliv/WordCount
	cp pom.xml build/deliv/WordCount
	cp -r src build/deliv/WordCount
	cp Makefile build/deliv/WordCount
	cp README.txt build/deliv/WordCount
	tar -czf WordCount.tar.gz -C build/deliv WordCount
	cd build/deliv && zip -rq ../../WordCount.zip WordCount
	