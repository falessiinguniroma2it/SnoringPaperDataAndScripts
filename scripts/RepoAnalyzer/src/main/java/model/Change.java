package model;

public class Change {

    private String id;
    private String fPath;
    private int startLine;
    private int numberOfLines;
    private String ticketId;
    private String newVersion;

    public Change(String id, String fPath, int startLine, int numberOfLines, String ticketId, String newVersion) {
        this.id = id;
        this.fPath = fPath;
        this.startLine = startLine;
        this.numberOfLines = numberOfLines;
        this.ticketId = ticketId;
        this.newVersion = newVersion;
    }
    public String toString() {
        return id + "," + fPath + "," + startLine + "," + numberOfLines + "," + ticketId+","+newVersion;
    }
    public String getFile() {
        return fPath;
    }

    public int getNumberOfLinesTouched() {return numberOfLines;}
}
