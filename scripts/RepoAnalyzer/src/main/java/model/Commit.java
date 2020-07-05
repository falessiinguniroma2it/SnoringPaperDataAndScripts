package model;

import java.util.ArrayList;
import java.util.List;

public class Commit {
    private String releaseName;
    private String commitID;
    private String jiraTicket;
    private int time;
    public List<String> injectList;
    public List<String> touchedJavaFiles;

    public Commit(String releaseName, String commitId, String jiraTicket, int time, List<String> touched){
        this.releaseName = releaseName;
        this.commitID = commitId;
        this.jiraTicket = jiraTicket;
        this.time = time;
        this.touchedJavaFiles = touched;
    }

    public Commit(String releaseName, String commitId, String jiraTicket, int time){
        this(releaseName, commitId, jiraTicket, time, new ArrayList<>());
    }

    public String getJiraTicket() {return jiraTicket;}

    public String getReleaseName() {
        return releaseName;
    }
    public int getTime() {
        return time;
    }

    public String getId() {
        return commitID;
    }

    public String toString() {
        return commitID + " " + releaseName;
    }

    public String serializeFilesTouched() {
        String output = "";
        for(int i = 0; i < touchedJavaFiles.size(); i++) {
            if(i != 0) output += ',';
            output += touchedJavaFiles.get(i);
        }
        return output;
    }
}
