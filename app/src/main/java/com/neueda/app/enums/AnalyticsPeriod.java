package com.neueda.app.enums;

/**
 * The time window the admin analytics cover, and how it is cut into chart points. Every period
 * except YEAR_TO_DATE is compared with the window of the same length just before it.
 */
public enum AnalyticsPeriod {
    TODAY(AnalyticsBucketSize.HOUR),
    LAST_7_DAYS(AnalyticsBucketSize.DAY),
    LAST_30_DAYS(AnalyticsBucketSize.DAY),
    LAST_90_DAYS(AnalyticsBucketSize.DAY),
    YEAR_TO_DATE(AnalyticsBucketSize.WEEK);

    private final AnalyticsBucketSize bucketSize;

    AnalyticsPeriod(AnalyticsBucketSize bucketSize) {
        this.bucketSize = bucketSize;
    }

    public AnalyticsBucketSize bucketSize() {
        return bucketSize;
    }
}
