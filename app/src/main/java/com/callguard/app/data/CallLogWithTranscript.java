package com.callguard.app.data;

import androidx.room.Embedded;
import androidx.room.Relation;

public class CallLogWithTranscript {
    @Embedded
    public CallLog callLog;

    @Relation(
        parentColumn = "id",
        entityColumn = "callLogId"
    )
    public Transcript transcript;
}
