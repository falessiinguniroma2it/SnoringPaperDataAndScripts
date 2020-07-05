package model;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;

import java.io.*;
import java.util.*;

public class GitRepository {

    final static String NO_TAG = "COMMIT_CREATED_BEFORE_ANY_RELEASES";
    private File localDirectory;
    private Git git;
    private Repository repository;
    private TicketPool ticketPool;
    private List<String> ticketIds;
    public List<Commit> commits;
    private List<Change> changeList;
    public HashMap<String, List<String>> blameMap;
    List<Tag> allTags;
    public Map<String,List<String>> fileMap;

    public GitRepository(String localPath, TicketPool tp) throws IOException, GitAPIException {
        this.localDirectory = new File(localPath);
        this.git = Git.open(localDirectory);
        this.repository = this.git.getRepository();
        this.ticketPool = tp;
        this.ticketIds = ticketPool.getAllTicketIds();
    }

    //git show-ref --tags -d
    public void setReleaseIds(String assetDirectory) throws FileNotFoundException {
        allTags = new ArrayList<>();
        fileMap = new HashMap<>();
        File tagged = new File(assetDirectory + "tags.txt");
        Scanner fScanner = new Scanner(tagged);
        while(fScanner.hasNextLine()) {
            String line = fScanner.nextLine();
            String [] tokens = line.split(" ");
            String id = tokens[0];
            String rName = tokens[1];
            int time = Integer.parseInt(tokens[2]);
            Tag tag = new Tag(rName, time);
            allTags.add(tag);

            List<String> allFiles = new ArrayList<>();
            for(int i = 3; i < tokens.length; i++) {
                allFiles.add(tokens[i]);
            }
            fileMap.put(rName, allFiles);
        }
        Collections.sort(allTags);
        fScanner.close();
    }

    public GitRepository(String githubUrl, String localPath) throws GitAPIException{
        this.localDirectory = new File(localPath);
        this.git = Git.cloneRepository()
                .setURI(githubUrl)
                .setDirectory(localDirectory)
                .call();
        this.repository = this.git.getRepository();
    }

    public String getReleaseName(int rTime) {
        int index = 0;
        while (index < allTags.size() && allTags.get(index).epoch < rTime) {
            index++;
        }
        if(index == allTags.size()) return null;
        return allTags.get(index).tag;
    }

    public List<Commit> getCommitIds() throws GitAPIException, IOException {
        Iterable<RevCommit> commits = git.log().call();

        List<Commit> commitList = new ArrayList<>();
        HashSet<String> tags = new HashSet<>();
        for(RevCommit commit : commits) {
            String ticketId = getJiraTicketId(commit);
            String commmitId = commit.getName();
            int rTime = commit.getCommitTime();
            String tag = getReleaseName(rTime);
            System.out.println(commmitId+ " " + tag);
            tags.add(tag);
            Commit newCommit = new Commit(tag, commmitId, ticketId, rTime);

            commitList.add(newCommit);
        }
        System.out.println(tags.size());
        for(Tag t : allTags) {
            if(tags.contains(t.tag)) {
                System.out.println(t.tag);
            }
        }
        return commitList;
    }
    public void writeCommitsToFile(String fileLocation) throws IOException {
        File writeFile = new File(fileLocation);
        writeFile.createNewFile();
        FileWriter fW = new FileWriter(writeFile);
        for(Commit c : commits) {
            fW.write(String.format("%s,%s,%s,%d,%s\n",
                    c.getId(), c.getReleaseName(), c.getJiraTicket(), c.getTime(), c.serializeFilesTouched()));
        }
        fW.close();
    }

