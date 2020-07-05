import subprocess
import sys

#This is an updated blamer that will use the affected version
gitDirectory = sys.argv[1].strip()
sourceDirectory = gitDirectory[:-4] #Drops the /.git
assetDirectory = sys.argv[2].strip()
theBranch = sys.argv[3].strip()
bugFile = sys.argv[4].strip() # Used to build the bug map
commitsFile = assetDirectory + "commits.txt"
print("ALL ARGUMENTS ARE ... ", sys.argv)

def getCommitList():
    commitList = []
    with open(commitsFile) as f:
        for line in f:
            tokens = line.strip().split(',')
            commit = tokens[0]
            theTime = int(tokens[3])
            toAdd = (commit, theTime)
            commitList.append(toAdd)
    commitList.sort(key=lambda x: x[1])
    return commitList

#Maps bug id to earliest release else 0
def getBugMap():
    bugMap = dict()  
    with open(bugFile) as f:
        for line in f:
            tokens = line.strip().split()
            bug = tokens[0]
            earliest = 9999999999999
            for i in range(2, len(tokens),2):
                theTime = tokens[i]
                if theTime.isdigit():
                    print(theTime)
                    theTime = int(theTime)
                    earliest = min(earliest, theTime)
            if earliest == 9999999999999:
                earliest = 0
            bugMap[bug] = earliest
    return bugMap

bMap = getBugMap()
#print(bMap)
commitList = getCommitList()
blameCache = dict() #This dictionary will be a lookup table for the commit in the commitList with time >= the value
print(commitList)

#Returns a set of line numbers if there is at least one non-whitespace, non-comment char
def nonCommentLines(fName):
    nonComs = set()
    with open(fName, encoding="utf8", errors='ignore') as f:
        lines = f.read().splitlines()
        lineNo = 1
        inBlockComment = False
        for line in lines:
            line = line.strip()
            if not line.find('//') == 0:
                if line.find("/*") != -1:
                    inBlockComment = True                
                if not inBlockComment and len(line) > 0:
                    nonComs.add(lineNo)
                if inBlockComment and line.find("*/") != -1:
                    inBlockComment = False
            lineNo += 1
    return nonComs
    

with open(assetDirectory + 'changes.txt') as f:
    lines = f.read().splitlines()
    lines = [x.split(",") for x in lines]

currentRepo = "NOT_SET"
blameFilePath = assetDirectory + "blames.txt"
#blameFilePath = assetDirectory + "DEBUGGING.txt"
blameFile = open(blameFilePath,"w") 


out = subprocess.call("git --git-dir=" + gitDirectory + " --work-tree=" + sourceDirectory + " checkout -f " + theBranch, shell=True)
if(out != 0):
    print("FAILED TO CHECKOUT COMMIT MASTER")
    exit()
totalRuns = len(lines)
currentRun = 0
skipCount = 0
timeCommand = 'git --git-dir=' + gitDirectory + ' --work-tree=' + sourceDirectory + ' show -s --format=%ct '
currentTime = subprocess.check_output(timeCommand , shell=True)
currentTime = int(currentTime.decode("utf-8", "ignore"))

for entry in lines:
    print("RUN: " + str(currentRun) + " / " + str(totalRuns))
    currentRun+=1
    repo = entry[0]
    theFile = entry[1]
    lineStart = int(entry[2])
    lineNumbers = int(entry[3])
    issue = entry[4]
    ticketId = entry[4]
    #Need to checkout the correct commit if not 
    if repo != currentRepo:
        out = subprocess.call("git --git-dir=" + gitDirectory + " --work-tree=" + sourceDirectory + " checkout -f " + repo, shell=True)
        currentTime = subprocess.check_output(timeCommand , shell=True)
        currentTime = int(currentTime.decode("utf-8", "ignore"))	
        currentRepo = repo
        if(out != 0):
            print("FAILED TO CHECKOUT COMMIT " + repo)
            exit()
    if ticketId not in bMap:
        bMap[ticketId] = 0
    if bMap[ticketId] != 0: #No need to use SZZ, let's find the first commit in the given release
        skipCount += 1
        issueStartedAt = bMap[ticketId]
        if issueStartedAt not in blameCache:
            for tup in commitList:
                someCommit = tup[0]
                someTime = tup[1]
                blameCache[issueStartedAt] = someCommit
                if someTime >= bMap[ticketId]:
                    break
        nonComLines = nonCommentLines(sourceDirectory + theFile)
        hasNonCommentLine = False
        for lineOffset in range(lineNumbers):
            if lineOffset + lineStart in nonComLines:
                hasNonCommentLine = True
                break
        blameTime = subprocess.check_output(timeCommand + ' ' + someCommit, shell=True) 
        blameTime = int(blameTime.decode("utf-8", "ignore"))
        if hasNonCommentLine and blameTime < currentTime:
            print("Writing with this thing over here")
            blameCommit = blameCache[issueStartedAt]
            blameFile.write(blameCommit + " " + theFile + " " + ticketId + "\n")
            continue
    #OK AT THIS POINT WE ARE IN THE CORRECT COMMIT
    #We need to get the outputs of the git blame
    # command = "git --git-dir=" + gitLocation + " blame " + theFile + " -L" + str(lineStart) + "," + str(lineStart + lineNumbers - 1)
    command = "git --git-dir=" + gitDirectory + " --work-tree=" + sourceDirectory + " blame " + sourceDirectory + theFile + " -L" + str(lineStart) + "," + str(lineStart + lineNumbers - 1)
    out = subprocess.check_output(command , shell=True)    # print(repo + " " + theFile + " " + str(lineStart) + " " + str(lineNumbers) + " " + ticketId)
    out = out.decode("utf-8", "ignore")
    outputLines = out.strip().split("\n")
    badCommits = set()
    nonComLines = nonCommentLines(sourceDirectory + theFile)
    for outLine in outputLines : 
        if lineStart in nonComLines:
            outLine = outLine.strip()
            if len(outLine) == 0:
                pass
            blameCommit = outLine.split()[0].strip()                
            badCommits.add(blameCommit)
            blameCommit = subprocess.check_output("git --git-dir=" + gitDirectory + " rev-parse " + blameCommit , shell=True) #Turn short commit into long commit
            blameCommit = blameCommit.decode("utf-8", "ignore").strip()
            print(blameCommit + " " + theFile + " " + ticketId + " " + str(lineStart))
        lineStart += 1
    for blameCommit in badCommits:
        blameCommit = subprocess.check_output("git --git-dir=" + gitDirectory + " rev-parse " + blameCommit , shell=True) #Turn short commit into long commit
        blameCommit = blameCommit.decode("utf-8", "ignore").strip()
        print("Just a couple penguins hanging out")
        blameFile.write(blameCommit + " " + theFile + " " + ticketId + "\n")
    print("|||")
    print(outputLines)
    print("|||")
blameFile.close()

#Revert the branch back to most recent commit
out = subprocess.call("git --git-dir=" + gitDirectory + " --work-tree=" + sourceDirectory + " checkout -f " + theBranch, shell=True)
if(out != 0):
    print("Failed to checkout file! to", sourceDirectory)
    print("git --git-dir=" + gitDirectory + " checkout -f " + hashId + ' ' + sourceDirectory)
    exit()
# print(lines)
print("SkipCount is ...", skipCount)
