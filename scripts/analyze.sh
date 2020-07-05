#Project will be the first command line argument
set -e
PROJECT=$1
BRANCH=$2
echo "Starting launcher script for $PROJECT $BRANCH"
#Step 1: First run of the jar to just generate tickets file
echo "Starting Step 1: $PROJECT"
java -cp $HOME/src/RepoAnalyzer-1.0-SNAPSHOT-jar-with-dependencies.jar Application -run 1 -apache $PROJECT -source $HOME/$PROJECT/.git -assets $HOME/$PROJECT"_ASSETS/" #&> /dev/null

#Step 2: Run the rcGenerator.py script to get a list of releases and all files in these releasesu
echo "Starting Step 2: $PROJECT LAST STEP CODE: $?"
python3.6 $HOME/src/rcGenerator.py $HOME/$PROJECT/.git $HOME/$PROJECT"_ASSETS/" $BRANCH #&> /dev/null

#Step 3: Second run of the jar to generate diffs
echo "Starting Step 3: $PROJECT LAST STEP CODE: $?"
java -cp $HOME/src/RepoAnalyzer-1.0-SNAPSHOT-jar-with-dependencies.jar Application -run 2 -apache $PROJECT -source $HOME/$PROJECT/.git -assets $HOME/$PROJECT"_ASSETS/" &> /dev/null

#Step 4: Run the blamer
echo "Starting Step 4: $PROJECT LAST STEP CODE: $?"
python3.6 $HOME/src/blamerAffect.py $HOME/$PROJECT/.git $HOME/$PROJECT"_ASSETS/" $BRANCH $HOME/"BugMaps2/"$PROJECT"_BUGS.TXT" &> /dev/null

#Step 5: 
echo "Starting Step 5: $PROJECT LAST STEP CODE: $?"
java -cp $HOME/src/RepoAnalyzer-1.0-SNAPSHOT-jar-with-dependencies.jar Application -run 3 -apache $PROJECT -source $HOME/$PROJECT/.git -assets $HOME/$PROJECT"_ASSETS/" &> /dev/null

#Step 6:
java -cp $HOME/src/MetricsGenerator-1.0-SNAPSHOT-jar-with-dependencies.jar Application -branch $BRANCH -assetPath $HOME/$PROJECT"_ASSETS/" -projectPath $HOME/$PROJECT/ &> /dev/null

echo "Finished analyzing: $PROJECT $?"
echo "$PROJECT" >> $HOME/done/finished.txt
