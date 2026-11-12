package studio72f.aishare;

// Answers come back on this, in order: any number of onText chunks, then exactly one onDone or onError.
oneway interface IAiShareCallback {
    void onText(String chunk);
    // "text": the whole answer; "toolArgs": the chosen tool's arguments as JSON, or absent.
    void onDone(in Bundle result);
    // code is one of Protocol's AIS- codes; never a raw message.
    void onError(String code);
}
