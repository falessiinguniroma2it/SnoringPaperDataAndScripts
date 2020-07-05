package model;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;


public class TicketPool {
    private final static String TICKET_KEY = "total";
    private final static String ISSUES_KEY = "issues";
    private final static String ID_KEY = "key";

    private HashSet<String> ticketIds;
    private String projectName;
    private OkHttpClient client;
    private ObjectMapper mapper;
    private int ticketCount;

    public TicketPool(String projectName) throws IOException {
        ticketIds = new HashSet<>();
        this.projectName = projectName;
        this.client = new OkHttpClient();
        this.mapper = new ObjectMapper();
        setTicketCount();
        fetchTicketNames();
    }
    public TicketPool(String fileName, boolean isFile) throws FileNotFoundException {
        File f = new File(fileName);
        Scanner fScanner = new Scanner(f);
        ticketIds = new HashSet<>();
        while(fScanner.hasNextLine()) {
            String line = fScanner.nextLine().trim();
            System.out.println(line);
            this.ticketIds.add(line) ;
        }
        fScanner.close();
        System.out.println("SIZE " + ticketIds.size());
    }

    private void fetchTicketNames() throws IOException {
        int PAGE_SIZE = 200;
        System.out.println("Total tickets: " + ticketCount);
        int calls = 1 + (this.ticketCount / PAGE_SIZE);
        for (int i = 0; i < calls; i++) {
            int start = PAGE_SIZE * i;
            System.out.println("Page starting at: " + start);
            String url = "https://issues.apache.org/jira/rest/api/2/search?jql=project=" + projectName + " and issuetype = Bug and Resolution = fixed&maxResults=" + PAGE_SIZE + "&startAt=" + start;
            Map<String, Object> map = get(url);
            List<Map> issues = (List<Map>) map.get(ISSUES_KEY);
            for (Map issue : issues) {
                String issueId = (String) issue.get(ID_KEY);
                ticketIds.add(issueId);
            }
        }
    }

    private void setTicketCount() throws IOException {
        String url = "https://issues.apache.org/jira/rest/api/2/search?jql=project=" + projectName +" and issuetype = Bug and Resolution = fixed&maxResults=0";
        Map<String, Object> map = get(url);
        this.ticketCount = (int) map.get(TICKET_KEY);
    }

    private Map<String, Object> get(String url) throws IOException {
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


    public List<String> getAllTicketIds() {
        List<String> ticketList = new ArrayList<>();
        ticketList.addAll(ticketIds);
        return ticketList;
    }

    public void writeToFile(String fName) throws IOException {
        List<String> tickets = getAllTicketIds();
        File file = new File(fName);
        file.createNewFile();
        FileWriter fw = new FileWriter(file);
        for(String t : tickets) {
            fw.write(t + "\n");
        }
        fw.close();
    }
}
