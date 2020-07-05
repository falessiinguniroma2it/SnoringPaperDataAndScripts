import model.*;
import org.apache.commons.cli.*;
import org.apache.log4j.Logger;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.InvalidPatternException;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

//Invocation would Ideally look something like this :
//java -cp someJar.jar Application <ALL-ARGS>
/*
Command line arguments
-run: This tells the program which block of code to instrument
-apache: This is the keyword that helps us id the ticket
-directory: This is the location that the git repository exists includes the .git
-assetsDirectory: This is the directory that all intermediate and final values will be stored
 TODO: use the logger
 */
public class Application {

    //THESE ARE THE COMMAND LINE PARAMS
    public static int RUN_NUMBER;
    public static String APACHE_TAG;
    public static String SOURCE_REPOSITORY;
    public static String ASSET_DIRECTORY;

    final static Logger log = Logger.getLogger(Application.class);

    public static void parseArguments(String [] args) throws ParseException {
        Option runOption = Option.builder("run")
                .required(true)
                .hasArg()
                .build();
        Option apacheOption = Option.builder("apache")
                .required(true)
                .hasArg()
                .build();
        Option sourceDirectoryOption = Option.builder("source")
                .required(true)
                .hasArg()
                .build();
        Option assetDirectoryOption= Option.builder("assets")
                .required(true)
                .hasArg()
                .build();
        Options options= new Options()
                .addOption(runOption)
                .addOption(apacheOption)
                .addOption(sourceDirectoryOption)
                .addOption(assetDirectoryOption);

        CommandLineParser parser = new DefaultParser();
        CommandLine parsed = parser.parse(options, args);

        String runString = parsed.getOptionValue("run");
        RUN_NUMBER = Integer.parseInt(runString);

        String apacheString = parsed.getOptionValue("apache");
        String sourceString = parsed.getOptionValue("source");
        String assetString = parsed.getOptionValue("assets");

        APACHE_TAG = apacheString;
        SOURCE_REPOSITORY = sourceString;
        ASSET_DIRECTORY = assetString;
    }


    public static void main(String [] args) throws GitAPIException, IOException, InvalidPatternException, InterruptedException, ParseException {
        //First thing to do is parse the arguments
        parseArguments(args);

        //This is the flag to compute metrics arround the project
        if(RUN_NUMBER == 10) {
            TicketPool tp = new TicketPool(ASSET_DIRECTORY + "tickets.txt", true);
            GitRepository repo = new GitRepository(SOURCE_REPOSITORY, tp);
            repo.setReleaseIds(ASSET_DIRECTORY);
            return;
        }
        if(RUN_NUMBER == 0) {
            System.out.println("This is a debug branch. Code should never reach here!");
            return;
        }

        //Todo: Run [1/3] , Simply create the ticketpool
        if(RUN_NUMBER == 1) {
            TicketPool tp = new TicketPool(APACHE_TAG);
            tp.writeToFile(ASSET_DIRECTORY + "tickets.txt");
            return;
        }
        TicketPool tp = new TicketPool(ASSET_DIRECTORY + "tickets.txt", true);
        GitRepository repo = new GitRepository(SOURCE_REPOSITORY, tp);

        //Todo: Run [2/3] Get the list of changes that will be fed to the blamer.py
        //This block of code should always be uncommented
        repo.setReleaseIds(ASSET_DIRECTORY);

        repo.commits = repo.getCommitIds();
        repo.setChangedJavaFiles();
        repo.writeCommitsToFile(ASSET_DIRECTORY + "commits.txt");
        //End of block of code to always be live

        //USE THIS TO GET THE SET OF CHANGES
        List<Commit> allCommits = repo.commits;
        List<Change> allChanges = new ArrayList<>();
        int counter = 0;
        for (int i = 0; i < allCommits.size() - 1; i++) {
            //System.out.println(i);
            String ticketId = allCommits.get(i).getJiraTicket();
            if (ticketId.length() == 0) continue;
            List<Change> changes = repo.diff(allCommits.get(i + 1).getId(), allCommits.get(i).getId(), ticketId);
            counter += changes.size();
//            System.out.println("FROM APP change size " + counter);
            allChanges.addAll(changes);
        }
        System.out.println("All changes are ..." + allChanges.size() + " Commit size: " + allCommits.size());
        //System.out.println(allCommits.size());
        writeChangesToFile(allChanges, ASSET_DIRECTORY + "changes.txt");


        //Todo: Run [3/3] We run this block of code after running the blamer also run the previous block
        if(RUN_NUMBER == 3) {
            repo.loadCommitsFromFile(ASSET_DIRECTORY + "commits.txt");
            repo.loadBlamesFromFile(ASSET_DIRECTORY + "blames.txt");
            repo.assignInjectDate();
            List<Release> rcs = repo.getReleaseCommits();
//            for(Release rel : rcs) {
//                for(Commit commit : rel.commits) {
//                    System.out.println(commit.getTime() + " " + commit.getId() + rel.getReleaseName());
//                }
//            }
//            System.out.println("OUTTING ALL commies");
            mySerialize(rcs, repo.commits);
            mySerialize2(rcs);
        }
        if(RUN_NUMBER == 4) {
            repo.loadCommitsFromFile(ASSET_DIRECTORY + "commits.txt");
            List<Release> rcs = repo.getReleaseCommits();
            mySerializeRQ2(rcs);
        }
    }

