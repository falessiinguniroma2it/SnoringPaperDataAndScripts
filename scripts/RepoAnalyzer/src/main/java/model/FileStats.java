package model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class FileStats {
    public HashSet<String> injected;
    HashSet<String> sleeping;
    HashSet<String> startedSleeping;
    HashSet<String> defective;
    public HashSet<String> fixed;

    public FileStats(){
        injected = new HashSet<>();
        sleeping = new HashSet<>();
        startedSleeping = new HashSet<>();
        defective = new HashSet<>();
        fixed = new HashSet<>();
    }

    public void addInjection(String bug) {
        injected.add(bug);
    }

    public void startedSleeping(String bug) {
        startedSleeping.add(bug);
    }

    public void addSleeping(String bug) {
        sleeping.add(bug);
    }

    public void setDefective() {
        defective.addAll(injected);
        defective.addAll(sleeping);
    }

    public void prune(HashSet<String> pruned) {
        List<String> sleepingRemovelist = new ArrayList<>();
        for(String report : sleeping) {
            if(pruned.contains(report)) continue;
            //We must do pruning here
            sleepingRemovelist.add(report); //Remove the last instance when was sleeping
            if(startedSleeping.contains(report)) startedSleeping.remove(report); //If bug slept for just 1 release, remove it
            pruned.add(report);
        }
        for(String report : sleepingRemovelist) {
            sleeping.remove(report);
        }
    }

    public void fix(String bug) {
        fixed.add(bug);
    }
}
