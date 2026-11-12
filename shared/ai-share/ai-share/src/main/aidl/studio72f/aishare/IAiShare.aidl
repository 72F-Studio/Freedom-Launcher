package studio72f.aishare;

import studio72f.aishare.IAiShareCallback;

// One 72F app asking another to run a model it holds. Every value travels in a Bundle so a newer app and an older
// one can still talk: unknown keys are ignored, and API_VERSION says what the host understands.
interface IAiShare {
    int apiVersion();

    // The models this app holds and can run: a Bundle with "models", an ArrayList of model ids (sha256).
    Bundle describe();

    // Starts a request (see Protocol for the keys). image is a read end of a pipe or null. Returns the request id;
    // the answer arrives on callback, streamed. Throws SecurityException if the caller isn't a 72F app.
    String start(in Bundle request, in ParcelFileDescriptor image, IAiShareCallback callback);

    void cancel(String requestId);
}