    private static void mySerializeRQ2(List<Release> rcs) throws IOException, GitAPIException {
        File f = new File(ASSET_DIRECTORY + "rq2.csv");
        f.createNewFile();
        FileWriter fw = new FileWriter(f);

        String header = "Absolute,Relative,Length(days),Open Bugs,# Commits, #Tickets Resolved, LOC touched";
        fw.write(header + '\n');
        int rCount = 0;
        for(Release rc : rcs) {
            if(rc.getReleaseName() == null || rc.getReleaseName().equals("null")) continue;;
            rCount++;
        }
        int current = 1;
        String body = "";
        for(Release rc : rcs) {
            if(rc.getReleaseName() == null || rc.getReleaseName().equals("null")) continue;;
            String absolute = current +"";
            String relative = ""+ (current + 0.0) / rCount;
            String length = rc.getReleaseWindow()+"";
            String open = ""+rc.getOpenBugCount(APACHE_TAG);
            String commits = rc.getNumberOfCommits()+"";
            String resolved = "" + rc.getClosedTicketCount(APACHE_TAG);
            String touched = "" + rc.getLinesChanged();
            body += absolute + ',' + relative + "," + length + ',' + open + ',' + commits + ',' + resolved + ',' + touched + '\n';
            current++;
        }
        body = body.trim();
        fw.write(body);
        System.out.println(header);
        System.out.println(body);
        fw.close();
    }

    private static void mySerialize2(List<Release> rcs) throws IOException {
        HashMap<String, Integer> sleepMap = new HashMap<>();
        for(Release rc : rcs) {
            List<String> sleeping = rc.getStillSleeping();
            for(String report : sleeping) {
                if(sleepMap.containsKey(report)) {
                    sleepMap.put(report, sleepMap.get(report) + 1);
                }else {
                    sleepMap.put(report, 1);
                }
            }
            List<String> justInjected = rc.getJustInjected();;
            for(String injectOnly : justInjected) {
                sleepMap.put(injectOnly, 0);
            }
        }
        File f = new File(ASSET_DIRECTORY + "report2.csv");
        f.createNewFile();
        FileWriter fw = new FileWriter(f);
        fw.write("File,Bug,Releases Slept\n");
        for(String key : sleepMap.keySet()) {
            String [] tokens = key.split(" ");
            String file = tokens[0];
            String bug  = tokens[1];
            int sleepLength = sleepMap.get(key);
            fw.write(file+","+bug+","+sleepLength + "\n");
        }
        fw.close();
    }

