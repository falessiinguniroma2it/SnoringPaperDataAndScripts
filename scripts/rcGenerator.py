import subprocess
import os
import os.path
import sys

gitDirectory = sys.argv[1].strip()
sourceDirectory = gitDirectory[:-5] #Drops the /.git
assetDirectory = sys.argv[2].strip()
theBranch = sys.argv[3].strip()
out = subprocess.call("git --git-dir=" + gitDirectory + " --work-tree=" + sourceDirectory + " checkout -f " + theBranch, shell=True)
if(out != 0):
    print("Failed to checkout! ", sourceDirectory)
    exit()
#Needs to first get a list of all the tags
getAllTagsCommand = 'git --git-dir=' + gitDirectory + ' show-ref --tags -d'      
getUnixTimeCommand = 'git --git-dir=' + gitDirectory + ' show -s --format=%ct '
out = subprocess.check_output(getAllTagsCommand, shell=True)
out = out.decode("utf-8", "ignore")
cntr = 0
outputLines = out.strip().split("\n")
enriched = ""
for line in outputLines :
    tokens = line.strip().split()
    hashId = tokens[0]
    tagName = tokens[1]
    if tagName.endswith('^{}'):
        print(tagName, tagName[:-3])
        tagName = tagName[:-3]    
    commTime = subprocess.check_output(getUnixTimeCommand + hashId, shell=True)
    commTime = commTime.decode('utf-8', 'ignore').strip()
    parsedCommTime = commTime.split()
    

    #In this case -> we are returned just the time, This is a legit commit, everything else noise
    if len(parsedCommTime) == 1:
        out = subprocess.call("git --git-dir=" + gitDirectory + " --work-tree=" + sourceDirectory + " checkout -f " + hashId, shell=True)
        if(out != 0):
            print("Failed to checkout file! to", sourceDirectory)
            print("git --git-dir=" + gitDirectory + " checkout -f " + hashId + ' ' + sourceDirectory)
            exit()

        files = []
        for dirpath, dirnames, filenames in os.walk(sourceDirectory):
            for filename in [f for f in filenames if f.endswith(".java")]:
                files.append(os.path.join(dirpath, filename).replace('\\', '/')[len(sourceDirectory) + 1:])
        cntr+=1
        outline = hashId + " " + tagName + " " + commTime
        #enriched = enriched + hashId + " " + tagName + " " + commTime
        for javaFile in files:
                #enriched = enriched + " " + javaFile
                outline = outline + " " + javaFile
        outline = outline + '\n'
        enriched += outline
        print (parsedCommTime)
#This is the number of releases found, should match the # of rcs on github for repo
print("Found ", cntr, "releases")
out = subprocess.call("git --git-dir=" + gitDirectory + " --work-tree=" + sourceDirectory + " checkout -f " + theBranch, shell=True)
if(out != 0):
    print("Failed to checkout file! to", sourceDirectory)
    print("git --git-dir=" + gitDirectory + " checkout -f " + hashId + ' ' + sourceDirectory)
    exit()
with open(assetDirectory + "tags.txt", "w") as tags_file:
    tags_file.write(enriched)
print("DONE WRITING ", (assetDirectory + "tags.txt"));
