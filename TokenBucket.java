/**
Didi Jungreisz - 304993553
Barak Gelman - 204038756
**/

class TokenBucket {

    private static final double MIN_WAIT_S = 0.005;
    private static final double MAX_WAIT_S = 0.1;
    private double bandwidth;
    private final double bucketSize;
    private double bucket;
    private long lastFill;

    /**
     * Create a new TokenBicket
     *
     * @param bandwidth the maximum bandwidth in tokens/s
     * @param bucketSize the maximum burst size
     */
    public TokenBucket(double bandwidth, double bucketSize) {
        this.bandwidth = bandwidth;
        this.bucketSize = bucketSize;

        bucket = 0;
        lastFill = System.currentTimeMillis();
    }

    /**
     * Update the bucket fill level.
     */
    private synchronized void updateBucket() {
        long time = System.currentTimeMillis();

        if (bandwidth == 0) {
            bucket = bucketSize;
        } else {
            bucket += bandwidth * (time - lastFill) / 1000;
            if (bucket > bucketSize) {
                bucket = bucketSize;
            }
        }
        lastFill = time;
    }

    /**
     * Wait until this many tokens may be send/received.
     *
     * @param tokens number if tokens
     */
    @SuppressWarnings("SleepWhileInLoop")
    public void waitForTokens(double tokens) {
        while (true) {
            updateBucket();
            synchronized (this) {
                if (bucket >= 0) {
                    break;
                }
            }
            double waitTime = -bucket / bandwidth;
            if (waitTime < MIN_WAIT_S || waitTime > MAX_WAIT_S) {
                waitTime = MIN_WAIT_S;
            }

            try {
                Thread.sleep((long) (waitTime * 1000));
            } catch (InterruptedException ex) {
                System.err.println("Token bucket failed!");
            }
        }
        synchronized (this) {
            bucket -= tokens;
        }
    }

    /**
     * Check if the number of tokens may be send/received right now.
     *
     * @param tokens number of tokens
     * @return send/received allowed right now?
     */
    public boolean tokensAvailable(double tokens) {
        updateBucket();
        synchronized (this) {
            if (bucket >= 0) {
                bucket -= tokens;
                return true;
            } else {
                return false;
            }
        }
    }

    /**
     * Set the maximum bandwidth.
     *
     * @param bandwidth the bandwidth in tokens/s
     */
    public synchronized void setBandwidth(double bandwidth) {
        this.bandwidth = bandwidth;
    }
}