    //
    private static void  mySerialize(List<Release> rcs, List<Commit> allCommits) throws IOException {
        System.out.println("---"  + rcs.size());
        File f = new File(ASSET_DIRECTORY + "report.csv");
        f.createNewFile();
        FileWriter fw = new FileWriter(f);
        HashMap<String, String> fixMap = getFixMap(allCommits);
        HashMap<String, List<String>> fixMapV2 = getFixMapForRelease(allCommits);
        Set<String> allFiles = new HashSet<>();
        for(Release rc : rcs) {
            System.out.println(rc.getReleaseName());
            //Ignore the nulls, these do not belong to any commit
            if(rc.getReleaseName() == null || rc.getReleaseName().equals("null")) continue;
            rc.setAllInjected(fixMap);
            rc.setFixed(fixMapV2);
            allFiles.addAll(rc.allFiles);
        }
        //Output the header over here
        String header = "Class";
        int theSize = 0;
        for(Release rc : rcs) {
            System.out.println("chubby we " + rc.getReleaseName());
            if(rc.getReleaseName() == null || rc.getReleaseName().equals("null")) continue;
            theSize++;
        }
        for(int i = 1; i <= theSize; i++) {
            header += ", R" + i;
        }
        fw.write(header + '\n');
        for(String fName : allFiles) {
            String emitLine = fName;
            for(Release rc : rcs) {
                if(rc.getReleaseName() == null || rc.getReleaseName().equals("null")) continue;
                if(!rc.fileStats.containsKey(fName)) {
                    emitLine += ",no-op";
                    continue;
                }
                String emitString = ",";
                FileStats fs = rc.fileStats.get(fName);
                for(String report: fs.injected) {
                    emitString += " INJECT " + report;
                }
                for(String report: fs.fixed) {
                    emitString += " FIX " + report;
                }
                emitString = emitString.trim();
                emitLine += emitString;
            }
            fw.write(emitLine + '\n');
        }
        fw.close();

        //Time to serialize the releases into
        //This list will maintain all the reports that have been left trimmed
        /*DO NOT PRUNE ANYMORE, Sleeping will now represent the set of post release defects*/
        /*
        HashSet<String> pruned = new HashSet<>();
        for(int i = rcs.size()-1; i >=0; i--) {
            Release rc = rcs.get(i);
            rc.prune(pruned);
            //Prune this entry and go on to the next one
        }*/

        //We are no longer serizling this table
//        fw.write("File,Release,Injected,Started Sleeping,Sleeping,Defective\n");
//        for(Release rc : rcs) {
//            String toWrite = rc.write();
//            fw.write(toWrite);
//            if(toWrite.length() > 0) fw.write('\n');
//        }
//        //Need to cleanse the release commits to remove the right most instance of the sleeping,
//            //If started sleeping in the same commit, this bug is no longer sleeping
//
//        System.out.println("NOT FOUND " + sleepingBugs);
//        fw.close();
    }

    //Requires all of the commits with most recent first and older later
    //Will for each report indicate the release in which the issue was last touched :
        //At this point the  issue w
    public static HashMap<String, String> getFixMap(List<Commit> allCommits) {
        HashMap<String, String> fixMap = new HashMap<>();
        for(Commit c : allCommits) {
            String bug = c.getJiraTicket();
            String release = c.getReleaseName();
            if(bug.equals("") || c.equals(null) || c.equals("null")) continue; //Ignore all commits without jira id
            List<String> changedSet = c.touchedJavaFiles;
            for(String file : changedSet) {
                String report = file + " " + bug;
                if(!fixMap.containsKey(report)) {
                    fixMap.put(report, release);
                }
            }
        }
        System.out.println("Done creating the fix map!");
        return fixMap;
    }

    //A function to get all the fixes introduced in a given releaase
    public static HashMap<String, List<String>> getFixMapForRelease(List<Commit> allCommits) {
        //Map<Release String, List<Report String>>
        HashMap<String, List<String>> releaseMap = new HashMap<>();
        for(Commit c : allCommits) {
            String bug = c.getJiraTicket();
            if(bug.equals("") || c.equals(null) || c.equals("null") || c.getReleaseName() == null || c.getReleaseName().equals("null")) continue; //Ignore all commits without jira id
            List<String> changedSet = c.touchedJavaFiles;
            String releaseName = c.getReleaseName();
            for(String file : changedSet) {
                String report = file + " " + bug;
                if(!releaseMap.containsKey(releaseName)) releaseMap.put(releaseName, new ArrayList<>());
                releaseMap.get(releaseName).add(report);
            }
        }
        System.out.println("Done creating the release fix map!");
        return releaseMap;
    }

    public static void writeChangesToFile(List<Change> changes, String fPath) throws IOException {
        File f = new File(fPath);
        f.createNewFile();
        FileWriter fw = new FileWriter(f);
        for(Change change : changes) {
            fw.write(change.toString());
            fw.write("\n");
        }
        fw.close();
    }

}