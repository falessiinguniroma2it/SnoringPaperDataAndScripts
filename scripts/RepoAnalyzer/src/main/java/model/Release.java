package model;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

public class Release {

    public final int SECONDS_IN_DAY = 86400;
    private OkHttpClient client;
    private ObjectMapper mapper;
    private GitRepository repo;

    public List<Commit> commits;
    public HashMap<String, FileStats> fileStats;
    public HashSet<String> injected;
    public List<String> allFiles;

    //These functions will be used to compute metrics for RQ2
    //Note that the release #(absolute order) or the relative order info will not be stored here

    //Metric 3 : Number of days in the release, ROUNDED UP
    public int getReleaseWindow() {
        int start = commits.get(0).getTime();
        int end = commits.get(commits.size()-1).getTime();
        int secondDuration = end - start;
        int dayDuration = secondDuration / SECONDS_IN_DAY;
        return dayDuration + 1;
    }

    //Metric 4: Number of open(Not fixed) bug tickets before release ends
    public int getOpenBugCount(String project) throws IOException {
        long start = commits.get(0).getTime();
        long end = commits.get(commits.size()-1).getTime();
        Date startDate = new Date(start * 1000);
        Date endDate = new Date(end * 1000);

        //2013/01/01
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy/MM/dd");
        String startString = "\"" + formatter.format(startDate) + "\"";
        String endString = "\"" + formatter.format(endDate) + "\"";

        String theQuery = "https://issues.apache.org/jira/rest/api/2/search?jql=project=" + project + " and issueType = Bug and Resolution !=fixed and created <= " + endString;

        //Make this query and the total
        Map<String, Object> jiraResults = get(theQuery);
        return (int) jiraResults.get("total");
    }
    private Map<String, Object> get(String url) throws IOException {
        if(client == null) {
            this.client = new OkHttpClient();
            this.mapper = new ObjectMapper();
        }
        Request request = new Request.Builder()
                .url(url)
                .build();
        Response response = client.newCall(request).execute();
        if(response.code() != 200) {
            throw new RuntimeException("On request to get ticket count from jira, non 200 response : " + response.code());
        }
        String json = response.body().string();
        return mapper.readValue(json, new TypeReference<HashMap<String, Object>>() {});
    }

    //Metric 5: Number of commits
    public int getNumberOfCommits() {
        return commits.size();
    }

    //Metric 6 : Number of tickets fixed of any type (between window)
    public int getClosedTicketCount(String project) throws IOException {
        long start = commits.get(0).getTime();
        long end = commits.get(commits.size()-1).getTime();
        Date startDate = new Date(start * 1000);
        Date endDate = new Date(end * 1000);
        //2013/01/01
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy/MM/dd");
        String startString = "\"" + formatter.format(startDate) + "\"";
        String endString = "\"" + formatter.format(endDate) + "\"";

        String theQuery = "https://issues.apache.org/jira/rest/api/2/search?jql=project=" + project + " and resolutiondate  >= " + startString + " and resolutiondate  <= " + endString;
        System.out.println("Created with " + start);
        System.out.println("Start is " + startDate);
        System.out.println("iow " + startDate.getTime());
        System.out.println("Query is...");
        System.out.println(theQuery);
        //Make this query and the total
        Map<String, Object> jiraResults = get(theQuery);
        return (int) jiraResults.get("total");
    }

    //Metric 7: LOC touched, a metric of how many
    public int getLinesChanged() throws GitAPIException, IOException {
        int locTouched = 0;
        for(int i = 1; i < commits.size(); i++) {
            String oldCommit = commits.get(i-1).getId();
            String newCommit = commits.get(i).getId();
            List<Change> changes = repo.diff(oldCommit, newCommit, "tickedid unused");
            for(Change c : changes) {
                locTouched += c.getNumberOfLinesTouched();
            }
        }
        return locTouched;
    }

    public Release(List<Commit> commits, List<String> allFiles, GitRepository repo){
        this.allFiles = allFiles;
        System.out.println(allFiles);
        this.commits= commits;
        this.repo = repo;
        this.fileStats = new HashMap<>();
        if(allFiles != null) {
            for (String file : allFiles) {
                fileStats.put(file, new FileStats());
            }
        }
    }

    public List<String> getStillSleeping() {
        List<String> reports = new ArrayList<>();
        for(String key : fileStats.keySet()) {
            FileStats fs = fileStats.get(key);
            for(String bug : fs.sleeping) {
                reports.add(key + " " + bug); //File name <space> bug
            }
        }
        return reports;
    }
    //Get the files and bugs that slept 0 releases but were a post release defect
    public List<String> getJustInjected() {
        HashSet<String> sleeping = new HashSet<>();
        sleeping.addAll(getStillSleeping());
        List<String> reports = new ArrayList<>();
        for(String key : fileStats.keySet()) {
            FileStats fs = fileStats.get(key);
            for(String bug : fs.injected) {
                if(!sleeping.contains(key + " " + bug)) {
                    reports.add(key + " " + bug); //File name <space> bug
                }
            }
        }
        return reports;
    }


