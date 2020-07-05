package model;

import java.util.Scanner;

public class Diff {

    private String fileName;
    private String diff;
    private int linesAdded;
    private int linesRemoved;

    public Diff(String fName, String diffOutput) {
        this.fileName = fName;
        this.diff = diffOutput;
        computeLOC();
    }
    private void computeLOC() {
        Scanner scanner = new Scanner(diff);
        for(int i = 0; i < 4; i++) {
            //Discard the first 4 lines of metadata
            if (scanner.hasNextLine()) scanner.nextLine();
        }
        while(scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if(line.charAt(0) == '+') linesAdded++;
            else if(line.charAt(0) == '-') linesRemoved++;
        }
        scanner.close();
    }

    public int getTouched() {
        return linesAdded + linesRemoved;
    }

    public int getAdded() {
        return linesAdded;
    }

    public int getChurn() {
        return linesAdded - linesRemoved;
    }
}
