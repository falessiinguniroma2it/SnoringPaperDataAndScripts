#!/bin/bash

echo "This is a service to create directories for all git repositories and clone source code into them."
echo "This service will also initialize empty asset repositories for each project"
echo "Usage ./cloneService.sh <projects.txt>"
PROJECTS_FILE=$1
echo "Using Projects File Located at : $PROJECTS_FILE"
#Use this block of code to loop thru every line in the projects.txt file
IFS=''
while read line
do
  project_name=$(echo $line | awk '{print $1}')
  github_url=$(echo $line | awk '{print $2}')
  project_path=$HOME"/"$project_name
  asset_path=$HOME"/"$project_name"_ASSETS"
  mkdir -p $project_path
  mkdir -p $asset_path
  git clone $github_url".git" $project_path
  echo "Done cloning "$project_path
done < $PROJECTS_FILE
echo "./cloneService.sh has finished cloning all repositories"