    public String getJiraTicketId(RevCommit commit) {
        String shortMessage = commit.getShortMessage().toUpperCase();
        String bestCandidate = "";
        int idx = -1;
        for(String ticketId: ticketIds) {
            if(shortMessage.contains(ticketId) && (idx == -1 || shortMessage.indexOf(ticketId) <  idx)) {
                //If there are more character that are part of the ticket, continue
                char nextChar = ' ';
                if(shortMessage.indexOf(ticketId) + ticketId.length() != shortMessage.length()){
                    nextChar = shortMessage.charAt(shortMessage.indexOf(ticketId) + ticketId.length());
                }
                if(nextChar <= '9' && nextChar >= '0') {
                    continue;
                }
                idx = shortMessage.indexOf(ticketId);
                bestCandidate = ticketId;
            }
        }
        if(bestCandidate.length() > 0) {
            return bestCandidate;
        }
        String longMessage = commit.getFullMessage().toUpperCase();
        for(String ticketId: ticketIds) {
            if(longMessage.contains(ticketId) && (idx == -1 || longMessage.indexOf(ticketId) <  idx)) {
                char nextChar = ' ';
                if(longMessage.indexOf(ticketId) + ticketId.length() != longMessage.length()){
                    nextChar = longMessage.charAt(longMessage.indexOf(ticketId) + ticketId.length());
                }
                if(nextChar <= '9' && nextChar >= '0') {
                    continue;
                }
                //System.out.println("CHUNKY MONKEY");
                idx = longMessage.indexOf(ticketId);
                bestCandidate = ticketId;
            }
        }
        return bestCandidate;
//        if(idx != -1) return bestCandidate;
        /*String longMessage = commit.getFullMessage().toUpperCase();
        for(String ticketId: ticketIds) {
            if(longMessage.contains(ticketId) && (idx == -1 || longMessage.indexOf(ticketId) <  idx)) {
                System.out.println("CHUNKY MONKEY");
                idx = longMessage.indexOf(ticketId);
                bestCandidate = ticketId;
            }
        }*/
//        return bestCandidate;
    }

    public List<Release> getReleaseCommits() throws GitAPIException, IOException {
        List<Commit> current = new ArrayList<>();
        List<Release> releaseCommits = new ArrayList<>();
        List<Commit> commitsReversed = new ArrayList<>();
        commitsReversed.addAll(commits);
        Collections.reverse(commitsReversed);
        String rName = commitsReversed.get(0).getReleaseName();
        for(Commit commit : commitsReversed) {
            if(!commit.getReleaseName().equals(rName)){
                Release rc = new Release (current, fileMap.get(rName), this);
                current = new ArrayList<>();
                releaseCommits.add(rc);
                rName = commit.getReleaseName();
            }
            current.add(commit);
        }
        releaseCommits.add(new Release (current, fileMap.get(rName), this));
        Collections.reverse(commits);
        return releaseCommits;
    }

    //Assumes that we are already checked out to the write repository
    public List<String> getAllJavaAbsoluteFilePaths() {
        return listFilesForFolder(localDirectory);
    }

    public List<String> listFilesForFolder(final File folder) {
        List<String> files = new ArrayList<>();
        for (final File fileEntry : folder.listFiles()) {
            if (fileEntry.isDirectory()) {
                files.addAll(listFilesForFolder(fileEntry));
            } else {
                if(fileEntry.getAbsolutePath().endsWith(".java")) {
                    files.add(fileEntry.getAbsolutePath());
                }
            }
        }
        return files;
    }


    private CanonicalTreeParser createTree(String commit) throws IOException {
        CanonicalTreeParser tree = new CanonicalTreeParser();
        ObjectId treeId = repository.resolve( commit+"^{tree}");
        try( ObjectReader reader = repository.newObjectReader() ) {
            tree.reset( reader, treeId );
        }
        return tree;
    }
public static int cSize = 0;
    public List<Change> diff(String oldVersion, String newVersion, String ticketId) throws GitAPIException, IOException {
        List<Change> changes = new ArrayList<>();
        CanonicalTreeParser oldTree = createTree(oldVersion);
        CanonicalTreeParser newTree = createTree(newVersion);

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        DiffFormatter df = new DiffFormatter(stream);
        df.setDiffComparator( RawTextComparator.WS_IGNORE_ALL );
        df.setContext(0);
        df.setRepository(repository);
        List<DiffEntry> diffEntries = df.scan( oldTree, newTree);

        for( DiffEntry entry : diffEntries ) {
            String oldPath = entry.getOldPath();

            //Skip all non .java files
            if(!oldPath.endsWith(".java")) continue;

            df.setContext(0);
            df.format( entry );
            String diffOutput = stream.toString();

            List<int []> lsList = getDeleteLinesFromDiff(diffOutput);
            for(int [] ls : lsList){
                if(ls == null || ls[0] == 0) continue; //If no deletions or modifications, skip
                Change thisChange = new Change(oldVersion, oldPath, ls[0], ls[1], ticketId, newVersion);
                changes.add(thisChange);
            }
            //Flush the previous diffentry parsed
            stream.reset();
        }
        cSize += changes.size();
        return changes;
    }

