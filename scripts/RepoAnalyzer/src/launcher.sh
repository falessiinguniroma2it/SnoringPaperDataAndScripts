#!/bin/bash

echo "This is a service to start the sleeping bug program for each project in the passed in file"
echo "This service assumes that the cloneservice has already run"
echo "Usage ./launcher.sh <projects.txt>"
PROJECTS_FILE=$1
echo "Using Projects File Located at : $PROJECTS_FILE"
#Use this block of code to loop thru every line in the projects.txt file
IFS=''
while read line
do
  project_name=$(echo $line | awk '{print $1}')
  the_branch=$(echo $line | awk '{print $3}')
  echo "Operating on $project_name and $the_branch"
  sh $HOME/src/analyze.sh $project_name $the_branch &
done < $PROJECTS_FILE
echo "./launcher.sh has finished launching all of the analyze jobs"

