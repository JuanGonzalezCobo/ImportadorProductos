package app.threads;

import java.util.function.Function;

public class WriteOnFileThread implements Runnable {

    private final Thread THREAD;
    private final Function<Object[], Void> FUNCTION;
    private final Object[] PARAM;

    public WriteOnFileThread(Object[] param, Function<Object[], Void> function) {
        this.THREAD = new Thread(this);
        this.PARAM = param;
        this.FUNCTION = function;
    }

    @Override
    public void run() {
        FUNCTION.apply(PARAM);
    }


    public void start() {
        this.THREAD.start();
    }

    public void join() throws InterruptedException {
        this.THREAD.join();
    }
}
