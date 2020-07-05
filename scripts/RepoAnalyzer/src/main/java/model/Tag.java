package model;

public class Tag implements Comparable<Tag> {

    public String tag;
    public int epoch;

    public Tag(String tag, int epoch) {
        this.tag = tag;
        this.epoch = epoch;
    }

    @Override
    public int compareTo(Tag other) {
        return this.epoch - other.epoch;
    }
}