    public void setAllInjected(HashMap<String, String> fixMap) {
        injected = new HashSet<>();

        for(Commit c : commits) {
            for(String report : c.injectList) {
                String releaseWhenBugFixed = fixMap.get(report);
                System.out.println("RBF " + releaseWhenBugFixed);
                System.out.println("ReleaseName " + getReleaseName());
                //Ignore the bug if it was fixed in the release, these are out of scope for study
                if(releaseWhenBugFixed.equals(getReleaseName())) continue;
                System.out.println(injected);
                injected.add(report.trim());
            }
        }
        for(String report : injected) {
            String [] tokens = report.split(" ");
            String fName = tokens[0];
            String bug = tokens[1];
            if(!fileStats.containsKey(fName)) {
                fileStats.put(fName, new FileStats());
                allFiles.add(fName);
            }
            fileStats.get(fName).addInjection(bug);
        }
    }
    //fixMap indicates for every report : the release that it was last fixed
    public HashSet<String> setStartedSleeping(HashMap<String, String> fixMap) {
        HashSet<String> startSleep = new HashSet<>();
        for(String report : injected) {
            String [] tokens = report.split(" ");
            String fName = tokens[0].trim();
            String bug = tokens[1].trim();
            //Check if solved in the commits for the given release
            //if so, the bug did not start sleeping
            String releaseWhenBugFixed = fixMap.get(report);
            if(releaseWhenBugFixed.equals(getReleaseName())) continue;

            startSleep.add(report.trim());
            if(!fileStats.containsKey(fName))fileStats.put(fName, new FileStats());
            fileStats.get(fName).startedSleeping(bug);
        }
        return startSleep;
    }

    public void setSleeping(HashSet<String> prevInjected, HashMap<String, String> fixMap) {
        List<String> removeList = new ArrayList<>();
        for(String report : prevInjected) {
            String [] tokens = report.split(" ");
            String fName = tokens[0];
            String bug = tokens[1];
            //Check if solved in the commits for the given release
            //if so, the bug is not sleeping anymore
            String releaseWhenBugFixed = fixMap.get(report);
            if(releaseWhenBugFixed.equals(getReleaseName())) {
                removeList.add(report);
                continue;
            }
            if(!fileStats.containsKey(fName))fileStats.put(fName, new FileStats());
            fileStats.get(fName).addSleeping(bug);
        }
        for(String report : removeList) {
            prevInjected.remove(report);
        }
        System.out.println("Still Sleeping " + prevInjected);
    }


    public String getId() {
        return commits.get(0).getId();
    }
    public String getReleaseName() {
        return commits.get(0).getReleaseName();
    }
    public String write() {
        String ret = "";
        String rName = getReleaseName();
        if(rName.equals("null")) {
            System.out.println("not outputing metrics for commits after last release but before another release");
            return "";
        }
        for(String key : fileStats.keySet()) {
            fileStats.get(key).setDefective();
        }
        for(Map.Entry<String, FileStats> entry : fileStats.entrySet()) {
            int injected = entry.getValue().injected.size();
            int startedSleeping = entry.getValue().startedSleeping.size();
            int sleeping = entry.getValue().sleeping.size();
            int defective = entry.getValue().defective.size();
            //boolean shouldEmit = injected != 0 || startedSleeping != 0 || sleeping != 0 || defective != 0;
            //if(shouldEmit) {
                ret += entry.getKey() + "," + rName + "," +
                        injected + "," +
                        startedSleeping + "," +
                        sleeping + "," +
                        defective;
                ret += '\n';
         //   }
        }
        return ret.trim();
    }

    //Remove the most revent sleeping entry and if sleeping for 1 momemnt, remove started sleeping
    public void prune(HashSet<String> pruned) {
        for(FileStats f : fileStats.values()) {
            f.prune(pruned);
        }

    }

    public void setFixed(HashMap<String, List<String>> fixMapV2) {
        HashSet<String> injects = new HashSet<>();
        for(Commit c : commits) {
            injects.addAll((c.injectList));
        }
        List<String> fixes = fixMapV2.get(this.getReleaseName());
        //If fixes were null, no fixes were introduced in this release
        if(fixes == null) return;
        for(String report : fixes) {
            if(injects.contains(report)) continue;
            String [] tokens = report.split(" ");
            String fName = tokens[0];
            String bug = tokens[1];
            if(!fileStats.containsKey(fName)) fileStats.put(fName, new FileStats());
            fileStats.get(fName).fix(bug);
        }
    }
}
