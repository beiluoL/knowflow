package com.knowflow.interview.contract;

/** Owner-scoped recent activity projection; preserves the existing three-row window. */
public interface RecentInterviewActivityQuery {
    /** Reads only the supplied authenticated owner's latest three records. */
    int boundedCount(long ownerId);
}
