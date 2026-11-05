package com.knowflow.exam.contract;

/** Owner-scoped recent activity projection; preserves the existing three-row window. */
public interface RecentExamActivityQuery {
    /** Reads only the supplied authenticated owner's latest three records. */
    java.util.List<String> statuses(long ownerId);
}
