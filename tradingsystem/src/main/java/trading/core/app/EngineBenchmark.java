package core.app;

/**
 * Tiny benchmark harness: run the engine for N seconds and print periodic metrics.
 */
public final class EngineBenchmark {

    private EngineBenchmark() {
    }

    public static void main(String[] args) {
        String seconds = args.length > 0 ? args[0] : "30";
        TradingEngine.main(new String[] { seconds });
    }
}