    public void loadCommitsFromFile(String fPath) throws FileNotFoundException {
        File commitsFile = new File(fPath);
        Scanner fScanner = new Scanner(commitsFile);
        commits = new ArrayList<>();
        while(fScanner.hasNextLine()) {
            String line = fScanner.nextLine();

            String [] tokens = line.split(",", -1);
            String hash = tokens[0];
            String rName = tokens[1];
            String jiraTicket = tokens[2];
            int rTime = Integer.parseInt(tokens[3]);
            List<String> changedFiles = new ArrayList<>();
            if(tokens.length == 5 && !tokens[4].equals("")) {
                String file = tokens[4];
                changedFiles.add(file);
            }
            for(int ind = 4; ind < tokens.length; ind++) changedFiles.add(tokens[ind].trim());
            commits.add(new Commit(rName, hash, jiraTicket, rTime, changedFiles));
        }

        System.out.println("Loaded commits from file. COUNT: " + commits.size());
        fScanner.close();
    }
    public List<Change> getChangeList() {
        changeList = new ArrayList<>();
        return changeList;
    }

//This now ought to return a list of int arrays : one diff can have multiple entries
    public List<int []> getDeleteLinesFromDiff(String diff) {
        List<int []> diffs = new ArrayList<>();
        Scanner diffScanner = new Scanner(diff);
        while(diffScanner.hasNextLine()) {
            String line = diffScanner.nextLine();
            //"+0,0" Meant to make sure that this isnt also a file deletion
            if(line.length() >0 && line.charAt(0) == '@' && line.indexOf("+0,0") == -1) {
                String [] tokens = line.split(" ");
                String deleteString = tokens[1];
                deleteString = deleteString.substring(1); //Remove the initial - sign
                if(deleteString.contains(",")) {
                    tokens = deleteString.split(",");
                    int first = Integer.parseInt(tokens[0]);
                    int second = Integer.parseInt(tokens[1]);
                    //We only want to add this record if there is a non-0 count of lines
                    if(second != 0) {
                        diffs.add(new int[]{first, second});
                    }
                }else {
                    int lineNumber = Integer.parseInt(deleteString);
                    diffs.add(new int[]{lineNumber, 1});
                }
            }
        }
        diffScanner.close();
        return diffs;
    }

    public void loadBlamesFromFile(String fPath) throws FileNotFoundException {
        File blamesFile = new File(fPath);
        Scanner fScanner = new Scanner(blamesFile );
        blameMap = new HashMap<>();
        while(fScanner.hasNextLine()) {
            String line = fScanner.nextLine();
            String [] tokens = line.split(" ", -1);
            if(tokens.length != 3) continue;
            String id = tokens[0];
            String file = tokens[1];
            String ticket = tokens[2];
            if(!blameMap.containsKey(id)) blameMap.put(id, new ArrayList<>());
            List<String> lis = blameMap.get(id);
            lis.add(file + " " + ticket);
        }
        fScanner.close();
        System.out.println("Done loading blames from file!");
    }
    public void assignInjectDate() {
        HashSet<String> alreadyInjected = new HashSet<>();
        Collections.reverse(commits);
        for(Commit commit : commits) {
            String id = commit.getId();
            commit.injectList = new ArrayList<>();
            if(blameMap.containsKey(id)) {
                for (String report : blameMap.get(id)) {
                    if (alreadyInjected.contains(report)) {
                        continue;
                    }
                    alreadyInjected.add(report);
                    commit.injectList.add(report);
                }
            }
        }
        Collections.reverse(commits);
    }

    public void setChangedJavaFiles() throws GitAPIException, IOException {
        for(int i = 0; i < commits.size() - 1; i++) {
            List<Change> changes = diff(commits.get(i+1).getId(), commits.get(i).getId(), commits.get(i).getJiraTicket());
            for(Change change : changes) {
                commits.get(i).touchedJavaFiles.add(change.getFile());
            }
        }
    }
}
